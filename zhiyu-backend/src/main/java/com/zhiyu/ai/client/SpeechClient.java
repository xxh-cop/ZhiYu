package com.zhiyu.ai.client;

/** Provider-neutral adapter. Credentials and network calls remain server-side. */
public interface SpeechClient {
    boolean available();
    String transcribe(byte[] audio,String mimeType);
    byte[] synthesize(String text);
}
