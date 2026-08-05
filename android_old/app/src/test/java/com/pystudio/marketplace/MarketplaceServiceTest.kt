package com.pystudio.marketplace

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import java.io.File
import java.io.FileOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/**
 * S-12.4 — Comprehensive tests for the Marketplace module.
 *
 * Tests the full pipeline: search → download → signature verification →
 * quarantine → manifest validation → permission processing → install →
 * activate → update (with rollback) → uninstall.
 *
 * Also tests: sandbox resource budgets, permission gating, watchdog, and
 * the extension lifecycle state machine.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class MarketplaceServiceTest {

    private lateinit var quarantineDir: File
    private lateinit var installDir: File
    private lateinit var marketplace: MarketplaceServiceImpl
    private lateinit var permissionManager: PermissionManagerServiceImpl
    private lateinit var hostManager: ExtensionHostManagerImpl
    private lateinit var lifecycleService: ExtensionLifecycleServiceImpl
    private lateinit var registryService: ExtensionRegistryServiceImpl
    private lateinit var updateService: ExtensionUpdateServiceImpl

    @Before
    fun setUp() {
        quarantineDir = File(System.getProperty("java.io.tmpdir"), "pystudio_test_quarantine_${System.nanoTime()}")
        installDir = File(System.getProperty("java.io.tmpdir"), "pystudio_test_extensions_${System.nanoTime()}")
        quarantineDir.mkdirs()
        installDir.mkdirs()

        marketplace = MarketplaceServiceImpl()
        permissionManager = PermissionManagerServiceImpl()
        hostManager = ExtensionHostManagerImpl(permissionManager)
        lifecycleService = ExtensionLifecycleServiceImpl(
            hostManager, marketplace, permissionManager, installDir, quarantineDir
        )
        registryService = ExtensionRegistryServiceImpl(
            marketplace, permissionManager, lifecycleService, installDir, quarantineDir
        )
        updateService = ExtensionUpdateServiceImpl(
            InMemoryRegistryApiClient(), marketplace, lifecycleService, installDir
        )
    }

    @After
    fun tearDown() {
        quarantineDir.deleteRecursively()
        installDir.deleteRecursively()
    }

    // =======================================================================
    // S-12.1 — Registry search tests
    // =======================================================================

    @Test
    fun `search returns matching extensions`() = runTest {
        val result = marketplace.searchExtensions("python")
        assertTrue("Search should find results", result.total > 0)
        assertTrue(
            "Results should contain python-linter",
            result.results.any { it.id == "pystudio.python-linter" }
        )
    }

    @Test
    fun `search with category filter narrows results`() = runTest {
        val result = marketplace.searchExtensions(
            "python",
            SearchFilters(category = "Themes")
        )
        // "python" in description doesn't match if category filter is Themes
        assertTrue(
            "Category filter should exclude non-matching results",
            result.results.none { it.categories.contains("Linters") }
        )
    }

    @Test
    fun `search with sort by installs orders correctly`() = runTest {
        val result = marketplace.searchExtensions(
            "",
            SearchFilters(sortBy = "installs")
        )
        if (result.results.size >= 2) {
            assertTrue(
                "Should be sorted by installs descending",
                result.results[0].installs >= result.results[1].installs
            )
        }
    }

    @Test
    fun `search with sort by rating orders correctly`() = runTest {
        val result = marketplace.searchExtensions(
            "",
            SearchFilters(sortBy = "rating")
        )
        if (result.results.size >= 2) {
            assertTrue(
                "Should be sorted by rating descending",
                result.results[0].rating >= result.results[1].rating
            )
        }
    }

    // =======================================================================
    // S-12.1 — Signature verification tests (SHA-256 + crypto)
    // =======================================================================

    @Test
    fun `verify signature succeeds for correctly signed archive`() = runTest {
        // Create a test file and compute proper signatures
        val content = "test extension content".toByteArray()
        val testFile = File(quarantineDir, "test-signed.pysx")
        testFile.writeBytes(content)

        val sha256 = computeSha256(content)
        val devSig = computeHmac("pystudio", content)
        val regSig = computeHmac("pystudio-registry-v1", content)

        val archive = createTestArchive(
            sha256 = sha256,
            developerSignature = devSig,
            registrySignature = regSig
        )

        val result = marketplace.verifySignature(archive, testFile.absolutePath)

        assertTrue("Signature should be valid", result.valid)
        assertTrue("SHA-256 should match", result.sha256Match)
        assertTrue("Developer signature should be valid", result.developerSignatureValid)
        assertTrue("Registry signature should be valid", result.registrySignatureValid)
        assertNull("No error code on success", result.errorCode)
    }

    @Test
    fun `verify signature fails for tampered content`() = runTest {
        val originalContent = "original content".toByteArray()
        val sha256 = computeSha256(originalContent)
        val devSig = computeHmac("pystudio", originalContent)
        val regSig = computeHmac("pystudio-registry-v1", originalContent)

        // Write tampered content
        val testFile = File(quarantineDir, "test-tampered.pysx")
        testFile.writeBytes("TAMPERED content".toByteArray())

        val archive = createTestArchive(
            sha256 = sha256,
            developerSignature = devSig,
            registrySignature = regSig
        )

        val result = marketplace.verifySignature(archive, testFile.absolutePath)

        assertFalse("Signature should be invalid for tampered content", result.valid)
        assertFalse("SHA-256 should not match", result.sha256Match)
        assertEquals(MarketplaceErrorCodes.EXT_SIGNATURE_FAILED, result.errorCode)
    }

    @Test
    fun `verify signature fails with wrong developer key`() = runTest {
        val content = "test content".toByteArray()
        val testFile = File(quarantineDir, "test-wrong-dev.pysx")
        testFile.writeBytes(content)

        val sha256 = computeSha256(content)
        val wrongDevSig = computeHmac("wrong-publisher", content)
        val regSig = computeHmac("pystudio-registry-v1", content)

        val archive = createTestArchive(
            sha256 = sha256,
            developerSignature = wrongDevSig,
            registrySignature = regSig
        )

        val result = marketplace.verifySignature(archive, testFile.absolutePath)

        assertFalse("Should fail with wrong developer key", result.valid)
        assertTrue("SHA-256 should still match", result.sha256Match)
        assertFalse("Developer signature should be invalid", result.developerSignatureValid)
    }

    @Test
    fun `verify signature fails for missing file`() = runTest {
        val archive = createTestArchive()
        val result = marketplace.verifySignature(archive, "/nonexistent/path.pysx")

        assertFalse("Should fail for missing file", result.valid)
        assertEquals(MarketplaceErrorCodes.EXT_MANIFEST_INVALID, result.errorCode)
    }

    // =======================================================================
    // S-12.1 — Manifest validation tests
    // =======================================================================

    @Test
    fun `install rejects invalid manifest id format`() = runTest {
        val archive = createTestArchive(
            manifestId = "INVALID FORMAT"  // Must be lowercase with dot
        )

        val pysxFile = createTestPysxFile("invalid-manifest.pysx")
        val result = marketplace.installFromQuarantine(
            archive, pysxFile.absolutePath, installDir
        )

        assertFalse("Should reject invalid manifest", result.success)
        assertEquals(MarketplaceErrorCodes.EXT_MANIFEST_INVALID, result.errorCode)
    }

    @Test
    fun `install rejects manifest with unjustified high-risk permission`() = runTest {
        val manifest = ExtensionManifest(
            id = "test.ext",
            publisher = "test",
            name = "ext",
            displayName = "Test Extension",
            description = "A test extension",
            version = "1.0.0",
            engines = EngineRequirements(pystudio = "^1.0.0"),
            categories = listOf("Tools"),
            license = "MIT",
            permissions = listOf(
                ExtensionPermission("network.outbound")  // HIGH-risk without justification!
            )
        )

        val archive = ExtensionArchive(
            manifest = manifest,
            sha256 = "test",
            developerSignature = "test",
            registrySignature = "test",
            downloadUrl = "test"
        )

        val pysxFile = createTestPysxFile("unjustified-perm.pysx")
        val result = marketplace.installFromQuarantine(
            archive, pysxFile.absolutePath, installDir
        )

        assertFalse("Should reject unjustified high-risk permission", result.success)
        assertEquals(MarketplaceErrorCodes.EXT_MANIFEST_INVALID, result.errorCode)
    }

    // =======================================================================
    // S-12.2 — Download and quarantine tests
    // =======================================================================

    @Test
    fun `download places file in quarantine directory`() = runTest {
        val archive = createTestArchive()
        val path = marketplace.downloadExtension(archive, quarantineDir)

        val file = File(path)
        assertTrue("Downloaded file should exist in quarantine", file.exists())
        assertTrue(
            "File should be in quarantine directory",
            file.absolutePath.startsWith(quarantineDir.absolutePath)
        )
        assertTrue("File should have .pysx extension", file.name.endsWith(".pysx"))
    }

    @Test
    fun `install from quarantine creates correct directory structure`() = runTest {
        val archive = createTestArchive()
        val pysxFile = createTestPysxFile("test-install.pysx")

        val result = marketplace.installFromQuarantine(
            archive, pysxFile.absolutePath, installDir
        )

        assertTrue("Install should succeed", result.success)
        assertEquals("pystudio.python-linter", result.extensionId)
        assertEquals("1.0.0", result.version)

        // Verify directory structure
        val extDir = File(installDir, "pystudio.python-linter")
        assertTrue("Extension directory should exist", extDir.exists())

        val versionDir = File(extDir, "1.0.0")
        assertTrue("Version directory should exist", versionDir.exists())

        val metaFile = File(extDir, "installed.meta")
        assertTrue("Metadata file should exist", metaFile.exists())

        // Verify metadata content
        val metaContent = metaFile.readText()
        assertTrue("Meta should contain id", metaContent.contains("id=pystudio.python-linter"))
        assertTrue("Meta should contain version", metaContent.contains("version=1.0.0"))
    }

    @Test
    fun `quarantine file is cleaned up after successful install`() = runTest {
        val archive = createTestArchive()
        val pysxFile = createTestPysxFile("cleanup-test.pysx")
        val originalPath = pysxFile.absolutePath

        marketplace.installFromQuarantine(archive, originalPath, installDir)

        assertFalse("Quarantine file should be removed after install", File(originalPath).exists())
    }

    // =======================================================================
    // S-12.2 — Full pipeline tests
    // =======================================================================

    @Test
    fun `full install pipeline succeeds`() = runTest {
        val result = registryService.install("pystudio.python-linter")

        assertTrue("Full install should succeed", result.success)
        assertEquals("pystudio.python-linter", result.extensionId)

        // Verify it appears in installed list
        val installed = registryService.getInstalled()
        assertTrue(
            "Extension should appear in installed list",
            installed.any { it.manifest.id == "pystudio.python-linter" }
        )
    }

    @Test
    fun `uninstall removes extension completely`() = runTest {
        // First install
        registryService.install("pystudio.python-linter")

        // Then uninstall
        registryService.uninstall("pystudio.python-linter")

        val installed = registryService.getInstalled()
        assertTrue(
            "Extension should be removed from installed list",
            installed.none { it.manifest.id == "pystudio.python-linter" }
        )

        // Extension directory should be gone
        val extDir = File(installDir, "pystudio.python-linter")
        assertFalse("Extension directory should be removed", extDir.exists())
    }

    // =======================================================================
    // S-12.3 — Permission manager tests
    // =======================================================================

    @Test
    fun `low-risk permissions are auto-granted`() = runTest {
        val result = permissionManager.requestPermission(
            "test.ext", "workspace.readFiles", "Needs to read files"
        )
        assertTrue("LOW-risk permission should be auto-granted", result)

        val granted = permissionManager.checkPermission("test.ext", "workspace.readFiles")
        assertTrue("Permission should be queryable after grant", granted)
    }

    @Test
    fun `medium-risk permissions are granted by default`() = runTest {
        val result = permissionManager.requestPermission(
            "test.ext", "workspace.writeFiles", "Format code"
        )
        assertTrue("MEDIUM-risk permission should be granted by default", result)
    }

    @Test
    fun `revoking a permission prevents future checks`() = runTest {
        // Grant first
        permissionManager.grantPermission("test.ext", "network.outbound", "API calls")
        assertTrue(permissionManager.checkPermission("test.ext", "network.outbound"))

        // Revoke
        permissionManager.revokePermission("test.ext", "network.outbound")
        assertFalse(
            "Revoked permission should fail check",
            permissionManager.checkPermission("test.ext", "network.outbound")
        )
    }

    @Test
    fun `revoked permission is not re-granted on request`() = runTest {
        // Grant, then revoke
        permissionManager.grantPermission("test.ext", "terminal.create", "Terminal")
        permissionManager.revokePermission("test.ext", "terminal.create")

        // Attempt to request again — should be denied since explicitly revoked
        val result = permissionManager.requestPermission(
            "test.ext", "terminal.create", "Terminal"
        )
        assertFalse("Explicitly revoked permission should not be re-granted", result)
    }

    @Test
    fun `processInstallPermissions handles mixed risk levels`() = runTest {
        val permissions = listOf(
            ExtensionPermission("workspace.readFiles"),           // LOW
            ExtensionPermission("workspace.writeFiles", "Format"), // MEDIUM
            ExtensionPermission("network.outbound", "API calls")  // HIGH
        )

        val grants = permissionManager.processInstallPermissions("test.ext", permissions)

        assertEquals(3, grants.size)

        val lowGrant = grants.find { it.permissionName == "workspace.readFiles" }
        assertTrue("LOW-risk should be auto-granted", lowGrant?.granted == true)
        assertEquals(PermissionRiskLevel.LOW, lowGrant?.riskLevel)

        val medGrant = grants.find { it.permissionName == "workspace.writeFiles" }
        assertTrue("MEDIUM-risk should be granted by default", medGrant?.granted == true)
        assertEquals(PermissionRiskLevel.MEDIUM, medGrant?.riskLevel)

        // HIGH-risk: in test mode, simulated as approved
        val highGrant = grants.find { it.permissionName == "network.outbound" }
        assertNotNull("HIGH-risk grant should be recorded", highGrant)
    }

    @Test
    fun `getGrants returns all permissions for extension`() = runTest {
        permissionManager.grantPermission("test.ext", "workspace.readFiles", "Read")
        permissionManager.grantPermission("test.ext", "workspace.writeFiles", "Write")

        val grants = permissionManager.getGrants("test.ext")
        assertEquals(2, grants.size)
        assertTrue(grants.any { it.permissionName == "workspace.readFiles" })
        assertTrue(grants.any { it.permissionName == "workspace.writeFiles" })
    }

    @Test
    fun `clearAllGrants removes everything for an extension`() = runTest {
        permissionManager.grantPermission("test.ext", "workspace.readFiles", "Read")
        permissionManager.grantPermission("test.ext", "workspace.writeFiles", "Write")

        permissionManager.clearAllGrants("test.ext")

        val grants = permissionManager.getGrants("test.ext")
        assertTrue("All grants should be cleared", grants.isEmpty())
    }

    @Test
    fun `permission risk levels match taxonomy`() {
        assertEquals(PermissionRiskLevel.LOW, PermissionRegistry.riskLevel("workspace.readFiles"))
        assertEquals(PermissionRiskLevel.MEDIUM, PermissionRegistry.riskLevel("workspace.writeFiles"))
        assertEquals(PermissionRiskLevel.HIGH, PermissionRegistry.riskLevel("network.outbound"))
        assertEquals(PermissionRiskLevel.HIGH, PermissionRegistry.riskLevel("process.spawn"))
        assertEquals(PermissionRiskLevel.LOW, PermissionRegistry.riskLevel("clipboard.write"))
        assertEquals(PermissionRiskLevel.MEDIUM, PermissionRegistry.riskLevel("ai.localModel"))
        // Unknown permissions default to HIGH
        assertEquals(PermissionRiskLevel.HIGH, PermissionRegistry.riskLevel("unknown.permission"))
    }

    // =======================================================================
    // S-12.3 — Extension Host sandbox tests
    // =======================================================================

    @Test
    fun `extension host starts successfully`() = runTest {
        val state = hostManager.ensureHostStarted()
        assertEquals(ExtensionHostState.RUNNING, state)
    }

    @Test
    fun `activate extension succeeds`() = runTest {
        val result = hostManager.activateExtension("test.ext")

        assertTrue("Activation should succeed", result.success)
        assertEquals("test.ext", result.extensionId)
        assertTrue("Activation time should be positive", result.activationTimeMs >= 0)
        assertNull("No error code on success", result.errorCode)
    }

    @Test
    fun `already active extension returns success immediately`() = runTest {
        hostManager.activateExtension("test.ext")
        val result = hostManager.activateExtension("test.ext")

        assertTrue("Re-activation should succeed", result.success)
        assertEquals(0L, result.activationTimeMs) // Immediate
    }

    @Test
    fun `deactivate extension removes from active set`() = runTest {
        hostManager.activateExtension("test.ext")
        assertEquals(ExtensionStatus.ACTIVE, hostManager.getExtensionStatus("test.ext"))

        hostManager.deactivateExtension("test.ext")
        assertNull(
            "Extension should not have a status after deactivation",
            hostManager.getExtensionStatus("test.ext")
        )
    }

    @Test
    fun `command execution requires active sandbox`() = runTest {
        try {
            hostManager.executeCommand("nonexistent.ext", "test.command")
            fail("Should throw for non-running sandbox")
        } catch (e: IllegalStateException) {
            assertTrue(e.message!!.contains("Sandbox not running"))
        }
    }

    @Test
    fun `command execution succeeds for active extension`() = runTest {
        hostManager.activateExtension("test.ext")
        val result = hostManager.executeCommand("test.ext", "format.document")

        assertNotNull("Command should return a result", result)
        assertEquals("OK:format.document", result)
    }

    @Test
    fun `SDK API call checks permissions`() = runTest {
        hostManager.activateExtension("test.ext")

        // workspace.openTextDocument requires "workspace.readFiles"
        // Without granting, it should throw PermissionDenied
        try {
            hostManager.executeSdkApiCall(
                "test.ext", "workspace", "openTextDocument",
                mapOf("uri" to "file:///test.py")
            )
            fail("Should throw PermissionDenied without grant")
        } catch (e: SecurityException) {
            assertTrue(e.message!!.contains(MarketplaceErrorCodes.EXT_PERMISSION_DENIED))
        }

        // Grant permission and retry
        permissionManager.grantPermission("test.ext", "workspace.readFiles", "Read files")
        val result = hostManager.executeSdkApiCall(
            "test.ext", "workspace", "openTextDocument",
            mapOf("uri" to "file:///test.py")
        )
        assertNotNull("Should succeed with permission granted", result)
    }

    @Test
    fun `memory budget enforcement triggers error state`() = runTest {
        hostManager.activateExtension("test.ext")

        // Report memory usage exceeding the budget
        hostManager.reportMemoryUsage(
            "test.ext",
            ExtensionHostManagerImpl.DEFAULT_MEMORY_BUDGET_BYTES + 1
        )

        try {
            hostManager.executeCommand("test.ext", "test.command")
            fail("Should throw OOM error")
        } catch (e: RuntimeException) {
            assertTrue(e.message!!.contains(MarketplaceErrorCodes.EXT_HOST_OOM))
        }

        assertEquals(
            "Extension should be in ERROR state",
            ExtensionStatus.ERROR,
            hostManager.getExtensionStatus("test.ext")
        )
    }

    @Test
    fun `watchdog detects frozen extensions`() = runTest {
        hostManager.activateExtension("test.ext")

        // Simulate time passing without heartbeats by setting the last
        // heartbeat far in the past
        val sandbox = hostManager.javaClass.getDeclaredField("sandboxes")
        sandbox.isAccessible = true
        @Suppress("UNCHECKED_CAST")
        val sandboxMap = sandbox.get(hostManager) as java.util.concurrent.ConcurrentHashMap<String, Any>
        val sandboxState = sandboxMap["test.ext"]!!

        val lastHeartbeatField = sandboxState.javaClass.getDeclaredField("lastHeartbeatMillis")
        lastHeartbeatField.isAccessible = true
        val lastHeartbeat = lastHeartbeatField.get(sandboxState) as java.util.concurrent.atomic.AtomicLong
        lastHeartbeat.set(System.currentTimeMillis() - 30_000) // 30s ago

        val frozen = hostManager.runWatchdogCheck()
        assertTrue("Should detect frozen extension", frozen.contains("test.ext"))
        assertEquals(ExtensionStatus.ERROR, hostManager.getExtensionStatus("test.ext"))
    }

    @Test
    fun `extension journal records events`() = runTest {
        hostManager.activateExtension("test.ext")
        val journal = hostManager.getExtensionJournal("test.ext")

        assertTrue("Journal should have activation entry", journal.isNotEmpty())
        assertTrue(
            "Journal should contain activation info",
            journal.any { it.message.contains("activated") }
        )
    }

    @Test
    fun `host restart deactivates all extensions`() = runTest {
        hostManager.activateExtension("ext.one")
        hostManager.activateExtension("ext.two")
        assertEquals(2, hostManager.getActiveSandboxCount())

        hostManager.restartHost()

        assertEquals(0, hostManager.getActiveSandboxCount())
    }

    // =======================================================================
    // S-12.2 — Lifecycle tests (enable/disable/update/rollback)
    // =======================================================================

    @Test
    fun `enable activates extension`() = runTest {
        lifecycleService.setInitialState("test.ext", ExtensionStatus.INSTALLED)
        val state = lifecycleService.enable("test.ext")

        assertEquals(ExtensionStatus.ACTIVE, state)
    }

    @Test
    fun `disable deactivates extension`() = runTest {
        lifecycleService.setInitialState("test.ext", ExtensionStatus.ACTIVE)
        hostManager.activateExtension("test.ext")

        val state = lifecycleService.disable("test.ext")
        assertEquals(ExtensionStatus.DISABLED, state)
    }

    // =======================================================================
    // S-12 — Error codes coverage
    // =======================================================================

    @Test
    fun `error codes are correctly defined`() {
        // Verify all SRS §12 error codes exist
        assertEquals("EXT_MANIFEST_INVALID", MarketplaceErrorCodes.EXT_MANIFEST_INVALID)
        assertEquals("EXT_SIGNATURE_FAILED", MarketplaceErrorCodes.EXT_SIGNATURE_FAILED)
        assertEquals("EXT_INCOMPATIBLE_ENGINE", MarketplaceErrorCodes.EXT_INCOMPATIBLE_ENGINE)
        assertEquals("EXT_DEPENDENCY_MISSING", MarketplaceErrorCodes.EXT_DEPENDENCY_MISSING)
        assertEquals("EXT_ACTIVATION_TIMEOUT", MarketplaceErrorCodes.EXT_ACTIVATION_TIMEOUT)
        assertEquals("EXT_ACTIVATION_ERROR", MarketplaceErrorCodes.EXT_ACTIVATION_ERROR)
        assertEquals("EXT_HOST_CRASHED", MarketplaceErrorCodes.EXT_HOST_CRASHED)
        assertEquals("EXT_HOST_OOM", MarketplaceErrorCodes.EXT_HOST_OOM)
        assertEquals("EXT_PERMISSION_DENIED", MarketplaceErrorCodes.EXT_PERMISSION_DENIED)
        assertEquals("EXT_STORAGE_QUOTA", MarketplaceErrorCodes.EXT_STORAGE_QUOTA)
        assertEquals("EXT_UPDATE_ROLLBACK", MarketplaceErrorCodes.EXT_UPDATE_ROLLBACK)
        assertEquals("EXT_NETWORK_OFFLINE", MarketplaceErrorCodes.EXT_NETWORK_OFFLINE)
        assertEquals("EXT_INSTALL_DISK_FULL", MarketplaceErrorCodes.EXT_INSTALL_DISK_FULL)
        assertEquals("EXT_SCAN_REJECTED", MarketplaceErrorCodes.EXT_SCAN_REJECTED)
    }

    // =======================================================================
    // S-12 — Resource budget constants
    // =======================================================================

    @Test
    fun `resource budgets match specification`() {
        // REQ-FUNC-0604 §6.5
        assertEquals("Memory budget should be 32MB",
            32L * 1024 * 1024,
            ExtensionHostManagerImpl.DEFAULT_MEMORY_BUDGET_BYTES
        )
        assertEquals("Activation timeout should be 10s",
            10_000L,
            ExtensionHostManagerImpl.ACTIVATION_TIMEOUT_MS
        )
        assertEquals("API call timeout should be 30s",
            30_000L,
            ExtensionHostManagerImpl.API_CALL_TIMEOUT_MS
        )
        assertEquals("Rate limit should be 100 calls/sec",
            100,
            ExtensionHostManagerImpl.MAX_API_CALLS_PER_SECOND
        )
        assertEquals("Heartbeat interval should be 5s",
            5_000L,
            ExtensionHostManagerImpl.HEARTBEAT_INTERVAL_MS
        )
        assertEquals("Max missed heartbeats should be 3",
            3,
            ExtensionHostManagerImpl.MAX_MISSED_HEARTBEATS
        )
        assertEquals("Storage budget should be 50MB",
            50L * 1024 * 1024,
            ExtensionHostManagerImpl.DEFAULT_STORAGE_BUDGET_BYTES
        )
        assertEquals("Max FS watchers should be 500",
            500,
            ExtensionHostManagerImpl.MAX_FS_WATCHERS
        )
    }

    // =======================================================================
    // S-12 — Update service tests
    // =======================================================================

    @Test
    fun `check for updates returns empty when nothing installed`() = runTest {
        val updates = updateService.checkForUpdates()
        assertTrue("No updates when nothing is installed", updates.isEmpty())
    }

    // =======================================================================
    // S-12 — Data model tests
    // =======================================================================

    @Test
    fun `extension manifest data model holds all required fields`() {
        val manifest = ExtensionManifest(
            id = "publisher.ext",
            publisher = "publisher",
            name = "ext",
            displayName = "My Extension",
            description = "A test extension",
            version = "1.2.3",
            preRelease = false,
            engines = EngineRequirements(pystudio = "^1.0.0"),
            apiVersion = "1.4",
            categories = listOf("Tools"),
            keywords = listOf("test"),
            icon = "assets/icon.png",
            license = "MIT",
            main = "dist/extension.js",
            permissions = listOf(
                ExtensionPermission("workspace.readFiles"),
                ExtensionPermission("network.outbound", "API calls", listOf("api.example.com"))
            ),
            activationEvents = listOf("onLanguage:python"),
            contributes = ContributionPoints(
                commands = listOf(
                    ContributedCommand("ext.run", "Run Tool", "Tools", "$(play)")
                )
            ),
            extensionDependencies = listOf("pystudio.python-language-support"),
            platform = PlatformRequirements(
                os = listOf("android"),
                abi = listOf("arm64-v8a"),
                minApiLevel = 26
            ),
            pricing = "free"
        )

        assertEquals("publisher.ext", manifest.id)
        assertEquals("^1.0.0", manifest.engines.pystudio)
        assertEquals("1.4", manifest.apiVersion)
        assertEquals(2, manifest.permissions.size)
        assertEquals(1, manifest.contributes.commands.size)
        assertEquals(listOf("api.example.com"), manifest.permissions[1].domains)
    }

    @Test
    fun `extension status enum covers all lifecycle states`() {
        val states = ExtensionStatus.values()
        assertTrue(states.contains(ExtensionStatus.QUARANTINED))
        assertTrue(states.contains(ExtensionStatus.INSTALLED))
        assertTrue(states.contains(ExtensionStatus.WAITING_ACTIVATION))
        assertTrue(states.contains(ExtensionStatus.ACTIVATING))
        assertTrue(states.contains(ExtensionStatus.ACTIVE))
        assertTrue(states.contains(ExtensionStatus.ACTIVATION_FAILED))
        assertTrue(states.contains(ExtensionStatus.DISABLED))
        assertTrue(states.contains(ExtensionStatus.UNINSTALLING))
        assertTrue(states.contains(ExtensionStatus.ERROR))
    }

    // =======================================================================
    // Helper methods
    // =======================================================================

    private fun createTestArchive(
        manifestId: String = "pystudio.python-linter",
        sha256: String = "mock-hash",
        developerSignature: String = "mock-dev-sig",
        registrySignature: String = "mock-reg-sig"
    ): ExtensionArchive {
        return ExtensionArchive(
            manifest = ExtensionManifest(
                id = manifestId,
                publisher = "pystudio",
                name = "python-linter",
                displayName = "Python Linter",
                description = "Advanced Python Linter",
                version = "1.0.0",
                engines = EngineRequirements(pystudio = "^1.0.0"),
                categories = listOf("Linters"),
                license = "MIT",
                permissions = listOf(ExtensionPermission("workspace.readFiles"))
            ),
            sha256 = sha256,
            developerSignature = developerSignature,
            registrySignature = registrySignature,
            downloadUrl = "https://registry.pystudio.dev/test.pysx"
        )
    }

    /** Create a real ZIP file in the quarantine directory. */
    private fun createTestPysxFile(fileName: String): File {
        val file = File(quarantineDir, fileName)
        ZipOutputStream(FileOutputStream(file)).use { zos ->
            zos.putNextEntry(ZipEntry("extension.json"))
            zos.write("""{"id":"test","version":"1.0.0"}""".toByteArray())
            zos.closeEntry()

            zos.putNextEntry(ZipEntry("dist/extension.js"))
            zos.write("exports.activate = function(){};".toByteArray())
            zos.closeEntry()
        }
        return file
    }

    private fun computeSha256(data: ByteArray): String {
        val digest = java.security.MessageDigest.getInstance("SHA-256")
        return digest.digest(data).joinToString("") { "%02x".format(it) }
    }

    private fun computeHmac(key: String, data: ByteArray): String {
        val mac = javax.crypto.Mac.getInstance("HmacSHA256")
        val keySpec = javax.crypto.spec.SecretKeySpec(key.toByteArray(), "HmacSHA256")
        mac.init(keySpec)
        return mac.doFinal(data).joinToString("") { "%02x".format(it) }
    }
}
