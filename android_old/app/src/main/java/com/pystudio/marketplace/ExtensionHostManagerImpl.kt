package com.pystudio.marketplace

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicLong

/**
 * S-12.3 — Extension Host Manager implementation.
 *
 * Manages the Extension Host process that runs QuickJS realms for each
 * extension. On Android this would be an `android:isolatedProcess` communicating
 * via AIDL/Binder (SRS §2.2, REQ-FUNC-0570).
 *
 * Key responsibilities:
 * - Start/stop the Extension Host process
 * - Create/destroy individual QuickJS realms per extension
 * - Enforce resource budgets per extension (SRS §6.5, REQ-FUNC-0604)
 * - Watchdog heartbeat monitoring (SRS §6.6, REQ-FUNC-0605)
 * - Permission-gated command execution (SRS §6.3, REQ-FUNC-0602)
 * - Activation timeout enforcement (10s, REQ-FUNC-0604)
 */
class ExtensionHostManagerImpl(
    private val permissionManager: PermissionManagerService
) : ExtensionHostManagerService {

    // -----------------------------------------------------------------------
    // Host-level state
    // -----------------------------------------------------------------------

    private val hostStateFlow = MutableStateFlow(ExtensionHostState.STOPPED)
    private val stateEventsFlow = MutableSharedFlow<ExtensionStateChangeEvent>(extraBufferCapacity = 32)

    // -----------------------------------------------------------------------
    // Per-extension sandbox state
    // -----------------------------------------------------------------------

    private data class SandboxState(
        val extensionId: String,
        val permissions: List<ExtensionPermission>,
        var status: ExtensionStatus,
        val activatedAtMillis: Long = System.currentTimeMillis(),
        val memoryUsageBytes: AtomicLong = AtomicLong(0),
        val apiCallCount: AtomicInteger = AtomicInteger(0),
        val lastHeartbeatMillis: AtomicLong = AtomicLong(System.currentTimeMillis()),
        val journal: MutableList<ExtensionLogEntry> = mutableListOf()
    )

    private val sandboxes = ConcurrentHashMap<String, SandboxState>()

    // -----------------------------------------------------------------------
    // Resource budgets per REQ-FUNC-0604
    // -----------------------------------------------------------------------

    companion object {
        /** Maximum heap memory per extension (default 32 MB). */
        const val DEFAULT_MEMORY_BUDGET_BYTES: Long = 32L * 1024 * 1024

        /** Maximum time for activate() to return (10 seconds). */
        const val ACTIVATION_TIMEOUT_MS: Long = 10_000

        /** Maximum time for a single API call (30 seconds). */
        const val API_CALL_TIMEOUT_MS: Long = 30_000

        /** Maximum API calls per second per extension (rate limit). */
        const val MAX_API_CALLS_PER_SECOND: Int = 100

        /** Heartbeat interval for watchdog (5 seconds). */
        const val HEARTBEAT_INTERVAL_MS: Long = 5_000

        /** Missed heartbeats before considering process frozen. */
        const val MAX_MISSED_HEARTBEATS: Int = 3

        /** Maximum storage per extension (50 MB default). */
        const val DEFAULT_STORAGE_BUDGET_BYTES: Long = 50L * 1024 * 1024

        /** Maximum FileSystemWatchers per extension. */
        const val MAX_FS_WATCHERS: Int = 500
    }

    // -----------------------------------------------------------------------
    // Host lifecycle
    // -----------------------------------------------------------------------

    override suspend fun ensureHostStarted(): ExtensionHostState = withContext(Dispatchers.IO) {
        if (hostStateFlow.value == ExtensionHostState.RUNNING) {
            return@withContext ExtensionHostState.RUNNING
        }

        hostStateFlow.value = ExtensionHostState.STARTING
        logSystem("Extension Host starting...")

        // In production: bind to the isolated process service via AIDL
        // Initialize the QuickJS engine, set up global API filters, etc.
        // Simulate startup latency (< 200ms budget per REQ-FUNC-0643 §14.1)
        delay(50)

        hostStateFlow.value = ExtensionHostState.RUNNING
        logSystem("Extension Host started successfully.")
        ExtensionHostState.RUNNING
    }

    override suspend fun restartHost() = withContext(Dispatchers.IO) {
        logSystem("Extension Host restarting...")
        hostStateFlow.value = ExtensionHostState.RESTARTING

        // Deactivate all extensions
        val activeIds = sandboxes.keys.toList()
        for (extId in activeIds) {
            deactivateExtension(extId)
        }

        hostStateFlow.value = ExtensionHostState.STOPPED
        delay(100)

        // Re-start
        ensureHostStarted()

        // Re-activate previously active extensions following their activation events
        // In production, the ExtensionLifecycleService would handle re-activation
        logSystem("Extension Host restarted. ${activeIds.size} extensions were active.")
    }

    override fun hostStateFlow(): Flow<ExtensionHostState> = hostStateFlow.asStateFlow()

    // -----------------------------------------------------------------------
    // Extension activation/deactivation (SRS §3.5, REQ-FUNC-0576)
    // -----------------------------------------------------------------------

    override suspend fun activateExtension(
        extensionId: String
    ): ActivationResult = withContext(Dispatchers.IO) {
        ensureHostStarted()

        if (sandboxes.containsKey(extensionId)) {
            val existing = sandboxes[extensionId]!!
            if (existing.status == ExtensionStatus.ACTIVE) {
                return@withContext ActivationResult(
                    success = true,
                    extensionId = extensionId,
                    activationTimeMs = 0
                )
            }
        }

        val startTime = System.currentTimeMillis()
        val sandbox = SandboxState(
            extensionId = extensionId,
            permissions = emptyList(),
            status = ExtensionStatus.ACTIVATING
        )
        sandboxes[extensionId] = sandbox
        emitStateChange(extensionId, ExtensionStatus.INSTALLED, ExtensionStatus.ACTIVATING)

        // Execute activate() with timeout (REQ-FUNC-0604: 10s budget)
        val activationResult = withTimeoutOrNull(ACTIVATION_TIMEOUT_MS) {
            try {
                // In production: send AIDL message to the isolated process:
                //   1. Create a new QuickJS realm for this extension
                //   2. Load the extension's dist/extension.js into the realm
                //   3. Call the exported activate(context) function
                //   4. Set up SDK proxy bindings for permitted APIs
                simulateActivation(extensionId)
                true
            } catch (e: Exception) {
                sandbox.journal.add(
                    ExtensionLogEntry(
                        timestamp = System.currentTimeMillis(),
                        level = "ERROR",
                        message = "Activation error: ${e.message}",
                        extensionId = extensionId
                    )
                )
                false
            }
        }

        val elapsed = System.currentTimeMillis() - startTime

        if (activationResult == null) {
            // Timeout
            sandbox.status = ExtensionStatus.ACTIVATION_FAILED
            emitStateChange(extensionId, ExtensionStatus.ACTIVATING, ExtensionStatus.ACTIVATION_FAILED)
            sandbox.journal.add(
                ExtensionLogEntry(
                    timestamp = System.currentTimeMillis(),
                    level = "ERROR",
                    message = "Activation timed out after ${ACTIVATION_TIMEOUT_MS}ms",
                    extensionId = extensionId
                )
            )
            return@withContext ActivationResult(
                success = false,
                extensionId = extensionId,
                activationTimeMs = elapsed,
                errorCode = MarketplaceErrorCodes.EXT_ACTIVATION_TIMEOUT,
                errorMessage = "activate() did not return within ${ACTIVATION_TIMEOUT_MS}ms"
            )
        }

        if (activationResult == false) {
            sandbox.status = ExtensionStatus.ACTIVATION_FAILED
            emitStateChange(extensionId, ExtensionStatus.ACTIVATING, ExtensionStatus.ACTIVATION_FAILED)
            return@withContext ActivationResult(
                success = false,
                extensionId = extensionId,
                activationTimeMs = elapsed,
                errorCode = MarketplaceErrorCodes.EXT_ACTIVATION_ERROR,
                errorMessage = "activate() threw an exception"
            )
        }

        sandbox.status = ExtensionStatus.ACTIVE
        emitStateChange(extensionId, ExtensionStatus.ACTIVATING, ExtensionStatus.ACTIVE)
        sandbox.journal.add(
            ExtensionLogEntry(
                timestamp = System.currentTimeMillis(),
                level = "INFO",
                message = "Extension activated in ${elapsed}ms",
                extensionId = extensionId
            )
        )

        ActivationResult(
            success = true,
            extensionId = extensionId,
            activationTimeMs = elapsed
        )
    }

    override suspend fun deactivateExtension(
        extensionId: String
    ) = withContext(Dispatchers.IO) {
        val sandbox = sandboxes[extensionId] ?: return@withContext
        val previousState = sandbox.status

        // In production: send AIDL message to call deactivate() in the realm,
        // then destroy the QuickJS realm and free resources
        sandbox.status = ExtensionStatus.DISABLED
        sandbox.journal.add(
            ExtensionLogEntry(
                timestamp = System.currentTimeMillis(),
                level = "INFO",
                message = "Extension deactivated",
                extensionId = extensionId
            )
        )
        emitStateChange(extensionId, previousState, ExtensionStatus.DISABLED)

        sandboxes.remove(extensionId)
    }

    // -----------------------------------------------------------------------
    // Command execution with permission gating (SRS §6.3, REQ-FUNC-0602)
    // -----------------------------------------------------------------------

    /**
     * Execute a command registered by an extension.
     * Each API call is intercepted by the PermissionManager (SRS §6.3).
     */
    suspend fun executeCommand(
        extensionId: String,
        command: String,
        args: List<Any> = emptyList()
    ): Any? = withContext(Dispatchers.IO) {
        val sandbox = sandboxes[extensionId]
            ?: throw IllegalStateException("Sandbox not running for extension: $extensionId")

        if (sandbox.status != ExtensionStatus.ACTIVE) {
            throw IllegalStateException("Extension $extensionId is not active (status: ${sandbox.status})")
        }

        // Rate limiting check (REQ-FUNC-0644 §15: 100 calls/sec)
        val callCount = sandbox.apiCallCount.incrementAndGet()
        if (callCount > MAX_API_CALLS_PER_SECOND) {
            sandbox.journal.add(
                ExtensionLogEntry(
                    timestamp = System.currentTimeMillis(),
                    level = "WARN",
                    message = "Rate limit exceeded: $callCount API calls",
                    extensionId = extensionId
                )
            )
            // Reset counter (simplified; in production use a sliding window)
            sandbox.apiCallCount.set(0)
        }

        // Memory budget check (REQ-FUNC-0604)
        val memUsage = sandbox.memoryUsageBytes.get()
        if (memUsage > DEFAULT_MEMORY_BUDGET_BYTES) {
            sandbox.status = ExtensionStatus.ERROR
            emitStateChange(extensionId, ExtensionStatus.ACTIVE, ExtensionStatus.ERROR)
            sandbox.journal.add(
                ExtensionLogEntry(
                    timestamp = System.currentTimeMillis(),
                    level = "ERROR",
                    message = "Memory budget exceeded: ${memUsage / (1024 * 1024)}MB > ${DEFAULT_MEMORY_BUDGET_BYTES / (1024 * 1024)}MB",
                    extensionId = extensionId
                )
            )
            throw RuntimeException("${MarketplaceErrorCodes.EXT_HOST_OOM}: Extension $extensionId exceeded memory budget")
        }

        // Execute with API call timeout (30s, REQ-FUNC-0604)
        val result = withTimeoutOrNull(API_CALL_TIMEOUT_MS) {
            // In production: serialize the command + args, send via AIDL to
            // the QuickJS realm, invoke the registered handler, return result
            simulateCommandExecution(extensionId, command, args)
        }

        if (result == null) {
            sandbox.journal.add(
                ExtensionLogEntry(
                    timestamp = System.currentTimeMillis(),
                    level = "WARN",
                    message = "API call timed out: $command",
                    extensionId = extensionId
                )
            )
        }

        // Update heartbeat
        sandbox.lastHeartbeatMillis.set(System.currentTimeMillis())

        result
    }

    /**
     * Execute an SDK API call with permission check (SRS §6.3 sequence).
     * This is the core interception point.
     */
    suspend fun executeSdkApiCall(
        extensionId: String,
        apiNamespace: String,
        method: String,
        args: Map<String, Any?> = emptyMap()
    ): Any? = withContext(Dispatchers.IO) {
        // Map API namespace to required permission
        val requiredPermission = mapApiToPermission(apiNamespace, method)

        if (requiredPermission != null) {
            val granted = permissionManager.checkPermission(extensionId, requiredPermission)
            if (!granted) {
                val sandbox = sandboxes[extensionId]
                sandbox?.journal?.add(
                    ExtensionLogEntry(
                        timestamp = System.currentTimeMillis(),
                        level = "WARN",
                        message = "Permission denied: $requiredPermission for $apiNamespace.$method",
                        extensionId = extensionId
                    )
                )
                throw SecurityException(
                    "${MarketplaceErrorCodes.EXT_PERMISSION_DENIED}: " +
                    "Extension $extensionId lacks permission '$requiredPermission' for $apiNamespace.$method"
                )
            }
        }

        executeCommand(extensionId, "$apiNamespace.$method", args.values.toList())
    }

    // -----------------------------------------------------------------------
    // Watchdog (SRS §6.6, REQ-FUNC-0605)
    // -----------------------------------------------------------------------

    /**
     * Check health of all running sandboxes.
     * Called periodically by a coroutine-based scheduler.
     */
    suspend fun runWatchdogCheck(): List<String> {
        val frozenExtensions = mutableListOf<String>()
        val now = System.currentTimeMillis()

        for ((extId, sandbox) in sandboxes) {
            if (sandbox.status != ExtensionStatus.ACTIVE) continue

            val lastBeat = sandbox.lastHeartbeatMillis.get()
            val missedBeats = (now - lastBeat) / HEARTBEAT_INTERVAL_MS

            if (missedBeats >= MAX_MISSED_HEARTBEATS) {
                sandbox.journal.add(
                    ExtensionLogEntry(
                        timestamp = now,
                        level = "ERROR",
                        message = "Watchdog: $missedBeats missed heartbeats, extension considered frozen",
                        extensionId = extId
                    )
                )
                frozenExtensions.add(extId)

                // Destroy the realm but keep the sandbox entry for diagnostics
                sandbox.status = ExtensionStatus.ERROR
                emitStateChange(extId, ExtensionStatus.ACTIVE, ExtensionStatus.ERROR)
            }
        }

        return frozenExtensions
    }

    /**
     * Record a heartbeat from the sandbox process.
     * In production, the QuickJS process sends these via AIDL every 5s.
     */
    fun recordHeartbeat(extensionId: String) {
        sandboxes[extensionId]?.lastHeartbeatMillis?.set(System.currentTimeMillis())
    }

    /**
     * Report memory usage from the sandbox process.
     */
    fun reportMemoryUsage(extensionId: String, bytes: Long) {
        sandboxes[extensionId]?.memoryUsageBytes?.set(bytes)
    }

    // -----------------------------------------------------------------------
    // Query methods
    // -----------------------------------------------------------------------

    fun getExtensionStatus(extensionId: String): ExtensionStatus? =
        sandboxes[extensionId]?.status

    fun getExtensionJournal(extensionId: String): List<ExtensionLogEntry> =
        sandboxes[extensionId]?.journal?.toList() ?: emptyList()

    fun getActiveSandboxCount(): Int =
        sandboxes.values.count { it.status == ExtensionStatus.ACTIVE }

    fun getMemoryUsage(extensionId: String): Long =
        sandboxes[extensionId]?.memoryUsageBytes?.get() ?: 0

    // -----------------------------------------------------------------------
    // Private helpers
    // -----------------------------------------------------------------------

    /**
     * Maps SDK API namespace + method to the required permission.
     * REQ-FUNC-0593 (permission taxonomy) cross-referenced with §3.2 namespaces.
     */
    private fun mapApiToPermission(namespace: String, method: String): String? {
        return when {
            namespace == "workspace" && method.startsWith("open") -> "workspace.readFiles"
            namespace == "workspace" && method.startsWith("apply") -> "workspace.writeFiles"
            namespace == "workspace" && method.startsWith("getConfig") -> "workspace.readConfig"
            namespace == "workspace" && method == "createFileSystemWatcher" -> "workspace.readFiles"
            namespace == "terminal" && method == "create" -> "terminal.create"
            namespace == "terminal" -> "terminal.readOnly"
            namespace == "scm" && method.startsWith("get") -> "scm.read"
            namespace == "scm" -> "scm.write"
            namespace == "ai" && method == "selectLanguageModel" -> "ai.localModel"
            namespace == "ai" && method == "getChatHistory" -> "ai.chatHistory"
            namespace == "debug" -> "debug.sessions"
            namespace == "notebooks" -> "notebooks.kernels"
            namespace == "window" && method == "createWebviewPanel" -> "webview.create"
            namespace == "env" && method == "getDeviceInfo" -> "env.deviceInfo"
            // commands, window (notifications), languages — no special permission needed
            else -> null
        }
    }

    /** Simulate QuickJS realm activation (< 500ms p95 per §14.1). */
    private suspend fun simulateActivation(extensionId: String) {
        // In production: AIDL call to create realm + load JS + call activate()
        delay(20) // Simulated activation latency
    }

    /** Simulate command execution in QuickJS realm. */
    private suspend fun simulateCommandExecution(
        extensionId: String,
        command: String,
        args: List<Any>
    ): Any? {
        // In production: serialize → AIDL → QuickJS realm → handler → result
        return "OK:$command"
    }

    private suspend fun emitStateChange(
        extensionId: String,
        previous: ExtensionStatus,
        new: ExtensionStatus
    ) {
        stateEventsFlow.emit(
            ExtensionStateChangeEvent(
                extensionId = extensionId,
                previousState = previous,
                newState = new,
                timestamp = System.currentTimeMillis()
            )
        )
    }

    private fun logSystem(message: String) {
        // In production: android.util.Log.i("ExtensionHost", message)
    }
}
