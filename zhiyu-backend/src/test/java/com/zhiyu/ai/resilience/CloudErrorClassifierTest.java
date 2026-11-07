package com.zhiyu.ai.resilience;

import com.zhiyu.ai.resilience.CloudErrorClassifier.Kind;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.ai.retry.NonTransientAiException;
import org.springframework.ai.retry.TransientAiException;
import org.springframework.http.HttpStatus;
import org.springframework.web.client.RestClientResponseException;
import org.springframework.web.reactive.function.client.WebClientResponseException;

import java.net.ConnectException;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * 云端异常分类矩阵（D2；2026-11-08 彩排批：Spring AI 通道窄匹配）。
 *
 * <p>钉死的边界：401/403→AUTH、429/transient→UNAVAILABLE 允许兜底；
 * 400/404、无状态码文本、非前缀出现的 "HTTP 401" 字样一律 FATAL 不兜底——
 * 宁可漏兜，不可把我方构造错误吞进兜底。
 */
class CloudErrorClassifierTest {

    // ── Spring AI NonTransientAiException 通道（同步 call 路径真实形态，彩排日志实证） ──

    @Test
    @DisplayName("NonTransient HTTP 401 前缀 → AUTH")
    void springAi401IsAuth() {
        var e = new NonTransientAiException("HTTP 401 - {\"error\":{\"message\":\"Authentication Fails\"}}");
        assertEquals(Kind.AUTH, CloudErrorClassifier.classify(e));
    }

    @Test
    @DisplayName("NonTransient HTTP 403 前缀 → AUTH")
    void springAi403IsAuth() {
        assertEquals(Kind.AUTH,
                CloudErrorClassifier.classify(new NonTransientAiException("HTTP 403 - {\"error\":{}}")));
    }

    @Test
    @DisplayName("NonTransient HTTP 429 前缀 → UNAVAILABLE")
    void springAi429IsUnavailable() {
        assertEquals(Kind.UNAVAILABLE,
                CloudErrorClassifier.classify(new NonTransientAiException("HTTP 429 - {\"error\":{}}")));
    }

    @Test
    @DisplayName("NonTransient HTTP 400 → FATAL（我方构造错误不兜底）")
    void springAi400IsFatal() {
        assertEquals(Kind.FATAL,
                CloudErrorClassifier.classify(new NonTransientAiException("HTTP 400 - {\"error\":{}}")));
    }

    @Test
    @DisplayName("NonTransient HTTP 500 文本 → FATAL（窄口径：只认 401/403/429，防把 5xx 文本糊成 AUTH）")
    void springAi500TextIsFatal() {
        assertEquals(Kind.FATAL,
                CloudErrorClassifier.classify(new NonTransientAiException("HTTP 500 - {\"error\":{}}")));
    }

    @Test
    @DisplayName("NonTransient 无状态码文本 → FATAL")
    void springAiNoStatusIsFatal() {
        assertEquals(Kind.FATAL,
                CloudErrorClassifier.classify(new NonTransientAiException("model does not exist")));
    }

    @Test
    @DisplayName("NonTransient null message → FATAL")
    void springAiNullMessageIsFatal() {
        assertEquals(Kind.FATAL,
                CloudErrorClassifier.classify(new NonTransientAiException(null)));
    }

    @Test
    @DisplayName("HTTP 401 非前缀出现 → FATAL（只按前缀精确匹配，不做 contains 模糊判断）")
    void springAiStatusNotAtPrefixIsFatal() {
        assertEquals(Kind.FATAL,
                CloudErrorClassifier.classify(new NonTransientAiException("retry failed, last: HTTP 401 - {}")));
    }

    @Test
    @DisplayName("NonTransient 被业务异常包装 → 沿 cause 链识别为 AUTH")
    void springAiWrappedStillClassified() {
        var wrapped = new IllegalStateException("call failed",
                new NonTransientAiException("HTTP 401 - {\"error\":{}}"));
        assertEquals(Kind.AUTH, CloudErrorClassifier.classify(wrapped));
    }

    // ── Spring AI TransientAiException 通道 ──

    @Test
    @DisplayName("TransientAiException → UNAVAILABLE（重试耗尽的 transient 故障）")
    void transientIsUnavailable() {
        assertEquals(Kind.UNAVAILABLE,
                CloudErrorClassifier.classify(new TransientAiException("HTTP 503 - {}")));
    }

    // ── 既有通道回归（不得被新分支破坏） ──

    @Test
    @DisplayName("WebClientResponseException 401 → AUTH（流式路径真实形态，彩排日志实证）")
    void webClient401IsAuth() {
        var e = WebClientResponseException.create(HttpStatus.UNAUTHORIZED.value(), "Unauthorized",
                null, null, null);
        assertEquals(Kind.AUTH, CloudErrorClassifier.classify(e));
    }

    @Test
    @DisplayName("RestClientResponseException 400 → FATAL")
    void restClient400IsFatal() {
        var e = new RestClientResponseException("bad request", HttpStatus.BAD_REQUEST, "Bad Request",
                null, null, null);
        assertEquals(Kind.FATAL, CloudErrorClassifier.classify(e));
    }

    @Test
    @DisplayName("ConnectException → UNAVAILABLE（死端点路径）")
    void connectExceptionIsUnavailable() {
        assertEquals(Kind.UNAVAILABLE,
                CloudErrorClassifier.classify(new ConnectException("connection refused")));
    }
}
