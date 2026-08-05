#pragma once

#include <string>
#include <functional>

namespace pystudio {
namespace pyembed {

using OutputCallback = std::function<void(const std::string& text, bool is_stderr)>;

// ─── Per-project environment config (S-2.4: venv emulation) ─────────────
struct EnvConfig {
    std::string pythonHome;       // e.g. /data/.../runtimes/arm64-v8a
    std::string stdlibZipPath;    // e.g. .../lib/python314.zip
    std::string dynloadPath;      // e.g. .../lib/lib-dynload
    std::string sitePackagesPath; // e.g. /data/.../envs/<envId>/site-packages
    bool writeBytecode = false;   // SRS: false for zip-based stdlib
    bool siteImport = false;      // SRS: skip site.py
};

class PythonEnv {
public:
    PythonEnv();
    ~PythonEnv();

    // Initialize with simple pythonHome (backward compat, uses defaults)
    bool Initialize(const std::string& pythonHome);

    // Initialize with full environment config (S-2.4: per-project venv)
    bool Initialize(const EnvConfig& config);
    
    // Finalize the Python interpreter
    void Finalize();

    // Run a string of Python code
    bool RunString(const std::string& code);
    
    // Run a Python script file
    bool RunFile(const std::string& filepath);

    // Force garbage collection
    void ForceGcCollect(int& collected, int& uncollectable);

    // Set callback for stdout/stderr streaming
    void SetOutputCallback(OutputCallback cb);

private:
    bool initialized_ = false;
    OutputCallback output_callback_;

    // Internal method to setup sys.stdout/sys.stderr redirect
    bool SetupIOInterceptor();
};

} // namespace pyembed
} // namespace pystudio
