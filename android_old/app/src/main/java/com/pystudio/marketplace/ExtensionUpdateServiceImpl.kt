package com.pystudio.marketplace

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.withContext

/**
 * S-12.2 — Extension Update Service implementation.
 *
 * Checks for available updates by comparing installed versions against
 * the remote registry, and orchestrates update application via
 * [ExtensionLifecycleServiceImpl] (which handles rollback on failure).
 *
 * Update policies per SRS §8.1 (REQ-FUNC-0614):
 * - Automatic: download + install in background when on Wi-Fi & charging
 * - Notification only: notify user, let them decide
 * - Manual: no automatic checks
 *
 * Frequency per SRS §8.6 (REQ-FUNC-0619):
 * - Wi-Fi + charging: every 4 hours
 * - Wi-Fi no charge:  every 12 hours
 * - Mobile data:      every 24 hours (notification only)
 * - Offline:          no check
 *
 * In production, these checks are scheduled via WorkManager.
 */
class ExtensionUpdateServiceImpl(
    private val registryClient: RegistryApiClient,
    private val marketplaceService: MarketplaceServiceImpl,
    private val lifecycleService: ExtensionLifecycleServiceImpl,
    private val installDir: java.io.File
) : ExtensionUpdateService {

    private val updatesFlow = MutableSharedFlow<List<AvailableUpdate>>(replay = 1)

    enum class UpdatePolicy {
        AUTOMATIC,
        NOTIFICATION_ONLY,
        MANUAL
    }

    var currentPolicy: UpdatePolicy = UpdatePolicy.AUTOMATIC

    // -----------------------------------------------------------------------
    // Check for updates (REQ-FUNC-0615 §8.2)
    // -----------------------------------------------------------------------

    override suspend fun checkForUpdates(): List<AvailableUpdate> = withContext(Dispatchers.IO) {
        // Get list of installed extensions and their versions
        val installed = marketplaceService.getInstalledExtensions(installDir)

        if (installed.isEmpty()) {
            updatesFlow.emit(emptyList())
            return@withContext emptyList()
        }

        val installedVersions = installed.map { it.manifest.id to it.manifest.version }

        // Check remote registry for newer versions
        val updates = registryClient.checkUpdates(installedVersions)

        // Filter by compatibility (REQ-FUNC-0618 §8.5)
        val compatibleUpdates = updates.filter { update ->
            // In production: verify engines.pystudio, apiVersion, dependencies, disk space
            // For now, all updates are considered compatible
            true
        }

        // Apply the 2-hour observation window for automatic updates (REQ-FUNC-0620 §8.7)
        // In production, updates published < 2 hours ago are excluded from automatic mode
        // unless the user has opted into "early updates"

        updatesFlow.emit(compatibleUpdates)
        compatibleUpdates
    }

    // -----------------------------------------------------------------------
    // Apply updates (REQ-FUNC-0615 §8.2)
    // -----------------------------------------------------------------------

    override suspend fun applyUpdate(extensionId: String): UpdateResult = withContext(Dispatchers.IO) {
        val updates = checkForUpdates()
        val update = updates.find { it.extensionId == extensionId }
            ?: return@withContext UpdateResult(
                success = false,
                extensionId = extensionId,
                previousVersion = "",
                newVersion = "",
                errorCode = "NO_UPDATE_AVAILABLE"
            )

        // Delegate to lifecycle service (which handles rollback on failure)
        lifecycleService.updateExtension(extensionId, update.latestVersion)
    }

    override suspend fun applyAllUpdates(): List<UpdateResult> = withContext(Dispatchers.IO) {
        val updates = checkForUpdates()
        updates.map { update ->
            lifecycleService.updateExtension(update.extensionId, update.latestVersion)
        }
    }

    override fun updatesFlow(): Flow<List<AvailableUpdate>> = updatesFlow.asSharedFlow()
}
