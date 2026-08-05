#include <gtest/gtest.h>
#include "pystudio/service_registry.h"
#include "pystudio/core.h"

#include <string>
#include <string_view>
#include <vector>

namespace pystudio {

// ─── Stub services for testing ───────────────────────────────────────────────
struct InitOrder {
  std::vector<std::string> inits;
  std::vector<std::string> shutdowns;
};

class StubService : public IService {
public:
  explicit StubService(std::string n, InitOrder& order, bool fail = false)
      : name_(std::move(n)), order_(order), fail_(fail) {}

  Result init() override {
    order_.inits.push_back(name_);
    if (fail_) return Result::Err(name_ + " failed to init");
    return Result::Ok();
  }

  void shutdown() override {
    order_.shutdowns.push_back(name_);
  }

  [[nodiscard]] std::string_view name() const noexcept override {
    return name_;
  }

private:
  std::string  name_;
  InitOrder&   order_;
  bool         fail_;
};

// ─── Tests ───────────────────────────────────────────────────────────────────
TEST(ServiceRegistryTest, InitOrderAndShutdownReverseOrder) {
  InitOrder order;
  ServiceRegistry reg;

  reg.emplace<StubService>("ServiceA", order);
  reg.emplace<StubService>("ServiceB", order);
  reg.emplace<StubService>("ServiceC", order);

  auto r = reg.init_all();
  EXPECT_TRUE(r.ok());
  EXPECT_EQ(order.inits, (std::vector<std::string>{"ServiceA", "ServiceB", "ServiceC"}));

  // shutdown_all called by destructor — we call it explicitly here
  reg.shutdown_all();
  EXPECT_EQ(order.shutdowns, (std::vector<std::string>{"ServiceC", "ServiceB", "ServiceA"}));
}

TEST(ServiceRegistryTest, GetByType) {
  InitOrder order;
  ServiceRegistry reg;
  auto& svc = reg.emplace<StubService>("GetTest", order);
  reg.init_all();

  StubService* found = reg.get<StubService>();
  EXPECT_EQ(found, &svc);
  reg.shutdown_all();
}

TEST(ServiceRegistryTest, InitFailureStopsChain) {
  InitOrder order;
  ServiceRegistry reg;

  reg.emplace<StubService>("GoodA", order);
  reg.emplace<StubService>("BadB",  order, /*fail=*/true);
  reg.emplace<StubService>("GoodC", order); // should never be reached

  auto r = reg.init_all();
  EXPECT_FALSE(r.ok());
  EXPECT_EQ(order.inits, (std::vector<std::string>{"GoodA", "BadB"}));
  // GoodC was never initialized
  EXPECT_EQ(order.inits.size(), 2u);
  reg.shutdown_all();
}

} // namespace pystudio
