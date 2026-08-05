package com.pystudio.core.packages

import android.util.Log
import java.io.BufferedReader
import java.io.File
import java.io.IOException
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest
import java.util.zip.ZipInputStream
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Callback for download progress events.
 * [packageName] identifies the package being downloaded.
 * [bytesDownloaded] is the cumulative count of bytes received so far.
 * [totalBytes] is the Content-Length reported by the server (-1 if unknown).
 */
fun interface ProgressListener {
    fun onProgress(packageName: String, bytesDownloaded: Long, totalBytes: Long)
}

class PackageInstallService(
    private val cache: UnifiedCacheService,
    private val securityGate: SecurityGateService,
    private val environmentService: EnvironmentService,
    private val pythonHome: String,
    private val progressListener: ProgressListener? = null
) {

    companion object {
        private const val TAG = "PackageInstall"
        private const val CONNECT_TIMEOUT_INDEX = 5_000
        private const val READ_TIMEOUT_INDEX = 5_000
        private const val CONNECT_TIMEOUT_DOWNLOAD = 10_000
        private const val READ_TIMEOUT_DOWNLOAD = 30_000
        private const val DOWNLOAD_BUFFER_SIZE = 8192
        private const val MAX_DOWNLOAD_ATTEMPTS = 3
    }

    suspend fun install(plan: InstallPlan, envId: String): InstallOutcome = withContext(Dispatchers.IO) {
        val envPath = environmentService.getEnvPath(envId)
        val sitePackages = File(envPath, "site-packages")

        try {
            for (pkg in plan.toAdd) {
                Log.i(TAG, "Installing ${pkg.name}==${pkg.version}")

                var wheel = cache.checkL3Wheel(pkg.name, pkg.version)
                if (wheel == null) {
                    wheel = downloadOrBuildWheel(pkg)
                    if (wheel != null) {
                        val sigFile = File(wheel.absolutePath + ".asc")
                        val artifactRef = ArtifactRef(
                            name = pkg.name,
                            version = pkg.version,
                            fileAbsolutePath = wheel.absolutePath,
                            sha256 = pkg.sha256,
                            signaturePath = if (sigFile.exists()) sigFile.absolutePath else null,
                            isLocal = pkg.source == "local_build"
                        )
                        val verification = securityGate.verify(artifactRef)
                        if (verification == VerificationResult.FAILED) {
                            return@withContext InstallOutcome.Failure(
                                "SIG_VERIFICATION_FAILED",
                                "Signature verification failed for ${pkg.name}"
                            )
                        }
                        cache.storeL3Wheel(wheel)
                    } else {
                        return@withContext InstallOutcome.Failure(
                            "DOWNLOAD_FAILED",
                            "Could not acquire wheel for ${pkg.name}: " +
                                "neither PyPI download nor local pip build succeeded"
                        )
                    }
                }

                val extractResult = installWheel(wheel, sitePackages.absolutePath, pkg)
                if (extractResult != null) {
                    return@withContext InstallOutcome.Failure("INSTALL_FAILED", extractResult)
                }
            }
        } catch (e: IOException) {
            return@withContext InstallOutcome.Failure(
                "NETWORK_ERROR",
                "Network error during installation: ${e.message ?: "Unknown network error"}"
            )
        }

        return@withContext InstallOutcome.Success(plan, lockfileChanged = true)
    }

    suspend fun uninstall(packageName: String, envId: String): InstallOutcome = withContext(Dispatchers.IO) {
        val envPath = environmentService.getEnvPath(envId)
        val sitePackages = File(envPath, "site-packages")

        if (!sitePackages.exists()) {
            return@withContext InstallOutcome.Failure(
                "ENV_NOT_FOUND",
                "site-packages directory does not exist for environment $envId"
            )
        }

        var removed = false

        // Remove the top-level package directory (e.g. "requests/")
        val pkgDir = File(sitePackages, packageName)
        if (pkgDir.exists()) {
            pkgDir.deleteRecursively()
            removed = true
        }

        // Also remove any .dist-info directories that match this package
        val distInfoDirs = sitePackages.listFiles { file ->
            file.isDirectory && file.name.startsWith("$packageName-", ignoreCase = true)
                && file.name.endsWith(".dist-info")
        }
        distInfoDirs?.forEach { dir ->
            dir.deleteRecursively()
            removed = true
        }

        if (removed) {
            Log.i(TAG, "Uninstalled $packageName from $envId")
        } else {
            Log.w(TAG, "Package $packageName was not found in $envId, nothing to uninstall")
        }

        InstallOutcome.Success(
            InstallPlan(
                emptyList(),
                emptyList(),
                listOf(PackageSummary(packageName, "unknown", "unknown", 0L, false))
            ),
            lockfileChanged = removed
        )
    }

    /**
     * Attempts to acquire a wheel file for [pkg]:
     * 1. Download a pre-built wheel from PyPI simple index (up to [MAX_DOWNLOAD_ATTEMPTS] retries)
     * 2. If no compatible wheel exists, fall back to `pip wheel --no-deps`
     * 3. Returns null if both strategies fail
     */
    private fun downloadOrBuildWheel(pkg: PackageSummary): File? {
        var lastException: IOException? = null
        val wheelTag = if (pkg.wheelTag.isNotEmpty()) pkg.wheelTag else "none-any"
        Log.i(TAG, "Attempting to download wheel for ${pkg.name} (${pkg.version}) with tag $wheelTag")

        var wheelFile: File? = null
        for (attempt in 1..MAX_DOWNLOAD_ATTEMPTS) {
            try {
                wheelFile = tryDownload(pkg, wheelTag)
                if (wheelFile != null) break
            } catch (e: IOException) {
                lastException = e
                Log.w(TAG, "Download attempt $attempt/$MAX_DOWNLOAD_ATTEMPTS failed: ${e.message}")
            }
        }

        if (wheelFile != null) return wheelFile

        Log.i(TAG, "No wheel available from PyPI, attempting to build with pip wheel --no-deps...")
        try {
            wheelFile = tryBuildWheel(pkg)
            if (wheelFile != null) return wheelFile
        } catch (e: Exception) {
            Log.e(TAG, "pip wheel build failed for ${pkg.name}==${pkg.version}", e)
        }

        if (lastException != null) {
            throw lastException
        }

        return null
    }

    /**
     * Queries the PyPI simple index for [pkg], parses the HTML to find a matching .whl link,
     * then downloads it.
     */
    private fun tryDownload(pkg: PackageSummary, wheelTag: String): File? {
        val url = URL("https://pypi.org/simple/${pkg.name}/")
        val conn = url.openConnection() as HttpURLConnection
        try {
            conn.connectTimeout = CONNECT_TIMEOUT_INDEX
            conn.readTimeout = READ_TIMEOUT_INDEX
            conn.setRequestProperty("Accept", "text/html")

            val responseCode = conn.responseCode
            if (responseCode != HttpURLConnection.HTTP_OK) {
                Log.w(TAG, "PyPI simple index returned HTTP $responseCode for ${pkg.name}")
                return null
            }

            val html = conn.inputStream.bufferedReader().use { it.readText() }
            val regex = "<a href=\"([^\"]+)\"[^>]*>([^<]+\\.whl)</a>".toRegex(RegexOption.IGNORE_CASE)

            val match = regex.findAll(html).find { result ->
                val filename = result.groupValues[2]
                filename.contains(pkg.version) && filename.contains(wheelTag)
            } ?: return null

            val downloadUrlStr = match.groupValues[1]
            val downloadUrl = URL(url, downloadUrlStr)

            return downloadWheelFile(downloadUrl, pkg)
        } finally {
            conn.disconnect()
        }
    }

    /**
     * Downloads the wheel binary from [url], computes SHA-256 on-the-fly, validates
     * against the lockfile digest, and emits progress events via [progressListener].
     */
    private fun downloadWheelFile(url: URL, pkg: PackageSummary): File {
        val conn = url.openConnection() as HttpURLConnection
        try {
            conn.connectTimeout = CONNECT_TIMEOUT_DOWNLOAD
            conn.readTimeout = READ_TIMEOUT_DOWNLOAD

            val responseCode = conn.responseCode
            if (responseCode != HttpURLConnection.HTTP_OK) {
                throw IOException(
                    "Failed to download wheel for ${pkg.name}, HTTP $responseCode from ${url.host}"
                )
            }

            val contentLength = conn.contentLengthLong
            val wheelTag = if (pkg.wheelTag.isNotEmpty()) pkg.wheelTag else "unknown"
            val wheelFile = File(cache.l3CacheDir, "${pkg.name}-${pkg.version}-$wheelTag.whl")

            val messageDigest = MessageDigest.getInstance("SHA-256")

            conn.inputStream.use { input ->
                wheelFile.outputStream().use { output ->
                    val buffer = ByteArray(DOWNLOAD_BUFFER_SIZE)
                    var totalBytesRead = 0L
                    var bytesRead: Int
                    while (input.read(buffer).also { bytesRead = it } != -1) {
                        output.write(buffer, 0, bytesRead)
                        messageDigest.update(buffer, 0, bytesRead)
                        totalBytesRead += bytesRead
                        progressListener?.onProgress(pkg.name, totalBytesRead, contentLength)
                    }
                }
            }

            val calculatedSha = messageDigest.digest().joinToString("") { "%02x".format(it) }
            if (pkg.sha256.isNotEmpty() && calculatedSha != pkg.sha256) {
                wheelFile.delete()
                throw IOException(
                    "SHA-256 mismatch for ${pkg.name}: expected ${pkg.sha256}, got $calculatedSha"
                )
            }

            Log.i(TAG, "Downloaded wheel for ${pkg.name}==${pkg.version} (${wheelFile.length()} bytes)")
            return wheelFile
        } finally {
            conn.disconnect()
        }
    }

    /**
     * Falls back to building a wheel locally via `pip wheel --no-deps`.
     * Captures stderr to provide actionable diagnostics on failure.
     */
    private fun tryBuildWheel(pkg: PackageSummary): File? {
        val command = listOf(
            File(pythonHome, "bin/python").absolutePath,
            "-m", "pip", "wheel", "${pkg.name}==${pkg.version}",
            "--no-deps",
            "--platform", "android_21_aarch64",
            "--wheel-dir", cache.l3CacheDir.absolutePath
        )
        val pb = ProcessBuilder(command)
            .directory(cache.l3CacheDir)
            .redirectErrorStream(false)

        val process = pb.start()

        // Drain stdout to prevent blocking
        val stdoutReader = BufferedReader(InputStreamReader(process.inputStream))
        val stderrReader = BufferedReader(InputStreamReader(process.errorStream))

        val stdoutLines = mutableListOf<String>()
        val stderrLines = mutableListOf<String>()

        // Read both streams to prevent deadlock on process buffer
        val stderrThread = Thread {
            stderrReader.useLines { lines -> lines.forEach { stderrLines.add(it) } }
        }
        stderrThread.start()
        stdoutReader.useLines { lines -> lines.forEach { stdoutLines.add(it) } }
        stderrThread.join()

        val exitCode = process.waitFor()

        if (exitCode != 0) {
            val errorOutput = stderrLines.joinToString("\n")
            Log.e(TAG, "pip wheel failed (exit code $exitCode) for ${pkg.name}==${pkg.version}:\n$errorOutput")
            throw IOException(
                "pip wheel exited with code $exitCode for ${pkg.name}==${pkg.version}: " +
                    stderrLines.lastOrNull().orEmpty()
            )
        }

        return cache.l3CacheDir.listFiles { _, name ->
            name.startsWith(pkg.name, ignoreCase = true) && name.endsWith(".whl")
        }?.firstOrNull()
    }

    /**
     * Extracts a .whl file (ZIP format) directly into [sitePackagesPath].
     * After extraction, ensures a .dist-info directory exists with METADATA and RECORD.
     * Returns null on success, or a specific error message on failure.
     */
    private fun installWheel(wheelFile: File, sitePackagesPath: String, pkg: PackageSummary): String? {
        return try {
            val sitePackagesDir = File(sitePackagesPath)
            if (!sitePackagesDir.exists()) sitePackagesDir.mkdirs()

            val extractedPaths = mutableListOf<String>()

            ZipInputStream(wheelFile.inputStream()).use { zis ->
                var entry = zis.nextEntry
                while (entry != null) {
                    val destFile = File(sitePackagesDir, entry.name)
                    // Zip-slip prevention: reject entries that escape site-packages
                    if (!destFile.canonicalPath.startsWith(sitePackagesDir.canonicalPath)) {
                        throw SecurityException("Zip path traversal detected: ${entry.name}")
                    }
                    if (entry.isDirectory) {
                        destFile.mkdirs()
                    } else {
                        destFile.parentFile?.mkdirs()
                        destFile.outputStream().use { os ->
                            zis.copyTo(os)
                        }
                        extractedPaths.add(entry.name)
                    }
                    zis.closeEntry()
                    entry = zis.nextEntry
                }
            }

            // Ensure .dist-info directory exists with METADATA and RECORD
            ensureDistInfo(sitePackagesDir, pkg, extractedPaths)

            Log.i(TAG, "Installed ${pkg.name}==${pkg.version} " +
                "(${extractedPaths.size} files extracted to $sitePackagesPath)")
            null // null indicates success
        } catch (e: SecurityException) {
            val msg = "Security error extracting wheel ${wheelFile.name}: ${e.message}"
            Log.e(TAG, msg, e)
            msg
        } catch (e: IOException) {
            val msg = "IO error extracting wheel ${wheelFile.name}: ${e.message}"
            Log.e(TAG, msg, e)
            msg
        } catch (e: Exception) {
            val msg = "Failed to extract wheel ${wheelFile.name}: ${e.message}"
            Log.e(TAG, msg, e)
            msg
        }
    }

    /**
     * Creates the `.dist-info` directory with METADATA and RECORD files
     * if not already present from the wheel archive.
     */
    private fun ensureDistInfo(
        sitePackagesDir: File,
        pkg: PackageSummary,
        extractedPaths: List<String>
    ) {
        val distInfoName = "${pkg.name}-${pkg.version}.dist-info"

        // Check if the wheel already contained a .dist-info directory
        val hasDistInfo = extractedPaths.any { path ->
            path.startsWith(distInfoName, ignoreCase = true)
        }

        val distInfoDir = File(sitePackagesDir, distInfoName)

        if (!hasDistInfo) {
            distInfoDir.mkdirs()
        }

        // Ensure METADATA file exists
        val metadataFile = File(distInfoDir, "METADATA")
        if (!metadataFile.exists()) {
            val metadataContent = buildString {
                appendLine("Metadata-Version: 2.1")
                appendLine("Name: ${pkg.name}")
                appendLine("Version: ${pkg.version}")
                if (pkg.source.isNotEmpty()) {
                    appendLine("Home-page: https://pypi.org/project/${pkg.name}/")
                }
                appendLine("Installer: pystudio")
            }
            metadataFile.writeText(metadataContent)
        }

        // Ensure RECORD file exists with all extracted paths
        val recordFile = File(distInfoDir, "RECORD")
        if (!recordFile.exists()) {
            val recordContent = buildString {
                for (path in extractedPaths) {
                    appendLine("$path,,")
                }
                appendLine("$distInfoName/METADATA,,")
                appendLine("$distInfoName/RECORD,,")
            }
            recordFile.writeText(recordContent)
        }

        // Write INSTALLER marker
        val installerFile = File(distInfoDir, "INSTALLER")
        if (!installerFile.exists()) {
            installerFile.writeText("pystudio\n")
        }
    }
}
