#include "cxxtoolchain.h"
#include "pystudio/logger.h"
#include <fstream>
#include <sstream>
#include <cstdlib>
#include <array>
#include <filesystem>

namespace fs = std::filesystem;

namespace pystudio {
namespace cxxtoolchain {

ToolchainManager::ToolchainManager(const std::string& toolchainDir) {
    if (toolchainDir.empty()) {
        cmake_path_ = FindExecutable("cmake");
        ninja_path_ = FindExecutable("ninja");
        clang_format_path_ = FindExecutable("clang-format");
        clang_tidy_path_ = FindExecutable("clang-tidy");
        clang_path_ = FindExecutable("clang");
        clangxx_path_ = FindExecutable("clang++");
        lld_path_ = FindExecutable("lld");
        llvm_ar_path_ = FindExecutable("llvm-ar");
        llvm_strip_path_ = FindExecutable("llvm-strip");
        sysroot_path_ = "";
    } else {
        fs::path tDir(toolchainDir);
        cmake_path_ = (tDir / "bin" / "cmake").string();
        ninja_path_ = (tDir / "bin" / "ninja").string();
        clang_format_path_ = (tDir / "bin" / "clang-format").string();
        clang_tidy_path_ = (tDir / "bin" / "clang-tidy").string();
        clang_path_ = (tDir / "bin" / "clang").string();
        clangxx_path_ = (tDir / "bin" / "clang++").string();
        lld_path_ = (tDir / "bin" / "lld").string();
        llvm_ar_path_ = (tDir / "bin" / "llvm-ar").string();
        llvm_strip_path_ = (tDir / "bin" / "llvm-strip").string();
        sysroot_path_ = (tDir / "sysroot").string();
    }
}

ToolchainManager::~ToolchainManager() = default;

bool ToolchainManager::InstallToolchain(const std::string& archivePath, const std::string& extractDir, const std::string& expectedSha256) {
    if (!fs::exists(archivePath)) {
        PS_LOG_E("ToolchainManager", "Toolchain archive not found: " + archivePath);
        return false;
    }

    if (!expectedSha256.empty()) {
        std::string cmd = "sha256sum " + archivePath + " | awk '{print $1}'";
        std::string output;
        if (RunCommand(cmd, output) != 0 || output.find(expectedSha256) == std::string::npos) {
            PS_LOG_E("ToolchainManager", "SHA-256 verification failed for toolchain. Expected: " + expectedSha256);
            return false;
        }
    }

    if (!fs::exists(extractDir)) {
        fs::create_directories(extractDir);
    }

    std::string extractCmd;
    if (archivePath.find(".zip") != std::string::npos) {
        extractCmd = "unzip -q -o " + archivePath + " -d " + extractDir;
    } else if (archivePath.find(".tar") != std::string::npos) {
        extractCmd = "tar -xf " + archivePath + " -C " + extractDir;
    } else {
        PS_LOG_E("ToolchainManager", "Unsupported archive format: " + archivePath);
        return false;
    }

    std::string output;
    if (RunCommand(extractCmd, output) != 0) {
        PS_LOG_E("ToolchainManager", "Failed to extract toolchain: " + output);
        return false;
    }

    PS_LOG_I("ToolchainManager", "Toolchain successfully installed to " + extractDir);
    return true;
}

std::string ToolchainManager::FindExecutable(const std::string& name) {
    // In a real environment, this might look in the downloaded NDK/sysroot.
    // Here we just use the system path or a specific runtimes path.
    std::string cmd = "which " + name;
    std::string output;
    if (RunCommand(cmd, output) == 0 && !output.empty()) {
        if (output.back() == '\n') output.pop_back();
        return output;
    }
    return name;
}

int ToolchainManager::RunCommand(const std::string& cmd, std::string& output) {
    PS_LOG_I("ToolchainManager", "Running: " + cmd);
    std::array<char, 128> buffer;
    output.clear();
    FILE* pipe = popen(cmd.c_str(), "r");
    if (!pipe) {
        PS_LOG_E("ToolchainManager", "popen() failed for: " + cmd);
        return -1;
    }
    while (fgets(buffer.data(), buffer.size(), pipe) != nullptr) {
        output += buffer.data();
    }
    int result = pclose(pipe);
    return WEXITSTATUS(result);
}

bool ToolchainManager::GenerateProjectFiles(const ProjectConfig& config) {
    fs::path dir(config.projectPath);
    if (!fs::exists(dir)) {
        fs::create_directories(dir);
    }

    // Generate CMakeLists.txt
    fs::path cmakeFile = dir / "CMakeLists.txt";
    if (!fs::exists(cmakeFile)) {
        std::ofstream out(cmakeFile);
        if (!out.is_open()) return false;
        
        out << "cmake_minimum_required(VERSION 3.22)\n";
        out << "project(" << config.projectName << " VERSION 1.0.0 LANGUAGES CXX)\n\n";
        out << "set(CMAKE_CXX_STANDARD 20)\n";
        out << "set(CMAKE_CXX_STANDARD_REQUIRED ON)\n\n";
        out << "set(CMAKE_EXPORT_COMPILE_COMMANDS ON)\n\n";
        out << "add_executable(${PROJECT_NAME} main.cpp)\n";
        out.close();
        
        fs::path mainFile = dir / "main.cpp";
        std::ofstream mainOut(mainFile);
        mainOut << "#include <iostream>\n\nint main() {\n    std::cout << \"Hello from " << config.projectName << "!\" << std::endl;\n    return 0;\n}\n";
        mainOut.close();
    }

    // Generate CMakePresets.json
    fs::path presetsFile = dir / "CMakePresets.json";
    std::ofstream pout(presetsFile);
    if (!pout.is_open()) return false;
    
    pout << "{\n";
    pout << "  \"version\": 3,\n";
    pout << "  \"configurePresets\": [\n";
    for (size_t i = 0; i < config.abis.size(); ++i) {
        const auto& abi = config.abis[i];
        pout << "    {\n";
        pout << "      \"name\": \"android-" << abi << "\",\n";
        pout << "      \"hidden\": false,\n";
        pout << "      \"generator\": \"Ninja\",\n";
        pout << "      \"binaryDir\": \"${sourceDir}/build/" << abi << "\",\n";
        pout << "      \"cacheVariables\": {\n";
        pout << "        \"CMAKE_SYSTEM_NAME\": \"Android\",\n";
        pout << "        \"CMAKE_ANDROID_ARCH_ABI\": \"" << abi << "\"\n";
        pout << "      }\n";
        pout << "    }" << (i + 1 < config.abis.size() ? "," : "") << "\n";
    }
    pout << "  ]\n";
    pout << "}\n";
    pout.close();

    PS_LOG_I("ToolchainManager", "Generated CMakeLists.txt and CMakePresets.json");
    return true;
}

bool ToolchainManager::ConfigureCMake(const std::string& projectPath, const std::string& abi) {
    std::string cmd = cmake_path_ + " --preset android-" + abi + " -S " + projectPath;
    
    // S-3.3: Use the embedded Clang, Ninja, and Sysroot if provided
    if (!sysroot_path_.empty()) {
        cmd += " -DCMAKE_C_COMPILER=" + clang_path_;
        cmd += " -DCMAKE_CXX_COMPILER=" + clangxx_path_;
        cmd += " -DCMAKE_SYSROOT=" + sysroot_path_;
        cmd += " -DCMAKE_MAKE_PROGRAM=" + ninja_path_;
    }

    std::string output;
    if (RunCommand(cmd, output) != 0) {
        PS_LOG_E("ToolchainManager", "CMake configure failed for ABI " + abi + ":\n" + output);
        return false;
    }
    return true;
}

std::string ToolchainManager::BuildNinja(const std::string& projectPath, const std::string& abi, int threads) {
    fs::path buildDir = fs::path(projectPath) / "build" / abi;
    std::string cmd = cmake_path_ + " --build " + buildDir.string();
    if (threads > 0) {
        cmd += " -j" + std::to_string(threads);
    }
    std::string output;
    if (RunCommand(cmd, output) != 0) {
        PS_LOG_E("ToolchainManager", "Ninja build failed for ABI " + abi + ":\n" + output);
    }
    return output;
}

bool ToolchainManager::FormatCode(const std::string& filePath) {
    std::string cmd = clang_format_path_ + " -i " + filePath;
    std::string output;
    return RunCommand(cmd, output) == 0;
}

std::string ToolchainManager::TidyCode(const std::string& filePath, const std::string& buildPath) {
    std::string cmd = clang_tidy_path_ + " -p " + buildPath + " " + filePath;
    std::string output;
    RunCommand(cmd, output);
    return output;
}

bool ToolchainManager::GenerateCompileCommands(const std::string& projectPath) {
    // If we use CMake with CMAKE_EXPORT_COMPILE_COMMANDS=ON, we can just copy it to root.
    // Assuming arm64-v8a as the primary one for clangd:
    fs::path buildPath = fs::path(projectPath) / "build" / "arm64-v8a";
    fs::path srcCmds = buildPath / "compile_commands.json";
    fs::path dstCmds = fs::path(projectPath) / "compile_commands.json";
    
    if (fs::exists(srcCmds)) {
        fs::copy_file(srcCmds, dstCmds, fs::copy_options::overwrite_existing);
        return true;
    }
    return false;
}

} // namespace cxxtoolchain
} // namespace pystudio
