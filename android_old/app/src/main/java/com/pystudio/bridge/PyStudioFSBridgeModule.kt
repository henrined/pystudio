package com.pystudio.bridge

import com.facebook.react.bridge.*
import com.facebook.react.modules.core.DeviceEventManagerModule
import com.pystudio.core.fs.FileSystemService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class PyStudioFSBridgeModule(private val reactContext: ReactApplicationContext) : ReactContextBaseJavaModule(reactContext) {
    private val scope = CoroutineScope(Dispatchers.IO)
    private val fsService = FileSystemService()
    
    override fun getName(): String = "PyStudioFSBridge"

    @ReactMethod
    fun readFile(path: String, promise: Promise) {
        scope.launch {
            try {
                val content = fsService.readFile(path)
                promise.resolve(content)
            } catch (e: Exception) {
                promise.reject("FS_ERROR", e)
            }
        }
    }

    @ReactMethod
    fun writeFile(path: String, content: String, promise: Promise) {
        scope.launch {
            try {
                fsService.writeFile(path, content)
                promise.resolve(true)
            } catch (e: Exception) {
                promise.reject("FS_ERROR", e)
            }
        }
    }

    @ReactMethod
    fun listDir(path: String, promise: Promise) {
        scope.launch {
            try {
                val list = fsService.listDirectory(path)
                val arr = Arguments.createArray()
                list.forEach { 
                    val map = Arguments.createMap()
                    map.putString("name", it)
                    val f = java.io.File(path, it)
                    map.putBoolean("isDirectory", f.isDirectory)
                    map.putDouble("size", f.length().toDouble())
                    arr.pushMap(map)
                }
                promise.resolve(arr)
            } catch (e: Exception) {
                promise.reject("FS_ERROR", e)
            }
        }
    }

    @ReactMethod
    fun watchDir(path: String, promise: Promise) {
        scope.launch {
            try {
                fsService.watchDirectory(path) { event, file ->
                    val map = Arguments.createMap()
                    map.putInt("event", event)
                    map.putString("file", file)
                    reactContext.getJSModule(DeviceEventManagerModule.RCTDeviceEventEmitter::class.java)
                        .emit("FS_EVENT_$path", map)
                }
                promise.resolve(path)
            } catch(e: Exception) {
                promise.reject("FS_ERROR", e)
            }
        }
    }
}
