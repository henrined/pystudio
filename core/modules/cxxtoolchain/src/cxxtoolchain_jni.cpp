#include <jni.h>
#include <string>
#include <filesystem>
#include "cxxtoolchain.h"

using namespace pystudio::cxxtoolchain;
namespace fs = std::filesystem;

extern "C" {

JNIEXPORT jboolean JNICALL
Java_com_pystudio_bridge_PyStudioBuildBridgeModule_nativeConfigureBuild(JNIEnv* env, jobject thiz, jstring projectPath, jstring preset) {
    const char* pPath = env->GetStringUTFChars(projectPath, nullptr);
    const char* pPreset = env->GetStringUTFChars(preset, nullptr);
    
    std::string pathStr(pPath);
    std::string presetStr(pPreset);
    
    // preset is like "android-arm64-v8a", extract abi
    std::string abi = presetStr;
    if (abi.rfind("android-", 0) == 0) {
        abi = abi.substr(8);
    }
    
    ToolchainManager manager;
    bool result = manager.ConfigureCMake(pathStr, abi);
    
    env->ReleaseStringUTFChars(projectPath, pPath);
    env->ReleaseStringUTFChars(preset, pPreset);
    return result ? JNI_TRUE : JNI_FALSE;
}

JNIEXPORT jstring JNICALL
Java_com_pystudio_bridge_PyStudioBuildBridgeModule_nativeBuild(JNIEnv* env, jobject thiz, jstring projectPath, jstring buildDir) {
    const char* pPath = env->GetStringUTFChars(projectPath, nullptr);
    const char* bDir = env->GetStringUTFChars(buildDir, nullptr);
    
    std::string pathStr(pPath);
    std::string buildDirStr(bDir);
    
    // extract abi from buildDir (it's $projectPath/build/$abi)
    std::string abi = fs::path(buildDirStr).filename().string();
    
    ToolchainManager manager;
    std::string output = manager.BuildNinja(pathStr, abi);
    
    env->ReleaseStringUTFChars(projectPath, pPath);
    env->ReleaseStringUTFChars(buildDir, bDir);
    return env->NewStringUTF(output.c_str());
}

JNIEXPORT jboolean JNICALL
Java_com_pystudio_bridge_PyStudioBuildBridgeModule_nativeClangFormat(JNIEnv* env, jobject thiz, jstring filePath) {
    const char* fPath = env->GetStringUTFChars(filePath, nullptr);
    std::string pathStr(fPath);
    
    ToolchainManager manager;
    bool result = manager.FormatCode(pathStr);
    
    env->ReleaseStringUTFChars(filePath, fPath);
    return result ? JNI_TRUE : JNI_FALSE;
}

JNIEXPORT jstring JNICALL
Java_com_pystudio_bridge_PyStudioBuildBridgeModule_nativeClangTidy(JNIEnv* env, jobject thiz, jstring filePath) {
    const char* fPath = env->GetStringUTFChars(filePath, nullptr);
    std::string pathStr(fPath);
    
    ToolchainManager manager;
    std::string output = manager.TidyCode(pathStr, ""); // let clang-tidy find compile_commands.json automatically
    
    env->ReleaseStringUTFChars(filePath, fPath);
    return env->NewStringUTF(output.c_str());
}

JNIEXPORT jboolean JNICALL
Java_com_pystudio_bridge_PyStudioBuildBridgeModule_nativeGenerateCompileCommands(JNIEnv* env, jobject thiz, jstring projectPath) {
    const char* pPath = env->GetStringUTFChars(projectPath, nullptr);
    std::string pathStr(pPath);
    
    ToolchainManager manager;
    bool result = manager.GenerateCompileCommands(pathStr);
    
    env->ReleaseStringUTFChars(projectPath, pPath);
    return result ? JNI_TRUE : JNI_FALSE;
}

JNIEXPORT jboolean JNICALL
Java_com_pystudio_bridge_PyStudioBuildBridgeModule_nativeInstallToolchain(JNIEnv* env, jobject thiz, jstring archivePath, jstring sha256, jstring destPath) {
    const char* aPath = env->GetStringUTFChars(archivePath, nullptr);
    const char* shaStr = env->GetStringUTFChars(sha256, nullptr);
    const char* dPath = env->GetStringUTFChars(destPath, nullptr);
    
    std::string archivePathStr(aPath);
    std::string expectedSha256(shaStr);
    std::string destPathStr(dPath);
    
    ToolchainManager manager;
    bool result = manager.InstallToolchain(archivePathStr, destPathStr, expectedSha256);
    
    env->ReleaseStringUTFChars(archivePath, aPath);
    env->ReleaseStringUTFChars(sha256, shaStr);
    env->ReleaseStringUTFChars(destPath, dPath);
    
    return result ? JNI_TRUE : JNI_FALSE;
}

JNIEXPORT jboolean JNICALL
Java_com_pystudio_bridge_PyStudioBuildBridgeModule_nativeScaffoldProject(JNIEnv* env, jobject thiz, jstring destPath, jstring templateName) {
    const char* dPath = env->GetStringUTFChars(destPath, nullptr);
    const char* tName = env->GetStringUTFChars(templateName, nullptr);
    
    std::string destStr(dPath);
    std::string templateStr(tName);
    
    ToolchainManager manager;
    ProjectConfig config;
    config.projectName = fs::path(destStr).filename().string();
    config.projectPath = destStr;
    config.abis = {"arm64-v8a", "armeabi-v7a", "x86", "x86_64"};
    
    bool result = manager.GenerateProjectFiles(config);
    
    env->ReleaseStringUTFChars(destPath, dPath);
    env->ReleaseStringUTFChars(templateName, tName);
    
    return result ? JNI_TRUE : JNI_FALSE;
}

} // extern "C"
