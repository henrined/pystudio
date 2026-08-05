#include "dbgbridge.h"
#include "pystudio/logger.h"
#include <unistd.h>
#include <sys/types.h>
#include <sys/wait.h>
#include <thread>
#include <iostream>
#include <sstream>
#include <regex>
#include <fcntl.h>
#include <string.h>
#include <mutex>
#include <condition_variable>
#include <atomic>
#include <chrono>

namespace pystudio {
namespace dbgbridge {

class LldbProcess {
public:
    int in_fd = -1;
    int out_fd = -1;
    pid_t pid = -1;
    std::thread read_thread;
    std::atomic<bool> running{false};
    DapEventCallback event_cb;

    std::mutex output_mutex;
    std::condition_variable output_cv;
    std::string current_output;
    bool command_done = false;

    void Start(DapEventCallback cb) {
        event_cb = cb;
        int pipe_in[2];
        int pipe_out[2];
        if (pipe(pipe_in) != 0 || pipe(pipe_out) != 0) return;

        pid = fork();
        if (pid == 0) {
            dup2(pipe_in[0], STDIN_FILENO);
            dup2(pipe_out[1], STDOUT_FILENO);
            dup2(pipe_out[1], STDERR_FILENO);
            close(pipe_in[1]);
            close(pipe_out[0]);
            // Run lldb in batch mode reading from stdin
            execlp("lldb", "lldb", "--no-use-colors", nullptr);
            exit(1);
        }
        
        close(pipe_in[0]);
        close(pipe_out[1]);
        in_fd = pipe_in[1];
        out_fd = pipe_out[0];
        running = true;

        read_thread = std::thread([this]() {
            char buffer[1024];
            std::string accumulated;
            while (running) {
                ssize_t n = read(out_fd, buffer, sizeof(buffer) - 1);
                if (n > 0) {
                    buffer[n] = '\0';
                    std::string output(buffer);
                    PS_LOG_I("DebugBridge", "LLDB: " + output);
                    
                    accumulated += output;
                    
                    // Basic parsing to trigger events
                    if (output.find("stopped") != std::string::npos || output.find("stop reason") != std::string::npos) {
                        if (event_cb) event_cb("stopped", "{\"reason\": \"pause\", \"threadId\": 1}");
                    }
                    if (output.find("exited") != std::string::npos) {
                        if (event_cb) event_cb("exited", "{\"exitCode\": 0}");
                        running = false;
                    }
                    
                    size_t prompt_pos = accumulated.find("(lldb) ");
                    if (prompt_pos != std::string::npos) {
                        std::lock_guard<std::mutex> lock(output_mutex);
                        current_output = accumulated.substr(0, prompt_pos);
                        command_done = true;
                        accumulated = accumulated.substr(prompt_pos + 7);
                        output_cv.notify_one();
                    }
                } else if (n == 0) {
                    break;
                }
            }
        });
        
        // Wait for the first (lldb) prompt to signify readiness
        {
            std::unique_lock<std::mutex> lock(output_mutex);
            output_cv.wait_for(lock, std::chrono::seconds(5), [this]{ return command_done; });
            command_done = false;
            current_output.clear();
        }
    }

    std::string SendCommand(const std::string& cmd) {
        if (in_fd >= 0) {
            {
                std::lock_guard<std::mutex> lock(output_mutex);
                command_done = false;
                current_output.clear();
            }
            std::string c = cmd + "\n";
            write(in_fd, c.c_str(), c.length());
            
            std::unique_lock<std::mutex> lock(output_mutex);
            if (output_cv.wait_for(lock, std::chrono::seconds(5), [this]{ return command_done; })) {
                std::string res = current_output;
                current_output.clear();
                command_done = false;
                return res;
            } else {
                PS_LOG_I("DebugBridge", "LLDB Command Timeout: " + cmd);
            }
        }
        return "";
    }

    void Stop() {
        running = false;
        if (in_fd >= 0) close(in_fd);
        if (out_fd >= 0) close(out_fd);
        if (pid > 0) {
            kill(pid, SIGTERM);
            waitpid(pid, nullptr, 0);
        }
        if (read_thread.joinable()) read_thread.join();
    }
};

static std::unique_ptr<LldbProcess> g_lldb;

DebugBridge::DebugBridge() {
    g_lldb = std::make_unique<LldbProcess>();
}

DebugBridge::~DebugBridge() {
    Disconnect();
}

void DebugBridge::SetEventCallback(DapEventCallback cb) {
    event_callback_ = cb;
}

void DebugBridge::SendEvent(const std::string& event, const std::string& payload) {
    if (event_callback_) {
        event_callback_(event, payload);
    }
}

bool DebugBridge::Initialize() {
    PS_LOG_I("DebugBridge", "Initializing Debug Bridge");
    g_lldb->Start(event_callback_);
    SendEvent("initialized", "{}");
    return true;
}

bool DebugBridge::Launch(const std::string& programPath, const std::vector<std::string>& args) {
    PS_LOG_I("DebugBridge", "Launching program: " + programPath);
    is_running_ = true;
    
    // Connect to lldb-server running in the isolated process
    g_lldb->SendCommand("platform select remote-android");
    g_lldb->SendCommand("platform connect unix-abstract:///tmp/lldb-server.sock");
    g_lldb->SendCommand("file " + programPath);
    
    // Pass args if any
    if (!args.empty()) {
        std::string argsCmd = "settings set target.run-args";
        for (const auto& a : args) argsCmd += " \"" + a + "\"";
        g_lldb->SendCommand(argsCmd);
    }
    
    g_lldb->SendCommand("run");
    SendEvent("process", "{\"name\": \"" + programPath + "\"}");
    return true;
}

bool DebugBridge::Attach(int pid) {
    PS_LOG_I("DebugBridge", "Attaching to PID: " + std::to_string(pid));
    is_running_ = true;
    g_lldb->SendCommand("platform select remote-android");
    g_lldb->SendCommand("platform connect unix-abstract:///tmp/lldb-server.sock");
    g_lldb->SendCommand("attach -p " + std::to_string(pid));
    return true;
}

std::vector<Breakpoint> DebugBridge::SetBreakpoints(const std::string& file, const std::vector<int>& lines) {
    PS_LOG_I("DebugBridge", "Setting breakpoints in " + file);
    std::vector<Breakpoint> bps;
    int id = 1;
    for (int line : lines) {
        g_lldb->SendCommand("breakpoint set --file " + file + " --line " + std::to_string(line));
        bps.push_back({file, line, true, id++});
    }
    return bps;
}

bool DebugBridge::Continue() {
    PS_LOG_I("DebugBridge", "Continue");
    g_lldb->SendCommand("continue");
    is_running_ = true;
    return true;
}

bool DebugBridge::StepOver() {
    PS_LOG_I("DebugBridge", "Step Over");
    g_lldb->SendCommand("thread step-over");
    return true;
}

bool DebugBridge::StepInto() {
    PS_LOG_I("DebugBridge", "Step Into");
    g_lldb->SendCommand("thread step-in");
    return true;
}

bool DebugBridge::StepOut() {
    PS_LOG_I("DebugBridge", "Step Out");
    g_lldb->SendCommand("thread step-out");
    return true;
}

bool DebugBridge::Pause() {
    PS_LOG_I("DebugBridge", "Pause");
    g_lldb->SendCommand("process interrupt");
    return true;
}

bool DebugBridge::Disconnect() {
    PS_LOG_I("DebugBridge", "Disconnecting");
    is_running_ = false;
    if (g_lldb) {
        g_lldb->SendCommand("quit");
        g_lldb->Stop();
    }
    SendEvent("exited", "{\"exitCode\": 0}");
    return true;
}

std::vector<StackFrame> DebugBridge::GetStackTrace(int threadId) {
    PS_LOG_I("DebugBridge", "GetStackTrace");
    std::string output = g_lldb->SendCommand("thread backtrace");
    std::vector<StackFrame> frames;
    
    std::regex re(R"(frame\s+#(\d+):.*?`([^\s(]+).*?\s+at\s+([^:]+):(\d+)(?::(\d+))?)");
    std::istringstream iss(output);
    std::string line;
    while (std::getline(iss, line)) {
        std::smatch match;
        if (std::regex_search(line, match, re)) {
            StackFrame sf;
            sf.id = std::stoi(match[1].str());
            sf.name = match[2].str();
            sf.source = match[3].str();
            sf.line = std::stoi(match[4].str());
            sf.column = match[5].matched ? std::stoi(match[5].str()) : 0;
            frames.push_back(sf);
        }
    }
    
    return frames;
}

std::vector<Variable> DebugBridge::GetScopes(int frameId) {
    PS_LOG_I("DebugBridge", "GetScopes");
    g_lldb->SendCommand("frame select " + std::to_string(frameId));
    return {{"Locals", "", "", 1000}};
}

std::vector<Variable> DebugBridge::GetVariables(int variablesReference) {
    PS_LOG_I("DebugBridge", "GetVariables");
    std::vector<Variable> vars;
    if (variablesReference == 1000) {
        std::string output = g_lldb->SendCommand("frame variable");
        
        std::regex re(R"(\(([^)]+)\)\s+([a-zA-Z0-9_]+)\s*=\s*(.*))");
        std::istringstream iss(output);
        std::string line;
        int nextRef = 1001;
        while (std::getline(iss, line)) {
            std::smatch match;
            if (std::regex_search(line, match, re)) {
                Variable v;
                v.type = match[1].str();
                v.name = match[2].str();
                v.value = match[3].str();
                
                if (v.type.find("struct") != std::string::npos || 
                    v.type.find("class") != std::string::npos ||
                    v.value.find("{") != std::string::npos) {
                    v.variablesReference = nextRef++;
                } else {
                    v.variablesReference = 0;
                }
                vars.push_back(v);
            }
        }
    }
    return vars;
}

Variable DebugBridge::Evaluate(const std::string& expression, int frameId) {
    PS_LOG_I("DebugBridge", "Evaluate: " + expression);
    std::string output = g_lldb->SendCommand("expr " + expression);
    
    std::regex re(R"(\(([^)]+)\)\s+([^\s]+)\s*=\s*(.*))");
    std::smatch match;
    Variable v;
    v.name = expression;
    v.type = "unknown";
    v.value = "error";
    v.variablesReference = 0;
    
    if (std::regex_search(output, match, re)) {
        v.type = match[1].str();
        v.value = match[3].str();
    }
    
    return v;
}

} // namespace dbgbridge
} // namespace pystudio

