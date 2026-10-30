# 倾听小结与 AI 治理实施计划

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 让结束后的倾听会话可以生成、编辑、保存、回看和删除独立小结；同时给管理员一个只展示脱敏审计聚合与能力配置状态的 AI 治理页。

**Architecture:** 倾听小结复用 `wellbeing_record`（新 kind `LISTENING_SUMMARY`）保存加密 JSON，生成与保存分离；AI 只读取本人已结束会话中的用户发言，输入/输出走 `AiClient` 的安全过滤、审计与 `llm-message` 熔断。AI 治理复用 `ai_audit_log` 做时间窗聚合，只返回场景/动作/原因分类计数和配置状态，不返回用户 ID、原文或自由文本原因。

**Tech Stack:** Java 17、Spring Boot 3.4、Spring AI 1.0、MyBatis-Plus/JdbcTemplate、JUnit 5、Mockito、Vue 3.4、UniApp、Vben Admin + Element Plus + ECharts 6。

**Spec:** `docs/superpowers/specs/2026-10-29-heritage-student-experience-design.md` 第 8 节

## Global Constraints

- 倾听小结只总结本人已结束会话中实际出现的用户发言；不读取其他会话，不把 AI 回复当作事实。
- 高危会话不生成、不保存 AI 小结；保留既有支持页与危机流程，不覆盖安全话术。
- AI 调用必须走 `AiClient`；输入/输出安全过滤与审计保留；复用 `llm-message` 熔断 key，不新建全局计数器。
- 生成与保存分离：生成接口不写库；只有用户确认保存后才写 `wellbeing_record`，payload 应用层加密。
- `wellbeing_record` 已随 `journey`、个人数据导出、`GrowthService.erase`、`AccountLifecycle` 清理；不新增表/迁移。
- 保存的 kind 固定 `LISTENING_SUMMARY`，record_key 为字符串 sessionId；同一会话只有一条小结。
- AI 治理只允许管理员读取；不返回 user_id、raw_input、ai_output、自由文本 reason 或匹配关键词。
- 不把“配置存在”写成“服务连通”；不推算调用量、成功率、延迟或疗效。
- 前端复用 `growth.css`、`AccessibleButton`/现有按钮语义与令牌；手机无横向溢出；页面有免责声明。
- 新接口同步 `openapi/openapi.yaml` 与 Gherkin；后端定向测试，学生端 H5/mp-weixin 构建，管理端 typecheck/build。
- Git 提交前必须展示 `git status --short` 与 `git diff --stat` 并得到用户确认；禁止 push/merge/rebase/reset/checkout；不触碰用户 docx。

---

### Task 1: 倾听小结后端

**Files:**
- Create: `zhiyu-backend/src/main/java/com/zhiyu/modules/emotion/service/ListeningSummaryService.java`
- Create: `zhiyu-backend/src/main/java/com/zhiyu/modules/emotion/controller/ListeningSummaryController.java`
- Create: `zhiyu-backend/src/main/resources/prompts/listening-summary.st`
- Modify: `zhiyu-backend/src/main/java/com/zhiyu/ai/client/LlmClient.java`
- Modify: `zhiyu-backend/src/main/java/com/zhiyu/ai/client/SpringAiLlmClient.java`
- Modify: `zhiyu-backend/src/main/java/com/zhiyu/ai/client/MockLlmClient.java`
- Modify: `zhiyu-backend/src/main/java/com/zhiyu/ai/AiClient.java`
- Modify: `zhiyu-backend/src/main/java/com/zhiyu/modules/emotion/controller/EmotionHistoryController.java`
- Test: `zhiyu-backend/src/test/java/com/zhiyu/modules/emotion/ListeningSummaryServiceTest.java`
- Test: `zhiyu-backend/src/test/java/com/zhiyu/ai/AiClientTest.java`（追加）
- OpenAPI: `openapi/openapi.yaml`
- Gherkin: `features/25-listening-summary.feature`

**Interfaces:**
- `ListeningSummaryService.generate(long sessionId, boolean consent)` → `Map{summary, generated, safetyHold}`；不写库。
- `save(long sessionId, String summary, boolean generated, boolean consent)` → 保存并返回同上结构。
- `get(long sessionId)` → `{saved, summary, generated, savedAt, safetyHold}`。
- `delete(long sessionId)`。
- `AiClient.ListeningResult(String text, boolean generated, boolean blocked)`。

- [ ] **Step 1: 写失败测试**

```java
package com.zhiyu.modules.emotion;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.zhiyu.ai.AiClient;
import com.zhiyu.ai.client.ChatMessage;
import com.zhiyu.common.crypto.AesEncryptor;
import com.zhiyu.common.exception.BusinessException;
import com.zhiyu.modules.emotion.service.EmotionService;
import com.zhiyu.modules.emotion.service.ListeningSummaryService;
import com.zhiyu.security.BusinessAccess;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.util.ReflectionTestUtils;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ListeningSummaryServiceTest {

    private JdbcTemplate db;
    private AesEncryptor crypto;
    private AiClient ai;
    private EmotionService emotionService;
    private BusinessAccess access;
    private ListeningSummaryService service;

    @BeforeEach
    void setUp() {
        db = mock(JdbcTemplate.class);
        crypto = mock(AesEncryptor.class);
        ai = mock(AiClient.class);
        emotionService = mock(EmotionService.class);
        access = mock(BusinessAccess.class);
        var prompt = new ByteArrayResource("{{turns}}|{{transcript}}".getBytes(StandardCharsets.UTF_8));
        service = new ListeningSummaryService(db, new ObjectMapper(), crypto, ai, emotionService, access, prompt);
        when(access.consentedUser()).thenReturn(7L);
        when(access.user()).thenReturn(7L);
        when(access.emotionSession(any(), any())).thenReturn(null);
        when(db.queryForList(anyString(), any(Object[].class))).thenReturn(List.of(Map.of("status", 1)));
    }

    @Test
    void shortConversationReturnsGuidanceWithoutModelCall() {
        when(emotionService.loadRecentHistory(11L, 40)).thenReturn(List.of(
                new ChatMessage("user", "今天有点累")));
        var result = service.generate(11L, true);
        assertFalse((Boolean) result.get("generated"));
        assertTrue(result.get("summary").toString().contains("还比较短"));
        verify(ai, never()).listeningSummary(any(), anyString(), anyString());
    }

    @Test
    void careReminderSessionNeverGeneratesAiSummary() {
        when(db.queryForList(anyString(), any(Object[].class)))
                .thenReturn(List.of(Map.of("status", 1)))
                .thenReturn(List.of(Map.of("count", 1)));
        var result = service.generate(11L, true);
        assertTrue((Boolean) result.get("safetyHold"));
        verify(ai, never()).listeningSummary(any(), anyString(), anyString());
    }

    @Test
    void generatedSummaryIsNotSavedUntilSaveIsCalled() {
        when(db.queryForList(anyString(), any(Object[].class)))
                .thenReturn(List.of(Map.of("status", 1)))
                .thenReturn(List.of(Map.of("count", 0)));
        when(emotionService.loadRecentHistory(11L, 40)).thenReturn(List.of(
                new ChatMessage("user", "最近作业很多，我有点喘不过气。"),
                new ChatMessage("user", "说出来以后轻一点了。")));
        when(ai.listeningSummary(any(), anyString(), anyString()))
                .thenReturn(new AiClient.ListeningResult("你提到作业压力，也发现自己说出来后轻了一点。", true, false));

        var generated = service.generate(11L, true);
        assertTrue((Boolean) generated.get("generated"));
        assertEquals("你提到作业压力，也发现自己说出来后轻了一点。", generated.get("summary"));
        verify(db, never()).update(anyString(), any(Object[].class));
    }

    @Test
    void saveRequiresConsentAndEndedSession() {
        when(db.queryForList(anyString(), any(Object[].class)))
                .thenReturn(List.of(Map.of("status", 0)));
        assertThrows(BusinessException.class,
                () -> service.save(11L, "小结", false, true));
    }
}
```

- [ ] **Step 2: 运行测试确认失败**

Run: `cd zhiyu-backend && mvn -q -Dtest=ListeningSummaryServiceTest test "-Dmaven.repo.local=../.ai-tmp/m2"`
Expected: 编译失败，服务与 `ListeningResult` 不存在。

- [ ] **Step 3: 扩展 LLM 接口与 AiClient**

`LlmClient` 增加：

```java
String listeningSummary(String prompt);
```

`SpringAiLlmClient` 与 `reflectionSummary` 同实现：

```java
@Override
public String listeningSummary(String prompt) {
    return chatClient.prompt().options(options()).system(safetyPrompt).user(prompt).call().content();
}
```

`MockLlmClient`：

```java
@Override
public String listeningSummary(String prompt) {
    return "这段对话已经结束。愿意停下来听听自己，本身就是一种照顾。";
}
```

`AiClient` 增加：

```java
public record ListeningResult(String text, boolean generated, boolean blocked) {
}

public ListeningResult listeningSummary(Long sessionId, String prompt, String userText) {
    SafetyResult input = contentSafetyFilter.checkInput(
            UserContext.getUserId(), sessionId, "listening", userText);
    if (input.getAction() == SafetyAction.BLOCK) {
        return new ListeningResult(null, false, true);
    }
    if (!breaker.allowRequest(KEY_MESSAGE)) {
        return new ListeningResult(null, false, false);
    }
    try {
        String result = llmClient.listeningSummary(prompt);
        if (result == null || result.isBlank()) throw new IllegalStateException("empty listening summary");
        breaker.recordSuccess(KEY_MESSAGE);
        SafetyResult output = contentSafetyFilter.checkOutput(
                UserContext.getUserId(), "listening", result);
        if (output.getAction() == SafetyAction.BLOCK) {
            return new ListeningResult(null, false, false);
        }
        String safe = output.getAction() == SafetyAction.REWRITE
                && output.getRewrittenText() != null
                && !output.getRewrittenText().isBlank()
                ? output.getRewrittenText() : result;
        return new ListeningResult(safe.trim(), true, false);
    } catch (Exception e) {
        breaker.recordFailure(KEY_MESSAGE);
        log.warn("倾听小结生成失败，由业务层使用记录引导");
        return new ListeningResult(null, false, false);
    }
}
```

- [ ] **Step 4: 写外置提示词**

`prompts/listening-summary.st`：

```text
你是「织愈」的倾听记录助手。只根据以下用户发言写一段很短的小结。
不要诊断、不要下结论、不要给治疗建议，不要编造用户没有说过的事实。
如果内容不足以形成小结，只写一句温和的记录引导。
不超过 220 个中文字符，不使用“你患有”“抑郁症”“焦虑症”等诊断措辞。
本系统不能替代专业心理咨询。

用户发言（共 {{turns}} 段）：
{{transcript}}
```

- [ ] **Step 5: 实现 ListeningSummaryService**

```java
package com.zhiyu.modules.emotion.service;

import com.baomidou.mybatisplus.core.toolkit.IdWorker;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.zhiyu.ai.AiClient;
import com.zhiyu.ai.client.ChatMessage;
import com.zhiyu.common.crypto.AesEncryptor;
import com.zhiyu.common.exception.BusinessException;
import com.zhiyu.modules.emotion.service.EmotionService;
import com.zhiyu.security.BusinessAccess;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** 独立倾听小结：只读本人已结束会话中的用户发言，生成与保存分离。 */
@Service
public class ListeningSummaryService {

    public static final String KIND = "LISTENING_SUMMARY";
    private static final ZoneId ZONE = ZoneId.of("Asia/Shanghai");
    private static final String SAFETY_HOLD =
            "这次对话里出现了需要优先照顾自己的信号。AI 小结不会覆盖这段经历；如果你愿意，可以在支持页找到学校心理中心与全国热线。本系统不能替代专业心理咨询。";
    private static final String FALLBACK =
            "这次倾听已经结束。AI 小结暂时没有生成，你仍可以在下方写下自己此刻想留下的一句话。";
    private static final String TOO_SHORT =
            "这段对话还比较短，可以只留下一句此刻的感受。";

    private final JdbcTemplate db;
    private final ObjectMapper json;
    private final AesEncryptor crypto;
    private final AiClient aiClient;
    private final EmotionService emotionService;
    private final BusinessAccess access;
    private final Resource promptResource;

    public ListeningSummaryService(JdbcTemplate db, ObjectMapper json, AesEncryptor crypto,
                                   AiClient aiClient, EmotionService emotionService,
                                   BusinessAccess access,
                                   @Value("classpath:prompts/listening-summary.st") Resource promptResource) {
        this.db = db;
        this.json = json;
        this.crypto = crypto;
        this.aiClient = aiClient;
        this.emotionService = emotionService;
        this.access = access;
        this.promptResource = promptResource;
    }

    public Map<String, Object> generate(long sessionId, boolean consent) {
        long uid = access.consentedUser();
        if (!consent) throw new BusinessException(1001, "请确认 AI 生成授权");
        requireEnded(uid, sessionId);
        if (hasCareReminder(uid, sessionId)) return result(SAFETY_HOLD, false, true);

        List<String> userTexts = new ArrayList<>();
        for (ChatMessage message : emotionService.loadRecentHistory(sessionId, 40)) {
            if (!"user".equals(message.role())) continue;
            String text = message.content() == null ? "" : message.content().trim();
            if (!text.isBlank()) userTexts.add(text.length() > 300 ? text.substring(0, 300) : text);
            if (userTexts.size() >= 12) break;
        }
        String transcript = userTexts.stream().map(text -> "用户：" + text).reduce((a, b) -> a + "\n" + b).orElse("");
        if (userTexts.size() < 2 || transcript.length() < 20) return result(TOO_SHORT, false, false);
        if (transcript.length() > 3000) transcript = transcript.substring(0, 3000);

        try {
            String prompt = promptResource.getContentAsString(StandardCharsets.UTF_8)
                    .replace("{{turns}}", String.valueOf(userTexts.size()))
                    .replace("{{transcript}}", transcript);
            AiClient.ListeningResult generated = aiClient.listeningSummary(sessionId, prompt, transcript);
            if (generated.blocked()) return result(SAFETY_HOLD, false, true);
            if (!generated.generated() || generated.text() == null || generated.text().isBlank()) {
                return result(FALLBACK, false, false);
            }
            String summary = generated.text().trim();
            return result(summary.length() > 300 ? summary.substring(0, 300) : summary, true, false);
        } catch (Exception e) {
            return result(FALLBACK, false, false);
        }
    }

    @Transactional
    public Map<String, Object> save(long sessionId, String summary, boolean generated, boolean consent) {
        long uid = access.consentedUser();
        if (!consent) throw new BusinessException(1001, "请确认保存授权");
        requireEnded(uid, sessionId);
        if (hasCareReminder(uid, sessionId)) throw new BusinessException(3001, "这段对话暂不生成或保存小结");
        String text = summary == null ? "" : summary.trim();
        if (text.isBlank()) throw new BusinessException(1001, "小结不能为空");
        if (text.length() > 300) throw new BusinessException(1001, "小结不得超过 300 字");

        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("sessionId", String.valueOf(sessionId));
        payload.put("summary", text);
        payload.put("generated", generated);
        payload.put("savedAt", LocalDateTime.now(ZONE).toString());
        String cipher = encode(payload);
        LocalDateTime now = LocalDateTime.now(ZONE);
        db.queryForObject("SELECT id FROM user WHERE id=? FOR UPDATE", Long.class, uid);
        int updated = db.update("""
                UPDATE wellbeing_record SET payload=?,deleted=0,update_time=?
                WHERE user_id=? AND kind=? AND record_key=?
                """, cipher, now, uid, KIND, String.valueOf(sessionId));
        if (updated == 0) {
            db.update("""
                    INSERT INTO wellbeing_record(id,user_id,kind,record_key,payload,create_time,update_time,deleted)
                    VALUES(?,?,?,?,?,?,?,0)
                    """, IdWorker.getId(), uid, KIND, String.valueOf(sessionId), cipher, now, now);
        }
        return get(sessionId);
    }

    public Map<String, Object> get(long sessionId) {
        long uid = access.user();
        List<Map<String, Object>> rows = db.queryForList("""
                SELECT payload,update_time FROM wellbeing_record
                WHERE user_id=? AND kind=? AND record_key=? AND deleted=0
                """, uid, KIND, String.valueOf(sessionId));
        if (rows.isEmpty()) return Map.of("saved", false);
        Map<String, Object> payload = decode(rows.get(0).get("payload").toString());
        payload.put("saved", true);
        payload.put("savedAt", rows.get(0).get("update_time").toString());
        return payload;
    }

    public void delete(long sessionId) {
        long uid = access.user();
        db.update("""
                UPDATE wellbeing_record SET deleted=1,update_time=?
                WHERE user_id=? AND kind=? AND record_key=? AND deleted=0
                """, LocalDateTime.now(ZONE), uid, KIND, String.valueOf(sessionId));
    }

    private void requireEnded(long uid, long sessionId) {
        access.emotionSession(sessionId, uid);
        List<Map<String, Object>> rows = db.queryForList(
                "SELECT status FROM emotion_session WHERE id=? AND user_id=? AND deleted=0", sessionId, uid);
        if (rows.isEmpty() || number(rows.get(0).get("status")) != 1) {
            throw new BusinessException(3001, "请先结束这次倾听");
        }
    }

    private boolean hasCareReminder(long uid, long sessionId) {
        List<Map<String, Object>> rows = db.queryForList(
                "SELECT COUNT(*) AS count FROM care_reminder WHERE user_id=? AND session_id=? AND deleted=0",
                uid, sessionId);
        return !rows.isEmpty() && number(rows.get(0).get("count")) > 0;
    }

    private Map<String, Object> result(String summary, boolean generated, boolean safetyHold) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("summary", summary);
        result.put("generated", generated);
        result.put("safetyHold", safetyHold);
        return result;
    }

    private String encode(Map<String, Object> payload) {
        try { return crypto.encrypt(json.writeValueAsString(payload)); }
        catch (Exception e) { throw new IllegalStateException("小结加密失败", e); }
    }

    private Map<String, Object> decode(String cipher) {
        try { return json.readValue(crypto.decrypt(cipher), new TypeReference<>() {}); }
        catch (Exception e) { throw new BusinessException(5000, "小结暂时无法读取"); }
    }

    private long number(Object value) { return ((Number) value).longValue(); }
}
```

- [ ] **Step 6: 增加 Controller**

```java
package com.zhiyu.modules.emotion.controller;

import com.zhiyu.common.result.Result;
import com.zhiyu.modules.emotion.service.ListeningSummaryService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/emotion/sessions/{id}/summary")
public class ListeningSummaryController {

    public record Generate(boolean consent) {
    }

    public record Save(@NotBlank @Size(max = 300) String summary,
                       boolean generated,
                       @AssertTrue(message = "请确认保存授权") boolean consent) {
    }

    private final ListeningSummaryService service;

    @PostMapping("/generate")
    public Result<?> generate(@PathVariable Long id, @Valid @RequestBody Generate input) {
        return Result.success(service.generate(id, input.consent()));
    }

    @GetMapping
    public Result<?> get(@PathVariable Long id) {
        return Result.success(service.get(id));
    }

    @PutMapping
    public Result<?> save(@PathVariable Long id, @Valid @RequestBody Save input) {
        return Result.success(service.save(id, input.summary(), input.generated(), input.consent()));
    }

    @DeleteMapping
    public Result<?> delete(@PathVariable Long id) {
        service.delete(id);
        return Result.success();
    }
}
```

- [ ] **Step 7: 会话删除时同步删除小结**

在 `EmotionHistoryController.delete` 中，删除会话前追加：

```java
jdbc.update("""
        UPDATE wellbeing_record SET deleted=1,update_time=CURRENT_TIMESTAMP
        WHERE user_id=? AND kind='LISTENING_SUMMARY' AND record_key=? AND deleted=0
        """, uid, String.valueOf(id));
```

- [ ] **Step 8: 补 OpenAPI 与 Gherkin**

OpenAPI 增加 `/api/emotion/sessions/{id}/summary` GET/PUT/DELETE 与 `/generate` POST；响应字段 `saved`、`summary`、`generated`、`safetyHold`、`savedAt`。Gherkin 至少覆盖：未结束拒绝、无授权拒绝、短对话固定引导、AI 失败固定引导、高危会话不生成、保存加密记录、删除小结、删除会话同步清理、导出包含记录。

Run: `cd zhiyu-backend && mvn -q -Dtest=ListeningSummaryServiceTest,AiClientTest test "-Dmaven.repo.local=../.ai-tmp/m2"`
Expected: 测试通过。

- [ ] **Step 9: 提交检查点**

Run: `git status --short && git diff --stat`
Expected: 等待用户确认后再 commit。

---

### Task 2: 学生端倾听小结

**Files:**
- Create: `zhiyu-app/src/components/ListeningSummaryCard.vue`
- Modify: `zhiyu-app/src/pages/emotion/history.vue`
- Modify: `zhiyu-app/src/pages/journey/index.vue`
- Test: `zhiyu-app/tests/listening-summary.test.mjs`

**Interfaces:**
- Consumes: `GET/POST/PUT/DELETE /api/emotion/sessions/{id}/summary`。
- Produces: `ListeningSummaryCard` props `sessionId: string|number`、`sessionStatus: number`。

- [ ] **Step 1: 写纯数据测试**

`tests/listening-summary.test.mjs` 测试小结记录的显示文案与截断，不依赖 DOM：

```js
import test from 'node:test'
import assert from 'node:assert/strict'
import { summaryDisplay, summaryKindLabel } from '../src/utils/listening-summary.js'

test('labels saved summaries and falls back for generated guidance', () => {
  assert.equal(summaryKindLabel('LISTENING_SUMMARY'), '倾听小结')
  assert.equal(summaryDisplay({ summary: '  ', generated: false }), '这段倾听还没有留下文字。')
  assert.equal(summaryDisplay({ summary: '  一句小结  ', generated: false }), '一句小结')
  assert.equal(summaryDisplay({ summary: 'a'.repeat(320) }).length, 300)
})
```

- [ ] **Step 2: 创建纯函数**

`zhiyu-app/src/utils/listening-summary.js`：

```js
export function summaryKindLabel(kind) {
  return kind === 'LISTENING_SUMMARY' ? '倾听小结' : ''
}

export function summaryDisplay(record) {
  const text = String((record && record.summary) || '').trim()
  if (!text) return '这段倾听还没有留下文字。'
  return text.length > 300 ? text.slice(0, 300) : text
}
```

- [ ] **Step 3: 实现 ListeningSummaryCard**

组件模板包含：标题、已保存/未保存标签、隐私说明、结束状态提示、授权勾选、生成按钮、textarea、保存/删除按钮、错误 role=alert、`safetyHold` 支持页按钮、免责声明。脚本：

```js
import { computed, onMounted, ref, watch } from 'vue'
import { request } from '../utils/request'

const props = defineProps({
  sessionId: { type: [String, Number], required: true },
  sessionStatus: { type: Number, default: 0 },
})
const loading = ref(false)
const busy = ref(false)
const error = ref('')
const saved = ref(false)
const summary = ref('')
const generated = ref(false)
const safetyHold = ref(false)
const consent = ref(false)
const ended = computed(() => props.sessionStatus === 1)
const canGenerate = computed(() => ended.value && consent.value && !busy.value)
const canSave = computed(() => !safetyHold.value && summary.value.trim().length > 0 && !busy.value)

async function load() {
  loading.value = true
  error.value = ''
  try {
    const data = await request({ url: `/api/emotion/sessions/${props.sessionId}/summary` })
    saved.value = data.saved === true
    summary.value = data.summary || ''
    generated.value = data.generated === true
    safetyHold.value = data.safetyHold === true
  } catch (e) {
    error.value = (e && e.message) || '小结暂时无法读取'
  } finally {
    loading.value = false
  }
}

async function generate() {
  if (!canGenerate.value) return
  busy.value = true
  error.value = ''
  try {
    const data = await request({
      url: `/api/emotion/sessions/${props.sessionId}/summary/generate`,
      method: 'POST',
      data: { consent: true },
    })
    summary.value = data.summary || ''
    generated.value = data.generated === true
    safetyHold.value = data.safetyHold === true
  } catch (e) {
    error.value = (e && e.message) || '小结暂时没有生成，请重试。'
  } finally {
    busy.value = false
  }
}

async function save() {
  if (!canSave.value) return
  busy.value = true
  error.value = ''
  try {
    const data = await request({
      url: `/api/emotion/sessions/${props.sessionId}/summary`,
      method: 'PUT',
      data: { summary: summary.value.trim(), generated: generated.value, consent: true },
    })
    saved.value = true
    summary.value = data.summary || summary.value.trim()
    uni.showToast({ title: '小结已保存', icon: 'success' })
  } catch (e) {
    error.value = (e && e.message) || '保存失败，请重试。'
  } finally {
    busy.value = false
  }
}

async function remove() {
  const result = await new Promise(resolve => uni.showModal({
    title: '删除倾听小结',
    content: '只删除这条小结，不删除倾听对话。是否继续？',
    success: resolve,
  }))
  if (!result.confirm) return
  busy.value = true
  try {
    await request({ url: `/api/emotion/sessions/${props.sessionId}/summary`, method: 'DELETE' })
    saved.value = false
    summary.value = ''
    generated.value = false
  } catch (e) {
    error.value = (e && e.message) || '删除失败，请重试。'
  } finally {
    busy.value = false
  }
}

function support() { uni.navigateTo({ url: '/pages/support/index' }) }

onMounted(load)
watch(() => props.sessionId, load)
```

- [ ] **Step 4: 接入倾听记录页**

在 `history.vue` 的详情卡片中、消息列表后加入：

```vue
<ListeningSummaryCard
  :session-id="detail.session.sessionId"
  :session-status="detail.session.sessionStatus || detail.session.status"
/>
```

并导入组件。`SessionVO` 字段为 `status`；按实际字段使用 `detail.session.status`。

- [ ] **Step 5: 接入成长旅程**

在 `journey/index.vue`：

- `history` 过滤增加 `'LISTENING_SUMMARY'`。
- 记录标题：`r.kind === 'LISTENING_SUMMARY' ? '倾听小结' : ...`。
- 显示 `r.summary` 或 `summaryDisplay(r)`。
- `LISTENING_SUMMARY` 记录显示“删除这条小结”按钮，调用 `DELETE /api/emotion/sessions/{r.sessionId}/summary`，确认后 `load()`。
- 记录列表底部说明新增：小结只属于本人，可导出、删除；不进入关怀视图。

- [ ] **Step 6: 运行测试与构建**

```bash
cd zhiyu-app
node --test tests/listening-summary.test.mjs
npm run build:h5
npm run build:mp-weixin
```

- [ ] **Step 7: 浏览器验收**

结束一次测试会话（仅用合成数据），在 375/768/1280 检查：
- 未结束会话不显示生成按钮；
- 生成后出现可编辑 textarea；
- 保存后显示已保存，刷新后仍可读取；
- 删除小结后不再显示，会话消息仍在；
- 成长旅程出现“倾听小结”记录，可删除；
- 无横向溢出，无诊断性措辞，说明“本系统不能替代专业心理咨询”。

- [ ] **Step 8: 提交检查点**

Run: `git status --short && git diff --stat`；等待确认后再 commit。

---

### Task 3: AI 治理聚合与管理员页面

**Files:**
- Create: `zhiyu-backend/src/main/java/com/zhiyu/modules/admin/controller/AdminAiGovernanceController.java`
- Create: `zhiyu-backend/src/main/java/com/zhiyu/modules/admin/service/AdminAiGovernanceService.java`
- Test: `zhiyu-backend/src/test/java/com/zhiyu/modules/admin/AdminAiGovernanceServiceTest.java`
- Test: `zhiyu-backend/src/test/java/com/zhiyu/modules/admin/AdminAiGovernanceControllerTest.java`
- Create: `zhiyu-admin-vben/apps/web-ele/src/api/zhiyu/admin-ai-governance.ts`
- Create: `zhiyu-admin-vben/apps/web-ele/src/views/zhiyu/ai-governance.vue`
- Modify: `zhiyu-admin-vben/apps/web-ele/src/router/routes/modules/zhiyu.ts`
- Modify: `zhiyu-admin-vben/apps/web-ele/src/views/zhiyu/audit-logs.vue`
- OpenAPI/Gherkin: `openapi/openapi.yaml`、`features/26-ai-governance.feature`

**Interfaces:**
- `GET /api/admin/ai-governance/overview?days=30` → `AdminAiGovernanceVO`。
- 只返回聚合与配置状态，不返回 user_id/raw_input/ai_output/reason 原文。

- [ ] **Step 1: 写失败测试**

```java
package com.zhiyu.modules.admin;

import com.zhiyu.ai.AiClient;
import com.zhiyu.ai.client.MockLlmClient;
import com.zhiyu.modules.admin.service.AdminAiGovernanceService;
import org.junit.jupiter.api.Test;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class AdminAiGovernanceServiceTest {

    @Test
    void normalizesReasonWithoutLeakingMatchedKeyword() {
        assertEquals("关键词命中", AdminAiGovernanceService.normalizeReason("关键词命中: 自伤"));
        assertEquals("输出越界词命中", AdminAiGovernanceService.normalizeReason("输出越界词命中: (?i)诊断"));
        assertEquals("未标注", AdminAiGovernanceService.normalizeReason(null));
    }

    @Test
    void overviewKeepsAggregatesAndConfigurationOnly() {
        JdbcTemplate db = mock(JdbcTemplate.class);
        when(db.queryForList(anyString(), any(Object[].class))).thenReturn(List.of());
        ObjectProvider<VectorStore> vectors = mock(ObjectProvider.class);
        when(vectors.getIfAvailable()).thenReturn(null);
        AiClient ai = mock(AiClient.class);
        when(ai.speechAvailable()).thenReturn(false);
        when(ai.breakerStoreName()).thenReturn("memory");
        var service = new AdminAiGovernanceService(db, vectors, mock(MockLlmClient.class), ai);

        var result = service.overview(30);
        assertEquals(30, result.get("windowDays"));
        assertTrue(result.containsKey("actionCounts"));
        assertTrue(result.containsKey("config"));
        assertFalse(result.toString().contains("userId"));
        assertFalse(result.toString().contains("rawInput"));
    }
}
```

- [ ] **Step 2: 实现聚合服务**

核心 SQL：

```sql
SELECT scene, COUNT(*) AS count FROM ai_audit_log
WHERE create_time >= ? GROUP BY scene ORDER BY count DESC;

SELECT action, COUNT(*) AS count FROM ai_audit_log
WHERE create_time >= ? GROUP BY action;

SELECT reason, COUNT(*) AS count FROM ai_audit_log
WHERE create_time >= ? GROUP BY reason;

SELECT id, scene, action, reason, create_time FROM ai_audit_log
WHERE create_time >= ? ORDER BY create_time DESC LIMIT 8;
```

服务字段：

```java
private final JdbcTemplate db;
private final ObjectProvider<VectorStore> vectorStore;
private final LlmClient llmClient;
private final AiClient aiClient;
```

`overview(int days)`：
- clamp days 1..90，默认 30；
- 查询窗口起点 `LocalDateTime.now().minusDays(days)`；
- actionCounts 固定 `PASS/REVIEW/REWRITE/BLOCK` 四项（缺失补 0）；
- sceneCounts 用中文 label（chat=对话，recommend=推荐，message/reflection/listening/rag=...，未知保留原值）；
- reasonCounts 通过 `normalizeReason` 合并计数，丢弃原文；
- recentEvents 只放 `id` 字符串、sceneLabel、action、reasonCategory、createTime；
- config:
```java
Map<String,Object> config = new LinkedHashMap<>();
config.put("chatMode", llmClient instanceof MockLlmClient ? "MOCK" : "EXTERNAL");
config.put("chatVerified", false);
config.put("vectorMode", vectorStore.getIfAvailable() == null ? "UNCONFIGURED" : "LOCAL_FILE");
config.put("vectorVerified", false);
config.put("speechConfigured", aiClient.speechAvailable());
config.put("resilienceStore", aiClient.breakerStoreName());
config.put("note", "配置存在不等于服务连通；本页只统计安全审核事件，不推算调用量、成功率或延迟。");
```
- `privacyNote` 固定文本。

- [ ] **Step 3: Controller 与契约**

```java
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/admin/ai-governance")
public class AdminAiGovernanceController {
    private final AdminAiGovernanceService service;

    @GetMapping("/overview")
    public Result<?> overview(@RequestParam(defaultValue = "30") int days) {
        return Result.success(service.overview(days));
    }
}
```

OpenAPI 增加 `/api/admin/ai-governance/overview`，字段：
`windowDays, generatedAt, totalEvents, actionCounts, sceneCounts, reasonCounts, recentEvents, config, privacyNote`；明确不返回用户 ID/原文/自由文本原因。

Gherkin 至少覆盖：ROLE_3 可读、ROLE_2/学生拒绝、无原文、原因归一化、配置状态不宣称连通、时间窗。

- [ ] **Step 4: 管理端 API client**

`admin-ai-governance.ts` 定义类型与 `getAiGovernanceOverview(days)`。

- [ ] **Step 5: 管理端页面**

`ai-governance.vue` 使用 `BusinessPageHeader`、`ChartCard`/`OperationsChart`、`DataErrorBanner`。页面包含：
- 顶部说明“只统计安全审核事件，不推算调用量/成功率/延迟；配置存在不等于连通”。
- 7/30/90 天切换。
- 4 个指标卡：总事件、BLOCK、REWRITE、聊天模式。
- 配置卡：聊天模式、向量检索、语音、熔断 store；每项显示“已配置/未配置/未验证”。
- 两个图表：动作分布（pie）、场景分布（bar）；原因分类（bar）。
- 最近事件表：时间、场景、动作、原因分类；不含用户/原文。
- 链接到现有 AI 审计日志页。
- 空态、错误重试、移动端单列。

- [ ] **Step 6: 路由与现有文案修正**

在 `zhiyu.ts` 的织愈业务 children 增加：

```ts
{
  name: 'ZhiyuAiGovernance',
  path: 'ai-governance',
  component: () => import('#/views/zhiyu/ai-governance.vue'),
  meta: { icon: 'lucide:shield-check', title: 'AI 治理', authority: ['ROLE_3'] },
},
```

同时把 `audit-logs.vue` 顶部说明改为“详情按隐私规则只展示占位文本，不返回原始输入/输出”，与后端实际行为一致。

- [ ] **Step 7: 测试与构建**

```bash
cd zhiyu-backend && mvn -q -Dtest=AdminAiGovernanceServiceTest,AdminAiGovernanceControllerTest test "-Dmaven.repo.local=../.ai-tmp/m2"
cd zhiyu-admin-vben && pnpm --filter @vben/web-ele typecheck
cd zhiyu-admin-vben && pnpm --filter @vben/web-ele build
```

- [ ] **Step 8: 管理端浏览器验收**

使用 `admin`/`admin123` 登录管理端，打开 `/zhiyu/business/ai-governance`：
- 页面显示 7/30/90 天切换、配置状态、审计聚合、最近事件；
- 无 userId/rawInput 字段；
- 空数据和错误态可重试；
- 375/1280 无横向溢出；console/page errors 0（图标 CDN 过滤除外）；
- 截图保存 `output/playwright/g2-ai-governance-375.png`、`g2-ai-governance-1280.png`。

- [ ] **Step 9: 提交检查点**

Run: `git status --short && git diff --stat`；等待确认后再 commit。

---

## Self-Review

- **Spec coverage**：倾听小结的生成/保存分离、本人会话、加密记录、回看/删除/导出、AI 失败固定引导、高危不进入小结由 Task 1/2 覆盖；AI 治理的场景/动作/原因分类、最近事件、配置状态、ROLE 限制与隐私边界由 Task 3 覆盖。未做：真实模型联调（需用户确认凭据）、跨端真机、管理员数据保留策略变更。
- **Placeholder scan**：核心后端代码与测试给出可编译片段；管理端页面按现有 `ChartCard`/`OperationsChart` 组件约定实现，无 TBD。
- **Type consistency**：`ListeningResult(text, generated, blocked)`、`ListeningSummaryService` 方法签名、`RagSourceVO` 不相关；小结 payload 使用 `sessionId/summary/generated/savedAt`，与 journey/export 消费一致。
