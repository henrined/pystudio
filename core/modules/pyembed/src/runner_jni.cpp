#include <jni.h>
#include <string>
#include "pyembed.h"
#include "pystudio/jni_utils.h"
#include "pystudio/logger.h"

// Global PythonEnv instance for this isolated process.
// Each RunnerService lives in its own android:isolatedProcess,
// so a single static instance is safe.
static pystudio::pyembed::PythonEnv g_env;
static jobject g_service_ref = nullptr;
static JavaVM* g_jvm = nullptr;

static JNIEnv* GetJNIEnv() {
    JNIEnv* env = nullptr;
    if (g_jvm) {
        g_jvm->GetEnv(reinterpret_cast<void**>(&env), JNI_VERSION_1_6);
    }
    return env;
}

extern "C" {

JNIEXPORT jint JNI_OnLoad(JavaVM* vm, void* reserved) {
    g_jvm = vm;
    return JNI_VERSION_1_6;
}

JNIEXPORT jboolean JNICALL
Java_com_pystudio_runner_RunnerService_nativeInitialize(
    JNIEnv* env, jobject thiz, jstring pythonHome) {

    // Keep a global ref to the service for callbacks
    if (g_service_ref) {
        env->DeleteGlobalRef(g_service_ref);
    }
    g_service_ref = env->NewGlobalRef(thiz);

    // Set up stdout/stderr streaming back to Kotlin via AIDL
    g_env.SetOutputCallback([](const std::string& text, bool is_stderr) {
        JNIEnv* jenv = GetJNIEnv();
        if (!jenv || !g_service_ref) return;

        jclass cls = jenv->GetObjectClass(g_service_ref);
        const char* method = is_stderr ? "onStderr" : "onStdout";
        jmethodID mid = jenv->GetMethodID(cls, method, "(Ljava/lang/String;)V");
        if (mid) {
            pystudio::jni::LocalRef<jstring> jtext(jenv, jenv->NewStringUTF(text.c_str()));
            jenv->CallVoidMethod(g_service_ref, mid, jtext.get());
        }
        jenv->DeleteLocalRef(cls);
    });

    std::string home = pystudio::jni::jstring_to_cpp(env, pythonHome);
    bool ok = g_env.Initialize(home);

    PS_LOG_I("runner_jni", ok ? "Python initialized successfully" : "Python initialization failed");
    return ok ? JNI_TRUE : JNI_FALSE;
}

JNIEXPORT jboolean JNICALL
Java_com_pystudio_runner_RunnerService_nativeRunString(
    JNIEnv* env, jobject thiz, jstring code) {

    std::string cppCode = pystudio::jni::jstring_to_cpp(env, code);
    bool ok = g_env.RunString(cppCode);
    return ok ? JNI_TRUE : JNI_FALSE;
}

JNIEXPORT jboolean JNICALL
Java_com_pystudio_runner_RunnerService_nativeRunFile(
    JNIEnv* env, jobject thiz, jstring filepath) {

    std::string cppPath = pystudio::jni::jstring_to_cpp(env, filepath);
    bool ok = g_env.RunFile(cppPath);
    return ok ? JNI_TRUE : JNI_FALSE;
}

JNIEXPORT void JNICALL
Java_com_pystudio_runner_RunnerService_nativeFinalize(
    JNIEnv* env, jobject thiz) {

    g_env.Finalize();
    if (g_service_ref) {
        env->DeleteGlobalRef(g_service_ref);
        g_service_ref = nullptr;
    }
    PS_LOG_I("runner_jni", "Python finalized");
}

} // extern "C"
