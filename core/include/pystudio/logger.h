#pragma once

#include "core.h"

#include <atomic>
#include <string>
#include <string_view>

namespace pystudio {

/**
 * Logger — Façade de log légère.
 *
 * Tous les modules écrivent via Logger. Sur Android, le backend délègue à
 * __android_log_print (logcat). En tests Termux, il écrit sur stderr.
 * Le callback est configurable via set_callback() (utilisé par le bridge JNI
 * pour relayer les logs vers React Native).
 */
class Logger {
public:
  static Logger& instance();

  void set_callback(LogCallback cb);
  void set_min_level(LogLevel level) noexcept { min_level_ = level; }

  void verbose(std::string_view tag, std::string_view msg);
  void debug  (std::string_view tag, std::string_view msg);
  void info   (std::string_view tag, std::string_view msg);
  void warn   (std::string_view tag, std::string_view msg);
  void error  (std::string_view tag, std::string_view msg);

  // Convenience: formatted log (printf-style)
  void logf(LogLevel level, std::string_view tag, const char* fmt, ...)
      __attribute__((format(printf, 4, 5)));

private:
  Logger() = default;

  void emit(LogLevel level, std::string_view tag, std::string_view msg);

  LogCallback           callback_;
  std::atomic<LogLevel> min_level_{LogLevel::kDebug};
};

// ─── Macros de commodité ─────────────────────────────────────────────────────
#define PS_LOG_V(tag, msg) ::pystudio::Logger::instance().verbose(tag, msg)
#define PS_LOG_D(tag, msg) ::pystudio::Logger::instance().debug(tag, msg)
#define PS_LOG_I(tag, msg) ::pystudio::Logger::instance().info(tag, msg)
#define PS_LOG_W(tag, msg) ::pystudio::Logger::instance().warn(tag, msg)
#define PS_LOG_E(tag, msg) ::pystudio::Logger::instance().error(tag, msg)

} // namespace pystudio
