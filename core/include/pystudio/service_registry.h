#pragma once

#include "core.h"

#include <memory>
#include <string>
#include <typeindex>
#include <unordered_map>
#include <vector>

namespace pystudio {

/**
 * ServiceRegistry — Registre central des services natifs.
 *
 * Pattern : chaque module (pyembed, gitengine, etc.) enregistre un singleton
 * IService au démarrage. Le registre initialise les services dans l'ordre
 * d'enregistrement et les arrête en ordre inverse (LIFO).
 *
 * Thread-safety : registre lui-même non thread-safe (accès séquentiel depuis
 * le thread principal Android / JNI). Les services eux-mêmes gèrent leur
 * propre concurrence interne.
 */
class ServiceRegistry final {
public:
  ServiceRegistry() = default;
  ~ServiceRegistry() { shutdown_all(); }

  // Non-copyable, non-movable (singleton global)
  ServiceRegistry(const ServiceRegistry&)            = delete;
  ServiceRegistry& operator=(const ServiceRegistry&) = delete;

  /// Register a service. Must be called before init_all().
  template<typename T, typename... Args>
  T& emplace(Args&&... args) {
    auto svc = std::make_unique<T>(std::forward<Args>(args)...);
    T* raw = svc.get();
    by_type_[std::type_index(typeid(T))] = raw;
    order_.push_back(std::move(svc));
    return *raw;
  }

  /// Get a previously registered service by type. Returns nullptr if not found.
  template<typename T>
  T* get() const noexcept {
    auto it = by_type_.find(std::type_index(typeid(T)));
    if (it == by_type_.end()) return nullptr;
    return static_cast<T*>(it->second);
  }

  /// Initialize all registered services in registration order.
  /// Stops and returns the first error encountered.
  Result init_all();

  /// Shutdown all services in reverse registration order.
  void shutdown_all();

  /// Global singleton accessor.
  static ServiceRegistry& instance();

private:
  std::vector<std::unique_ptr<IService>>          order_;
  std::unordered_map<std::type_index, IService*>  by_type_;
  bool                                            initialized_ = false;
};

} // namespace pystudio
