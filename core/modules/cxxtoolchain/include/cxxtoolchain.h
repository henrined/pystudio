#pragma once

#include <string>
#include <vector>
#include <memory>
#include <functional>

namespace pystudio {
namespace cxxtoolchain {

struct ProjectConfig {
    std::string projectName;
    std::string projectPath;
    std::vector<std::string> abis;
    bool enableAsan = false;
};

class ToolchainManager {
public:
    ToolchainManager(const std::string& toolchainDir = "");
    ~ToolchainManager();

    // S-3.1, S-3.2, S-3.3: Installation et vérification de la toolchain (Clang, CMake, Ninja, Sysroot)
    bool InstallToolchain(const std::string& archivePath, const std::string& extractDir, const std::string& expectedSha256 = "");

    // S-3.5: Génération automatique de CMakeLists.txt et CMakePresets.json
    bool GenerateProjectFiles(const ProjectConfig& config);

    // S-3.4 / S-3.6: Pilotage CMake/Ninja et Build multi-ABI
    bool ConfigureCMake(const std::string& projectPath, const std::string& abi);
    std::string BuildNinja(const std::string& projectPath, const std::string& abi, int threads = 0);

    // S-3.7: clang-format et clang-tidy
    bool FormatCode(const std::string& filePath);
    std::string TidyCode(const std::string& filePath, const std::string& buildPath);

    // S-3.8: Génération de compile_commands.json pour clangd (souvent généré par CMake, on peut l'extraire)
    bool GenerateCompileCommands(const std::string& projectPath);
    
private:
    std::string FindExecutable(const std::string& name);
    int RunCommand(const std::string& cmd, std::string& output);
    
    std::string cmake_path_;
    std::string ninja_path_;
    std::string clang_format_path_;
    std::string clang_tidy_path_;
    std::string clang_path_;
    std::string clangxx_path_;
    std::string lld_path_;
    std::string llvm_ar_path_;
    std::string llvm_strip_path_;
    std::string sysroot_path_;
};

} // namespace cxxtoolchain
} // namespace pystudio
