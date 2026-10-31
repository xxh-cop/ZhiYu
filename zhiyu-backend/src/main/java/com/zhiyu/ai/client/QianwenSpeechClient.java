package com.zhiyu.ai.client;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.zhiyu.common.exception.BusinessException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.time.Duration;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 千问AI平台语音适配器。
 *
 * <p>ASR：multimodal-generation 接口接收 Base64 Data URI，返回 output.text。
 * TTS：SpeechSynthesizer 返回 output.audio.url 或 output.audio.data，服务端下载并校验 MP3。
 * 仅当 {@code SPEECH_PROVIDER=qianwen} 时注册；密钥只来自服务端环境变量。
 */
@Component
@ConditionalOnProperty(name = "zhiyu.speech.provider", havingValue = "qianwen")
public class QianwenSpeechClient implements SpeechClient {

    private static final int MAX_AUDIO_BYTES = 5 * 1024 * 1024;
    private static final int MAX_JSON_BYTES = 1 * 1024 * 1024;
    private static final Set<String> LOCAL_HOSTS = Set.of("localhost", "127.0.0.1", "[::1]");

    private final ObjectMapper json;
    private final boolean enabled;
    private final String key;
    private final String baseUrl;
    private final String asrModel;
    private final String ttsModel;
    private final String ttsVoice;
    private final int sampleRate;
    private HttpClient client;

    public QianwenSpeechClient(ObjectMapper json,
                               @Value("${QIANWEN_SPEECH_ENABLED:false}") boolean enabled,
                               @Value("${QIANWEN_API_KEY:}") String key,
                               @Value("${QIANWEN_BASE_URL:https://maas.qianwenaiapi.com/api/v1}") String baseUrl,
                               @Value("${QIANWEN_ASR_MODEL:fun-asr-flash-2026-06-15}") String asrModel,
                               @Value("${QIANWEN_TTS_MODEL:qwen-audio-3.1-tts-flash}") String ttsModel,
                               @Value("${QIANWEN_TTS_VOICE:longanhuan_v3.1}") String ttsVoice,
                               @Value("${QIANWEN_ASR_SAMPLE_RATE:16000}") int sampleRate) {
        this.json = json;
        this.enabled = enabled;
        this.key = key == null ? "" : key.trim();
        this.baseUrl = stripSlash(baseUrl);
        this.asrModel = asrModel;
        this.ttsModel = ttsModel;
        this.ttsVoice = ttsVoice;
        this.sampleRate = sampleRate > 0 ? sampleRate : 16000;
    }

    @Override
    public boolean available() {
        if (!enabled || key.isBlank()) return false;
        try {
            URI uri = URI.create(baseUrl);
            String host = uri.getHost();
            if (host == null || uri.getUserInfo() != null || uri.getQuery() != null
                    || uri.getFragment() != null) return false;
            return "https".equalsIgnoreCase(uri.getScheme())
                    || ("http".equalsIgnoreCase(uri.getScheme()) && LOCAL_HOSTS.contains(host));
        } catch (Exception e) {
            return false;
        }
    }

    @Override
    public String transcribe(byte[] audio, String mimeType) {
        if (audio == null || audio.length == 0 || audio.length > MAX_AUDIO_BYTES) {
            throw new BusinessException(4001, "录音需小于 5MB");
        }
        String mime = mimeType == null ? "" : mimeType.split(";")[0];
        String format = audioFormat(mime);
        String dataUri = "data:" + mime + ";base64," + Base64.getEncoder().encodeToString(audio);

        Map<String, Object> inputAudio = Map.of("data", dataUri);
        Map<String, Object> content = Map.of("type", "input_audio", "input_audio", inputAudio);
        Map<String, Object> message = Map.of("role", "user", "content", List.of(content));
        Map<String, Object> input = Map.of("messages", List.of(message));
        Map<String, Object> parameters = new LinkedHashMap<>();
        parameters.put("format", format);
        parameters.put("sample_rate", String.valueOf(effectiveSampleRate(audio, mime)));
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("model", asrModel);
        body.put("input", input);
        body.put("parameters", parameters);

        JsonNode root = call("/services/aigc/multimodal-generation/generation", body, true);
        String text = text(root);
        if (text.isBlank()) throw new BusinessException(4001, "未识别到有效内容，请缩短录音后重试");
        if (text.length() > 2000) throw new BusinessException(4001, "识别文本过长，请缩短录音后重试");
        return text;
    }

    @Override
    public byte[] synthesize(String text) {
        if (text == null || text.isBlank()) throw new BusinessException(4001, "待合成文本不能为空");
        Map<String, Object> input = new LinkedHashMap<>();
        input.put("text", text);
        input.put("voice", ttsVoice);
        input.put("format", "mp3");
        input.put("sample_rate", 24000);
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("model", ttsModel);
        body.put("input", input);

        JsonNode root = call("/services/audio/tts/SpeechSynthesizer", body, false);
        JsonNode audio = root.path("output").path("audio");
        String base64 = audio.path("data").asText("");
        if (!base64.isBlank()) return decodeAudio(base64);
        if (audio.isTextual()) return decodeAudio(audio.asText());
        String url = audio.path("url").asText("");
        if (url.isBlank()) throw new BusinessException(4001, "语音返回格式无效，请使用文字引导");
        return download(url);
    }

    private JsonNode call(String path, Map<String, Object> body, boolean asr) {
        if (!available()) throw new BusinessException(4001, "语音服务尚未配置，请使用文字输入或文字引导");
        try {
            HttpRequest.Builder builder = HttpRequest.newBuilder(URI.create(baseUrl + path))
                    .timeout(Duration.ofSeconds(25))
                    .header("Authorization", "Bearer " + key)
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(json.writeValueAsString(body)));
            if (asr) builder.header("X-DashScope-SSE", "disable");
            HttpResponse<java.io.InputStream> response = client().send(
                    builder.build(), HttpResponse.BodyHandlers.ofInputStream());
            try (var stream = response.body()) {
                if (response.statusCode() != 200) {
                    throw new IllegalStateException("qianwen speech rejected");
                }
                byte[] bytes = stream.readNBytes(MAX_JSON_BYTES + 1);
                if (bytes.length > MAX_JSON_BYTES) throw new IllegalStateException("qianwen speech response too large");
                return json.readTree(bytes);
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new BusinessException(4001, "语音请求已取消");
        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            throw new BusinessException(4001, "语音服务暂不可用，请使用文字");
        }
    }

    private byte[] download(String url) {
        try {
            URI uri = URI.create(url);
            String host = uri.getHost();
            if (host == null || LOCAL_HOSTS.contains(host) || privateHost(host)) {
                throw new BusinessException(4001, "语音返回地址不安全");
            }
            if ("http".equalsIgnoreCase(uri.getScheme()) && trustedCdn(host)) {
                // 仅替换 scheme，保留 OSS 签名查询串的原始编码，避免重编码破坏签名。
                uri = URI.create(url.replaceFirst("(?i)^http://", "https://"));
            }
            if (!"https".equalsIgnoreCase(uri.getScheme())) {
                throw new BusinessException(4001, "语音返回地址不安全");
            }
            HttpRequest request = HttpRequest.newBuilder(uri)
                    .timeout(Duration.ofSeconds(25))
                    .GET().build();
            HttpResponse<java.io.InputStream> response = client().send(
                    request, HttpResponse.BodyHandlers.ofInputStream());
            try (var stream = response.body()) {
                if (response.statusCode() != 200) throw new IllegalStateException("audio download rejected");
                byte[] bytes = stream.readNBytes(MAX_AUDIO_BYTES + 1);
                if (bytes.length > MAX_AUDIO_BYTES) throw new IllegalStateException("audio too large");
                validateMp3(bytes);
                return bytes;
            }
        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            throw new BusinessException(4001, "语音返回格式无效，请使用文字引导");
        }
    }

    private byte[] decodeAudio(String base64) {
        try {
            byte[] bytes = Base64.getDecoder().decode(base64);
            validateMp3(bytes);
            return bytes;
        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            throw new BusinessException(4001, "语音返回格式无效，请使用文字引导");
        }
    }

    private void validateMp3(byte[] bytes) {
        if (bytes == null || bytes.length < 3 || bytes.length > MAX_AUDIO_BYTES
                || !((bytes[0] == 'I' && bytes[1] == 'D' && bytes[2] == '3')
                || ((bytes[0] & 255) == 255 && (bytes[1] & 224) == 224))) {
            throw new BusinessException(4001, "语音返回格式无效，请使用文字引导");
        }
    }

    private String text(JsonNode root) {
        String text = root.path("output").path("text").asText("");
        if (text.isBlank()) text = root.path("output").path("output").path("text").asText("");
        if (text.isBlank()) text = root.path("output").path("sentence").path("text").asText("");
        return text.trim();
    }

    private int effectiveSampleRate(byte[] audio, String mime) {
        if (("audio/wav".equals(mime) || "audio/x-wav".equals(mime))
                && audio.length >= 28
                && audio[0] == 'R' && audio[1] == 'I' && audio[2] == 'F' && audio[3] == 'F') {
            int rate = ByteBuffer.wrap(audio, 24, 4).order(ByteOrder.LITTLE_ENDIAN).getInt();
            if (rate >= 8000 && rate <= 48000) return rate;
        }
        return sampleRate;
    }

    private String audioFormat(String mime) {
        return switch (mime) {
            case "audio/wav", "audio/x-wav" -> "wav";
            case "audio/mpeg", "audio/mp3" -> "mp3";
            default -> throw new BusinessException(4001, "当前语音服务支持 WAV/MP3 录音");
        };
    }

    private boolean privateHost(String host) {
        return host.startsWith("10.") || host.startsWith("127.")
                || host.startsWith("192.168.") || host.startsWith("169.254.")
                || host.matches("172\\.(1[6-9]|2\\d|3[0-1])\\..*");
    }

    private boolean trustedCdn(String host) {
        return host.endsWith(".aliyuncs.com") || host.endsWith(".qianwenaiapi.com")
                || host.endsWith(".qianwenai.com");
    }

    private HttpClient client() {
        if (client == null) {
            client = HttpClient.newBuilder()
                    .connectTimeout(Duration.ofSeconds(5))
                    .followRedirects(HttpClient.Redirect.NEVER)
                    .build();
        }
        return client;
    }

    private String stripSlash(String value) {
        if (value == null) return "";
        String text = value.trim();
        while (text.endsWith("/")) text = text.substring(0, text.length() - 1);
        return text;
    }
}
