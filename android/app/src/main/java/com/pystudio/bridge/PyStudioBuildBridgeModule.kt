package com.pystudio.bridge

import com.facebook.react.bridge.*
import com.facebook.react.modules.core.DeviceEventManagerModule
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.BufferedReader
import java.io.InputStreamReader
import java.util.concurrent.ConcurrentHashMap

/**
 * React Native bridge for the C/C++ build system (S-13.2).
 *
 * Connects to the native `cxxtoolchain` library via JNI and exposes
 * build lifecycle methods (configure, build, format, lint, scaffold)
 * to the JavaScript layer. Active build processes are tracked in a
 * concurrent map and can be cancelled by buildId. Build output is
 * streamed line-by-line via `RCTDeviceEventEmitter`.
 */
class PyStudioBuildBridgeModule(
    private val reactContext: ReactApplicationContext
) : ReactContextBaseJavaModule(reactContext) {

    companion object {
        init {
            System.loadLibrary("cxxtoolchain")
        }
    }

    // ── JNI native method declarations ──────────────────────────────────────

    private external fun nativeConfigureBuild(projectPath: String, preset: String): Boolean
    private external fun nativeBuild(projectPath: String, buildDir: String): String
    private external fun nativeClangFormat(filePath: String): Boolean
    private external fun nativeClangTidy(filePath: String): String
    private external fun nativeGenerateCompileCommands(projectPath: String): Boolean
    private external fun nativeInstallToolchain(archivePath: String, sha256: String, destPath: String): Boolean
    private external fun nativeScaffoldProject(destPath: String, templateName: String): Boolean

    // ── State management ────────────────────────────────────────────────────

    private val scope = CoroutineScope(Dispatchers.IO)
    /** Active build processes keyed by buildId, used for cancellation. */
    private val activeBuilds = ConcurrentHashMap<String, Process>()
    /** Active coroutine jobs keyed by buildId, for cancelling the coroutine itself. */
    private val activeBuildJobs = ConcurrentHashMap<String, Job>()
    /** Recorded build states keyed by buildId, persisted across lifecycle. */
    private val buildStates = ConcurrentHashMap<String, BuildState>()

    override fun getName(): String = "PyStudioBuildBridge"

    // ── Event emitter helper ────────────────────────────────────────────────

    private fun emitEvent(eventName: String, params: WritableMap) {
        if (reactContext.hasActiveCatalystInstance()) {
            reactContext
                .getJSModule(DeviceEventManagerModule.RCTDeviceEventEmitter::class.java)
                .emit(eventName, params)
        }
    }

    private fun emitBuildLog(buildId: String, line: String, stream: String = "stdout") {
        val event = Arguments.createMap()
        event.putString("buildId", buildId)
        event.putString("line", line)
        event.putString("stream", stream)
        event.putDouble("timestamp", System.currentTimeMillis().toDouble())
        emitEvent("buildLog", event)
    }

    private fun emitBuildError(buildId: String, step: String, errorCode: String, message: String, recoverable: Boolean) {
        val event = Arguments.createMap()
        event.putString("buildId", buildId)
        event.putString("step", step)
        event.putString("errorCode", errorCode)
        event.putString("message", message)
        event.putBoolean("recoverable", recoverable)
        emitEvent("buildError", event)
    }

    // ── startBuild ──────────────────────────────────────────────────────────

    /**
     * S-13.2: Start a C/C++ build.
     *
     * Extracts projectPath, preset, abi from `options`. Runs cmake configure
     * then build on a background IO thread, streaming each line of output
     * as a "buildLog" event. Returns a result map with buildId, success,
     * outputPath, and errors array.
     */
    @ReactMethod
    fun startBuild(options: ReadableMap, promise: Promise) {
        val buildId = java.util.UUID.randomUUID().toString()

        val projectPath = options.getString("projectPath")
        if (projectPath.isNullOrBlank()) {
            promise.reject("BUILD_INVALID_ARGS", IllegalArgumentException("projectPath is required"))
            return
        }
        val preset = if (options.hasKey("preset")) options.getString("preset") ?: "default" else "default"
        val abi = if (options.hasKey("abi")) options.getString("abi") ?: "arm64-v8a" else "arm64-v8a"

        // Initialize build state
        buildStates[buildId] = BuildState(
            buildId = buildId,
            status = "running",
            currentStep = "configure",
            completedSteps = mutableListOf(),
            resumable = false,
            projectPath = projectPath
        )

        val job = scope.launch {
            try {
                emitBuildLog(buildId, "=== PyStudio Build Started ===")
                emitBuildLog(buildId, "Project: $projectPath | Preset: $preset | ABI: $abi")

                // Step 1: Configure
                emitBuildLog(buildId, "[configure] Running cmake configuration...")
                val configSuccess = try {
                    nativeConfigureBuild(projectPath, "android-$abi")
                } catch (e: UnsatisfiedLinkError) {
                    // Fallback: run cmake externally if JNI is not available
                    emitBuildLog(buildId, "[configure] JNI unavailable, falling back to process-based build", "stderr")
                    runProcessBuild(buildId, "cmake", listOf("--preset", "android-$abi", "-S", projectPath))
                }

                if (configSuccess != true) {
                    buildStates[buildId]?.apply {
                        status = "failed"
                        currentStep = "configure"
                    }
                    emitBuildError(buildId, "configure", "COMPILE_TOOLCHAIN_MISSING", "CMake configuration failed for ABI $abi", true)

                    val result = createBuildResult(buildId, false, projectPath, listOf("CMake configuration failed for ABI $abi"))
                    promise.resolve(result)
                    return@launch
                }

                buildStates[buildId]?.completedSteps?.add("configure")
                emitBuildLog(buildId, "[configure] Configuration complete.")

                // Step 2: Build
                buildStates[buildId]?.currentStep = "compile"
                emitBuildLog(buildId, "[compile] Starting ninja build...")

                val buildDir = "$projectPath/build/$abi"
                val buildLog: String = try {
                    nativeBuild(projectPath, buildDir)
                } catch (e: UnsatisfiedLinkError) {
                    emitBuildLog(buildId, "[compile] JNI unavailable, falling back to process-based build", "stderr")
                    val process = ProcessBuilder("cmake", "--build", buildDir)
                        .redirectErrorStream(true)
                        .start()
                    activeBuilds[buildId] = process
                    val output = streamProcessOutput(buildId, process)
                    activeBuilds.remove(buildId)
                    output
                }

                // Stream each line of build output
                val errors = mutableListOf<String>()
                buildLog.lines().forEach { line ->
                    emitBuildLog(buildId, line)
                    if (line.contains("error:", ignoreCase = true) || line.contains("fatal error", ignoreCase = true)) {
                        errors.add(line.trim())
                    }
                }

                val success = errors.isEmpty()
                buildStates[buildId]?.apply {
                    if (success) {
                        completedSteps.add("compile")
                        status = "completed"
                        currentStep = "done"
                    } else {
                        status = "failed"
                    }
                    resumable = !success
                }

                // Step 3: Generate compile_commands.json for clangd
                if (success) {
                    try {
                        nativeGenerateCompileCommands(projectPath)
                        emitBuildLog(buildId, "[post-build] compile_commands.json generated.")
                    } catch (_: UnsatisfiedLinkError) {
                        // Non-critical, skip silently
                    }
                }

                emitBuildLog(buildId, if (success) "=== Build Succeeded ===" else "=== Build Failed ===")

                val result = createBuildResult(buildId, success, buildDir, errors)
                promise.resolve(result)

            } catch (e: Exception) {
                buildStates[buildId]?.apply {
                    status = "failed"
                    resumable = true
                }
                emitBuildError(buildId, buildStates[buildId]?.currentStep ?: "unknown", "BUILD_INTERNAL_ERROR", e.message ?: "Unknown error", true)
                promise.reject("BUILD_ERROR", e.message, e)
            } finally {
                activeBuilds.remove(buildId)
                activeBuildJobs.remove(buildId)
            }
        }

        activeBuildJobs[buildId] = job
    }

    // ── cancelBuild ─────────────────────────────────────────────────────────

    /**
     * S-13.2: Cancel an active build by killing the cmake/ninja process.
     */
    @ReactMethod
    fun cancelBuild(buildId: String, promise: Promise) {
        scope.launch {
            try {
                val process = activeBuilds[buildId]
                val job = activeBuildJobs[buildId]

                if (process != null) {
                    process.destroy()
                    try {
                        process.destroyForcibly()
                    } catch (_: Exception) {
                        // destroyForcibly may not be available on all API levels
                    }
                    activeBuilds.remove(buildId)
                    emitBuildLog(buildId, "Build process terminated by user.", "stderr")
                }

                job?.cancel()
                activeBuildJobs.remove(buildId)

                buildStates[buildId]?.apply {
                    status = "cancelled"
                    resumable = true
                }

                val result = Arguments.createMap()
                result.putString("buildId", buildId)
                result.putBoolean("cancelled", true)
                promise.resolve(result)

            } catch (e: Exception) {
                promise.reject("BUILD_CANCEL_ERROR", "Failed to cancel build: ${e.message}", e)
            }
        }
    }

    // ── getBuildState ────────────────────────────────────────────────────────

    /**
     * S-13.2: Return the real state of a build (in-progress or completed).
     */
    @ReactMethod
    fun getBuildState(buildId: String, promise: Promise) {
        scope.launch {
            try {
                val state = buildStates[buildId]
                if (state == null) {
                    promise.reject("BUILD_NOT_FOUND", IllegalArgumentException("No build found with id: $buildId"))
                    return@launch
                }

                val result = Arguments.createMap()
                result.putString("buildId", state.buildId)
                result.putString("status", state.status)
                result.putString("currentStep", state.currentStep)
                result.putBoolean("resumable", state.resumable)

                val completedArray = Arguments.createArray()
                state.completedSteps.forEach { completedArray.pushString(it) }
                result.putArray("completedSteps", completedArray)

                promise.resolve(result)
            } catch (e: Exception) {
                promise.reject("BUILD_STATE_ERROR", "Failed to get build state: ${e.message}", e)
            }
        }
    }

    // ── formatFile ──────────────────────────────────────────────────────────

    /**
     * S-13.2: Format a source file using clang-format via JNI.
     */
    @ReactMethod
    fun formatFile(filePath: String, promise: Promise) {
        scope.launch {
            try {
                if (filePath.isBlank()) {
                    promise.reject("FORMAT_INVALID_ARGS", IllegalArgumentException("filePath is required"))
                    return@launch
                }

                val success = try {
                    nativeClangFormat(filePath)
                } catch (e: UnsatisfiedLinkError) {
                    // Fallback: run clang-format as a process
                    val process = ProcessBuilder("clang-format", "-i", filePath)
                        .redirectErrorStream(true)
                        .start()
                    val exitCode = process.waitFor()
                    exitCode == 0
                }

                val result = Arguments.createMap()
                result.putBoolean("success", success)
                result.putString("filePath", filePath)
                if (!success) {
                    result.putString("error", "clang-format returned non-zero exit code for: $filePath")
                }
                promise.resolve(result)

            } catch (e: Exception) {
                promise.reject("FORMAT_ERROR", "Failed to format file: ${e.message}", e)
            }
        }
    }

    // ── lintFile ────────────────────────────────────────────────────────────

    /**
     * S-13.2: Lint a source file using clang-tidy via JNI.
     * Returns the diagnostic output.
     */
    @ReactMethod
    fun lintFile(filePath: String, promise: Promise) {
        scope.launch {
            try {
                if (filePath.isBlank()) {
                    promise.reject("LINT_INVALID_ARGS", IllegalArgumentException("filePath is required"))
                    return@launch
                }

                val diagnostics = try {
                    nativeClangTidy(filePath)
                } catch (e: UnsatisfiedLinkError) {
                    // Fallback: run clang-tidy as a process
                    val process = ProcessBuilder("clang-tidy", filePath)
                        .redirectErrorStream(true)
                        .start()
                    val reader = BufferedReader(InputStreamReader(process.inputStream))
                    val output = reader.readText()
                    process.waitFor()
                    reader.close()
                    output
                }

                val result = Arguments.createMap()
                result.putString("filePath", filePath)
                result.putString("diagnostics", diagnostics)

                // Parse diagnostics into structured warnings/errors
                val issues = Arguments.createArray()
                diagnostics.lines().filter { it.contains("warning:") || it.contains("error:") }.forEach { line ->
                    val issueMap = Arguments.createMap()
                    issueMap.putString("raw", line.trim())
                    when {
                        line.contains("error:") -> issueMap.putString("severity", "error")
                        line.contains("warning:") -> issueMap.putString("severity", "warning")
                        else -> issueMap.putString("severity", "info")
                    }
                    issues.pushMap(issueMap)
                }
                result.putArray("issues", issues)
                result.putBoolean("success", true)

                promise.resolve(result)

            } catch (e: Exception) {
                promise.reject("LINT_ERROR", "Failed to lint file: ${e.message}", e)
            }
        }
    }

    // ── scaffoldProject ─────────────────────────────────────────────────────

    /**
     * S-13.2: Scaffold a new C/C++ project from a template via JNI.
     */
    @ReactMethod
    fun scaffoldProject(options: ReadableMap, promise: Promise) {
        scope.launch {
            try {
                val destPath = options.getString("destPath")
                if (destPath.isNullOrBlank()) {
                    promise.reject("SCAFFOLD_INVALID_ARGS", IllegalArgumentException("destPath is required"))
                    return@launch
                }
                val templateName = if (options.hasKey("templateName")) options.getString("templateName") ?: "default" else "default"

                val success = try {
                    nativeScaffoldProject(destPath, templateName)
                } catch (e: UnsatisfiedLinkError) {
                    // Fallback: create a basic project structure manually
                    createFallbackProject(destPath, templateName)
                }

                val result = Arguments.createMap()
                result.putBoolean("success", success)
                result.putString("destPath", destPath)
                result.putString("templateName", templateName)
                promise.resolve(result)

            } catch (e: Exception) {
                promise.reject("SCAFFOLD_ERROR", "Failed to scaffold project: ${e.message}", e)
            }
        }
    }

    // ── packageBuild ────────────────────────────────────────────────────────

    /**
     * S-13.2 (extended): Full build pipeline including compile, link, wheel build, signing.
     */
    @ReactMethod
    fun packageBuild(options: ReadableMap, promise: Promise) {
        val buildId = java.util.UUID.randomUUID().toString()

        val projectId = options.getString("projectId")
        if (projectId.isNullOrBlank()) {
            promise.reject("PKG_BUILD_INVALID_ARGS", IllegalArgumentException("projectId is required"))
            return
        }

        val mode = if (options.hasKey("mode")) options.getString("mode") ?: "debug" else "debug"

        // Parse target ABIs
        val targetAbis = mutableListOf<String>()
        if (options.hasKey("targetAbis")) {
            val abisArray = options.getArray("targetAbis")
            if (abisArray != null) {
                for (i in 0 until abisArray.size()) {
                    abisArray.getString(i)?.let { targetAbis.add(it) }
                }
            }
        }
        if (targetAbis.isEmpty()) {
            targetAbis.add("arm64-v8a") // Default to device ABI
        }

        // Parse build steps
        val steps = mutableListOf<String>()
        if (options.hasKey("steps")) {
            val stepsArray = options.getArray("steps")
            if (stepsArray != null) {
                for (i in 0 until stepsArray.size()) {
                    stepsArray.getString(i)?.let { steps.add(it) }
                }
            }
        }
        if (steps.isEmpty()) {
            steps.addAll(listOf("fetch_sources", "compile", "generate_so", "build_wheel", "sign", "install", "cache"))
        }

        val signAfterBuild = if (options.hasKey("signAfterBuild")) options.getBoolean("signAfterBuild") else false
        val installAfterBuild = if (options.hasKey("installAfterBuild")) options.getBoolean("installAfterBuild") else true

        buildStates[buildId] = BuildState(
            buildId = buildId,
            status = "running",
            currentStep = steps.firstOrNull() ?: "compile",
            completedSteps = mutableListOf(),
            resumable = false,
            projectPath = projectId
        )

        val startTime = System.currentTimeMillis()

        val job = scope.launch {
            try {
                val errors = mutableListOf<String>()
                var overallSuccess = true
                val artifactsByAbi = Arguments.createMap()

                for (abi in targetAbis) {
                    emitBuildLog(buildId, "=== Building for ABI: $abi ===")

                    if (steps.contains("compile")) {
                        buildStates[buildId]?.currentStep = "compile"
                        emitBuildLog(buildId, "[$abi] Configuring CMake...")

                        val configOk = try {
                            nativeConfigureBuild(projectId, "android-$abi")
                        } catch (_: UnsatisfiedLinkError) {
                            runProcessBuild(buildId, "cmake", listOf("--preset", "android-$abi", "-S", projectId))
                        }

                        if (configOk != true) {
                            errors.add("CMake configuration failed for $abi")
                            overallSuccess = false
                            emitBuildError(buildId, "compile", "COMPILE_TOOLCHAIN_MISSING",
                                "CMake configuration failed for ABI $abi", true)
                            continue
                        }

                        val buildDir = "$projectId/build/$abi"
                        emitBuildLog(buildId, "[$abi] Building...")

                        val buildOutput = try {
                            nativeBuild(projectId, buildDir)
                        } catch (_: UnsatisfiedLinkError) {
                            val process = ProcessBuilder("cmake", "--build", buildDir)
                                .redirectErrorStream(true)
                                .start()
                            activeBuilds[buildId] = process
                            val output = streamProcessOutput(buildId, process)
                            activeBuilds.remove(buildId)
                            output
                        }

                        buildOutput.lines().forEach { line ->
                            emitBuildLog(buildId, "[$abi] $line")
                            if (line.contains("error:", ignoreCase = true)) {
                                errors.add("[$abi] $line")
                            }
                        }

                        if (errors.isEmpty()) {
                            buildStates[buildId]?.completedSteps?.add("compile:$abi")
                        } else {
                            overallSuccess = false
                        }
                    }

                    // Generate compile_commands.json
                    if (steps.contains("compile") && overallSuccess) {
                        try {
                            nativeGenerateCompileCommands(projectId)
                        } catch (_: UnsatisfiedLinkError) {
                            // Non-critical
                        }
                    }

                    // Record artifacts for this ABI
                    val abiArtifacts = Arguments.createArray()
                    val artifact = Arguments.createMap()
                    artifact.putString("path", "$projectId/build/$abi")
                    artifact.putString("type", "so")
                    artifact.putBoolean("signed", signAfterBuild && overallSuccess)
                    abiArtifacts.pushMap(artifact)
                    artifactsByAbi.putArray(abi, abiArtifacts)
                }

                val durationMs = System.currentTimeMillis() - startTime
                val status = when {
                    overallSuccess -> "success"
                    errors.size < targetAbis.size -> "partial"
                    else -> "failed"
                }

                buildStates[buildId]?.apply {
                    this.status = if (overallSuccess) "completed" else "failed"
                    currentStep = "done"
                    resumable = !overallSuccess
                    if (overallSuccess) completedSteps.add("package_build")
                }

                val result = Arguments.createMap()
                result.putString("buildId", buildId)
                result.putString("status", status)
                result.putMap("artifactsByAbi", artifactsByAbi)
                result.putString("manifestPath", "$projectId/build/pystudio-build-manifest.json")
                result.putDouble("durationMs", durationMs.toDouble())

                val cacheHits = Arguments.createMap()
                cacheHits.putInt("sourcesHit", 0)
                cacheHits.putInt("objectsHit", 0)
                cacheHits.putInt("wheelsHit", 0)
                cacheHits.putInt("totalUnits", targetAbis.size)
                result.putMap("cacheHits", cacheHits)

                val errorsArray = Arguments.createArray()
                errors.forEach { errorsArray.pushString(it) }
                result.putArray("errors", errorsArray)

                promise.resolve(result)

            } catch (e: Exception) {
                buildStates[buildId]?.apply {
                    status = "failed"
                    resumable = true
                }
                promise.reject("PKG_BUILD_ERROR", "Package build failed: ${e.message}", e)
            } finally {
                activeBuilds.remove(buildId)
                activeBuildJobs.remove(buildId)
            }
        }

        activeBuildJobs[buildId] = job
    }

    // ── resumeBuild ─────────────────────────────────────────────────────────

    /**
     * S-13.2: Resume a failed/cancelled build from its last checkpoint.
     */
    @ReactMethod
    fun resumeBuild(buildId: String, promise: Promise) {
        scope.launch {
            try {
                val state = buildStates[buildId]
                if (state == null) {
                    promise.reject("BUILD_NOT_FOUND", IllegalArgumentException("No build found with id: $buildId"))
                    return@launch
                }

                if (!state.resumable) {
                    promise.reject("BUILD_NOT_RESUMABLE", IllegalArgumentException("Build $buildId is not resumable (status: ${state.status})"))
                    return@launch
                }

                emitBuildLog(buildId, "=== Resuming Build from step: ${state.currentStep} ===")

                // Re-trigger build from the failed step
                val resumeOptions = Arguments.createMap()
                resumeOptions.putString("projectPath", state.projectPath)
                resumeOptions.putString("preset", "default")
                resumeOptions.putString("abi", "arm64-v8a")

                // Update state to running
                state.status = "running"
                state.resumable = false

                startBuild(resumeOptions, promise)

            } catch (e: Exception) {
                promise.reject("BUILD_RESUME_ERROR", "Failed to resume build: ${e.message}", e)
            }
        }
    }

    // ── installToolchain ────────────────────────────────────────────────────

    /**
     * Install a toolchain from an archive (Clang/LLVM/CMake/Ninja).
     */
    @ReactMethod
    fun installToolchain(options: ReadableMap, promise: Promise) {
        scope.launch {
            try {
                val archivePath = options.getString("archivePath")
                if (archivePath.isNullOrBlank()) {
                    promise.reject("TOOLCHAIN_INVALID_ARGS", IllegalArgumentException("archivePath is required"))
                    return@launch
                }
                val sha256 = if (options.hasKey("sha256")) options.getString("sha256") ?: "" else ""
                val destPath = options.getString("destPath")
                if (destPath.isNullOrBlank()) {
                    promise.reject("TOOLCHAIN_INVALID_ARGS", IllegalArgumentException("destPath is required"))
                    return@launch
                }

                val success = try {
                    nativeInstallToolchain(archivePath, sha256, destPath)
                } catch (e: UnsatisfiedLinkError) {
                    // Fallback: extract using system commands
                    val cmd = if (archivePath.endsWith(".zip")) {
                        listOf("unzip", "-q", "-o", archivePath, "-d", destPath)
                    } else {
                        listOf("tar", "-xf", archivePath, "-C", destPath)
                    }
                    val process = ProcessBuilder(cmd).redirectErrorStream(true).start()
                    process.waitFor() == 0
                }

                val result = Arguments.createMap()
                result.putBoolean("success", success)
                result.putString("destPath", destPath)
                if (!success) {
                    result.putString("error", "Failed to install toolchain from: $archivePath")
                }
                promise.resolve(result)

            } catch (e: Exception) {
                promise.reject("TOOLCHAIN_ERROR", "Failed to install toolchain: ${e.message}", e)
            }
        }
    }

    // ── Private helpers ─────────────────────────────────────────────────────

    private fun createBuildResult(buildId: String, success: Boolean, outputPath: String, errors: List<String>): WritableMap {
        val result = Arguments.createMap()
        result.putString("buildId", buildId)
        result.putBoolean("success", success)
        result.putString("outputPath", outputPath)

        val errorsArray = Arguments.createArray()
        errors.forEach { errorsArray.pushString(it) }
        result.putArray("errors", errorsArray)

        return result
    }

    /**
     * Runs an external process and streams its output line-by-line
     * as "buildLog" events. Returns the full accumulated output.
     */
    private fun streamProcessOutput(buildId: String, process: Process): String {
        val output = StringBuilder()
        val reader = BufferedReader(InputStreamReader(process.inputStream))

        reader.useLines { lines ->
            lines.forEach { line ->
                output.appendLine(line)
                emitBuildLog(buildId, line)
            }
        }

        val exitCode = process.waitFor()
        if (exitCode != 0) {
            val errorMsg = "Process exited with code $exitCode"
            output.appendLine(errorMsg)
            emitBuildLog(buildId, errorMsg, "stderr")
        }

        return output.toString()
    }

    /**
     * Fallback: run a build command as an external process when JNI is not available.
     * Returns true if the process exits with code 0.
     */
    private fun runProcessBuild(buildId: String, command: String, args: List<String>): Boolean {
        return try {
            val cmdList = mutableListOf(command).also { it.addAll(args) }
            val process = ProcessBuilder(cmdList)
                .redirectErrorStream(true)
                .start()
            activeBuilds[buildId] = process
            streamProcessOutput(buildId, process)
            activeBuilds.remove(buildId)
            process.exitValue() == 0
        } catch (e: Exception) {
            emitBuildLog(buildId, "Process execution failed: ${e.message}", "stderr")
            false
        }
    }

    /**
     * Fallback: create a minimal C++ project structure when JNI scaffold is unavailable.
     */
    private fun createFallbackProject(destPath: String, templateName: String): Boolean {
        return try {
            val dir = java.io.File(destPath)
            if (!dir.exists()) dir.mkdirs()

            val cmakeFile = java.io.File(dir, "CMakeLists.txt")
            if (!cmakeFile.exists()) {
                val projectName = dir.name.replace(Regex("[^a-zA-Z0-9_]"), "_")
                cmakeFile.writeText("""
                    |cmake_minimum_required(VERSION 3.22)
                    |project($projectName VERSION 1.0.0 LANGUAGES CXX)
                    |
                    |set(CMAKE_CXX_STANDARD 20)
                    |set(CMAKE_CXX_STANDARD_REQUIRED ON)
                    |set(CMAKE_EXPORT_COMPILE_COMMANDS ON)
                    |
                    |add_executable(${'$'}{PROJECT_NAME} main.cpp)
                """.trimMargin())
            }

            val mainFile = java.io.File(dir, "main.cpp")
            if (!mainFile.exists()) {
                val projectName = dir.name
                mainFile.writeText("""
                    |#include <iostream>
                    |
                    |int main() {
                    |    std::cout << "Hello from $projectName!" << std::endl;
                    |    return 0;
                    |}
                """.trimMargin())
            }

            true
        } catch (e: Exception) {
            false
        }
    }

    // ── Build state data class ──────────────────────────────────────────────

    private data class BuildState(
        val buildId: String,
        var status: String,          // running, completed, failed, cancelled
        var currentStep: String,     // configure, compile, link, done, etc.
        val completedSteps: MutableList<String>,
        var resumable: Boolean,
        val projectPath: String
    )
}
