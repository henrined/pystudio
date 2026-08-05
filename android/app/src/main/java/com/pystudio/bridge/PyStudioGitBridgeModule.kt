package com.pystudio.bridge

import com.facebook.react.bridge.*
import com.facebook.react.modules.core.DeviceEventManagerModule
import com.pystudio.core.GitMergeService
import com.pystudio.core.GitRepositoryService
import com.pystudio.core.GitSyncService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.util.concurrent.ConcurrentHashMap

class PyStudioGitBridgeModule(private val reactContext: ReactApplicationContext) : ReactContextBaseJavaModule(reactContext) {
    private val scope = CoroutineScope(Dispatchers.IO)
    private val repoServices = ConcurrentHashMap<String, GitRepositoryService>()
    private val syncServices = ConcurrentHashMap<String, GitSyncService>()
    private val mergeServices = ConcurrentHashMap<String, GitMergeService>()

    override fun getName(): String = "PyStudioGitBridge"

    private fun getRepoService(repoId: String): GitRepositoryService {
        return repoServices.getOrPut(repoId) {
            GitRepositoryService().also { service ->
                scope.launch {
                    service.cloneProgress().collect { progress ->
                        progress?.let {
                            val event = Arguments.createMap()
                            event.putString("repoId", repoId)
                            event.putString("operation", it.operation)
                            event.putDouble("bytesTransferred", it.bytesTransferred.toDouble())
                            it.totalBytes?.let { tb -> event.putDouble("totalBytes", tb.toDouble()) }
                            event.putInt("objectsProcessed", it.objectsProcessed)
                            it.totalObjects?.let { to -> event.putInt("totalObjects", to) }
                            emitEvent("gitTransferProgress", event)
                        }
                    }
                }
            }
        }
    }

    private fun getSyncService(repoId: String): GitSyncService {
        return syncServices.getOrPut(repoId) {
            GitSyncService().also { service ->
                scope.launch {
                    service.transferProgress().collect { progress ->
                        progress?.let {
                            val event = Arguments.createMap()
                            event.putString("repoId", repoId)
                            event.putString("operation", it.operation)
                            event.putDouble("bytesTransferred", it.bytesTransferred.toDouble())
                            it.totalBytes?.let { tb -> event.putDouble("totalBytes", tb.toDouble()) }
                            event.putInt("objectsProcessed", it.objectsProcessed)
                            it.totalObjects?.let { to -> event.putInt("totalObjects", to) }
                            emitEvent("gitTransferProgress", event)
                        }
                    }
                }
            }
        }
    }

    private fun getMergeService(repoId: String): GitMergeService {
        return mergeServices.getOrPut(repoId) { GitMergeService() }
    }

    private fun emitEvent(eventName: String, params: WritableMap) {
        if (reactContext.hasActiveCatalystInstance()) {
            reactContext
                .getJSModule(DeviceEventManagerModule.RCTDeviceEventEmitter::class.java)
                .emit(eventName, params)
        }
    }

    @ReactMethod
    fun clone(options: ReadableMap, promise: Promise) {
        scope.launch {
            try {
                val url = options.getString("url") ?: throw IllegalArgumentException("url is required")
                val dest = options.getString("destinationPath") ?: throw IllegalArgumentException("destinationPath is required")
                val username = if (options.hasKey("username")) options.getString("username") ?: "" else ""
                val token = if (options.hasKey("token")) options.getString("token") ?: "" else ""

                val service = getRepoService(dest)
                val success = service.clone(url, dest, username, token)

                val result = Arguments.createMap()
                result.putString("repoId", dest)
                result.putBoolean("success", success)
                promise.resolve(result)
            } catch (e: Exception) {
                promise.reject("GIT_CLONE_ERROR", e)
            }
        }
    }

    @ReactMethod
    fun getStatus(repoId: String, promise: Promise) {
        scope.launch {
            try {
                val service = getRepoService(repoId)
                val status = service.status(repoId)
                
                val result = Arguments.createMap()
                result.putString("currentBranch", status.currentBranch)
                result.putInt("ahead", status.ahead)
                result.putInt("behind", status.behind)
                
                val modifiedFiles = Arguments.createArray()
                status.modifiedFiles.forEach { modifiedFiles.pushString(it) }
                result.putArray("modifiedFiles", modifiedFiles)

                val untrackedFiles = Arguments.createArray()
                status.untrackedFiles.forEach { untrackedFiles.pushString(it) }
                result.putArray("untrackedFiles", untrackedFiles)

                val stagedFiles = Arguments.createArray()
                status.stagedFiles.forEach { stagedFiles.pushString(it) }
                result.putArray("stagedFiles", stagedFiles)

                val conflictedFiles = Arguments.createArray()
                status.conflictedFiles.forEach { conflictedFiles.pushString(it) }
                result.putArray("conflictedFiles", conflictedFiles)

                promise.resolve(result)
            } catch (e: Exception) {
                promise.reject("GIT_STATUS_ERROR", e)
            }
        }
    }

    @ReactMethod
    fun stage(repoId: String, filePath: String, promise: Promise) {
        scope.launch {
            try {
                val service = getRepoService(repoId)
                val success = service.stageFile(repoId, filePath)
                val result = Arguments.createMap()
                result.putBoolean("success", success)
                promise.resolve(result)
            } catch (e: Exception) {
                promise.reject("GIT_STAGE_ERROR", e)
            }
        }
    }

    @ReactMethod
    fun unstage(repoId: String, filePath: String, promise: Promise) {
        scope.launch {
            try {
                val service = getRepoService(repoId)
                val success = service.unstageFile(repoId, filePath)
                val result = Arguments.createMap()
                result.putBoolean("success", success)
                promise.resolve(result)
            } catch (e: Exception) {
                promise.reject("GIT_UNSTAGE_ERROR", e)
            }
        }
    }

    @ReactMethod
    fun commit(repoId: String, message: String, options: ReadableMap?, promise: Promise) {
        scope.launch {
            try {
                val authorName = if (options?.hasKey("authorName") == true) options.getString("authorName") ?: "" else ""
                val authorEmail = if (options?.hasKey("authorEmail") == true) options.getString("authorEmail") ?: "" else ""

                val service = getRepoService(repoId)
                val success = service.commit(repoId, message, authorName, authorEmail)
                val result = Arguments.createMap()
                result.putBoolean("success", success)
                promise.resolve(result)
            } catch (e: Exception) {
                promise.reject("GIT_COMMIT_ERROR", e)
            }
        }
    }

    @ReactMethod
    fun createBranch(repoId: String, name: String, promise: Promise) {
        scope.launch {
            try {
                val service = getRepoService(repoId)
                val success = service.createBranch(repoId, name)
                val result = Arguments.createMap()
                result.putBoolean("success", success)
                promise.resolve(result)
            } catch (e: Exception) {
                promise.reject("GIT_CREATE_BRANCH_ERROR", e)
            }
        }
    }

    @ReactMethod
    fun checkoutBranch(repoId: String, name: String, promise: Promise) {
        scope.launch {
            try {
                val service = getRepoService(repoId)
                val success = service.checkoutBranch(repoId, name)
                val result = Arguments.createMap()
                result.putBoolean("success", success)
                promise.resolve(result)
            } catch (e: Exception) {
                promise.reject("GIT_CHECKOUT_BRANCH_ERROR", e)
            }
        }
    }

    @ReactMethod
    fun deleteBranch(repoId: String, name: String, promise: Promise) {
        scope.launch {
            try {
                val service = getRepoService(repoId)
                val success = service.deleteBranch(repoId, name)
                val result = Arguments.createMap()
                result.putBoolean("success", success)
                promise.resolve(result)
            } catch (e: Exception) {
                promise.reject("GIT_DELETE_BRANCH_ERROR", e)
            }
        }
    }

    @ReactMethod
    fun listBranches(repoId: String, promise: Promise) {
        scope.launch {
            try {
                val service = getRepoService(repoId)
                val branches = service.listBranches(repoId)
                val result = Arguments.createArray()
                branches.forEach { result.pushString(it) }
                promise.resolve(result)
            } catch (e: Exception) {
                promise.reject("GIT_LIST_BRANCHES_ERROR", e)
            }
        }
    }

    @ReactMethod
    fun merge(repoId: String, sourceBranch: String, promise: Promise) {
        scope.launch {
            try {
                val service = getMergeService(repoId)
                val success = service.merge(repoId, sourceBranch)
                val result = Arguments.createMap()
                result.putBoolean("success", success)
                promise.resolve(result)
            } catch (e: Exception) {
                promise.reject("GIT_MERGE_ERROR", e)
            }
        }
    }

    @ReactMethod
    fun rebase(repoId: String, targetBranch: String, promise: Promise) {
        scope.launch {
            try {
                val service = getMergeService(repoId)
                val success = service.rebase(repoId, targetBranch)
                val result = Arguments.createMap()
                result.putBoolean("success", success)
                promise.resolve(result)
            } catch (e: Exception) {
                promise.reject("GIT_REBASE_ERROR", e)
            }
        }
    }

    @ReactMethod
    fun push(repoId: String, options: ReadableMap?, promise: Promise) {
        scope.launch {
            try {
                val remoteName = if (options?.hasKey("remoteName") == true) options.getString("remoteName") ?: "origin" else "origin"
                val username = if (options?.hasKey("username") == true) options.getString("username") ?: "" else ""
                val token = if (options?.hasKey("token") == true) options.getString("token") ?: "" else ""

                val service = getSyncService(repoId)
                val success = service.push(repoId, remoteName, username, token)
                val result = Arguments.createMap()
                result.putBoolean("success", success)
                promise.resolve(result)
            } catch (e: Exception) {
                promise.reject("GIT_PUSH_ERROR", e)
            }
        }
    }

    @ReactMethod
    fun pull(repoId: String, options: ReadableMap?, promise: Promise) {
        scope.launch {
            try {
                val remoteName = if (options?.hasKey("remoteName") == true) options.getString("remoteName") ?: "origin" else "origin"
                val username = if (options?.hasKey("username") == true) options.getString("username") ?: "" else ""
                val token = if (options?.hasKey("token") == true) options.getString("token") ?: "" else ""

                val service = getSyncService(repoId)
                val success = service.pull(repoId, remoteName, username, token)
                val result = Arguments.createMap()
                result.putBoolean("success", success)
                promise.resolve(result)
            } catch (e: Exception) {
                promise.reject("GIT_PULL_ERROR", e)
            }
        }
    }

    @ReactMethod
    fun diff(repoId: String, filePath: String?, promise: Promise) {
        scope.launch {
            try {
                val service = getRepoService(repoId)
                val diffResult = service.diff(repoId, filePath ?: "")
                val result = Arguments.createMap()
                result.putString("diff", diffResult)
                promise.resolve(result)
            } catch (e: Exception) {
                promise.reject("GIT_DIFF_ERROR", e)
            }
        }
    }

    @ReactMethod
    fun log(repoId: String, maxCount: Int, promise: Promise) {
        scope.launch {
            try {
                val service = getRepoService(repoId)
                val logHistory = service.log(repoId, maxCount)
                val result = Arguments.createArray()
                logHistory.forEach { commit ->
                    val commitMap = Arguments.createMap()
                    commitMap.putString("hash", commit.hash)
                    commitMap.putString("message", commit.message)
                    commitMap.putString("author", commit.author)
                    commitMap.putDouble("timestamp", commit.timestamp.toDouble())
                    result.pushMap(commitMap)
                }
                promise.resolve(result)
            } catch (e: Exception) {
                promise.reject("GIT_LOG_ERROR", e)
            }
        }
    }
}
