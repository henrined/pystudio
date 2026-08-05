package com.pystudio.core.workspace

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import android.util.Log
import java.io.File

// S-7.2: WorkspaceService - cycle de vie des projets, persistance SQLite
class WorkspaceService(context: Context) {
    private val dbHelper = WorkspaceDbHelper(context)
    private val activeWorkspaces = mutableSetOf<String>()

    // S-7.2.1: Création, ouverture, fermeture de workspace
    fun createWorkspace(projectId: String, path: String) {
        val db = dbHelper.writableDatabase
        db.execSQL("INSERT OR REPLACE INTO workspaces (project_id, path) VALUES (?, ?)", arrayOf(projectId, path))
        activeWorkspaces.add(projectId)
        Log.i("WorkspaceService", "Created/Opened workspace $projectId at $path")
    }

    fun closeWorkspace(projectId: String) {
        activeWorkspaces.remove(projectId)
        Log.i("WorkspaceService", "Closed workspace $projectId")
    }

    // S-7.2.2: Indexation des fichiers (incrémentale)
    fun indexFiles(projectId: String, rootPath: String) {
        val rootFile = File(rootPath)
        if (!rootFile.exists() || !rootFile.isDirectory) {
            Log.e("WorkspaceService", "Cannot index workspace: invalid root path $rootPath")
            return
        }

        val db = dbHelper.writableDatabase
        db.beginTransaction()
        try {
            // Retrieve old index to determine what was deleted
            val existingFiles = mutableMapOf<String, Long>()
            val cursor = db.rawQuery("SELECT file_path, last_modified FROM file_index WHERE project_id = ?", arrayOf(projectId))
            while (cursor.moveToNext()) {
                existingFiles[cursor.getString(0)] = cursor.getLong(1)
            }
            cursor.close()

            val currentFiles = mutableSetOf<String>()
            var indexedCount = 0

            rootFile.walkTopDown().filter { it.isFile }.forEach { file ->
                val relativePath = file.absolutePath.removePrefix(rootFile.absolutePath).removePrefix("/")
                val lastModified = file.lastModified()
                currentFiles.add(relativePath)

                val oldLastModified = existingFiles[relativePath]
                if (oldLastModified == null || oldLastModified != lastModified) {
                    db.execSQL(
                        "INSERT OR REPLACE INTO file_index (project_id, file_path, last_modified) VALUES (?, ?, ?)",
                        arrayOf(projectId, relativePath, lastModified)
                    )
                    indexedCount++
                }
            }

            // Remove deleted files
            val deletedFiles = existingFiles.keys - currentFiles
            for (deletedFile in deletedFiles) {
                db.execSQL(
                    "DELETE FROM file_index WHERE project_id = ? AND file_path = ?",
                    arrayOf(projectId, deletedFile)
                )
            }

            db.setTransactionSuccessful()
            Log.i("WorkspaceService", "Indexed $indexedCount updated files, removed ${deletedFiles.size} deleted files for workspace $projectId")
        } finally {
            db.endTransaction()
        }
    }

    // S-7.2.3: Gestion de l'état de session (onglets ouverts, curseurs)
    fun saveSessionState(projectId: String, openTabs: List<String>, activeCursor: String) {
        val db = dbHelper.writableDatabase
        val tabsStr = openTabs.joinToString(",")
        db.execSQL("INSERT OR REPLACE INTO session_state (project_id, open_tabs, active_cursor) VALUES (?, ?, ?)", arrayOf(projectId, tabsStr, activeCursor))
        Log.i("WorkspaceService", "Saved session state for $projectId")
    }

    fun getSessionState(projectId: String): Pair<List<String>, String>? {
        val db = dbHelper.readableDatabase
        var result: Pair<List<String>, String>? = null
        val cursor = db.rawQuery("SELECT open_tabs, active_cursor FROM session_state WHERE project_id = ?", arrayOf(projectId))
        if (cursor.moveToFirst()) {
            val tabsStr = cursor.getString(0) ?: ""
            val openTabs = if (tabsStr.isNotEmpty()) tabsStr.split(",") else emptyList()
            val activeCursor = cursor.getString(1) ?: ""
            result = Pair(openTabs, activeCursor)
        }
        cursor.close()
        return result
    }
}

class WorkspaceDbHelper(context: Context) : SQLiteOpenHelper(context, "pystudio_workspace.db", null, 2) {
    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL("CREATE TABLE workspaces (project_id TEXT PRIMARY KEY, path TEXT)")
        db.execSQL("CREATE TABLE file_index (project_id TEXT, file_path TEXT, last_modified INTEGER, PRIMARY KEY(project_id, file_path))")
        db.execSQL("CREATE TABLE session_state (project_id TEXT PRIMARY KEY, open_tabs TEXT, active_cursor TEXT)")
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        if (oldVersion < 2) {
            db.execSQL("DROP TABLE IF EXISTS file_index")
            db.execSQL("CREATE TABLE file_index (project_id TEXT, file_path TEXT, last_modified INTEGER, PRIMARY KEY(project_id, file_path))")
        } else {
            db.execSQL("DROP TABLE IF EXISTS workspaces")
            db.execSQL("DROP TABLE IF EXISTS file_index")
            db.execSQL("DROP TABLE IF EXISTS session_state")
            onCreate(db)
        }
    }
}
