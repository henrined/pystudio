package com.pystudio;

interface ILspCallback {
    void onMessage(String jsonRpcMessage);
    void onError(String errorMessage);
}
