# 语音服务接入

织愈支持两种语音接入方式，由服务端环境变量 `SPEECH_PROVIDER` 选择：

- `qianwen`：千问AI平台语音适配器（ASR + TTS）。
- `gateway`：自建织愈 JSON 网关协议（默认）。

语音凭据只放服务端环境变量，不进入前端、数据库或管理端配置。管理员在「语音服务」页只能看到配置状态与环境变量名。

## 一、千问AI平台适配器（推荐）

`.env`：

```env
SPEECH_PROVIDER=qianwen
QIANWEN_SPEECH_ENABLED=true
QIANWEN_API_KEY=你的千问平台密钥
QIANWEN_BASE_URL=https://maas.qianwenaiapi.com/api/v1
QIANWEN_ASR_MODEL=fun-asr-flash-2026-06-15
QIANWEN_TTS_MODEL=qwen-audio-3.1-tts-flash
QIANWEN_TTS_VOICE=longanhuan_v3.1
QIANWEN_ASR_SAMPLE_RATE=16000
```

实现说明：

- ASR：`POST {QIANWEN_BASE_URL}/services/aigc/multimodal-generation/generation`，发送 `input_audio.data` 的 Base64 Data URI，读取 `output.text`。当前适配器只接受 **WAV / MP3**，其他容器会返回 4001，避免把未知格式伪装成 ASR 输入。H5 录音在浏览器端编码为单声道 WAV，适配器会从 WAV 头读取实际采样率；小程序使用 MP3。
- TTS：`POST {QIANWEN_BASE_URL}/services/audio/tts/SpeechSynthesizer`，读取 `output.audio.data` 或 `output.audio.url`，服务端下载后校验 MP3（不超过 5 MB）。
- 录音最多 5 MB；识别/合成文本经过 `AiClient` 安全检查与加密审计；ASR、TTS 分别使用独立熔断资源。
- 模型名和音色可由环境变量覆盖；不同账号/模型的音色列表以千问平台控制台为准。
- `QIANWEN_API_KEY` 必须已开通对应 ASR/TTS 模型权限；**配置存在不等于额度或权限已开通**，需用短录音实际验证。
- TTS 返回的音频 URL 只允许公网地址；若厂商返回 HTTP 的 OSS 地址，仅对 `*.aliyuncs.com` / `*.qianwenai.com` / `*.qianwenaiapi.com` 自动升级为 HTTPS，并保留原始签名查询串；其他 HTTP、localhost 或私网地址会被拒绝。

验证：

1. 启动后端前加载 `.env`。
2. 管理端「语音服务」应显示千问适配器已配置。
3. 用一段短的 WAV/MP3 调 `POST /api/speech/transcribe`，确认返回文本。
4. 调 `POST /api/speech/synthesize`，确认返回 MP3 Base64。
5. 检查 `ai_audit_log` 只有加密文本审计，没有原始音频。

## 二、自建 JSON 网关

`.env`：

```env
SPEECH_PROVIDER=gateway
ZHIYU_SPEECH_ENABLED=true
ZHIYU_SPEECH_ENDPOINT=https://你的语音网关域名
ZHIYU_SPEECH_API_KEY=你的网关密钥
```

网关通过 `Authorization: Bearer` 验证服务端凭据，只接受 HTTPS，或本机 localhost / 127.0.0.1 的 HTTP。

- `POST {endpoint}/asr` 接收 `audioBase64`、`mimeType`、`language=zh-CN`；返回 JSON 的 `text` 字段，最多 2000 字。
- `POST {endpoint}/tts` 接收 `text`、`language=zh-CN`、`format=mp3`；返回 JSON 的 `audioBase64`，内容必须为 MP3，最多 5 MB。
- 网关请求超时 25 秒，失败返回文字路径提示；网关自身日志与供应商数据保留策略需在部署前确认。

## 三、当前边界

- 语音“已配置”只表示满足格式与开关，不表示真实服务已通过联调。
- 当前没有视觉能力；视觉凭据单独接入，不与语音共用。
- 本系统不能替代专业心理咨询。
