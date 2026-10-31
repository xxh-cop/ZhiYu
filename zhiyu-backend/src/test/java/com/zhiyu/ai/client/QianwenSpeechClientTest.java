package com.zhiyu.ai.client;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpServer;
import com.zhiyu.common.exception.BusinessException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class QianwenSpeechClientTest {

    private HttpServer server;
    private QianwenSpeechClient client;
    private final AtomicReference<String> asrBody = new AtomicReference<>();
    private final AtomicReference<String> asrAuth = new AtomicReference<>();
    private final AtomicReference<String> ttsBody = new AtomicReference<>();
    private final byte[] mp3 = new byte[]{'I', 'D', '3', 4, 5, 6, 7};

    @BeforeEach
    void setUp() throws Exception {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/api/v1/services/aigc/multimodal-generation/generation", exchange -> {
            asrBody.set(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
            asrAuth.set(exchange.getRequestHeaders().getFirst("Authorization"));
            respond(exchange, "{\"output\":{\"text\":\"你好，今天慢慢来。\"}}");
        });
        server.createContext("/api/v1/services/audio/tts/SpeechSynthesizer", exchange -> {
            ttsBody.set(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
            respond(exchange, "{\"output\":{\"audio\":{\"data\":\""
                    + Base64.getEncoder().encodeToString(mp3) + "\"}}}");
        });
        server.start();
        String base = "http://127.0.0.1:" + server.getAddress().getPort() + "/api/v1";
        client = new QianwenSpeechClient(new ObjectMapper(), true, "test-key", base,
                "asr-model", "tts-model", "test-voice", 16000);
    }

    @AfterEach
    void tearDown() {
        if (server != null) server.stop(0);
    }

    @Test
    @DisplayName("ASR 使用 Base64 Data URI 并读取 output.text")
    void transcribesWithDataUri() {
        String text = client.transcribe(mp3, "audio/mpeg");

        assertEquals("你好，今天慢慢来。", text);
        assertEquals("Bearer test-key", asrAuth.get());
        assertTrue(asrBody.get().contains("\"model\":\"asr-model\""));
        assertTrue(asrBody.get().contains("data:audio/mpeg;base64,"));
        assertTrue(asrBody.get().contains("\"format\":\"mp3\""));
        assertTrue(asrBody.get().contains("\"sample_rate\":\"16000\""));
    }

    @Test
    @DisplayName("WAV 使用文件头采样率，兼容 H5 16k/48k 录音")
    void usesWavHeaderSampleRate() {
        byte[] wav = new byte[44];
        wav[0] = 'R'; wav[1] = 'I'; wav[2] = 'F'; wav[3] = 'F';
        wav[24] = (byte) 0x80; wav[25] = (byte) 0xBB; wav[26] = 0; wav[27] = 0; // 48000 little-endian
        client.transcribe(wav, "audio/wav");
        assertTrue(asrBody.get().contains("\"sample_rate\":\"48000\""));
        assertTrue(asrBody.get().contains("\"format\":\"wav\""));
    }

    @Test
    @DisplayName("TTS 读取 output.audio.data 并校验 MP3")
    void synthesizesFromBase64() {
        byte[] audio = client.synthesize("慢慢来");

        assertEquals(mp3.length, audio.length);
        assertTrue(ttsBody.get().contains("\"model\":\"tts-model\""));
        assertTrue(ttsBody.get().contains("\"voice\":\"test-voice\""));
        assertTrue(ttsBody.get().contains("\"format\":\"mp3\""));
    }

    @Test
    @DisplayName("只接受 WAV/MP3，避免把未知容器伪装成 ASR 输入")
    void rejectsUnsupportedAudioContainer() {
        BusinessException error = assertThrows(BusinessException.class,
                () -> client.transcribe(mp3, "audio/webm"));
        assertEquals(4001, error.getCode().intValue());
    }

    @Test
    @DisplayName("未启用或缺少 key 时 available=false")
    void unavailableWithoutConfiguration() {
        var missing = new QianwenSpeechClient(new ObjectMapper(), true, "", "https://example.com", "a", "t", "v", 16000);
        assertFalse(missing.available());
        assertThrows(BusinessException.class, () -> missing.synthesize("hello"));
    }

    private void respond(com.sun.net.httpserver.HttpExchange exchange, String json) throws java.io.IOException {
        byte[] bytes = json.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().add("Content-Type", "application/json");
        exchange.sendResponseHeaders(200, bytes.length);
        exchange.getResponseBody().write(bytes);
        exchange.close();
    }
}
