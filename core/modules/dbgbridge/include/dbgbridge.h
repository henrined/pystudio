#pragma once

#include <string>
#include <vector>
#include <functional>
#include <memory>

namespace pystudio {
namespace dbgbridge {

struct Breakpoint {
    std::string file;
    int line;
    bool verified = false;
    int id = 0;
};

struct Variable {
    std::string name;
    std::string value;
    std::string type;
    int variablesReference = 0;
};

struct StackFrame {
    int id;
    std::string name;
    std::string source;
    int line;
    int column;
};

// Callback pour envoyer des événements DAP (Debug Adapter Protocol)
using DapEventCallback = std::function<void(const std::string& event, const std::string& payload)>;

class DebugBridge {
public:
    DebugBridge();
    ~DebugBridge();

    void SetEventCallback(DapEventCallback cb);

    // DAP Requests
    bool Initialize();
    bool Launch(const std::string& programPath, const std::vector<std::string>& args);
    bool Attach(int pid);
    
    std::vector<Breakpoint> SetBreakpoints(const std::string& file, const std::vector<int>& lines);
    
    bool Continue();
    bool StepOver();
    bool StepInto();
    bool StepOut();
    bool Pause();
    bool Disconnect();

    std::vector<StackFrame> GetStackTrace(int threadId);
    std::vector<Variable> GetScopes(int frameId);
    std::vector<Variable> GetVariables(int variablesReference);
    Variable Evaluate(const std::string& expression, int frameId);

private:
    DapEventCallback event_callback_;
    bool is_running_ = false;
    
    // Internal RPC to lldb-server or liblldb would go here.
    void SendEvent(const std::string& event, const std::string& payload);
};

} // namespace dbgbridge
} // namespace pystudio
