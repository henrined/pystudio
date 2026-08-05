#!/bin/bash
SPECS_DIR="android/app/src/main/js/specs"
KT_DIR="android/app/src/main/java/com/pystudio/bridge"
mkdir -p "$SPECS_DIR"
mkdir -p "$KT_DIR"

bridges=("Runtime" "Build" "Debug" "FS" "Git" "Jupyter" "AI" "Marketplace" "LSP")

for b in "${bridges[@]}"; do
  # TS Spec
  cat <<EOF > "$SPECS_DIR/Native${b}Bridge.ts"
import type { TurboModule } from 'react-native';
import { TurboModuleRegistry } from 'react-native';

export interface Spec extends TurboModule {
  ping(): Promise<boolean>;
}
export default TurboModuleRegistry.getEnforcing<Spec>('PyStudio${b}Bridge');
EOF

  # Kotlin Module
  cat <<EOF > "$KT_DIR/PyStudio${b}BridgeModule.kt"
package com.pystudio.bridge

import com.facebook.react.bridge.Promise
import com.facebook.react.bridge.ReactApplicationContext
import com.facebook.react.bridge.ReactContextBaseJavaModule
import com.facebook.react.bridge.ReactMethod

class PyStudio${b}BridgeModule(reactContext: ReactApplicationContext) : ReactContextBaseJavaModule(reactContext) {
    override fun getName(): String = "PyStudio${b}Bridge"

    @ReactMethod
    fun ping(promise: Promise) {
        promise.resolve(true)
    }
}
EOF
done

# Package file
cat <<EOF > "$KT_DIR/PyStudioBridgesPackage.kt"
package com.pystudio.bridge

import com.facebook.react.ReactPackage
import com.facebook.react.bridge.NativeModule
import com.facebook.react.bridge.ReactApplicationContext
import com.facebook.react.uimanager.ViewManager

class PyStudioBridgesPackage : ReactPackage {
    override fun createNativeModules(reactContext: ReactApplicationContext): List<NativeModule> {
        return listOf(
$(for b in "${bridges[@]}"; do echo "            PyStudio${b}BridgeModule(reactContext),"; done)
        )
    }

    override fun createViewManagers(reactContext: ReactApplicationContext): List<ViewManager<*, *>> {
        return emptyList()
    }
}
EOF

echo "Bridges generated."
