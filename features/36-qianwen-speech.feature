#language: zh-CN
功能: 千问AI平台语音适配
  作为学生
  我希望用语音说出此刻的感受并听到文字引导
  以便在不方便打字时也能使用陪伴

  场景: ASR 使用 Base64 Data URI
    当 学生上传不超过 5MB 的 WAV 或 MP3 录音
    那么 适配器调用千问 multimodal-generation
    并且 读取 output.text 作为识别文本
    并且 识别文本仍经过安全过滤与审计

  场景: TTS 返回音频
    当 学生请求合成一段文字
    那么 适配器请求 SpeechSynthesizer
    并且 读取 output.audio.data 或 output.audio.url
    并且 只接受合法 MP3，最大 5MB

  场景: TTS 的 OSS 地址可安全下载
    当 TTS 返回 HTTP 的受信任 OSS 音频地址
    那么 仅将 scheme 升级为 HTTPS
    并且 保留原始签名查询串
    并且 拒绝其他 HTTP、localhost 或私网地址

  场景: 不支持的容器被拒绝
    当 学生上传 WebM/MP4 等未支持格式
    那么 返回 code 4001 并提示使用 WAV/MP3
    并且 不把未知格式发送给厂商

  场景: 服务商切换
    当 服务端设置 SPEECH_PROVIDER=qianwen
    那么 注册千问适配器而不注册 JSON 网关
    当 设置 SPEECH_PROVIDER=gateway
    那么 注册 JSON 网关而不注册千问适配器

  场景: 凭据不下发
    当 管理端读取语音状态
    那么 只返回配置状态、变量名与协议
    并且 不返回 API Key、模型调用原文或音频
