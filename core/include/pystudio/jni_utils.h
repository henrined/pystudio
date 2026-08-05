#pragma once

#include "pystudio/core.h"
#include <jni.h>
#include <string>

namespace pystudio {
namespace jni {

/**
 * RAII wrapper for JNI Local References to avoid memory leaks
 * when JNI functions are called repeatedly in a loop.
 */
template <typename T>
class LocalRef {
public:
  LocalRef(JNIEnv* env, T ref) : env_(env), ref_(ref) {}
  ~LocalRef() {
    if (ref_) {
      env_->DeleteLocalRef(ref_);
    }
  }

  // Non-copyable
  LocalRef(const LocalRef&) = delete;
  LocalRef& operator=(const LocalRef&) = delete;

  // Movable
  LocalRef(LocalRef&& other) noexcept : env_(other.env_), ref_(other.ref_) {
    other.ref_ = nullptr;
  }
  LocalRef& operator=(LocalRef&& other) noexcept {
    if (this != &other) {
      if (ref_) env_->DeleteLocalRef(ref_);
      env_ = other.env_;
      ref_ = other.ref_;
      other.ref_ = nullptr;
    }
    return *this;
  }

  T get() const noexcept { return ref_; }
  operator T() const noexcept { return ref_; }
  bool operator==(std::nullptr_t) const noexcept { return ref_ == nullptr; }
  bool operator!=(std::nullptr_t) const noexcept { return ref_ != nullptr; }

private:
  JNIEnv* env_;
  T ref_;
};

/**
 * Verifies if a JNI exception occurred.
 * If yes, it clears it and returns an Error Result with the exception message.
 */
Result check_and_clear_exceptions(JNIEnv* env);

/**
 * Converts a JNI string (jstring) to a std::string (UTF-8).
 */
std::string jstring_to_cpp(JNIEnv* env, jstring jstr);

/**
 * Converts a std::string (UTF-8) to a JNI string (jstring).
 */
LocalRef<jstring> cpp_to_jstring(JNIEnv* env, const std::string& str);

} // namespace jni
} // namespace pystudio
