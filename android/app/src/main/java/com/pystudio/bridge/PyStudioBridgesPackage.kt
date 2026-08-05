package com.pystudio.bridge

import com.facebook.react.ReactPackage
import com.facebook.react.bridge.NativeModule
import com.facebook.react.bridge.ReactApplicationContext
import com.facebook.react.uimanager.ViewManager

class PyStudioBridgesPackage : ReactPackage {
    override fun createNativeModules(reactContext: ReactApplicationContext): List<NativeModule> {
        return listOf(
            PyStudioRuntimeBridgeModule(reactContext),
            PyStudioBuildBridgeModule(reactContext),
            PyStudioDebugBridgeModule(reactContext),
            PyStudioFSBridgeModule(reactContext),
            PyStudioGitBridgeModule(reactContext),
            PyStudioJupyterBridgeModule(reactContext),
            PyStudioAIBridgeModule(reactContext),
            PyStudioMarketplaceBridgeModule(reactContext),
            PyStudioLSPBridgeModule(reactContext),
        )
    }

    override fun createViewManagers(reactContext: ReactApplicationContext): List<ViewManager<*, *>> {
        return emptyList()
    }
}
