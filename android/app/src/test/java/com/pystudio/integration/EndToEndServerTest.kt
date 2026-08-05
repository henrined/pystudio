package com.pystudio.integration

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.facebook.react.bridge.Arguments
import com.facebook.react.bridge.Promise
import com.facebook.react.bridge.ReactApplicationContext
import com.facebook.react.bridge.WritableArray
import com.facebook.react.bridge.WritableMap
import com.facebook.react.modules.core.DeviceEventManagerModule
import com.pystudio.bridge.PyStudioRuntimeBridgeModule
import com.pystudio.core.GitRepositoryService
import com.pystudio.core.GitStatus
import com.pystudio.core.CommitLog
import com.pystudio.core.fs.FileSystemService
import com.pystudio.core.packages.Abi
import com.pystudio.core.packages.DependencyResolverService
import com.pystudio.core.packages.PystudioToml
import com.pystudio.core.packages.ResolutionContext
import com.pystudio.core.packages.ResolutionOutcome
import com.pystudio.core.packages.UnifiedCacheService
import com.pystudio.core.workspace.WorkspaceService
import com.pystudio.runner.RunnerClient
import io.mockk.*
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File

@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE)
class EndToEndServerTest {

    private lateinit var context: Context
    private lateinit var reactContext: ReactApplicationContext

    @Before
    fun setup() {
        context = ApplicationProvider.getApplicationContext()
        reactContext = mockk<ReactApplicationContext>(relaxed = true)
        every { reactContext.applicationContext } returns context
        
        mockkStatic(System::class)
        every { System.loadLibrary(any()) } just Runs
        
        mockkStatic(Arguments::class)
        every { Arguments.createMap() } answers { 
            val map = mockk<WritableMap>(relaxed = true)
            val storage = mutableMapOf<String, Any?>()
            every { map.putString(any(), any()) } answers { storage[it.invocation.args[0] as String] = it.invocation.args[1] }
            every { map.putInt(any(), any()) } answers { storage[it.invocation.args[0] as String] = it.invocation.args[1] }
            every { map.getString(any()) } answers { storage[it.invocation.args[0] as String] as? String }
            every { map.getInt(any()) } answers { storage[it.invocation.args[0] as String] as? Int ?: 0 }
            map
        }
        every { Arguments.createArray() } answers { mockk<WritableArray>(relaxed = true) }
    }

    @Test
    fun testPythonRun() = runBlocking {
        val tempFile = File.createTempFile("test_run", ".py")
        tempFile.writeText("print('hello')")

        val module = PyStudioRuntimeBridgeModule(reactContext)
        val promise = mockk<Promise>(relaxed = true)

        mockkConstructor(RunnerClient::class)
        var listener: RunnerClient.Listener? = null

        every { anyConstructed<RunnerClient>().setListener(any()) } answers {
            listener = it.invocation.args[0] as RunnerClient.Listener?
        }
        every { anyConstructed<RunnerClient>().connect() } answers {
            listener?.onConnected()
            listener?.onStdout("hello\n")
            listener?.onDisconnected()
        }
        every { anyConstructed<RunnerClient>().getPid() } returns 1234
        every { anyConstructed<RunnerClient>().runFile(any()) } just Runs
        every { anyConstructed<RunnerClient>().initialize(any()) } returns true
        every { anyConstructed<RunnerClient>().disconnect() } just Runs

        val emitter = mockk<DeviceEventManagerModule.RCTDeviceEventEmitter>(relaxed = true)
        every { reactContext.getJSModule(DeviceEventManagerModule.RCTDeviceEventEmitter::class.java) } returns emitter

        module.run(tempFile.absolutePath, null, promise)

        Thread.sleep(100)

        verify {
            emitter.emit("runtimeStdout", match {
                val map = it as WritableMap
                map.getString("text")?.contains("hello") == true
            })
        }

        verify {
            emitter.emit("runtimeExited", match {
                val map = it as WritableMap
                map.getInt("exitCode") == 0
            })
        }
    }

    @Test
    fun testFileLifecycle() {
        val fs = FileSystemService()
        val tempDir = File.createTempFile("workspace", "")
        tempDir.delete()
        tempDir.mkdirs()

        val testFile = File(tempDir, "test.txt").absolutePath
        fs.writeFile(testFile, "initial content")

        val content = fs.readFile(testFile)
        assertEquals("initial content", content)

        var eventFired = false
        fs.watchDirectory(tempDir.absolutePath) { _, file ->
            if (file == "test.txt") {
                eventFired = true
            }
        }

        fs.writeFile(testFile, "modified content")
        
        fs.stopWatching(tempDir.absolutePath)
        
        val newContent = fs.readFile(testFile)
        assertEquals("modified content", newContent)
    }

    @Test
    fun testGitWorkflow() {
        val gitService = spyk(GitRepositoryService())
        every { gitService.nativeClone(any(), any(), any(), any()) } returns true
        every { gitService.nativeOpen(any()) } returns true
        every { gitService.nativeStageFile(any(), any()) } returns true
        every { gitService.nativeCommit(any(), any(), any(), any()) } returns true
        every { gitService.nativeGetStatus(any()) } returns GitStatus(
            currentBranch = "main", ahead = 0, behind = 0,
            modifiedFiles = emptyList(), untrackedFiles = emptyList(), stagedFiles = emptyList(), conflictedFiles = emptyList()
        )
        every { gitService.nativeLog(any(), any()) } returns listOf(
            CommitLog("hash", "Initial commit", "Author", System.currentTimeMillis())
        )

        val tempRepo = File.createTempFile("repo", "")
        tempRepo.delete()
        tempRepo.mkdirs()

        val opened = gitService.open(tempRepo.absolutePath)
        assertTrue(opened)

        gitService.stageFile(tempRepo.absolutePath, "file.txt")
        gitService.commit(tempRepo.absolutePath, "Initial commit", "Me", "me@me.com")

        val status = gitService.status(tempRepo.absolutePath)
        assertTrue(status.modifiedFiles.isEmpty())

        val log = gitService.log(tempRepo.absolutePath, 10)
        assertEquals(1, log.size)
        assertEquals("Initial commit", log[0].message)
    }

    @Test
    fun testWorkspacePersistence() = runBlocking {
        val workspaceService = WorkspaceService(context)
        val tempDir = File.createTempFile("workspace", "")
        tempDir.delete()
        tempDir.mkdirs()

        val projectId = "project1"
        workspaceService.createWorkspace(projectId, tempDir.absolutePath)

        File(tempDir, "main.py").writeText("print('hello')")
        workspaceService.indexFiles(projectId, tempDir.absolutePath)

        workspaceService.saveSessionState(projectId, listOf("main.py"), "main.py:1")
        workspaceService.closeWorkspace(projectId)

        workspaceService.createWorkspace(projectId, tempDir.absolutePath)
        val state = workspaceService.getSessionState(projectId)

        assertNotNull(state)
        assertEquals(listOf("main.py"), state!!.first)
        assertEquals("main.py:1", state.second)
    }

    @Test
    fun testPackageResolution() = runBlocking {
        val unifiedCache = UnifiedCacheService(context)
        val resolver = DependencyResolverService(unifiedCache)

        val toml = PystudioToml(
            projectName = "test",
            requiresPython = "3.11",
            dependencies = mapOf("appdirs" to "")
        )
        val ctx = ResolutionContext(Abi.ARM64_V8A, 34, "3.11")

        val result = resolver.resolve(toml, ctx)

        assertTrue("Resolution should succeed", result is ResolutionOutcome.Success)
        val success = result as ResolutionOutcome.Success
        val packages = success.lockfile.packages

        assertTrue("Packages list should not be empty", packages.isNotEmpty())
        val appdirs = packages.find { it.name == "appdirs" }
        assertNotNull("appdirs package should be resolved", appdirs)

        val sha256 = appdirs!!.sha256
        assertTrue("SHA256 must be exactly 64 characters long", sha256.length == 64)
        assertTrue("SHA256 must contain only hex characters", sha256.matches(Regex("[0-9a-f]{64}")))
        assertFalse("SHA256 must be a real hash, not mocked", sha256.startsWith("mock_hash_"))
    }
}
