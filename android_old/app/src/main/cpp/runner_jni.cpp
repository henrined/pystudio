#include <jni.h>
#include <string>
#include "pyembed.h"

using namespace pystudio::pyembed;

static PythonEnv g_pythonEnv;
static JavaVM* g_jvm = nullptr;
static jobject g_serviceObj = nullptr; // Global reference to RunnerService

extern "C" JNIEXPORT jint JNICALL JNI_OnLoad(JavaVM* vm, void* reserved) {
    g_jvm = vm;
    return JNI_VERSION_1_6;
}

extern "C" JNIEXPORT void JNICALL JNI_OnUnload(JavaVM* vm, void* reserved) {
    if (g_serviceObj) {
        JNIEnv* env;
        if (vm->GetEnv(reinterpret_cast<void**>(&env), JNI_VERSION_1_6) == JNI_OK) {
            env->DeleteGlobalRef(g_serviceObj);
            g_serviceObj = nullptr;
        }
    }
}

extern "C" JNIEXPORT jboolean JNICALL
Java_com_pystudio_runner_RunnerService_nativeInitialize(JNIEnv *env, jobject thiz, jstring pythonHome) {
    if (g_serviceObj) {
        env->DeleteGlobalRef(g_serviceObj);
    }
    g_serviceObj = env->NewGlobalRef(thiz);

    g_pythonEnv.SetOutputCallback([](const std::string& text, bool is_stderr) {
        if (!g_jvm || !g_serviceObj) return;
        
        JNIEnv* current_env = nullptr;
        bool attached = false;
        
        jint res = g_jvm->GetEnv(reinterpret_cast<void**>(&current_env), JNI_VERSION_1_6);
        if (res == JNI_EDETACHED) {
            if (g_jvm->AttachCurrentThread(&current_env, nullptr) != JNI_OK) return;
            attached = true;
        } else if (res != JNI_OK) {
            return;
        }

        jclass clazz = current_env->GetObjectClass(g_serviceObj);
        if (clazz) {
            jmethodID methodId = nullptr;
            if (is_stderr) {
                methodId = current_env->GetMethodID(clazz, "onStderr", "(Ljava/lang/String;)V");
            } else {
                methodId = current_env->GetMethodID(clazz, "onStdout", "(Ljava/lang/String;)V");
            }
            
            if (methodId) {
                jstring jText = current_env->NewStringUTF(text.c_str());
                current_env->CallVoidMethod(g_serviceObj, methodId, jText);
                current_env->DeleteLocalRef(jText);
            }
            current_env->DeleteLocalRef(clazz);
        }

        if (attached) {
            g_jvm->DetachCurrentThread();
        }
    });

    const char *path = env->GetStringUTFChars(pythonHome, nullptr);
    bool result = g_pythonEnv.Initialize(path);
    env->ReleaseStringUTFChars(pythonHome, path);
    return result;
}

extern "C" JNIEXPORT jboolean JNICALL
Java_com_pystudio_runner_RunnerService_nativeRunString(JNIEnv *env, jobject thiz, jstring code) {
    const char *codeStr = env->GetStringUTFChars(code, nullptr);
    bool result = g_pythonEnv.RunString(codeStr);
    env->ReleaseStringUTFChars(code, codeStr);
    return result;
}

extern "C" JNIEXPORT jboolean JNICALL
Java_com_pystudio_runner_RunnerService_nativeRunFile(JNIEnv *env, jobject thiz, jstring filepath) {
    const char *path = env->GetStringUTFChars(filepath, nullptr);
    bool result = g_pythonEnv.RunFile(path);
    env->ReleaseStringUTFChars(filepath, path);
    return result;
}

extern "C" JNIEXPORT void JNICALL
Java_com_pystudio_runner_RunnerService_nativeFinalize(JNIEnv *env, jobject thiz) {
    g_pythonEnv.Finalize();
    
    if (g_serviceObj) {
        env->DeleteGlobalRef(g_serviceObj);
        g_serviceObj = nullptr;
    }
}

extern "C" JNIEXPORT jintArray JNICALL
Java_com_pystudio_runner_RunnerService_nativeForceGcCollect(JNIEnv *env, jobject thiz) {
    int collected = 0;
    int uncollectable = 0;
    g_pythonEnv.ForceGcCollect(collected, uncollectable);

    jintArray arr = env->NewIntArray(2);
    jint fill[2] = {collected, uncollectable};
    env->SetIntArrayRegion(arr, 0, 2, fill);
    return arr;
}
