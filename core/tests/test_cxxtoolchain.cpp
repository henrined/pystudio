#include <gtest/gtest.h>
#include "cxxtoolchain.h"
#include <filesystem>
#include <fstream>

using namespace pystudio::cxxtoolchain;
namespace fs = std::filesystem;

TEST(CxxToolchainTest, GenerateAndConfigure) {
    ToolchainManager manager;
    ProjectConfig config;
    config.projectName = "TestApp";
    config.projectPath = "/data/data/com.termux/files/home/pystudio/core/build/test_project";
    config.abis = {"arm64-v8a"};
    
    if (fs::exists(config.projectPath)) {
        fs::remove_all(config.projectPath);
    }
    
    ASSERT_TRUE(manager.GenerateProjectFiles(config));
    
    ASSERT_TRUE(fs::exists(fs::path(config.projectPath) / "CMakeLists.txt"));
    ASSERT_TRUE(fs::exists(fs::path(config.projectPath) / "CMakePresets.json"));
    ASSERT_TRUE(fs::exists(fs::path(config.projectPath) / "main.cpp"));
}

TEST(CxxToolchainTest, InstallToolchain) {
    ToolchainManager manager;
    
    // Create dummy archive and directory
    fs::path archiveDir = "/data/data/com.termux/files/home/pystudio/core/build/test_toolchain";
    fs::path extractDir = archiveDir / "extracted";
    fs::path archivePath = archiveDir / "toolchain.zip";
    
    if (fs::exists(archiveDir)) {
        fs::remove_all(archiveDir);
    }
    fs::create_directories(archiveDir);
    
    // We create a dummy file and zip it (assuming zip is available in Termux)
    std::system(("touch " + archiveDir.string() + "/dummy.txt").c_str());
    std::system(("cd " + archiveDir.string() + " && zip -q toolchain.zip dummy.txt").c_str());
    
    // Test InstallToolchain (no sha256 to bypass checksum on dummy)
    ASSERT_TRUE(manager.InstallToolchain(archivePath.string(), extractDir.string(), ""));
    ASSERT_TRUE(fs::exists(extractDir / "dummy.txt"));
    
    // Test ToolchainManager initialization from path (S-3.1, S-3.2, S-3.3)
    ToolchainManager customManager(extractDir.string());
    
    // Test that the customManager configures cmake command with the specified sysroot
    // We can't easily intercept the `ConfigureCMake` internal run here without mocking,
    // but initializing without crashing is a good smoke test.
}
