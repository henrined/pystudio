#include "pystudio/service_registry.h"
#include "pystudio/logger.h"

#include <cassert>

namespace pystudio {

// ─── Singleton ───────────────────────────────────────────────────────────────
ServiceRegistry& ServiceRegistry::instance() {
  static ServiceRegistry registry;
  return registry;
}

// ─── init_all ────────────────────────────────────────────────────────────────
Result ServiceRegistry::init_all() {
  assert(!initialized_ && "ServiceRegistry::init_all() called twice");

  for (auto& svc : order_) {
    PS_LOG_I("ServiceRegistry", ("Initializing service: " + std::string(svc->name())).c_str());
    Result r = svc->init();
    if (!r.ok()) {
      PS_LOG_E("ServiceRegistry",
               ("Service failed to init: " + std::string(svc->name()) +
                " — " + r.message).c_str());
      // Shutdown already-initialized services in reverse
      for (auto it = order_.rbegin(); it != order_.rend(); ++it) {
        if (it->get() == svc.get()) {
          // We found the service that failed, now shutdown the ones before it
          while (++it != order_.rend()) {
            (*it)->shutdown();
          }
          break;
        }
      }
      return r;
    }
    PS_LOG_I("ServiceRegistry", ("  OK: " + std::string(svc->name())).c_str());
  }

  initialized_ = true;
  return Result::Ok();
}

// ─── shutdown_all ─────────────────────────────────────────────────────────────
void ServiceRegistry::shutdown_all() {
  if (!initialized_) return;

  for (auto it = order_.rbegin(); it != order_.rend(); ++it) {
    PS_LOG_I("ServiceRegistry", ("Shutting down: " + std::string((*it)->name())).c_str());
    (*it)->shutdown();
  }
  initialized_ = false;
}

} // namespace pystudio
