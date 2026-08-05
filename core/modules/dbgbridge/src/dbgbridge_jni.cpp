#include <jni.h>
#include <string>
#include <vector>
#include "dbgbridge.h"
#include <nlohmann/json.hpp>

using namespace pystudio::dbgbridge;
using json = nlohmann::json;

static DebugBridge* g_debugBridge = nullptr;

extern "C" {

JNIEXPORT jint JNICALL JNI_OnLoad(JavaVM* vm, void* reserved) {
    g_debugBridge = new DebugBridge();
    return JNI_VERSION_1_6;
}

JNIEXPORT void JNICALL JNI_OnUnload(JavaVM* vm, void* reserved) {
    if (g_debugBridge) {
        delete g_debugBridge;
        g_debugBridge = nullptr;
    }
}

JNIEXPORT jboolean JNICALL
Java_com_pystudio_debug_DebugService_nativeInitialize(JNIEnv* env, jobject thiz) {
    if (!g_debugBridge) return JNI_FALSE;
    
    // We need to keep a global ref to the object and the VM to call back later
    JavaVM* jvm = nullptr;
    env->GetJavaVM(&jvm);
    jobject globalObj = env->NewGlobalRef(thiz);
    
    g_debugBridge->SetEventCallback([jvm, globalObj](const std::string& event, const std::string& payload) {
        JNIEnv* localEnv = nullptr;
        bool didAttach = false;
        jint res = jvm->GetEnv((void**)&localEnv, JNI_VERSION_1_6);
        if (res == JNI_EDETACHED) {
#if defined(__ANDROID__)
            if (jvm->AttachCurrentThread(&localEnv, nullptr) == 0) {
                didAttach = true;
            }
#else
            if (jvm->AttachCurrentThread((void**)&localEnv, nullptr) == 0) {
                didAttach = true;
            }
#endif
        }
        if (localEnv) {
            jclass cls = localEnv->GetObjectClass(globalObj);
            jmethodID mid = localEnv->GetMethodID(cls, "onDapEvent", "(Ljava/lang/String;Ljava/lang/String;)V");
            if (mid) {
                jstring jevent = localEnv->NewStringUTF(event.c_str());
                jstring jpayload = localEnv->NewStringUTF(payload.c_str());
                localEnv->CallVoidMethod(globalObj, mid, jevent, jpayload);
                localEnv->DeleteLocalRef(jevent);
                localEnv->DeleteLocalRef(jpayload);
            }
            localEnv->DeleteLocalRef(cls);
        }
        if (didAttach) {
            jvm->DetachCurrentThread();
        }
    });

    return g_debugBridge->Initialize() ? JNI_TRUE : JNI_FALSE;
}

JNIEXPORT jboolean JNICALL
Java_com_pystudio_debug_DebugService_nativeLaunch(JNIEnv* env, jobject thiz, jstring programPath, jobjectArray args) {
    if (!g_debugBridge) return JNI_FALSE;
    const char* pathStr = env->GetStringUTFChars(programPath, nullptr);
    std::string path(pathStr);
    env->ReleaseStringUTFChars(programPath, pathStr);

    std::vector<std::string> argsVec;
    if (args != nullptr) {
        jsize len = env->GetArrayLength(args);
        for (jsize i = 0; i < len; ++i) {
            jstring jstr = (jstring) env->GetObjectArrayElement(args, i);
            const char* argStr = env->GetStringUTFChars(jstr, nullptr);
            argsVec.push_back(argStr);
            env->ReleaseStringUTFChars(jstr, argStr);
            env->DeleteLocalRef(jstr);
        }
    }

    return g_debugBridge->Launch(path, argsVec) ? JNI_TRUE : JNI_FALSE;
}

JNIEXPORT jboolean JNICALL
Java_com_pystudio_debug_DebugService_nativeAttach(JNIEnv* env, jobject thiz, jint pid) {
    if (!g_debugBridge) return JNI_FALSE;
    return g_debugBridge->Attach(pid) ? JNI_TRUE : JNI_FALSE;
}

JNIEXPORT jstring JNICALL
Java_com_pystudio_debug_DebugService_nativeSetBreakpoints(JNIEnv* env, jobject thiz, jstring file, jintArray lines) {
    if (!g_debugBridge) return env->NewStringUTF("[]");
    const char* fileStr = env->GetStringUTFChars(file, nullptr);
    std::string filePath(fileStr);
    env->ReleaseStringUTFChars(file, fileStr);

    std::vector<int> linesVec;
    if (lines != nullptr) {
        jsize len = env->GetArrayLength(lines);
        jint* linesElements = env->GetIntArrayElements(lines, nullptr);
        linesVec.assign(linesElements, linesElements + len);
        env->ReleaseIntArrayElements(lines, linesElements, JNI_ABORT);
    }

    auto bps = g_debugBridge->SetBreakpoints(filePath, linesVec);
    json j_bps = json::array();
    for (const auto& bp : bps) {
        j_bps.push_back({
            {"id", bp.id},
            {"line", bp.line},
            {"verified", bp.verified}
        });
    }
    
    return env->NewStringUTF(j_bps.dump().c_str());
}

JNIEXPORT jboolean JNICALL
Java_com_pystudio_debug_DebugService_nativeContinue(JNIEnv* env, jobject thiz) {
    if (!g_debugBridge) return JNI_FALSE;
    return g_debugBridge->Continue() ? JNI_TRUE : JNI_FALSE;
}

JNIEXPORT jboolean JNICALL
Java_com_pystudio_debug_DebugService_nativeStepOver(JNIEnv* env, jobject thiz) {
    if (!g_debugBridge) return JNI_FALSE;
    return g_debugBridge->StepOver() ? JNI_TRUE : JNI_FALSE;
}

JNIEXPORT jboolean JNICALL
Java_com_pystudio_debug_DebugService_nativeStepInto(JNIEnv* env, jobject thiz) {
    if (!g_debugBridge) return JNI_FALSE;
    return g_debugBridge->StepInto() ? JNI_TRUE : JNI_FALSE;
}

JNIEXPORT jboolean JNICALL
Java_com_pystudio_debug_DebugService_nativeStepOut(JNIEnv* env, jobject thiz) {
    if (!g_debugBridge) return JNI_FALSE;
    return g_debugBridge->StepOut() ? JNI_TRUE : JNI_FALSE;
}

JNIEXPORT jboolean JNICALL
Java_com_pystudio_debug_DebugService_nativePause(JNIEnv* env, jobject thiz) {
    if (!g_debugBridge) return JNI_FALSE;
    return g_debugBridge->Pause() ? JNI_TRUE : JNI_FALSE;
}

JNIEXPORT jboolean JNICALL
Java_com_pystudio_debug_DebugService_nativeDisconnect(JNIEnv* env, jobject thiz) {
    if (!g_debugBridge) return JNI_FALSE;
    return g_debugBridge->Disconnect() ? JNI_TRUE : JNI_FALSE;
}

JNIEXPORT jstring JNICALL
Java_com_pystudio_debug_DebugService_nativeGetStackTrace(JNIEnv* env, jobject thiz, jint threadId) {
    if (!g_debugBridge) return env->NewStringUTF("[]");
    auto frames = g_debugBridge->GetStackTrace(threadId);
    json j_frames = json::array();
    for (const auto& frame : frames) {
        j_frames.push_back({
            {"id", frame.id},
            {"name", frame.name},
            {"source", {{"path", frame.source}}},
            {"line", frame.line},
            {"column", frame.column}
        });
    }
    return env->NewStringUTF(j_frames.dump().c_str());
}

JNIEXPORT jstring JNICALL
Java_com_pystudio_debug_DebugService_nativeGetScopes(JNIEnv* env, jobject thiz, jint frameId) {
    if (!g_debugBridge) return env->NewStringUTF("[]");
    auto scopes = g_debugBridge->GetScopes(frameId);
    json j_scopes = json::array();
    for (const auto& scope : scopes) {
        j_scopes.push_back({
            {"name", scope.name},
            {"variablesReference", scope.variablesReference},
            {"expensive", false}
        });
    }
    return env->NewStringUTF(j_scopes.dump().c_str());
}

JNIEXPORT jstring JNICALL
Java_com_pystudio_debug_DebugService_nativeGetVariables(JNIEnv* env, jobject thiz, jint variablesReference) {
    if (!g_debugBridge) return env->NewStringUTF("[]");
    auto vars = g_debugBridge->GetVariables(variablesReference);
    json j_vars = json::array();
    for (const auto& var : vars) {
        j_vars.push_back({
            {"name", var.name},
            {"value", var.value},
            {"type", var.type},
            {"variablesReference", var.variablesReference}
        });
    }
    return env->NewStringUTF(j_vars.dump().c_str());
}

JNIEXPORT jstring JNICALL
Java_com_pystudio_debug_DebugService_nativeEvaluate(JNIEnv* env, jobject thiz, jstring expression, jint frameId) {
    if (!g_debugBridge) return env->NewStringUTF("{}");
    const char* exprStr = env->GetStringUTFChars(expression, nullptr);
    std::string expr(exprStr);
    env->ReleaseStringUTFChars(expression, exprStr);

    auto result = g_debugBridge->Evaluate(expr, frameId);
    json j_res = {
        {"result", result.value},
        {"type", result.type},
        {"variablesReference", result.variablesReference}
    };
    return env->NewStringUTF(j_res.dump().c_str());
}

} // extern "C"
