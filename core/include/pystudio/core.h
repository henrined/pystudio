#pragma once

#include <cstdint>
#include <functional>
#include <memory>
#include <string>
#include <string_view>

namespace pystudio {

// ─── Version ─────────────────────────────────────────────────────────────────
inline constexpr int kVersionMajor = 0;
inline constexpr int kVersionMinor = 1;
inline constexpr int kVersionPatch = 0;

// ─── Result type ─────────────────────────────────────────────────────────────
enum class Status : uint8_t {
  kOk      = 0,
  kError   = 1,
  kTimeout = 2,
  kCancelled = 3,
};

struct Result {
  Status      status  = Status::kOk;
  std::string message;

  [[nodiscard]] bool ok() const noexcept { return status == Status::kOk; }

  static Result Ok()                          { return {Status::kOk, {}}; }
  static Result Err(std::string msg)          { return {Status::kError, std::move(msg)}; }
  static Result Cancelled(std::string msg={}) { return {Status::kCancelled, std::move(msg)}; }
};

// ─── Service interface ───────────────────────────────────────────────────────
class IService {
public:
  virtual ~IService() = default;

  /// Called once by the ServiceRegistry after registration.
  virtual Result init() = 0;

  /// Called by the ServiceRegistry on shutdown (reverse order of registration).
  virtual void   shutdown() = 0;

  /// Human-readable name used for logging and diagnostics.
  [[nodiscard]] virtual std::string_view name() const noexcept = 0;
};

// ─── Log callback ─────────────────────────────────────────────────────────────
enum class LogLevel : uint8_t { kVerbose, kDebug, kInfo, kWarn, kError };

using LogCallback = std::function<void(LogLevel level,
                                       std::string_view tag,
                                       std::string_view message)>;

} // namespace pystudio
