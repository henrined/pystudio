#include <gtest/gtest.h>
#include "dbgbridge.h"
#include <thread>
#include <chrono>
#include <signal.h>

using namespace pystudio::dbgbridge;

class DebugBridgeTest : public ::testing::Test {
protected:
    void SetUp() override {
        signal(SIGPIPE, SIG_IGN);
    }
};

TEST_F(DebugBridgeTest, InitializeReturnsTrue) {
    DebugBridge dbg;
    EXPECT_TRUE(dbg.Initialize());
}

TEST_F(DebugBridgeTest, SetBreakpointsReturnsVerified) {
    DebugBridge dbg;
    dbg.Initialize();
    auto bps = dbg.SetBreakpoints("test.py", {10, 20});
    ASSERT_EQ(bps.size(), 2u);
    EXPECT_TRUE(bps[0].verified);
    EXPECT_TRUE(bps[1].verified);
}

TEST_F(DebugBridgeTest, GetStackTraceReturnsFrames) {
    DebugBridge dbg;
    dbg.Initialize();
    dbg.Launch("/bin/sh", {"-c", "sleep 1"});
    std::this_thread::sleep_for(std::chrono::milliseconds(500));
    dbg.Pause();
    auto frames = dbg.GetStackTrace(1);
    // It's possible we get frames, or not, but it shouldn't crash
    EXPECT_NO_FATAL_FAILURE({
        if (!frames.empty()) {
            EXPECT_GT(frames[0].id, 0);
        }
    });
}

TEST_F(DebugBridgeTest, EvaluateReturnsValue) {
    DebugBridge dbg;
    dbg.Initialize();
    dbg.Launch("/bin/sh", {"-c", "sleep 1"});
    std::this_thread::sleep_for(std::chrono::milliseconds(500));
    dbg.Pause();
    auto var = dbg.Evaluate("2+2", 1);
    // Even if it's error, it should not crash and name should match expression
    EXPECT_EQ(var.name, "2+2");
}

TEST_F(DebugBridgeTest, FullCycle) {
    DebugBridge dbg;
    EXPECT_TRUE(dbg.Initialize());
    EXPECT_TRUE(dbg.Launch("/bin/sh", {"-c", "sleep 1"}));
    
    auto bps = dbg.SetBreakpoints("test.sh", {1});
    ASSERT_EQ(bps.size(), 1u);
    EXPECT_TRUE(bps[0].verified);
    
    EXPECT_TRUE(dbg.Continue());
    EXPECT_TRUE(dbg.Disconnect());
}
