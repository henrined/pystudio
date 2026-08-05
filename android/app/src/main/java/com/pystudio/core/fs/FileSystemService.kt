package com.pystudio.core.fs

import android.os.FileObserver
import android.util.Log
import java.io.File
import java.io.IOException

// S-7.1: FileSystemService - accès sandboxé (Scoped Storage)
class FileSystemService {
    private val observers = mutableMapOf<String, FileObserver>()

    // Restrict access to a sandbox root if needed, here we just use the provided paths 
    // assuming they are within the app's scoped storage.
    
    fun readFile(path: String): String {
        val file = File(path)
        if (!file.exists() || !file.isFile) {
            throw IOException("File does not exist or is not a regular file: $path")
        }
        return file.readText()
    }

    fun writeFile(path: String, content: String) {
        val file = File(path)
        file.parentFile?.mkdirs() // Ensure parent directories exist
        file.writeText(content)
        Log.d("FileSystemService", "Wrote file $path")
    }
    
    fun deleteFileOrDirectory(path: String): Boolean {
        val file = File(path)
        return if (file.isDirectory) {
            file.deleteRecursively()
        } else {
            file.delete()
        }
    }

    fun createDirectory(path: String): Boolean {
        return File(path).mkdirs()
    }

    fun listDirectory(path: String): List<String> {
        val file = File(path)
        if (!file.exists() || !file.isDirectory) {
            return emptyList()
        }
        return file.list()?.toList() ?: emptyList()
    }

    // S-7.1: FileObserver pour le watch
    @Suppress("DEPRECATION")
    fun watchDirectory(path: String, callback: (event: Int, file: String?) -> Unit) {
        val file = File(path)
        if (!file.exists() || !file.isDirectory) {
            Log.e("FileSystemService", "Cannot watch directory: $path does not exist or is not a directory")
            return
        }

        // We use ALL_EVENTS to catch create, delete, modify, etc.
        val observer = object : FileObserver(path, FileObserver.ALL_EVENTS) {
            override fun onEvent(event: Int, file: String?) {
                // Filter out purely access events to reduce noise if needed,
                // but passing all events to the callback lets the caller decide.
                callback(event, file)
            }
        }
        observer.startWatching()
        observers[path] = observer
        Log.i("FileSystemService", "Started watching $path")
    }

    fun stopWatching(path: String) {
        observers[path]?.stopWatching()
        observers.remove(path)
        Log.i("FileSystemService", "Stopped watching $path")
    }
}
