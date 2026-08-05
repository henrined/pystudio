#include <jni.h>
#include <string>
#include <vector>
#include <memory>
#include "dbgbridge.h"

using namespace pystudio::dbgbridge;

static std::unique_ptr<DebugBridge> g_debug_bridge;
static jobject g_service_instance = nullptr;
static jmethodID g_on_dap_event = nullptr;
static JavaVM* g_jvm = nullptr;

extern "C" JNIEXPORT jint JNICALL JNI_OnLoad(JavaVM* vm, void* reserved) {
    g_jvm = vm;
    return JNI_VERSION_1_6;
}

static void OnDapEvent(const std::string& event, const std::string& payload) {
    if (!g_jvm || !g_service_instance || !g_on_dap_event) return;

    JNIEnv* env = nullptr;
    bool attached = false;
    if (g_jvm->GetEnv(reinterpret_cast<void**>(&env), JNI_VERSION_1_6) != JNI_OK) {
        g_jvm->AttachCurrentThread(&env, nullptr);
        attached = true;
    }

    if (env) {
        jstring j_event = env->NewStringUTF(event.c_str());
        jstring j_payload = env->NewStringUTF(payload.c_str());
        env->CallVoidMethod(g_service_instance, g_on_dap_event, j_event, j_payload);
        env->DeleteLocalRef(j_event);
        env->DeleteLocalRef(j_payload);
    }

    if (attached) {
        g_jvm->DetachCurrentThread();
    }
}

extern "C" JNIEXPORT jboolean JNICALL
Java_com_pystudio_debug_DebugService_nativeInitialize(JNIEnv* env, jobject thiz) {
    if (!g_debug_bridge) {
        g_debug_bridge = std::make_unique<DebugBridge>();
    }
    
    if (g_service_instance) {
        env->DeleteGlobalRef(g_service_instance);
    }
    g_service_instance = env->NewGlobalRef(thiz);
    
    jclass clazz = env->GetObjectClass(thiz);
    g_on_dap_event = env->GetMethodID(clazz, "onDapEvent", "(Ljava/lang/String;Ljava/lang/String;)V");
    env->DeleteLocalRef(clazz);
    
    g_debug_bridge->SetEventCallback(OnDapEvent);
    
    return g_debug_bridge->Initialize() ? JNI_TRUE : JNI_FALSE;
}

extern "C" JNIEXPORT jboolean JNICALL
Java_com_pystudio_debug_DebugService_nativeLaunch(JNIEnv* env, jobject thiz, jstring programPath, jobjectArray args) {
    if (!g_debug_bridge) return JNI_FALSE;
    const char* c_path = env->GetStringUTFChars(programPath, nullptr);
    std::string path(c_path);
    env->ReleaseStringUTFChars(programPath, c_path);
    
    std::vector<std::string> v_args;
    int len = env->GetArrayLength(args);
    for (int i = 0; i < len; ++i) {
        jstring j_arg = (jstring) env->GetObjectArrayElement(args, i);
        const char* c_arg = env->GetStringUTFChars(j_arg, nullptr);
        v_args.push_back(c_arg);
        env->ReleaseStringUTFChars(j_arg, c_arg);
        env->DeleteLocalRef(j_arg);
    }
    
    return g_debug_bridge->Launch(path, v_args) ? JNI_TRUE : JNI_FALSE;
}

extern "C" JNIEXPORT jboolean JNICALL
Java_com_pystudio_debug_DebugService_nativeAttach(JNIEnv* env, jobject thiz, jint pid) {
    if (!g_debug_bridge) return JNI_FALSE;
    return g_debug_bridge->Attach(pid) ? JNI_TRUE : JNI_FALSE;
}

extern "C" JNIEXPORT jstring JNICALL
Java_com_pystudio_debug_DebugService_nativeSetBreakpoints(JNIEnv* env, jobject thiz, jstring file, jintArray lines) {
    if (!g_debug_bridge) return env->NewStringUTF("[]");
    
    const char* c_file = env->GetStringUTFChars(file, nullptr);
    std::string s_file(c_file);
    env->ReleaseStringUTFChars(file, c_file);
    
    std::vector<int> v_lines;
    jsize len = env->GetArrayLength(lines);
    jint* elements = env->GetIntArrayElements(lines, nullptr);
    for (int i = 0; i < len; ++i) {
        v_lines.push_back(elements[i]);
    }
    env->ReleaseIntArrayElements(lines, elements, 0);
    
    auto bps = g_debug_bridge->SetBreakpoints(s_file, v_lines);
    
    // Simplistic JSON generation
    std::string json = "[";
    for (size_t i = 0; i < bps.size(); ++i) {
        json += "{\"id\":" + std::to_string(bps[i].id) + ",\"verified\":true}";
        if (i < bps.size() - 1) json += ",";
    }
    json += "]";
    
    return env->NewStringUTF(json.c_str());
}

extern "C" JNIEXPORT jboolean JNICALL
Java_com_pystudio_debug_DebugService_nativeContinue(JNIEnv* env, jobject thiz) {
    if (!g_debug_bridge) return JNI_FALSE;
    return g_debug_bridge->Continue() ? JNI_TRUE : JNI_FALSE;
}

extern "C" JNIEXPORT jboolean JNICALL
Java_com_pystudio_debug_DebugService_nativeStepOver(JNIEnv* env, jobject thiz) {
    if (!g_debug_bridge) return JNI_FALSE;
    return g_debug_bridge->StepOver() ? JNI_TRUE : JNI_FALSE;
}

extern "C" JNIEXPORT jboolean JNICALL
Java_com_pystudio_debug_DebugService_nativeStepInto(JNIEnv* env, jobject thiz) {
    if (!g_debug_bridge) return JNI_FALSE;
    return g_debug_bridge->StepInto() ? JNI_TRUE : JNI_FALSE;
}

extern "C" JNIEXPORT jboolean JNICALL
Java_com_pystudio_debug_DebugService_nativeStepOut(JNIEnv* env, jobject thiz) {
    if (!g_debug_bridge) return JNI_FALSE;
    return g_debug_bridge->StepOut() ? JNI_TRUE : JNI_FALSE;
}

extern "C" JNIEXPORT jboolean JNICALL
Java_com_pystudio_debug_DebugService_nativePause(JNIEnv* env, jobject thiz) {
    if (!g_debug_bridge) return JNI_FALSE;
    return g_debug_bridge->Pause() ? JNI_TRUE : JNI_FALSE;
}

extern "C" JNIEXPORT jboolean JNICALL
Java_com_pystudio_debug_DebugService_nativeDisconnect(JNIEnv* env, jobject thiz) {
    if (!g_debug_bridge) return JNI_FALSE;
    return g_debug_bridge->Disconnect() ? JNI_TRUE : JNI_FALSE;
}

extern "C" JNIEXPORT jstring JNICALL
Java_com_pystudio_debug_DebugService_nativeGetStackTrace(JNIEnv* env, jobject thiz, jint threadId) {
    if (!g_debug_bridge) return env->NewStringUTF("[]");
    auto frames = g_debug_bridge->GetStackTrace(threadId);
    std::string json = "[";
    for (size_t i = 0; i < frames.size(); ++i) {
        json += "{\"id\":" + std::to_string(frames[i].id) + ",\"name\":\"" + frames[i].name + "\",\"line\":" + std::to_string(frames[i].line) + "}";
        if (i < frames.size() - 1) json += ",";
    }
    json += "]";
    return env->NewStringUTF(json.c_str());
}

extern "C" JNIEXPORT jstring JNICALL
Java_com_pystudio_debug_DebugService_nativeGetScopes(JNIEnv* env, jobject thiz, jint frameId) {
    if (!g_debug_bridge) return env->NewStringUTF("[]");
    auto scopes = g_debug_bridge->GetScopes(frameId);
    std::string json = "[";
    for (size_t i = 0; i < scopes.size(); ++i) {
        json += "{\"name\":\"" + scopes[i].name + "\",\"variablesReference\":" + std::to_string(scopes[i].variablesReference) + "}";
        if (i < scopes.size() - 1) json += ",";
    }
    json += "]";
    return env->NewStringUTF(json.c_str());
}

extern "C" JNIEXPORT jstring JNICALL
Java_com_pystudio_debug_DebugService_nativeGetVariables(JNIEnv* env, jobject thiz, jint variablesReference) {
    if (!g_debug_bridge) return env->NewStringUTF("[]");
    auto vars = g_debug_bridge->GetVariables(variablesReference);
    std::string json = "[";
    for (size_t i = 0; i < vars.size(); ++i) {
        json += "{\"name\":\"" + vars[i].name + "\",\"value\":" + vars[i].value + ",\"type\":\"" + vars[i].type + "\"}";
        if (i < vars.size() - 1) json += ",";
    }
    json += "]";
    return env->NewStringUTF(json.c_str());
}

extern "C" JNIEXPORT jstring JNICALL
Java_com_pystudio_debug_DebugService_nativeEvaluate(JNIEnv* env, jobject thiz, jstring expression, jint frameId) {
    if (!g_debug_bridge) return env->NewStringUTF("{}");
    const char* c_expr = env->GetStringUTFChars(expression, nullptr);
    std::string s_expr(c_expr);
    env->ReleaseStringUTFChars(expression, c_expr);
    
    auto var = g_debug_bridge->Evaluate(s_expr, frameId);
    std::string json = "{\"result\":\"" + var.value + "\",\"type\":\"" + var.type + "\"}";
    return env->NewStringUTF(json.c_str());
}
