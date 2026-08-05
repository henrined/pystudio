package com.pystudio.marketplace

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.withContext
import java.io.File
import java.util.concurrent.ConcurrentHashMap

/**
 * S-12.2 — Extension Lifecycle Service implementation.
 *
 * Manages the full lifecycle of installed extensions:
 * - Enable / Disable (REQ-FUNC-0576 §3.5 state diagram)
 * - Update with automatic rollback on failure (REQ-FUNC-0616 §8.2, §8.3)
 * - State tracking and event emission
 *
 * Collaborates with:
 * - [ExtensionHostManagerImpl] for activation/deactivation in the sandbox
 * - [MarketplaceServiceImpl] for downloading and installing new versions
 * - [PermissionManagerServiceImpl] for permission management on updates
 */
class ExtensionLifecycleServiceImpl(
    private val hostManager: ExtensionHostManagerImpl,
    private val marketplaceService: MarketplaceServiceImpl,
    private val permissionManager: PermissionManagerServiceImpl,
    private val installDir: File,
    private val quarantineDir: File
) : ExtensionLifecycleService {

    private val stateChanges = MutableSharedFlow<ExtensionStateChangeEvent>(extraBufferCapacity = 32)
    private val extensionStates = ConcurrentHashMap<String, ExtensionStatus>()

    // -----------------------------------------------------------------------
    // Enable / Disable (REQ-FUNC-0576)
    // -----------------------------------------------------------------------

    override suspend fun enable(extensionId: String): ExtensionStatus = withContext(Dispatchers.IO) {
        val currentState = extensionStates[extensionId] ?: ExtensionStatus.INSTALLED

        if (currentState == ExtensionStatus.ACTIVE) {
            return@withContext ExtensionStatus.ACTIVE
        }

        // Activate the extension in the sandbox
        val result = hostManager.activateExtension(extensionId)

        val newState = if (result.success) {
            ExtensionStatus.ACTIVE
        } else {
            ExtensionStatus.ACTIVATION_FAILED
        }

        val previousState = extensionStates.put(extensionId, newState) ?: ExtensionStatus.INSTALLED
        emitStateChange(extensionId, previousState, newState)
        newState
    }

    override suspend fun disable(extensionId: String): ExtensionStatus = withContext(Dispatchers.IO) {
        val previousState = extensionStates[extensionId] ?: ExtensionStatus.INSTALLED

        // Deactivate in the sandbox
        hostManager.deactivateExtension(extensionId)

        extensionStates[extensionId] = ExtensionStatus.DISABLED
        emitStateChange(extensionId, previousState, ExtensionStatus.DISABLED)
        ExtensionStatus.DISABLED
    }

    // -----------------------------------------------------------------------
    // Update with rollback (REQ-FUNC-0616 §8.2, REQ-FUNC-0628 §8.3)
    // -----------------------------------------------------------------------

    /**
     * Update an extension to a new version.
     *
     * Sequence (from SRS §13.3):
     * 1. Backup current version
     * 2. Deactivate the extension
     * 3. Replace files atomically (current → new)
     * 4. Attempt activation of new version
     * 5. On failure → rollback to previous version
     * 6. On success → keep new version, purge old after 72h
     */
    override suspend fun updateExtension(
        extensionId: String,
        newVersion: String
    ): UpdateResult = withContext(Dispatchers.IO) {
        val extDir = File(installDir, extensionId)
        if (!extDir.exists()) {
            return@withContext UpdateResult(
                success = false,
                extensionId = extensionId,
                previousVersion = "",
                newVersion = newVersion,
                errorCode = MarketplaceErrorCodes.EXT_MANIFEST_INVALID
            )
        }

        // Find current version directory
        val currentVersionDir = extDir.listFiles { f -> f.isDirectory }
            ?.maxByOrNull { it.lastModified() }
        val previousVersion = currentVersionDir?.name ?: "0.0.0"

        if (previousVersion == newVersion) {
            return@withContext UpdateResult(
                success = true,
                extensionId = extensionId,
                previousVersion = previousVersion,
                newVersion = newVersion
            )
        }

        // Step 1: Backup current version (rename to .bak)
        val backupDir = if (currentVersionDir != null) {
            val backup = File(extDir, "${currentVersionDir.name}.bak")
            if (backup.exists()) backup.deleteRecursively()
            currentVersionDir.copyRecursively(backup, overwrite = true)
            backup
        } else null

        // Step 2: Deactivate extension
        val previousState = extensionStates[extensionId]
        if (previousState == ExtensionStatus.ACTIVE) {
            hostManager.deactivateExtension(extensionId)
        }

        // Step 3: Download and install the new version
        // In production, the archive would come from ExtensionUpdateService.checkForUpdates()
        // For now, we search for it and download
        val searchResult = marketplaceService.searchExtensions(extensionId)
        val archiveEntry = searchResult.results.find { it.id == extensionId }

        if (archiveEntry == null) {
            // Rollback: re-activate previous version
            if (previousState == ExtensionStatus.ACTIVE) {
                hostManager.activateExtension(extensionId)
                extensionStates[extensionId] = previousState
            }
            return@withContext UpdateResult(
                success = false,
                extensionId = extensionId,
                previousVersion = previousVersion,
                newVersion = newVersion,
                errorCode = MarketplaceErrorCodes.EXT_NETWORK_OFFLINE
            )
        }

        // Step 4: Create new version directory
        val newVersionDir = File(extDir, newVersion)
        newVersionDir.mkdirs()

        // Write a placeholder — in production, the full .pysx content would be
        // downloaded, verified, and extracted here
        File(newVersionDir, "extension.json").writeText(
            """{"id":"$extensionId","version":"$newVersion"}"""
        )

        // Update metadata
        val metaFile = File(extDir, "installed.meta")
        if (metaFile.exists()) {
            val lines = metaFile.readLines().toMutableList()
            val versionLineIdx = lines.indexOfFirst { it.startsWith("version=") }
            if (versionLineIdx >= 0) {
                lines[versionLineIdx] = "version=$newVersion"
            }
            metaFile.writeText(lines.joinToString("\n"))
        }

        // Step 5: Attempt activation of new version
        val activationResult = hostManager.activateExtension(extensionId)

        if (!activationResult.success) {
            // ROLLBACK — REQ-FUNC-0628 §8.3
            // Restore files from backup
            newVersionDir.deleteRecursively()
            if (backupDir != null && currentVersionDir != null) {
                backupDir.copyRecursively(currentVersionDir, overwrite = true)
                backupDir.deleteRecursively()
            }

            // Re-activate previous version
            hostManager.activateExtension(extensionId)
            extensionStates[extensionId] = previousState ?: ExtensionStatus.INSTALLED

            // Update metadata back
            if (metaFile.exists()) {
                val lines = metaFile.readLines().toMutableList()
                val versionLineIdx = lines.indexOfFirst { it.startsWith("version=") }
                if (versionLineIdx >= 0) {
                    lines[versionLineIdx] = "version=$previousVersion"
                }
                metaFile.writeText(lines.joinToString("\n"))
            }

            emitStateChange(extensionId, ExtensionStatus.ACTIVATING, previousState ?: ExtensionStatus.INSTALLED)

            return@withContext UpdateResult(
                success = false,
                extensionId = extensionId,
                previousVersion = previousVersion,
                newVersion = newVersion,
                rolledBack = true,
                errorCode = MarketplaceErrorCodes.EXT_UPDATE_ROLLBACK
            )
        }

        // Success — cleanup backup after recording it for 72h rollback window
        // In production, a WorkManager job would purge .bak after 72 hours
        extensionStates[extensionId] = ExtensionStatus.ACTIVE
        emitStateChange(extensionId, ExtensionStatus.ACTIVATING, ExtensionStatus.ACTIVE)

        UpdateResult(
            success = true,
            extensionId = extensionId,
            previousVersion = previousVersion,
            newVersion = newVersion,
            rollbackAvailable = backupDir?.exists() == true
        )
    }

    // -----------------------------------------------------------------------
    // Manual rollback (REQ-FUNC-0628 §8.3)
    // -----------------------------------------------------------------------

    override suspend fun rollback(extensionId: String): RollbackResult = withContext(Dispatchers.IO) {
        val extDir = File(installDir, extensionId)
        if (!extDir.exists()) {
            return@withContext RollbackResult(
                success = false,
                extensionId = extensionId,
                restoredVersion = "",
                errorCode = MarketplaceErrorCodes.EXT_MANIFEST_INVALID
            )
        }

        // Find the backup (.bak) directory
        val backupDirs = extDir.listFiles { f -> f.isDirectory && f.name.endsWith(".bak") }
        val latestBackup = backupDirs?.maxByOrNull { it.lastModified() }

        if (latestBackup == null) {
            return@withContext RollbackResult(
                success = false,
                extensionId = extensionId,
                restoredVersion = "",
                errorCode = "NO_BACKUP_AVAILABLE"
            )
        }

        val restoredVersion = latestBackup.name.removeSuffix(".bak")

        // Deactivate current version
        hostManager.deactivateExtension(extensionId)

        // Remove current version directory (non-backup)
        val currentVersionDirs = extDir.listFiles { f -> f.isDirectory && !f.name.endsWith(".bak") }
        currentVersionDirs?.forEach { it.deleteRecursively() }

        // Restore from backup
        val restoredDir = File(extDir, restoredVersion)
        latestBackup.copyRecursively(restoredDir, overwrite = true)
        latestBackup.deleteRecursively()

        // Update metadata
        val metaFile = File(extDir, "installed.meta")
        if (metaFile.exists()) {
            val lines = metaFile.readLines().toMutableList()
            val versionLineIdx = lines.indexOfFirst { it.startsWith("version=") }
            if (versionLineIdx >= 0) {
                lines[versionLineIdx] = "version=$restoredVersion"
            }
            metaFile.writeText(lines.joinToString("\n"))
        }

        // Re-activate restored version
        val activationResult = hostManager.activateExtension(extensionId)
        extensionStates[extensionId] = if (activationResult.success) {
            ExtensionStatus.ACTIVE
        } else {
            ExtensionStatus.ACTIVATION_FAILED
        }

        RollbackResult(
            success = activationResult.success,
            extensionId = extensionId,
            restoredVersion = restoredVersion
        )
    }

    // -----------------------------------------------------------------------
    // State queries
    // -----------------------------------------------------------------------

    override suspend fun getState(extensionId: String): ExtensionStatus? {
        return extensionStates[extensionId]
            ?: hostManager.getExtensionStatus(extensionId)
    }

    override fun stateChangesFlow(): Flow<ExtensionStateChangeEvent> = stateChanges.asSharedFlow()

    // -----------------------------------------------------------------------
    // Internal helpers used by ExtensionRegistryServiceImpl
    // -----------------------------------------------------------------------

    fun setInitialState(extensionId: String, state: ExtensionStatus) {
        extensionStates[extensionId] = state
    }

    // -----------------------------------------------------------------------
    // Private helpers
    // -----------------------------------------------------------------------

    private suspend fun emitStateChange(
        extensionId: String,
        previous: ExtensionStatus,
        new: ExtensionStatus
    ) {
        stateChanges.emit(
            ExtensionStateChangeEvent(
                extensionId = extensionId,
                previousState = previous,
                newState = new,
                timestamp = System.currentTimeMillis()
            )
        )
    }
}
