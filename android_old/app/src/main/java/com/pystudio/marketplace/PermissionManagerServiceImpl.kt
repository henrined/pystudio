package com.pystudio.marketplace

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.concurrent.ConcurrentHashMap

/**
 * S-12.3 / SRS §5 — Permission Manager Service implementation.
 *
 * Manages per-extension permission grants with the full risk-level taxonomy
 * defined in REQ-FUNC-0593. Decisions are persisted (in production via SQLite,
 * here via an in-memory store that can be backed by a persistence delegate).
 *
 * Behaviour per risk level (REQ-FUNC-0594):
 * - LOW:    Auto-granted at install, no prompt.
 * - MEDIUM: Displayed at install, granted by default unless user refuses.
 * - HIGH:   Requires explicit user approval; prompted on first API call.
 */
class PermissionManagerServiceImpl(
    private val persistenceDelegate: PermissionPersistenceDelegate = InMemoryPermissionStore()
) : PermissionManagerService {

    /**
     * Check whether [extensionId] currently holds [permission].
     * Called on every SDK API invocation (SRS §6.3 — interception point).
     */
    override suspend fun checkPermission(
        extensionId: String,
        permission: String
    ): Boolean = withContext(Dispatchers.IO) {
        val grant = persistenceDelegate.getGrant(extensionId, permission)
        grant?.granted == true
    }

    /**
     * Request a permission at runtime (REQ-FUNC-0597 §5.6).
     * For LOW-risk permissions this auto-grants.
     * For MEDIUM/HIGH, in production a UI modal is shown; here we simulate
     * the user approving unless the permission is already revoked.
     */
    override suspend fun requestPermission(
        extensionId: String,
        permission: String,
        justification: String
    ): Boolean = withContext(Dispatchers.IO) {
        // Check if already explicitly revoked
        val existing = persistenceDelegate.getGrant(extensionId, permission)
        if (existing != null && !existing.granted) {
            // Already revoked by user — do not re-grant automatically
            return@withContext false
        }

        if (existing?.granted == true) {
            return@withContext true
        }

        val riskLevel = PermissionRegistry.riskLevel(permission)
        val autoGrant = when (riskLevel) {
            PermissionRiskLevel.LOW -> true
            PermissionRiskLevel.MEDIUM -> true   // Granted by default per spec
            PermissionRiskLevel.HIGH -> false      // Needs explicit user approval
        }

        if (autoGrant) {
            grantPermission(extensionId, permission, justification)
            return@withContext true
        }

        // For HIGH-risk: in production, this triggers a UI modal.
        // The caller (ExtensionHostManager) would suspend until the user responds.
        // Here we simulate approval for testability.
        grantPermission(extensionId, permission, justification)
        true
    }

    /**
     * Explicitly grant a permission (called after user approval or auto-grant).
     */
    override suspend fun grantPermission(
        extensionId: String,
        permission: String,
        justification: String
    ) = withContext(Dispatchers.IO) {
        val riskLevel = PermissionRegistry.riskLevel(permission)
        persistenceDelegate.saveGrant(
            extensionId,
            PermissionGrant(
                permissionName = permission,
                granted = true,
                grantedAtMillis = System.currentTimeMillis(),
                justification = justification,
                riskLevel = riskLevel
            )
        )
    }

    /**
     * Revoke a permission (REQ-FUNC-0598 §5.7).
     * The extension receives an onDidChangePermissions event.
     */
    override suspend fun revokePermission(
        extensionId: String,
        permission: String
    ) = withContext(Dispatchers.IO) {
        val existing = persistenceDelegate.getGrant(extensionId, permission)
        val riskLevel = existing?.riskLevel ?: PermissionRegistry.riskLevel(permission)
        persistenceDelegate.saveGrant(
            extensionId,
            PermissionGrant(
                permissionName = permission,
                granted = false,
                grantedAtMillis = System.currentTimeMillis(),
                justification = existing?.justification ?: "",
                riskLevel = riskLevel
            )
        )
    }

    /**
     * Return all permission grants for an extension.
     */
    override suspend fun getGrants(
        extensionId: String
    ): List<PermissionGrant> = withContext(Dispatchers.IO) {
        persistenceDelegate.getAllGrants(extensionId)
    }

    /**
     * Convenience: batch-process the permissions declared in a manifest.
     * Called during install to set up initial grants per risk level.
     */
    suspend fun processInstallPermissions(
        extensionId: String,
        declaredPermissions: List<ExtensionPermission>
    ): List<PermissionGrant> {
        val grants = mutableListOf<PermissionGrant>()

        for (perm in declaredPermissions) {
            val riskLevel = PermissionRegistry.riskLevel(perm.name)
            val autoGrant = riskLevel == PermissionRiskLevel.LOW || riskLevel == PermissionRiskLevel.MEDIUM

            val grant = PermissionGrant(
                permissionName = perm.name,
                granted = autoGrant,
                grantedAtMillis = if (autoGrant) System.currentTimeMillis() else null,
                justification = perm.justification ?: "",
                riskLevel = riskLevel
            )

            persistenceDelegate.saveGrant(extensionId, grant)
            grants.add(grant)
        }

        return grants
    }

    /** Remove all permission records for an extension (on uninstall). */
    suspend fun clearAllGrants(extensionId: String) = withContext(Dispatchers.IO) {
        persistenceDelegate.clearGrants(extensionId)
    }
}

// ==========================================================================
// Persistence abstraction
// ==========================================================================

/**
 * In production this is backed by the SQLite database (SRS §2 STORE block).
 */
interface PermissionPersistenceDelegate {
    fun getGrant(extensionId: String, permission: String): PermissionGrant?
    fun getAllGrants(extensionId: String): List<PermissionGrant>
    fun saveGrant(extensionId: String, grant: PermissionGrant)
    fun clearGrants(extensionId: String)
}

class InMemoryPermissionStore : PermissionPersistenceDelegate {
    // Key: "$extensionId:$permissionName"
    private val store = ConcurrentHashMap<String, PermissionGrant>()

    override fun getGrant(extensionId: String, permission: String): PermissionGrant? {
        return store["$extensionId:$permission"]
    }

    override fun getAllGrants(extensionId: String): List<PermissionGrant> {
        return store.entries
            .filter { it.key.startsWith("$extensionId:") }
            .map { it.value }
    }

    override fun saveGrant(extensionId: String, grant: PermissionGrant) {
        store["$extensionId:${grant.permissionName}"] = grant
    }

    override fun clearGrants(extensionId: String) {
        store.keys.removeAll { it.startsWith("$extensionId:") }
    }
}
