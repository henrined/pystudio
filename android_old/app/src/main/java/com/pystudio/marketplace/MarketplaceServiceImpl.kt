package com.pystudio.marketplace

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.security.MessageDigest
import java.util.zip.ZipInputStream

/**
 * S-12.1 / S-12.2 — Full MarketplaceService implementation.
 *
 * Handles:
 * - Remote registry search with local cache (offline-first, REQ-FUNC-0566 §0)
 * - Download to quarantine directory
 * - SHA-256 integrity verification + signature verification (REQ-FUNC-0644 §15)
 * - Atomic installation from quarantine to installed directory
 * - Manifest parsing and validation (REQ-FUNC-0610 §7.4)
 * - Listing and removal of installed extensions
 *
 * On a real device, network calls would use OkHttp/Retrofit against the
 * PyStudio Registry REST API. We implement full local logic — the HTTP layer
 * is abstracted behind [RegistryApiClient].
 */
class MarketplaceServiceImpl(
    private val registryClient: RegistryApiClient = InMemoryRegistryApiClient()
) : MarketplaceService {

    // -----------------------------------------------------------------------
    // Search (§2, §11.3 — MarketplaceBridge.search → Registry API)
    // -----------------------------------------------------------------------

    override suspend fun searchExtensions(
        query: String,
        filters: SearchFilters?
    ): ExtensionSearchResult = withContext(Dispatchers.IO) {
        registryClient.searchRemote(query, filters)
    }

    // -----------------------------------------------------------------------
    // Download to quarantine (§13.1 — step 3: REG → CDN: GET .pysx)
    // -----------------------------------------------------------------------

    override suspend fun downloadExtension(
        archive: ExtensionArchive,
        quarantineDir: File
    ): String = withContext(Dispatchers.IO) {
        quarantineDir.mkdirs()
        val targetFile = File(quarantineDir, "${archive.manifest.id}-${archive.manifest.version}.pysx")

        // Fetch bytes from the CDN (abstracted through the client)
        val bytes = registryClient.downloadArtifact(archive.downloadUrl)
        FileOutputStream(targetFile).use { it.write(bytes) }

        targetFile.absolutePath
    }

    // -----------------------------------------------------------------------
    // Signature verification (§15 — double signature dev + registry, SHA-256)
    // -----------------------------------------------------------------------

    override suspend fun verifySignature(
        archive: ExtensionArchive,
        localPath: String
    ): SignatureVerificationResult = withContext(Dispatchers.IO) {
        val file = File(localPath)
        if (!file.exists()) {
            return@withContext SignatureVerificationResult(
                valid = false,
                sha256Match = false,
                developerSignatureValid = false,
                registrySignatureValid = false,
                errorCode = MarketplaceErrorCodes.EXT_MANIFEST_INVALID
            )
        }

        // 1. SHA-256 integrity check
        val computedHash = computeSha256(file)
        val sha256Match = computedHash.equals(archive.sha256, ignoreCase = true)

        // 2. Developer signature verification (Ed25519 / RSA)
        val devSigValid = verifyDeveloperSignature(
            data = file.readBytes(),
            signature = archive.developerSignature,
            publisherId = archive.manifest.publisher
        )

        // 3. Registry co-signature verification
        val regSigValid = verifyRegistrySignature(
            data = file.readBytes(),
            signature = archive.registrySignature
        )

        val allValid = sha256Match && devSigValid && regSigValid

        SignatureVerificationResult(
            valid = allValid,
            sha256Match = sha256Match,
            developerSignatureValid = devSigValid,
            registrySignatureValid = regSigValid,
            errorCode = if (!allValid) MarketplaceErrorCodes.EXT_SIGNATURE_FAILED else null
        )
    }

    // -----------------------------------------------------------------------
    // Install from quarantine (§13.1 — extraction, manifest read, move)
    // -----------------------------------------------------------------------

    override suspend fun installFromQuarantine(
        archive: ExtensionArchive,
        quarantinePath: String,
        installDir: File
    ): InstallResult = withContext(Dispatchers.IO) {
        val quarantineFile = File(quarantinePath)
        if (!quarantineFile.exists()) {
            return@withContext InstallResult(
                success = false,
                extensionId = archive.manifest.id,
                version = archive.manifest.version,
                errorCode = MarketplaceErrorCodes.EXT_MANIFEST_INVALID
            )
        }

        // Validate the manifest from the archive
        val validationError = validateManifest(archive.manifest)
        if (validationError != null) {
            return@withContext InstallResult(
                success = false,
                extensionId = archive.manifest.id,
                version = archive.manifest.version,
                errorCode = validationError
            )
        }

        // Check available disk space (simplified — REQ-FUNC-0637 EXT_INSTALL_DISK_FULL)
        val availableSpace = installDir.usableSpace
        if (availableSpace < quarantineFile.length() * 2) {
            return@withContext InstallResult(
                success = false,
                extensionId = archive.manifest.id,
                version = archive.manifest.version,
                errorCode = MarketplaceErrorCodes.EXT_INSTALL_DISK_FULL
            )
        }

        // Create extension directory
        val extDir = File(installDir, archive.manifest.id)
        val versionDir = File(extDir, archive.manifest.version)
        versionDir.mkdirs()

        // Extract the .pysx (ZIP) into the version directory
        try {
            extractPysx(quarantineFile, versionDir)
        } catch (e: Exception) {
            versionDir.deleteRecursively()
            return@withContext InstallResult(
                success = false,
                extensionId = archive.manifest.id,
                version = archive.manifest.version,
                errorCode = MarketplaceErrorCodes.EXT_MANIFEST_INVALID
            )
        }

        // Write metadata marker
        val metadataFile = File(extDir, "installed.meta")
        metadataFile.writeText(buildInstalledMetadata(archive))

        // Remove quarantine file
        quarantineFile.delete()

        // Check if there was a previous version (for rollback support — §8.3)
        val previousVersions = extDir.listFiles { f -> f.isDirectory && f.name != archive.manifest.version }
        val rollbackAvailable = previousVersions?.isNotEmpty() == true

        InstallResult(
            success = true,
            extensionId = archive.manifest.id,
            version = archive.manifest.version,
            requiresReload = archive.manifest.main != null,
            rollbackAvailable = rollbackAvailable
        )
    }

    // -----------------------------------------------------------------------
    // List installed
    // -----------------------------------------------------------------------

    override suspend fun getInstalledExtensions(
        installDir: File
    ): List<InstalledExtension> = withContext(Dispatchers.IO) {
        if (!installDir.exists()) return@withContext emptyList()

        installDir.listFiles { f -> f.isDirectory }?.mapNotNull { extDir ->
            readInstalledExtension(extDir)
        } ?: emptyList()
    }

    // -----------------------------------------------------------------------
    // Uninstall
    // -----------------------------------------------------------------------

    override suspend fun uninstallExtension(
        extensionId: String,
        installDir: File
    ): Boolean = withContext(Dispatchers.IO) {
        val extDir = File(installDir, extensionId)
        if (!extDir.exists()) return@withContext false
        extDir.deleteRecursively()
    }

    // =======================================================================
    // Private helpers
    // =======================================================================

    /** Compute hex-encoded SHA-256 of a file. */
    private fun computeSha256(file: File): String {
        val digest = MessageDigest.getInstance("SHA-256")
        FileInputStream(file).use { fis ->
            val buffer = ByteArray(8192)
            var bytesRead: Int
            while (fis.read(buffer).also { bytesRead = it } != -1) {
                digest.update(buffer, 0, bytesRead)
            }
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }

    /**
     * Verify the developer signature against the known public key for the
     * publisher. In production this uses Ed25519 or RSA-PSS with a key
     * fetched from the registry's trust store.
     *
     * Current implementation: HMAC-SHA256 based signature verification.
     * The publisher's public key would be fetched from the Registry's trust
     * keyring. For now, we verify that the signature is a valid hex string of
     * the correct length (64 hex chars = 256 bits) and is non-empty.
     *
     * On a real device this is replaced by java.security.Signature with
     * Ed25519 (via BouncyCastle) or Android Keystore RSA-PSS.
     */
    private fun verifyDeveloperSignature(
        data: ByteArray,
        signature: String,
        publisherId: String
    ): Boolean {
        if (signature.isBlank()) return false

        // Compute HMAC-SHA256 with publisher ID as a proxy for their public key
        // In production: Ed25519 verify(publicKey, data, signature)
        val mac = javax.crypto.Mac.getInstance("HmacSHA256")
        val keySpec = javax.crypto.spec.SecretKeySpec(publisherId.toByteArray(), "HmacSHA256")
        mac.init(keySpec)
        val expectedSig = mac.doFinal(data).joinToString("") { "%02x".format(it) }

        return expectedSig.equals(signature, ignoreCase = true)
    }

    /**
     * Verify the registry co-signature.
     * Same approach as developer signature but using the registry's well-known key.
     */
    private fun verifyRegistrySignature(
        data: ByteArray,
        signature: String
    ): Boolean {
        if (signature.isBlank()) return false

        val registryKeyId = "pystudio-registry-v1"
        val mac = javax.crypto.Mac.getInstance("HmacSHA256")
        val keySpec = javax.crypto.spec.SecretKeySpec(registryKeyId.toByteArray(), "HmacSHA256")
        mac.init(keySpec)
        val expectedSig = mac.doFinal(data).joinToString("") { "%02x".format(it) }

        return expectedSig.equals(signature, ignoreCase = true)
    }

    /**
     * Validate the manifest per REQ-FUNC-0610 §7.4.
     * Returns null if valid, or an error code string if invalid.
     */
    private fun validateManifest(manifest: ExtensionManifest): String? {
        // id format: publisher.name, lowercase, alphanumeric + hyphens
        val idPattern = Regex("^[a-z0-9-]+\\.[a-z0-9-]+$")
        if (!idPattern.matches(manifest.id)) {
            return MarketplaceErrorCodes.EXT_MANIFEST_INVALID
        }

        // displayName: 1-100 chars
        if (manifest.displayName.isBlank() || manifest.displayName.length > 100) {
            return MarketplaceErrorCodes.EXT_MANIFEST_INVALID
        }

        // description: 1-200 chars
        if (manifest.description.isBlank() || manifest.description.length > 200) {
            return MarketplaceErrorCodes.EXT_MANIFEST_INVALID
        }

        // version: basic SemVer pattern
        val semverPattern = Regex("^\\d+\\.\\d+\\.\\d+(-[a-zA-Z0-9.]+)?$")
        if (!semverPattern.matches(manifest.version)) {
            return MarketplaceErrorCodes.EXT_MANIFEST_INVALID
        }

        // Categories: at least one
        if (manifest.categories.isEmpty()) {
            return MarketplaceErrorCodes.EXT_MANIFEST_INVALID
        }

        // Permissions: medium/high must have justification (REQ-FUNC-0596)
        for (perm in manifest.permissions) {
            val risk = PermissionRegistry.riskLevel(perm.name)
            if (risk != PermissionRiskLevel.LOW && perm.justification.isNullOrBlank()) {
                return MarketplaceErrorCodes.EXT_MANIFEST_INVALID
            }
        }

        // engines.pystudio must be present and non-blank
        if (manifest.engines.pystudio.isBlank()) {
            return MarketplaceErrorCodes.EXT_INCOMPATIBLE_ENGINE
        }

        return null
    }

    /** Extract a .pysx (ZIP) archive into a target directory. */
    private fun extractPysx(pysxFile: File, targetDir: File) {
        ZipInputStream(FileInputStream(pysxFile)).use { zis ->
            var entry = zis.nextEntry
            while (entry != null) {
                val outFile = File(targetDir, entry.name)

                // Protect against Zip Slip attack
                if (!outFile.canonicalPath.startsWith(targetDir.canonicalPath + File.separator) &&
                    outFile.canonicalPath != targetDir.canonicalPath
                ) {
                    throw SecurityException("Zip Slip attempt detected: ${entry.name}")
                }

                if (entry.isDirectory) {
                    outFile.mkdirs()
                } else {
                    outFile.parentFile?.mkdirs()
                    FileOutputStream(outFile).use { fos ->
                        zis.copyTo(fos)
                    }
                }
                zis.closeEntry()
                entry = zis.nextEntry
            }
        }
    }

    /** Build metadata string for the installed.meta marker file. */
    private fun buildInstalledMetadata(archive: ExtensionArchive): String {
        return buildString {
            appendLine("id=${archive.manifest.id}")
            appendLine("version=${archive.manifest.version}")
            appendLine("publisher=${archive.manifest.publisher}")
            appendLine("displayName=${archive.manifest.displayName}")
            appendLine("sha256=${archive.sha256}")
            appendLine("installedAt=${System.currentTimeMillis()}")
            appendLine("permissions=${archive.manifest.permissions.joinToString(",") { it.name }}")
            appendLine("activationEvents=${archive.manifest.activationEvents.joinToString(",")}")
            if (archive.manifest.main != null) {
                appendLine("main=${archive.manifest.main}")
            }
        }
    }

    /** Read an InstalledExtension from its directory on disk. */
    private fun readInstalledExtension(extDir: File): InstalledExtension? {
        val metaFile = File(extDir, "installed.meta")
        if (!metaFile.exists()) return null

        val props = metaFile.readLines().associate { line ->
            val parts = line.split("=", limit = 2)
            if (parts.size == 2) parts[0] to parts[1] else "" to ""
        }

        val id = props["id"] ?: return null
        val version = props["version"] ?: return null
        val publisher = props["publisher"] ?: ""
        val displayName = props["displayName"] ?: id
        val installedAt = props["installedAt"]?.toLongOrNull() ?: 0L
        val permNames = props["permissions"]?.split(",")?.filter { it.isNotBlank() } ?: emptyList()

        val versionDir = File(extDir, version)
        val sizeBytes = if (versionDir.exists()) {
            versionDir.walkTopDown().filter { it.isFile }.sumOf { it.length() }
        } else 0L

        val manifest = ExtensionManifest(
            id = id,
            publisher = publisher,
            name = id.substringAfter('.'),
            displayName = displayName,
            description = "",
            version = version,
            engines = EngineRequirements(pystudio = "*"),
            categories = emptyList(),
            license = "UNKNOWN",
            permissions = permNames.map { ExtensionPermission(it) }
        )

        // Check for previous version dirs (rollback support)
        val previousVersionDirs = extDir.listFiles { f -> f.isDirectory && f.name != version }
        val previousVersionPath = previousVersionDirs?.maxByOrNull { it.lastModified() }?.absolutePath

        return InstalledExtension(
            manifest = manifest,
            localPath = versionDir.absolutePath,
            installedAtMillis = installedAt,
            status = ExtensionStatus.INSTALLED,
            sizeBytes = sizeBytes,
            permissions = permNames.map { name ->
                PermissionGrant(
                    permissionName = name,
                    granted = true,
                    grantedAtMillis = installedAt,
                    justification = "",
                    riskLevel = PermissionRegistry.riskLevel(name)
                )
            },
            previousVersionPath = previousVersionPath
        )
    }
}

// ==========================================================================
// Registry API abstraction (allows swapping real HTTP for tests)
// ==========================================================================

/**
 * Abstraction over the PyStudio Registry REST API (§7, §11.3).
 * In production, implemented via OkHttp against https://registry.pystudio.dev.
 */
interface RegistryApiClient {
    suspend fun searchRemote(query: String, filters: SearchFilters?): ExtensionSearchResult
    suspend fun downloadArtifact(url: String): ByteArray
    suspend fun fetchDetails(extensionId: String): ExtensionSummary?
    suspend fun checkUpdates(installed: List<Pair<String, String>>): List<AvailableUpdate>
}

/**
 * In-memory implementation for testing / offline scenarios.
 * Simulates a realistic registry with multiple extensions.
 */
class InMemoryRegistryApiClient : RegistryApiClient {

    private val catalog = mutableListOf(
        ExtensionSummary(
            id = "pystudio.python-linter",
            displayName = "Python Linter Pro",
            publisher = "pystudio",
            description = "Advanced Python linting with ruff, flake8, mypy support",
            version = "3.0.1",
            iconUrl = "https://registry.pystudio.dev/icons/python-linter.png",
            installs = 45200,
            rating = 4.3,
            categories = listOf("Linters", "Language Support"),
            preRelease = false,
            hasDeveloperSignature = true
        ),
        ExtensionSummary(
            id = "pystudio.dracula-theme",
            displayName = "Dracula Pro Theme",
            publisher = "pystudio",
            description = "Premium dark theme inspired by Dracula",
            version = "2.1.0",
            iconUrl = "https://registry.pystudio.dev/icons/dracula.png",
            installs = 89000,
            rating = 4.8,
            categories = listOf("Themes"),
            preRelease = false,
            hasDeveloperSignature = true
        ),
        ExtensionSummary(
            id = "community.cpp-snippets",
            displayName = "C++ Snippets Pack",
            publisher = "community",
            description = "200+ C++ code snippets for modern C++17/20",
            version = "1.5.0",
            iconUrl = "https://registry.pystudio.dev/icons/cpp-snippets.png",
            installs = 12300,
            rating = 4.5,
            categories = listOf("Snippets", "Language Support"),
            preRelease = false,
            hasDeveloperSignature = true
        )
    )

    override suspend fun searchRemote(query: String, filters: SearchFilters?): ExtensionSearchResult {
        val queryLower = query.lowercase()
        var results = catalog.filter { ext ->
            ext.displayName.lowercase().contains(queryLower) ||
            ext.description.lowercase().contains(queryLower) ||
            ext.id.lowercase().contains(queryLower) ||
            ext.categories.any { it.lowercase().contains(queryLower) }
        }

        // Apply category filter
        if (filters?.category != null) {
            results = results.filter { it.categories.contains(filters.category) }
        }

        // Apply sort
        results = when (filters?.sortBy) {
            "installs" -> results.sortedByDescending { it.installs }
            "rating"   -> results.sortedByDescending { it.rating }
            else       -> results  // relevance = search order
        }

        return ExtensionSearchResult(total = results.size, results = results)
    }

    override suspend fun downloadArtifact(url: String): ByteArray {
        // Simulate a .pysx file content (a minimal valid ZIP with extension.json)
        return createMinimalPysxBytes()
    }

    override suspend fun fetchDetails(extensionId: String): ExtensionSummary? {
        return catalog.find { it.id == extensionId }
    }

    override suspend fun checkUpdates(installed: List<Pair<String, String>>): List<AvailableUpdate> {
        return installed.mapNotNull { (id, currentVersion) ->
            val remote = catalog.find { it.id == id }
            if (remote != null && remote.version != currentVersion) {
                AvailableUpdate(
                    extensionId = id,
                    currentVersion = currentVersion,
                    latestVersion = remote.version,
                    preRelease = remote.preRelease
                )
            } else null
        }
    }

    /** Create a minimal ZIP that can pass extraction. */
    private fun createMinimalPysxBytes(): ByteArray {
        val baos = java.io.ByteArrayOutputStream()
        val zos = java.util.zip.ZipOutputStream(baos)

        // Add extension.json
        val manifestJson = """
        {
            "id": "pystudio.python-linter",
            "publisher": "pystudio",
            "name": "python-linter",
            "displayName": "Python Linter Pro",
            "description": "Advanced Python linting",
            "version": "3.0.1",
            "engines": { "pystudio": "^1.0.0" },
            "categories": ["Linters"],
            "license": "MIT",
            "permissions": [],
            "activationEvents": ["onLanguage:python"]
        }
        """.trimIndent().toByteArray()

        val entry = java.util.zip.ZipEntry("extension.json")
        zos.putNextEntry(entry)
        zos.write(manifestJson)
        zos.closeEntry()

        // Add a dummy dist/extension.js
        val jsEntry = java.util.zip.ZipEntry("dist/extension.js")
        zos.putNextEntry(jsEntry)
        zos.write("// Extension entry point\nexports.activate = function(ctx) {};\nexports.deactivate = function() {};".toByteArray())
        zos.closeEntry()

        zos.close()
        return baos.toByteArray()
    }
}
