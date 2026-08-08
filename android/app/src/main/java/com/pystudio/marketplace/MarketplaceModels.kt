package com.pystudio.marketplace

import kotlinx.coroutines.flow.Flow

// ---------------------------------------------------------------------------
// S-12.1 — Data models for the extension manifest (SRS §10, REQ-FUNC-0630)
// ---------------------------------------------------------------------------

/**
 * Full extension manifest as declared in `extension.json`.
 * Covers REQ-FUNC-0631 (schema) and REQ-FUNC-0632 (mandatory fields).
 */
data class ExtensionManifest(
    val id: String,                           // Format: publisher.extension-name
    val publisher: String,
    val name: String,
    val displayName: String,
    val description: String,                  // max 200 chars
    val version: String,                      // SemVer / PEP 440
    val preRelease: Boolean = false,
    val engines: EngineRequirements,
    val apiVersion: String = "1.0",
    val categories: List<String>,
    val keywords: List<String> = emptyList(),
    val icon: String = "assets/icon.png",
    val license: String,
    val main: String? = null,                 // dist/extension.js for programmatic extensions
    val permissions: List<ExtensionPermission>,
    val activationEvents: List<String> = emptyList(),
    val contributes: ContributionPoints = ContributionPoints(),
    val extensionDependencies: List<String> = emptyList(),
    val extensionPack: List<String> = emptyList(),
    val platform: PlatformRequirements = PlatformRequirements(),
    val pricing: String = "free"
)

data class EngineRequirements(
    val pystudio: String                      // SemVer range e.g. "^1.2.0"
)

data class PlatformRequirements(
    val os: List<String> = listOf("android"),
    val abi: List<String> = listOf("*"),
    val minApiLevel: Int = 21
)

/**
 * Contribution points — static declarations read from the manifest without
 * executing code. REQ-FUNC-0574. All 16 types from SRS §3.3 table.
 */
data class ContributionPoints(
    val commands: List<ContributedCommand> = emptyList(),
    val menus: Map<String, List<ContributedMenuItem>> = emptyMap(),
    val keybindings: List<ContributedKeybinding> = emptyList(),
    val languages: List<ContributedLanguage> = emptyList(),
    val grammars: List<ContributedGrammar> = emptyList(),
    val themes: List<ContributedTheme> = emptyList(),
    val iconThemes: List<ContributedIconTheme> = emptyList(),
    val snippets: List<ContributedSnippet> = emptyList(),
    val configuration: ContributedConfiguration? = null,
    val viewsContainers: Map<String, List<ContributedViewContainer>> = emptyMap(),
    val views: Map<String, List<ContributedView>> = emptyMap(),
    val debuggers: List<ContributedDebugger> = emptyList(),
    val taskDefinitions: List<ContributedTaskDefinition> = emptyList(),
    val problemMatchers: List<ContributedProblemMatcher> = emptyList(),
    val walkthroughs: List<ContributedWalkthrough> = emptyList(),
    val chatParticipants: List<ContributedChatParticipant> = emptyList(),
    val notebookRenderers: List<ContributedNotebookRenderer> = emptyList()
)

data class ContributedCommand(
    val command: String,
    val title: String,
    val category: String? = null,
    val icon: String? = null
)

data class ContributedMenuItem(
    val command: String,
    val `when`: String? = null,
    val group: String? = null
)

data class ContributedKeybinding(
    val command: String,
    val key: String,
    val `when`: String? = null
)

data class ContributedTheme(
    val label: String,
    val uiTheme: String,
    val path: String
)

data class ContributedIconTheme(
    val id: String,
    val label: String,
    val path: String
)

data class ContributedSnippet(
    val language: String,
    val path: String
)

data class ContributedLanguage(
    val id: String,
    val aliases: List<String> = emptyList(),
    val extensions: List<String> = emptyList(),
    val configuration: String? = null
)

data class ContributedGrammar(
    val language: String,
    val scopeName: String,
    val path: String
)

data class ContributedConfiguration(
    val title: String,
    val properties: Map<String, ConfigProperty> = emptyMap()
)

data class ConfigProperty(
    val type: String,
    val default: Any? = null,
    val description: String = ""
)

data class ContributedViewContainer(
    val id: String,
    val title: String,
    val icon: String? = null
)

data class ContributedView(
    val id: String,
    val name: String,
    val `when`: String? = null
)

data class ContributedDebugger(
    val type: String,
    val label: String,
    val program: String? = null,
    val runtime: String? = null,
    val languages: List<String> = emptyList()
)

data class ContributedTaskDefinition(
    val type: String,
    val properties: Map<String, Any> = emptyMap()
)

data class ContributedProblemMatcher(
    val name: String,
    val owner: String,
    val pattern: List<Map<String, String>> = emptyList()
)

data class ContributedWalkthrough(
    val id: String,
    val title: String,
    val steps: List<ContributedWalkthroughStep> = emptyList()
)

data class ContributedWalkthroughStep(
    val id: String,
    val title: String,
    val description: String = ""
)

data class ContributedChatParticipant(
    val id: String,
    val name: String,
    val description: String = "",
    val isSticky: Boolean = false
)

data class ContributedNotebookRenderer(
    val id: String,
    val displayName: String,
    val mimeTypes: List<String> = emptyList()
)

// ---------------------------------------------------------------------------
// S-12.1 — Permission model (SRS §5, REQ-FUNC-0591 → 0598)
// ---------------------------------------------------------------------------

/**
 * A declared permission in the extension manifest.
 */
data class ExtensionPermission(
    val name: String,
    val justification: String? = null,
    val domains: List<String>? = null          // For network.outbound / network.domains
)

/**
 * Risk levels per REQ-FUNC-0594.
 */
enum class PermissionRiskLevel {
    LOW,      // Granted automatically
    MEDIUM,   // Displayed with explanation, granted by default
    HIGH      // Requires explicit user approval
}

/**
 * Persistent record of a granted permission — REQ-FUNC-0595 / 0596.
 */
data class PermissionGrant(
    val permissionName: String,
    val granted: Boolean,
    val grantedAtMillis: Long? = null,
    val justification: String,
    val riskLevel: PermissionRiskLevel
)

// ---------------------------------------------------------------------------
// S-12 — Extension lifecycle states (SRS §3.5, REQ-FUNC-0576)
// ---------------------------------------------------------------------------

enum class ExtensionStatus {
    QUARANTINED,
    INSTALLED,
    WAITING_ACTIVATION,
    ACTIVATING,
    ACTIVE,
    ACTIVATION_FAILED,
    DISABLED,
    UNINSTALLING,
    ERROR
}

// ---------------------------------------------------------------------------
// S-12.1 — Extension archive and registry types
// ---------------------------------------------------------------------------

/**
 * A signed extension archive on the registry.
 * Contains double signature (developer + registry) per REQ-FUNC-0644 §15.
 */
data class ExtensionArchive(
    val manifest: ExtensionManifest,
    val sha256: String,                        // Hex-encoded SHA-256 of .pysx content
    val developerSignature: String,            // Ed25519 or RSA developer signature
    val registrySignature: String,             // Registry co-signature
    val downloadUrl: String,
    val sizeBytesRemote: Long = 0
)

/**
 * Summary for search results — SRS §11.1 ExtensionSummary.
 */
data class ExtensionSummary(
    val id: String,
    val displayName: String,
    val publisher: String,
    val description: String,
    val version: String,
    val iconUrl: String,
    val installs: Long,
    val rating: Double,
    val categories: List<String>,
    val preRelease: Boolean,
    val hasDeveloperSignature: Boolean
)

/**
 * Detailed extension info — SRS §11.1 ExtensionDetails.
 * Extends ExtensionSummary with full metadata for the detail screen.
 */
data class ExtensionDetails(
    val id: String,
    val displayName: String,
    val publisher: String,
    val description: String,
    val version: String,
    val iconUrl: String,
    val installs: Long,
    val rating: Double,
    val categories: List<String>,
    val preRelease: Boolean,
    val hasDeveloperSignature: Boolean,
    val readme: String,
    val changelog: String,
    val license: String,
    val repository: String? = null,
    val permissions: List<ExtensionPermission>,
    val dependencies: List<String>,
    val engines: EngineRequirements,
    val platform: PlatformRequirements,
    val versions: List<VersionInfo> = emptyList(),
    val ratings: RatingDistribution = RatingDistribution()
)

data class VersionInfo(
    val version: String,
    val publishedAt: Long,
    val preRelease: Boolean = false,
    val yanked: Boolean = false
)

data class RatingDistribution(
    val star5: Int = 0,
    val star4: Int = 0,
    val star3: Int = 0,
    val star2: Int = 0,
    val star1: Int = 0
)

data class ExtensionSearchResult(
    val total: Int,
    val results: List<ExtensionSummary>
)

data class SearchFilters(
    val category: String? = null,
    val sortBy: String = "relevance",          // relevance | installs | rating | updated
    val targetAbi: String? = null,
    val pystudioVersion: String? = null
)

/**
 * Locally installed extension record, stored in SQLite.
 * Matches SRS §11.1 InstalledExtension.
 */
data class InstalledExtension(
    val manifest: ExtensionManifest,
    val localPath: String,                     // Absolute path on device
    val installedAtMillis: Long,
    val status: ExtensionStatus,
    val enabled: Boolean = true,
    val sizeBytes: Long,
    val permissions: List<PermissionGrant>,
    val hasUpdate: Boolean = false,
    val latestVersion: String? = null,
    val previousVersionPath: String? = null     // For rollback (§8.3)
)

/**
 * Runtime state of an extension — SRS §11.1 ExtensionState.
 * Richer than the simple ExtensionStatus enum.
 */
data class ExtensionState(
    val state: ExtensionStatus,
    val activationTimeMs: Long? = null,
    val memoryUsageBytes: Long? = null,
    val lastError: String? = null,
    val journal: List<ExtensionLogEntry> = emptyList()
)

// ---------------------------------------------------------------------------
// S-12 — Operation results (SRS §11.1)
// ---------------------------------------------------------------------------

data class InstallResult(
    val success: Boolean,
    val extensionId: String,
    val version: String,
    val requiresReload: Boolean = false,
    val rollbackAvailable: Boolean = false,
    val errorCode: String? = null
)

data class UpdateResult(
    val success: Boolean,
    val extensionId: String,
    val previousVersion: String,
    val newVersion: String,
    val rolledBack: Boolean = false,
    val rollbackAvailable: Boolean = false,
    val errorCode: String? = null
)

data class RollbackResult(
    val success: Boolean,
    val extensionId: String,
    val restoredVersion: String,
    val errorCode: String? = null
)

data class AvailableUpdate(
    val extensionId: String,
    val currentVersion: String,
    val latestVersion: String,
    val preRelease: Boolean
)

// ---------------------------------------------------------------------------
// S-12 — Extension Host state (SRS §6, REQ-FUNC-0599)
// ---------------------------------------------------------------------------

enum class ExtensionHostState {
    STOPPED,
    STARTING,
    RUNNING,
    CRASHED,
    RESTARTING
}

data class ActivationResult(
    val success: Boolean,
    val extensionId: String,
    val activationTimeMs: Long,
    val errorCode: String? = null,
    val errorMessage: String? = null
)

// ---------------------------------------------------------------------------
// S-12 — Events
// ---------------------------------------------------------------------------

data class ExtensionInstallEvent(
    val extensionId: String,
    val version: String,
    val timestamp: Long
)

data class ExtensionUninstallEvent(
    val extensionId: String,
    val timestamp: Long
)

data class ExtensionUpdateEvent(
    val extensionId: String,
    val previousVersion: String,
    val newVersion: String,
    val rolledBack: Boolean,
    val timestamp: Long
)

data class ExtensionStateChangeEvent(
    val extensionId: String,
    val previousState: ExtensionStatus,
    val newState: ExtensionStatus,
    val timestamp: Long
)

data class ExtensionLogEntry(
    val timestamp: Long,
    val level: String,         // DEBUG, INFO, WARN, ERROR
    val message: String,
    val extensionId: String
)

// ---------------------------------------------------------------------------
// S-12 — Error codes (SRS §12, REQ-FUNC-0637)
// ---------------------------------------------------------------------------

object MarketplaceErrorCodes {
    const val EXT_MANIFEST_INVALID = "EXT_MANIFEST_INVALID"
    const val EXT_SIGNATURE_FAILED = "EXT_SIGNATURE_FAILED"
    const val EXT_INCOMPATIBLE_ENGINE = "EXT_INCOMPATIBLE_ENGINE"
    const val EXT_DEPENDENCY_MISSING = "EXT_DEPENDENCY_MISSING"
    const val EXT_ACTIVATION_TIMEOUT = "EXT_ACTIVATION_TIMEOUT"
    const val EXT_ACTIVATION_ERROR = "EXT_ACTIVATION_ERROR"
    const val EXT_HOST_CRASHED = "EXT_HOST_CRASHED"
    const val EXT_HOST_OOM = "EXT_HOST_OOM"
    const val EXT_PERMISSION_DENIED = "EXT_PERMISSION_DENIED"
    const val EXT_STORAGE_QUOTA = "EXT_STORAGE_QUOTA"
    const val EXT_UPDATE_ROLLBACK = "EXT_UPDATE_ROLLBACK"
    const val EXT_NETWORK_OFFLINE = "EXT_NETWORK_OFFLINE"
    const val EXT_INSTALL_DISK_FULL = "EXT_INSTALL_DISK_FULL"
    const val EXT_SCAN_REJECTED = "EXT_SCAN_REJECTED"
}

// ---------------------------------------------------------------------------
// S-12 — Permission taxonomy (SRS §5.2, REQ-FUNC-0593)
// ---------------------------------------------------------------------------

object PermissionRegistry {
    /** Maps each known permission to its risk level. */
    val TAXONOMY: Map<String, PermissionRiskLevel> = mapOf(
        "workspace.readFiles"       to PermissionRiskLevel.LOW,
        "workspace.writeFiles"      to PermissionRiskLevel.MEDIUM,
        "workspace.readConfig"      to PermissionRiskLevel.LOW,
        "workspace.writeConfig"     to PermissionRiskLevel.MEDIUM,
        "filesystem.readExternal"   to PermissionRiskLevel.HIGH,
        "filesystem.writeExternal"  to PermissionRiskLevel.HIGH,
        "network.outbound"          to PermissionRiskLevel.HIGH,
        "network.domains"           to PermissionRiskLevel.MEDIUM,
        "terminal.create"           to PermissionRiskLevel.HIGH,
        "terminal.readOnly"         to PermissionRiskLevel.MEDIUM,
        "process.spawn"             to PermissionRiskLevel.HIGH,
        "ai.localModel"             to PermissionRiskLevel.MEDIUM,
        "ai.chatHistory"            to PermissionRiskLevel.HIGH,
        "scm.read"                  to PermissionRiskLevel.LOW,
        "scm.write"                 to PermissionRiskLevel.HIGH,
        "debug.sessions"            to PermissionRiskLevel.MEDIUM,
        "notebooks.kernels"         to PermissionRiskLevel.MEDIUM,
        "clipboard.read"            to PermissionRiskLevel.MEDIUM,
        "clipboard.write"           to PermissionRiskLevel.LOW,
        "storage.local"             to PermissionRiskLevel.LOW,
        "storage.secrets"           to PermissionRiskLevel.MEDIUM,
        "authentication.providers"  to PermissionRiskLevel.HIGH,
        "env.deviceInfo"            to PermissionRiskLevel.LOW,
        "webview.create"            to PermissionRiskLevel.MEDIUM
    )

    fun riskLevel(permission: String): PermissionRiskLevel =
        TAXONOMY[permission] ?: PermissionRiskLevel.HIGH
}

// ---------------------------------------------------------------------------
// S-12 — Service interfaces (SRS §11.2, REQ-FUNC-0635)
// ---------------------------------------------------------------------------

/**
 * SRS §11.2 REQ-FUNC-0635 — exact Kotlin interface contract.
 */
interface ExtensionRegistryService {
    suspend fun search(query: String, filters: SearchFilters? = null): ExtensionSearchResult
    suspend fun getDetails(extensionId: String): ExtensionDetails
    suspend fun install(extensionId: String, version: String? = null): InstallResult
    suspend fun uninstall(extensionId: String)
    suspend fun getInstalled(): List<InstalledExtension>
    fun installEventsFlow(): Flow<ExtensionInstallEvent>
}

interface ExtensionHostManagerService {
    suspend fun ensureHostStarted(): ExtensionHostState
    suspend fun activateExtension(extensionId: String): ActivationResult
    suspend fun deactivateExtension(extensionId: String)
    suspend fun restartHost()
    fun hostStateFlow(): Flow<ExtensionHostState>
}

interface ExtensionLifecycleService {
    suspend fun enable(extensionId: String): ExtensionStatus
    suspend fun disable(extensionId: String): ExtensionStatus
    suspend fun updateExtension(extensionId: String, newVersion: String): UpdateResult
    suspend fun rollback(extensionId: String): RollbackResult
    suspend fun getState(extensionId: String): ExtensionStatus?
    fun stateChangesFlow(): Flow<ExtensionStateChangeEvent>
}

interface PermissionManagerService {
    suspend fun checkPermission(extensionId: String, permission: String): Boolean
    suspend fun requestPermission(extensionId: String, permission: String, justification: String): Boolean
    suspend fun grantPermission(extensionId: String, permission: String, justification: String)
    suspend fun revokePermission(extensionId: String, permission: String)
    suspend fun getGrants(extensionId: String): List<PermissionGrant>
}

interface ExtensionUpdateService {
    suspend fun checkForUpdates(): List<AvailableUpdate>
    suspend fun applyUpdate(extensionId: String): UpdateResult
    suspend fun applyAllUpdates(): List<UpdateResult>
    fun updatesFlow(): Flow<List<AvailableUpdate>>
}

/**
 * Low-level marketplace operations: download, verify, quarantine.
 * Used internally by ExtensionRegistryService.
 */
interface MarketplaceService {
    suspend fun searchExtensions(query: String, filters: SearchFilters? = null): ExtensionSearchResult
    suspend fun downloadExtension(archive: ExtensionArchive, quarantineDir: java.io.File): String
    suspend fun verifySignature(archive: ExtensionArchive, localPath: String): SignatureVerificationResult
    suspend fun installFromQuarantine(archive: ExtensionArchive, quarantinePath: String, installDir: java.io.File): InstallResult
    suspend fun getInstalledExtensions(installDir: java.io.File): List<InstalledExtension>
    suspend fun uninstallExtension(extensionId: String, installDir: java.io.File): Boolean
}

data class SignatureVerificationResult(
    val valid: Boolean,
    val sha256Match: Boolean,
    val developerSignatureValid: Boolean,
    val registrySignatureValid: Boolean,
    val errorCode: String? = null
)
