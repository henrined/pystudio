package com.pystudio.core.workspace

import android.content.Context
import org.junit.Test
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import androidx.test.core.app.ApplicationProvider
import java.io.File

// S-7.3: Tests: création de projet, survie de l'état au redémarrage
@RunWith(RobolectricTestRunner::class)
class WorkspaceServiceTest {

    @Test
    fun testWorkspaceLifecycleAndStateSurvival() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        
        // Use a temporary directory for testing file indexation
        val tempDir = File(context.cacheDir, "test_workspace")
        tempDir.mkdirs()
        File(tempDir, "main.py").writeText("print('hello')")
        File(tempDir, "utils.py").writeText("def util(): pass")
        
        // 1. Création de projet
        val service = WorkspaceService(context)
        service.createWorkspace("proj_1", tempDir.absolutePath)
        
        // 2. Indexation incrémentale
        service.indexFiles("proj_1", tempDir.absolutePath)
        
        // 3. Sauvegarde d'état (survie au redémarrage de l'app)
        service.saveSessionState("proj_1", listOf("main.py", "utils.py"), "main.py:10")
        
        // 4. Fermeture
        service.closeWorkspace("proj_1")
        
        // Simulate restart by creating a new service instance
        val restartedService = WorkspaceService(context)
        val sessionState = restartedService.getSessionState("proj_1")
        
        assertNotNull("Session state should survive restart", sessionState)
        assertEquals("Should have 2 open tabs", 2, sessionState!!.first.size)
        assertEquals("Active cursor should be main.py:10", "main.py:10", sessionState.second)
        
        // Cleanup
        tempDir.deleteRecursively()
    }
}
