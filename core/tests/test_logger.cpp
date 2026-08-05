#include <gtest/gtest.h>
#include "pystudio/logger.h"

#include <string>
#include <vector>

namespace pystudio {

struct LogEntry {
  LogLevel    level;
  std::string tag;
  std::string message;
};

TEST(LoggerTest, CallbackReceivesMessages) {
  std::vector<LogEntry> entries;

  Logger::instance().set_callback([&](LogLevel level,
                                       std::string_view tag,
                                       std::string_view msg) {
    entries.push_back({level, std::string(tag), std::string(msg)});
  });
  Logger::instance().set_min_level(LogLevel::kVerbose);

  Logger::instance().info("TestTag", "Hello from logger");
  Logger::instance().error("TestTag", "An error occurred");

  ASSERT_EQ(entries.size(), 2u);
  EXPECT_EQ(entries[0].level,   LogLevel::kInfo);
  EXPECT_EQ(entries[0].tag,     "TestTag");
  EXPECT_EQ(entries[0].message, "Hello from logger");
  EXPECT_EQ(entries[1].level,   LogLevel::kError);

  // Reset callback to avoid interference with other tests
  Logger::instance().set_callback(nullptr);
}

TEST(LoggerTest, MinLevelFiltersMessages) {
  std::vector<LogEntry> entries;

  Logger::instance().set_callback([&](LogLevel level,
                                       std::string_view tag,
                                       std::string_view msg) {
    entries.push_back({level, std::string(tag), std::string(msg)});
  });
  Logger::instance().set_min_level(LogLevel::kWarn);

  Logger::instance().debug("T", "should be filtered");
  Logger::instance().info("T",  "should be filtered");
  Logger::instance().warn("T",  "should pass");
  Logger::instance().error("T", "should pass");

  EXPECT_EQ(entries.size(), 2u);

  Logger::instance().set_callback(nullptr);
  Logger::instance().set_min_level(LogLevel::kDebug);
}

} // namespace pystudio
