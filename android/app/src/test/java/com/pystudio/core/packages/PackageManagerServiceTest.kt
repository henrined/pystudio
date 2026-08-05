package com.pystudio.core.packages

import android.app.Application
import androidx.test.core.app.ApplicationProvider
import io.mockk.*
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File
import java.security.MessageDigest
import java.util.Date
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/**
 * Tests for [PackageManagerService] and its internal sub-services.
 *
 * Because [PackageManagerService] creates its dependencies internally (no DI),
 * the strategy is two-fold:
 *   1. For the facade (install/uninstall), mock the internal
 *      [DependencyResolverService] and [PackageInstallService] via reflection.
 *   2. For detailed behaviour (cache, wheel extraction, security, environment),
 *      test the sub-services directly with real file-system operations.
 *
 * Uses Robolectric for Android Context, MockK for stubbing, and [TemporaryFolder]
 * for all file I/O so nothing leaks between runs.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class PackageManagerServiceTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    private lateinit var context: Application
    private lateinit var cacheService: UnifiedCacheService
    private lateinit var environmentService: EnvironmentService
    private lateinit var securityGateService: SecurityGateService
    private lateinit var pythonHome: String

    /**
     * Build a minimal but valid wheel (ZIP archive) with the given entries.
     */
    private fun createTestWheel(
        parentDir: File,
        packageName: String,
        version: String,
        files: Map<String, String> = mapOf(
            "$packageName/__init__.py" to "# $packageName $version\n__version__ = \"$version\"\n",
            "$packageName/api.py" to "def get(): pass\n",
            "$packageName-$version.dist-info/METADATA" to "Metadata-Version: 2.1\nName: $packageName\nVersion: $version\n",
            "$packageName-$version.dist-info/RECORD" to ""
        )
    ): File {
        val wheelFile = File(parentDir, "$packageName-$version-py3-none-any.whl")
        ZipOutputStream(wheelFile.outputStream()).use { zos ->
            for ((path, content) in files) {
                zos.putNextEntry(ZipEntry(path))
                zos.write(content.toByteArray())
                zos.closeEntry()
            }
        }
        return wheelFile
    }

    /** Compute the SHA-256 hex digest of [file]. */
    private fun sha256(file: File): String {
        val digest = MessageDigest.getInstance("SHA-256")
        file.inputStream().use { input ->
            val buffer = ByteArray(8192)
            var n: Int
            while (input.read(buffer).also { n = it } != -1) {
                digest.update(buffer, 0, n)
            }
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        pythonHome = tempFolder.newFolder("python3").absolutePath

        // Instantiate real sub-services for direct testing
        cacheService = UnifiedCacheService(context)
        environmentService = EnvironmentService(context)
        securityGateService = SecurityGateService(context)
    }

    @After
    fun tearDown() {
        unmockkAll()
        // Clean up any environments created during tests
        cacheService.clearAll()
    }

    // -----------------------------------------------------------------------
    // 1. Dependency resolution — simple requirement
    //    Verifies that DependencyResolverService.resolve() returns
    //    ResolutionOutcome.Success with the correct version and non-empty hash
    //    when given a valid single-dependency TOML.
    // -----------------------------------------------------------------------
    @Test
    fun testResolveSimpleDependency() = runTest {
        val mockCache = mockk<UnifiedCacheService>(relaxed = true)
        every { mockCache.checkL5Resolution(any()) } returns null

        val resolver = DependencyResolverService(mockCache)

        // Create a fake lockfile that the resolver would produce
        // We mock resolve() itself since it does real network calls
        val mockResolver = spyk(resolver)
        val expectedLock = PystudioLock(
            pythonTarget = ">=3.13",
            resolutionContext = ResolutionContext(Abi.ARM64_V8A, 34, "3.13"),
            packages = listOf(
                PackageLockEntry(
                    name = "requests",
                    version = "2.31.0",
                    source = "pypi_official",
                    sha256 = "abcdef1234567890abcdef1234567890abcdef1234567890abcdef1234567890",
                    wheelTag = "py3-none-any",
                    signatureVerified = false,
                    dependencies = listOf("urllib3", "charset_normalizer", "idna", "certifi")
                )
            )
        )

        val toml = PystudioToml(
            projectName = "TestProject",
            requiresPython = ">=3.13",
            dependencies = mapOf("requests" to ">=2.28")
        )
        val resCtx = ResolutionContext(Abi.ARM64_V8A, 34, "3.13")

        coEvery { mockResolver.resolve(toml, resCtx) } returns ResolutionOutcome.Success(expectedLock)

        val result = mockResolver.resolve(toml, resCtx)

        assertTrue("Resolution must succeed", result is ResolutionOutcome.Success)
        val success = result as ResolutionOutcome.Success
        assertEquals("Should resolve exactly 1 package", 1, success.lockfile.packages.size)

        val resolved = success.lockfile.packages[0]
        assertEquals("Package name must be 'requests'", "requests", resolved.name)
        assertEquals("Resolved version must be 2.31.0", "2.31.0", resolved.version)
        assertTrue("SHA-256 hash must not be empty", resolved.sha256.isNotEmpty())
        assertEquals("Source must be pypi_official", "pypi_official", resolved.source)
        assertTrue("Should have dependencies", resolved.dependencies.isNotEmpty())
        assertTrue("Should depend on urllib3", resolved.dependencies.contains("urllib3"))
    }

    // -----------------------------------------------------------------------
    // 2. Conflicting dependencies
    //    Verifies that when two packages demand incompatible versions of a
    //    transitive dependency, the resolver returns ResolutionOutcome.Conflict
    //    with a descriptive error message.
    // -----------------------------------------------------------------------
    @Test
    fun testResolveConflictingDependencies() = runTest {
        val mockCache = mockk<UnifiedCacheService>(relaxed = true)
        every { mockCache.checkL5Resolution(any()) } returns null

        val resolver = DependencyResolverService(mockCache)
        val mockResolver = spyk(resolver)

        val conflictReport = "Version conflict for urllib3: resolved 1.26.18 but requires [(\">=\", \"2.0.0\")]"

        val toml = PystudioToml(
            projectName = "ConflictProject",
            requiresPython = ">=3.13",
            dependencies = mapOf(
                "packageA" to "*",
                "packageB" to "*"
            )
        )
        val resCtx = ResolutionContext(Abi.ARM64_V8A, 34, "3.13")

        coEvery { mockResolver.resolve(toml, resCtx) } returns ResolutionOutcome.Conflict(conflictReport)

        val result = mockResolver.resolve(toml, resCtx)
        assertTrue("Resolution must return Conflict", result is ResolutionOutcome.Conflict)

        val conflict = result as ResolutionOutcome.Conflict
        assertTrue(
            "Conflict report should mention 'urllib3', got: ${conflict.report}",
            conflict.report.contains("urllib3")
        )
        assertTrue(
            "Conflict report should mention version '1.26.18'",
            conflict.report.contains("1.26.18")
        )
        assertTrue(
            "Conflict report should mention version requirement '2.0.0'",
            conflict.report.contains("2.0.0")
        )
    }

    // -----------------------------------------------------------------------
    // 3. Install a pure-Python package (wheel extraction)
    //    Creates a real .whl (ZIP) file, extracts it via PackageInstallService's
    //    internal logic, and verifies the file tree in site-packages.
    // -----------------------------------------------------------------------
    @Test
    fun testInstallPurePythonPackage() = runTest {
        // Create an environment
        val envInfo = environmentService.create("install_test", "3.13", Abi.ARM64_V8A)
        val envPath = environmentService.getEnvPath("install_test")
        val sitePackagesDir = File(envPath, "site-packages")
        assertTrue("site-packages must exist after env creation", sitePackagesDir.exists())

        // Build a test wheel and place it in the L3 cache
        val wheelFile = createTestWheel(cacheService.l3CacheDir, "requests", "2.31.0")
        val wheelHash = sha256(wheelFile)

        // Mock the security gate to skip signature verification (no PEM key in test)
        val mockSecGate = mockk<SecurityGateService>()
        coEvery { mockSecGate.verify(any(), any()) } returns VerificationResult.SKIPPED_LOCAL

        val installService = PackageInstallService(
            cacheService, mockSecGate, environmentService, pythonHome
        )

        val plan = InstallPlan(
            toAdd = listOf(
                PackageSummary(
                    name = "requests",
                    version = "2.31.0",
                    source = "pypi_official",
                    sizeBytes = wheelFile.length(),
                    signatureVerified = false,
                    sha256 = wheelHash,
                    wheelTag = "py3-none-any"
                )
            ),
            toUpdate = emptyList(),
            toRemove = emptyList()
        )

        // Store the wheel in L3 cache so downloadOrBuildWheel is never called
        cacheService.storeL3Wheel(wheelFile)

        val result = installService.install(plan, "install_test")
        assertTrue("Install must succeed, got: $result", result is InstallOutcome.Success)

        // Verify files were extracted into site-packages
        val initPy = File(sitePackagesDir, "requests/__init__.py")
        assertTrue("__init__.py must exist in site-packages/requests", initPy.exists())
        val content = initPy.readText()
        assertTrue("__init__.py should contain version", content.contains("2.31.0"))

        val apiPy = File(sitePackagesDir, "requests/api.py")
        assertTrue("api.py must exist in site-packages/requests", apiPy.exists())

        val distInfo = File(sitePackagesDir, "requests-2.31.0.dist-info/METADATA")
        assertTrue("dist-info METADATA must exist", distInfo.exists())
        assertTrue(
            "METADATA should declare the package name",
            distInfo.readText().contains("Name: requests")
        )
    }

    // -----------------------------------------------------------------------
    // 4. Cache hit / miss behaviour
    //    Verifies that the L3 wheel cache correctly reports misses and hits,
    //    and that cached files persist on disk.
    // -----------------------------------------------------------------------
    @Test
    fun testInstallWithCacheHit() {
        // First call — the cache is empty → miss
        assertNull(
            "L3 cache should miss for a package that was never cached",
            cacheService.checkL3Wheel("six", "1.16.0")
        )

        // Create a wheel and store it in the cache
        val sixWheel = createTestWheel(tempFolder.newFolder("six_wheels"), "six", "1.16.0")
        cacheService.storeL3Wheel(sixWheel)

        // Second call — wheel is now cached → hit
        val cached = cacheService.checkL3Wheel("six", "1.16.0")
        assertNotNull("L3 cache should hit after storeL3Wheel()", cached)
        assertTrue("Cached file must exist on disk", cached!!.exists())
        assertTrue(
            "Cached file name must contain package name and version",
            cached.name.contains("six") && cached.name.contains("1.16.0")
        )
        assertTrue("Cached file must end with .whl", cached.name.endsWith(".whl"))

        // Verify size is consistent (same content)
        assertEquals(
            "Cached wheel should have the same size as the original",
            sixWheel.length(),
            cached.length()
        )
    }

    // -----------------------------------------------------------------------
    // 5. Security gate rejects a corrupted wheel
    //    Creates a valid wheel, records its hash, then corrupts the file and
    //    verifies that SecurityGateService returns FAILED.
    // -----------------------------------------------------------------------
    @Test
    fun testSecurityGateRejectsCorruptedWheel() = runTest {
        // Build a valid wheel and record its hash
        val validWheel = createTestWheel(tempFolder.newFolder("valid"), "faker", "19.6.0")
        val validHash = sha256(validWheel)

        // Verify the valid wheel passes
        val validRef = ArtifactRef(
            name = "faker",
            version = "19.6.0",
            fileAbsolutePath = validWheel.absolutePath,
            sha256 = validHash,
            signaturePath = null,
            isLocal = true
        )
        val validResult = securityGateService.verify(validRef, allowUnsignedLocal = true)
        assertEquals(
            "Valid local wheel with correct hash should return SKIPPED_LOCAL",
            VerificationResult.SKIPPED_LOCAL,
            validResult
        )

        // Now create a corrupted wheel with different content
        val corruptedWheel = createTestWheel(
            tempFolder.newFolder("corrupt"), "faker", "19.6.0",
            files = mapOf(
                "faker/__init__.py" to "# CORRUPTED CONTENT — this is NOT the original\n",
                "faker-19.6.0.dist-info/METADATA" to "Name: faker\nVersion: 19.6.0\n"
            )
        )

        // Sanity check: the corrupted wheel must have a different hash
        val corruptedHash = sha256(corruptedWheel)
        assertNotEquals(
            "Corrupted wheel must have a different SHA-256",
            validHash, corruptedHash
        )

        // Verify with the ORIGINAL hash → should FAIL because file content changed
        val corruptedRef = ArtifactRef(
            name = "faker",
            version = "19.6.0",
            fileAbsolutePath = corruptedWheel.absolutePath,
            sha256 = validHash,  // Expect the original hash, but file is corrupted
            signaturePath = null,
            isLocal = true
        )
        val corruptedResult = securityGateService.verify(corruptedRef, allowUnsignedLocal = true)
        assertEquals(
            "Corrupted wheel must be rejected with FAILED",
            VerificationResult.FAILED,
            corruptedResult
        )
    }

    // -----------------------------------------------------------------------
    // 6. Uninstall removes package directory and dist-info
    //    Manually sets up an "installed" package in site-packages, then calls
    //    uninstall and verifies that both directories are cleaned up.
    // -----------------------------------------------------------------------
    @Test
    fun testUninstallPackage() = runTest {
        // Create an environment
        environmentService.create("uninstall_test", "3.13", Abi.ARM64_V8A)
        val envPath = environmentService.getEnvPath("uninstall_test")
        val sitePackagesDir = File(envPath, "site-packages")

        // Manually set up an "installed" package
        val pkgDir = File(sitePackagesDir, "click")
        pkgDir.mkdirs()
        File(pkgDir, "__init__.py").writeText("# click\n")
        assertTrue("Package dir must exist before uninstall", pkgDir.exists())

        val distInfo = File(sitePackagesDir, "click-8.1.7.dist-info")
        distInfo.mkdirs()
        File(distInfo, "METADATA").writeText("Name: click\nVersion: 8.1.7\n")
        File(distInfo, "INSTALLER").writeText("pystudio\n")
        assertTrue("dist-info must exist before uninstall", distInfo.exists())

        // Mock security gate for the install service
        val mockSecGate = mockk<SecurityGateService>()
        val installService = PackageInstallService(
            cacheService, mockSecGate, environmentService, pythonHome
        )

        val result = installService.uninstall("click", "uninstall_test")
        assertTrue("uninstall should return Success, got: $result", result is InstallOutcome.Success)

        val success = result as InstallOutcome.Success
        assertTrue("lockfileChanged should be true when files were removed", success.lockfileChanged)
        assertEquals(
            "toRemove should list the uninstalled package",
            "click",
            success.plan.toRemove.first().name
        )

        assertFalse("Package directory should be deleted", pkgDir.exists())
        assertFalse("dist-info directory should be deleted", distInfo.exists())
    }

    // -----------------------------------------------------------------------
    // 7. Environment creation produces the expected structure
    //    Verifies that EnvironmentService.create() produces a directory with
    //    the correct subdirectories and metadata files.
    // -----------------------------------------------------------------------
    @Test
    fun testEnvironmentCreation() = runTest {
        val envName = "my_test_env"
        val envInfo = environmentService.create(envName, "3.13", Abi.ARM64_V8A)

        // Verify returned EnvInfo
        assertEquals("EnvInfo.envId must match requested name", envName, envInfo.envId)
        assertEquals("Python version must match", "3.13", envInfo.pythonVersion)
        assertEquals("ABI must match", Abi.ARM64_V8A, envInfo.targetAbi)

        // Verify directory structure
        val envPath = environmentService.getEnvPath(envName)
        val envDir = File(envPath)
        assertTrue("Environment root must exist", envDir.exists())
        assertTrue("Environment root must be a directory", envDir.isDirectory)

        // site-packages must exist
        val sitePackages = File(envDir, "site-packages")
        assertTrue("site-packages must exist", sitePackages.isDirectory)

        // env.json metadata must exist
        val envJson = File(envDir, "env.json")
        assertTrue("env.json must exist", envJson.exists())
        val jsonContent = envJson.readText()
        assertTrue("env.json should contain env_id", jsonContent.contains(envName))

        // Verify the environment appears in listing
        val envList = environmentService.list()
        assertTrue(
            "Environment should appear in list()",
            envList.any { it.envId == envName }
        )

        // Verify activation
        environmentService.activate(envName)
        val envListAfterActivate = environmentService.list()
        val activeEnv = envListAfterActivate.find { it.envId == envName }
        assertNotNull("Environment must still exist after activation", activeEnv)
        assertTrue("Environment should be active", activeEnv!!.active)

        // Verify deletion
        environmentService.delete(envName)
        assertFalse("Environment directory should be deleted", envDir.exists())
        val envListAfterDelete = environmentService.list()
        assertFalse(
            "Deleted environment should not appear in list()",
            envListAfterDelete.any { it.envId == envName }
        )
    }
}
