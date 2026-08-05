#!/bin/bash
set -e

echo "1. Creating temporary Expo Bare Minimum app..."
cd /data/data/com.termux/files/home/pystudio
npx -y create-expo-app@latest tmp_expo_app -t expo-template-bare-minimum

echo "2. Copying existing React Native frontend code..."
# We keep our UI code and overwrite the default Expo App.tsx
cp -r mobile/src tmp_expo_app/
cp mobile/App.tsx tmp_expo_app/App.tsx

echo "3. Merging Android native code..."
# Our existing native code in android/app/src/main
cp -r android/app/src/main/java/com/pystudio tmp_expo_app/android/app/src/main/java/com/
cp -r android/app/src/main/aidl tmp_expo_app/android/app/src/main/

echo "4. Injecting CMake and AIDL configuration into Android app/build.gradle..."
# We need to inject the CMake externalNativeBuild into the android { defaultConfig { ... } } and android { ... }
# And buildFeatures { aidl true }
cat << 'EOF' >> tmp_expo_app/android/app/build.gradle

// --- PyStudio Native Config Injected ---
android {
    defaultConfig {
        externalNativeBuild {
            cmake {
                cppFlags "-std=c++20"
                arguments "-DPYSTUDIO_BUILD_TESTS=OFF"
            }
        }
        ndk {
            abiFilters 'arm64-v8a', 'armeabi-v7a', 'x86_64'
        }
    }
    externalNativeBuild {
        cmake {
            path "../../../core/CMakeLists.txt"
            version "3.22.1"
        }
    }
    buildFeatures {
        aidl true
    }
    sourceSets {
        main {
            aidl.srcDirs = ['src/main/aidl']
        }
    }
}
EOF

echo "5. Moving everything to the root..."
# Move old directories out of the way
mv android android_old
mv mobile mobile_old

# Move the newly configured Expo project to root
mv tmp_expo_app/* .
mv tmp_expo_app/.* . 2>/dev/null || true
rm -rf tmp_expo_app

echo "6. Installing our dependencies (zustand, etc)..."
npm install zustand

echo "Done! The Expo Bare project is initialized with all our native code integrated."
