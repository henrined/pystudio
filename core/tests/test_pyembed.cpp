#include <gtest/gtest.h>
#include "pyembed.h"

using namespace pystudio::pyembed;

TEST(PyEmbedTest, InitializationAndStreaming) {
    PythonEnv env;
    
    // We pass the absolute path to the runtimes directory.
    // In our test environment, we might need a hardcoded path or relative path, but let's assume
    // it's run from `core/build` so `../../runtimes/arm64-v8a` is the path.
    // However, it's just a test, it might fail if runtimes path is incorrect.
    
    std::string captured_stdout;
    env.SetOutputCallback([&](const std::string& text, bool is_stderr) {
        if (!is_stderr) {
            captured_stdout += text;
        }
    });

    // We assume test is run from core/build.
    bool init_ok = env.Initialize("/data/data/com.termux/files/home/pystudio/runtimes/arm64-v8a");
    // If it fails due to missing python home, we shouldn't fail the test completely if we can't find it,
    // but let's assert true for now.
    ASSERT_TRUE(init_ok);

    bool run_ok = env.RunString("print('Hello from Python!', end='')");
    ASSERT_TRUE(run_ok);

    EXPECT_EQ(captured_stdout, "Hello from Python!");
}
