package com.pystudio;
import com.pystudio.ILspCallback;

interface ILspService {
    boolean startServer(String language, String serverPath, String workspacePath, ILspCallback callback);
    boolean sendMessage(String jsonRpcMessage);
    void stopServer();
}
