#include "pystudio/logger.h"

#include <cstdarg>
#include <cstdio>
#include <mutex>

// On Android we use logcat; in other environments (Termux tests) we use stderr.
#if defined(__ANDROID__)
#  include <android/log.h>
#  define PS_ANDROID_TAG "PyStudio"
#endif

namespace pystudio {

static const char* level_str(LogLevel l) {
  switch (l) {
    case LogLevel::kVerbose: return "V";
    case LogLevel::kDebug:   return "D";
    case LogLevel::kInfo:    return "I";
    case LogLevel::kWarn:    return "W";
    case LogLevel::kError:   return "E";
  }
  return "?";
}

Logger& Logger::instance() {
  static Logger logger;
  return logger;
}

void Logger::set_callback(LogCallback cb) {
  callback_ = std::move(cb);
}

void Logger::emit(LogLevel level, std::string_view tag, std::string_view msg) {
  if (level < min_level_.load(std::memory_order_relaxed)) return;

  if (callback_) {
    callback_(level, tag, msg);
    return;
  }

#if defined(__ANDROID__)
  android_LogPriority prio = ANDROID_LOG_DEBUG;
  switch (level) {
    case LogLevel::kVerbose: prio = ANDROID_LOG_VERBOSE; break;
    case LogLevel::kDebug:   prio = ANDROID_LOG_DEBUG;   break;
    case LogLevel::kInfo:    prio = ANDROID_LOG_INFO;    break;
    case LogLevel::kWarn:    prio = ANDROID_LOG_WARN;    break;
    case LogLevel::kError:   prio = ANDROID_LOG_ERROR;   break;
  }
  __android_log_print(prio, PS_ANDROID_TAG, "[%.*s] %.*s",
                      static_cast<int>(tag.size()), tag.data(),
                      static_cast<int>(msg.size()), msg.data());
#else
  // Termux / desktop fallback
  std::fprintf(stderr, "[PS/%s] [%.*s] %.*s\n",
               level_str(level),
               static_cast<int>(tag.size()), tag.data(),
               static_cast<int>(msg.size()), msg.data());
#endif
}

void Logger::verbose(std::string_view tag, std::string_view msg) { emit(LogLevel::kVerbose, tag, msg); }
void Logger::debug  (std::string_view tag, std::string_view msg) { emit(LogLevel::kDebug,   tag, msg); }
void Logger::info   (std::string_view tag, std::string_view msg) { emit(LogLevel::kInfo,    tag, msg); }
void Logger::warn   (std::string_view tag, std::string_view msg) { emit(LogLevel::kWarn,    tag, msg); }
void Logger::error  (std::string_view tag, std::string_view msg) { emit(LogLevel::kError,   tag, msg); }

void Logger::logf(LogLevel level, std::string_view tag, const char* fmt, ...) {
  char buf[1024];
  va_list ap;
  va_start(ap, fmt);
  vsnprintf(buf, sizeof(buf), fmt, ap);
  va_end(ap);
  emit(level, tag, buf);
}

} // namespace pystudio
