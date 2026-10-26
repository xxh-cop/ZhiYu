# 语音网关接入

当前没有真实语音服务，默认关闭。不需要配置即可继续使用文字聊天、图文步骤和已上传的教学音频。

管理员在“语音服务”页面查看配置状态。服务端设置 `ZHIYU_SPEECH_ENABLED=true`、`ZHIYU_SPEECH_ENDPOINT`、`ZHIYU_SPEECH_API_KEY` 后重启。密钥仅使用环境变量，不进入前端或数据库；状态接口不返回配置值。端点只接受 HTTPS，或本机 localhost / 127.0.0.1 的 HTTP。

内置的是织愈自定义 JSON 网关协议，不是某个厂商的原生 API。后续选择服务商时，实现 `SpeechClient` 适配器，或由网关转换厂商接口；不要直接把厂商地址当作兼容网关。

网关通过 `Authorization: Bearer` 验证服务端凭据：

- `POST {endpoint}/asr` 接收 `audioBase64`、`mimeType`、`language=zh-CN`；返回 JSON 的 `text` 字段，最多 2000 字。
- `POST {endpoint}/tts` 接收 `text`、`language=zh-CN`、`format=mp3`；返回 JSON 的 `audioBase64`，内容必须为 MP3，最多 5 MB。

客户端录音最多 5 MB，不保存到上传目录。用户需同意隐私政策。识别文本和合成文本经过安全检查与加密审计；ASR、TTS 分别使用独立熔断资源。网关请求超时 25 秒，失败返回文字路径提示。网关自身的日志、供应商数据保留策略和授权需要在部署前确认。

“已配置”仅表示配置满足格式，不表示真实服务已通过联调。目前尚未接入真实厂商，不能宣称语音识别质量、可用率或小程序真机录音已经验收。
