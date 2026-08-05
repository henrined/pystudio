package com.pystudio;

interface IDebugCallback {
    void onDapEvent(String event, String jsonPayload);
}
