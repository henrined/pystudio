#include "pystudio/jni_utils.h"

namespace pystudio {
namespace jni {

Result check_and_clear_exceptions(JNIEnv* env) {
  if (env->ExceptionCheck()) {
    // Get the exception
    jthrowable exception = env->ExceptionOccurred();
    env->ExceptionClear(); // Clear it so we can safely call other JNI functions

    // Get the exception's toString() representation
    jclass obj_cls = env->FindClass("java/lang/Object");
    jmethodID to_string_mid = env->GetMethodID(obj_cls, "toString", "()Ljava/lang/String;");
    
    LocalRef<jstring> jmsg(env, (jstring)env->CallObjectMethod(exception, to_string_mid));
    std::string msg = jstring_to_cpp(env, jmsg.get());
    
    env->DeleteLocalRef(exception);
    env->DeleteLocalRef(obj_cls);

    return Result::Err("JNI Exception: " + msg);
  }
  return Result::Ok();
}

std::string jstring_to_cpp(JNIEnv* env, jstring jstr) {
  if (!jstr) return "";
  const char* chars = env->GetStringUTFChars(jstr, nullptr);
  if (!chars) return "";
  std::string cpp_str(chars);
  env->ReleaseStringUTFChars(jstr, chars);
  return cpp_str;
}

LocalRef<jstring> cpp_to_jstring(JNIEnv* env, const std::string& str) {
  return LocalRef<jstring>(env, env->NewStringUTF(str.c_str()));
}

} // namespace jni
} // namespace pystudio
