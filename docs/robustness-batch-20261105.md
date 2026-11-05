# 工程健壮性四修（2026-11-05 深夜）

> 软档判定与开关保持现状（默认 false，抽检/降权未实现），本批不碰判定逻辑。
> 全量 `mvn test`：**923 通过 / 0 失败 / 1 跳过**（跳过项为评测 IT 的无 key 门控）。
> 真实 LLM 调用：1 次（≤10）。未 commit、未 push。

## 1. moderation 走 JSON 结构化输出（修 JSON 转义类降级）

**改了什么**：[LlmClient](file:///d:/develop/Zhi_Yu/zhiyu-backend/src/main/java/com/zhiyu/ai/client/LlmClient.java) 新增 `chatWithUsageJson(prompt, model)` 默认方法（不支持的实现回退普通对话）；[SpringAiLlmClient](file:///d:/develop/Zhi_Yu/zhiyu-backend/src/main/java/com/zhiyu/ai/client/SpringAiLlmClient.java) 以 `OpenAiChatOptions.responseFormat = ResponseFormat(Type.JSON_OBJECT)` 实现（Spring AI 1.0.0 jar 内类已核实存在，非假设）；[AiClient.moderationSuggest](file:///d:/develop/Zhi_Yu/zhiyu-backend/src/main/java/com/zhiyu/ai/AiClient.java) strong 档与 standard 回落**都**走 JSON mode（只动 moderation，其他链路未碰）。解析失败语义不变：仍降级 UNCERTAIN/不可用，不静默归软档。

**证据**：[AiClientModerationTest](file:///d:/develop/Zhi_Yu/zhiyu-backend/src/test/java/com/zhiyu/ai/AiClientModerationTest.java) 4 条单测（含熔断隔离、回落同走 JSON mode）；真实冒烟（MySQL + 18083 实例）`POST /api/admin/works/{id}/ai-suggest` → 200，available=true，deepseek-reasoner 返回合法 JSON（ai_trace：input 646 / output 68 tokens，OK）——**DeepSeek reasoner 接受 response_format=json_object**，实测确认。

**未验项**：JSON mode 只做了 1 次真实调用；b08 类「内嵌未转义引号」故障在 JSON mode 下应由 provider 侧消除，但未做大规模复跑验证（上轮枚举批已无截断，1 例转义残留）。

## 2. usage 采集覆盖 recommend / message / summary

**改了什么**：LlmClient 新增 `recommendReasonWithUsage / workMessageWithUsage / reflectionSummaryWithUsage` 默认方法（包装 String 版，usage=null 不估算）；SpringAiLlmClient 覆盖实现，三个 String 方法改为委托。AiClient 四处调用点切换到带 usage 变体并把 tokens 透传进 ai_trace；`contextSummary` 由 `chat()` 切到既有 `chatWithUsage()`。MockLlmClient 零改动（继承默认，usage 保持 null）。既有桩测试同步更新（AiClientTest 6 处、AiClientMetricsTest 3 处）。

**前后可观测性差别（ai_trace 既有数据）**：修复前 llm-recommend 22 行、llm-message 2 行 tokens **全为 NULL**；修复后新调用将落真实 tokens（AiClientMetricsTest 断言 11/7 透传）。llm-strong（倾听小结）此前已有 usage。

**未验项 / 暂缓**：llm-chat（SSE 流式对话）未覆盖——DeepSeek 流式 usage 需 `stream_options.include_usage` 且在 SSE 末块聚合，改造涉及流式装配层，代价大，本批暂缓；listeningSummary 的 standard 回落路径仍无 usage（strong 主路径已有）。冒烟未对 recommend/message/summary 发真实调用（usage 落库由单测覆盖，真机验证留待下个窗口）。

## 3. 空 multipart 上传 → 400/1001

**改了什么**：[GlobalExceptionHandler](file:///d:/develop/Zhi_Yu/zhiyu-backend/src/main/java/com/zhiyu/common/exception/GlobalExceptionHandler.java) 新增 `MissingServletRequestPartException` 与 `MultipartException` 两个 handler（此前被兜底 Exception 吃掉成 500/5000），固定 400 + 1001，不回显内部细节。一处修复覆盖全部上传端点（image/video/audio/private-image——缺 file 字段的异常抛在进 Controller 之前）。

**证据**：[FileUploadControllerTest](file:///d:/develop/Zhi_Yu/zhiyu-backend/src/test/java/com/zhiyu/modules/file/controller/FileUploadControllerTest.java) 新增 1 条测试覆盖 audio/image/video 三端点缺 file → 400/1001；真实冒烟：`POST /api/files/upload/audio` 空表单 → `{"code":1001,"message":"请上传文件（缺少 file 字段）"}` ✓（修复前为 500/5000）。

**前端影响**：无破坏性——错误从 HTTP 500/5000 变为 HTTP 400/1001；学生端若对 500 有兜底提示会少触发，1001 文案可直接展示。前端无需跟改。

## 4. 本人删除自己上传媒体：DELETE /api/media/{id}

**改了什么**（先 openapi 后代码）：[openapi.yaml](file:///d:/develop/Zhi_Yu/openapi/openapi.yaml#L1433-L1444) 新增端点登记；[MediaService.deleteOwned](file:///d:/develop/Zhi_Yu/zhiyu-backend/src/main/java/com/zhiyu/modules/file/MediaService.java) 实现——未登录 1002 / 不存在 1004 / 非本人 1003 / **被引用（步骤音频、项目视频封面、作品视频封面，与 retireOrphanedMedia 同口径）3001 拒绝且不删**；删除语义复用退役机制：逻辑注销 + file_cleanup_job 登记，物理删除仍由 FileCleanupWorker 异步重试（冒烟中 M1/M2 物理文件即由该 worker 清掉，机制自证）。未实现批量删除。

**证据**：[MediaServiceTest](file:///d:/develop/Zhi_Yu/zhiyu-backend/src/test/java/com/zhiyu/modules/file/MediaServiceTest.java) +3 条（成功+幂等 / 他人+不存在+未登录 / 被引用拒绝与解除后可删）；真实冒烟：上传 → 本删 200 ✓；删他人 → HTTP 403 + 1003 ✓；作品引用后删除 → 200 + code 3001 ✓。

**前端跟改项**：学生端「我的媒体」（/api/media/mine 有列表）目前无删除入口——如需暴露，加删除按钮调 `DELETE /api/media/{id}` 并处理 3001（提示先解除引用）；管理端无影响。

## 冒烟台账与清理

- 冒烟实例：18083（自有，已停）；8080 未碰；用户的 18082 实例未动。
- 探测数据全部按明确 ID 清理并复核 0 残留：user ×3（2990000000000000101-103）、work ×1（2990000000000000201）、media_asset ×3（2118359809383317505 / 2118359883496669185 / 2118360045690404866）、file_cleanup_job 3 键、物理文件 3 个。
- 冒烟产生的 ai_audit_log / ai_trace 行（scene=moderation，ref work 2990000000000000201，操作者=冒烟管理员）按 append-only 纪律保留：ai_audit_log 约 3 行、ai_trace 1 行，id 落在 2118360120xxxxxxxxx–2118360127xxxxxxxxx 区间（MCP 输出对 BIGINT 末位有显示舍入，精确值以库内为准）。
- mvn 锁已释放。
