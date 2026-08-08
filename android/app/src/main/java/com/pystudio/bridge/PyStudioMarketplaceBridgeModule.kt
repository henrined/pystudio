package com.pystudio.bridge

import com.facebook.react.bridge.*
import com.facebook.react.modules.core.DeviceEventManagerModule
import com.pystudio.marketplace.MarketplaceServiceImpl
import com.pystudio.marketplace.SearchFilters
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.io.File

class PyStudioMarketplaceBridgeModule(private val reactContext: ReactApplicationContext) : ReactContextBaseJavaModule(reactContext) {
    private val scope = CoroutineScope(Dispatchers.IO)
    private val marketplaceService = MarketplaceServiceImpl()
    private val permissionManager = com.pystudio.marketplace.PermissionManagerServiceImpl()
    private val hostManager = com.pystudio.marketplace.ExtensionHostManagerImpl(permissionManager)
    private val installDir by lazy { File(reactContext.filesDir, "extensions") }
    private val quarantineDir by lazy { File(reactContext.cacheDir, "quarantine") }
    private val lifecycleService by lazy { com.pystudio.marketplace.ExtensionLifecycleServiceImpl(hostManager, marketplaceService, permissionManager, installDir, quarantineDir) }
    private val registryService by lazy { com.pystudio.marketplace.ExtensionRegistryServiceImpl(marketplaceService, permissionManager, lifecycleService, installDir, quarantineDir) }

    override fun getName(): String = "PyStudioMarketplaceBridge"

    @ReactMethod
    fun search(query: String, filters: ReadableMap?, promise: Promise) {
        scope.launch {
            try {
                val parsedFilters = filters?.let {
                    SearchFilters(
                        category = if (it.hasKey("category")) it.getString("category") else null,
                        sortBy = if (it.hasKey("sortBy")) it.getString("sortBy") ?: "relevance" else "relevance",
                        targetAbi = if (it.hasKey("targetAbi")) it.getString("targetAbi") else null,
                        pystudioVersion = if (it.hasKey("pystudioVersion")) it.getString("pystudioVersion") else null
                    )
                }
                val result = marketplaceService.searchExtensions(query, parsedFilters)
                val out = Arguments.createMap()
                out.putInt("total", result.total)
                val resultsArr = Arguments.createArray()
                result.results.forEach { ext ->
                    val extMap = Arguments.createMap()
                    extMap.putString("id", ext.id)
                    extMap.putString("displayName", ext.displayName)
                    extMap.putString("publisher", ext.publisher)
                    extMap.putString("version", ext.version)
                    extMap.putDouble("installs", ext.installs.toDouble())
                    extMap.putDouble("rating", ext.rating)
                    resultsArr.pushMap(extMap)
                }
                out.putArray("results", resultsArr)
                promise.resolve(out)
            } catch(e: Exception) {
                promise.reject("MARKETPLACE_ERROR", e)
            }
        }
    }

    @ReactMethod
    fun install(extensionId: String, version: String?, promise: Promise) {
        scope.launch {
            try {
                val installResult = registryService.install(extensionId, version)
                val out = Arguments.createMap()
                out.putBoolean("success", installResult.success)
                out.putString("extensionId", installResult.extensionId)
                out.putString("version", installResult.version)
                out.putBoolean("requiresReload", installResult.requiresReload)
                out.putBoolean("rollbackAvailable", installResult.rollbackAvailable)
                if (installResult.errorCode != null) {
                    out.putString("errorCode", installResult.errorCode)
                }
                promise.resolve(out)
            } catch(e: Exception) {
                promise.reject("MARKETPLACE_ERROR", e)
            }
        }
    }

    @ReactMethod
    fun uninstall(extensionId: String, promise: Promise) {
        scope.launch {
            try {
                registryService.uninstall(extensionId)
                promise.resolve(true)
            } catch(e: Exception) {
                promise.reject("MARKETPLACE_ERROR", e)
            }
        }
    }

    @ReactMethod
    fun listInstalled(promise: Promise) {
        scope.launch {
            try {
                val list = marketplaceService.getInstalledExtensions(installDir)
                val arr = Arguments.createArray()
                list.forEach { ext ->
                    val map = Arguments.createMap()
                    map.putString("id", ext.manifest.id)
                    map.putString("version", ext.manifest.version)
                    map.putString("displayName", ext.manifest.displayName)
                    map.putString("state", "active")
                    arr.pushMap(map)
                }
                promise.resolve(arr)
            } catch(e: Exception) {
                promise.reject("MARKETPLACE_ERROR", e)
            }
        }
    }
}
