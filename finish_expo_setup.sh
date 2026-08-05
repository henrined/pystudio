#!/bin/bash
set -e

cd /data/data/com.termux/files/home/pystudio

echo "2. Copying existing React Native frontend code..."
cp -r mobile/src tmp_expo_app/
cp mobile/App.tsx tmp_expo_app/App.tsx

echo "3. Merging Android native code..."
cp -r android/app/src/main/java/com/pystudio tmp_expo_app/android/app/src/main/java/com/
cp -r android/app/src/main/aidl tmp_expo_app/android/app/src/main/

echo "4. Injecting CMake and AIDL configuration into Android app/build.gradle..."
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
mv android android_old
mv mobile mobile_old

mv tmp_expo_app/* .
mv tmp_expo_app/.* . 2>/dev/null || true
rm -rf tmp_expo_app

echo "6. Installing our dependencies (zustand, etc)..."
npm install zustand

echo "Done! The Expo Bare project is initialized with all our native code integrated."
