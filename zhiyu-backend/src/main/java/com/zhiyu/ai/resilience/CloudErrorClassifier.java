package com.zhiyu.ai.resilience;

/**
 * 云端调用异常分类（2026-11-08 装配层批，D2；同日彩排批增补 Spring AI 异常识别）。
 *
 * <p>只有「云不可用」才允许本地兜底；请求自身构造错误（400/404/422 等）
 * 与未知异常不兜底——那多半是我方 bug，打本地也会败，且会掩盖问题。
 *
 * <p>词表：
 * <ul>
 *   <li>{@link Kind#UNAVAILABLE} —— 超时/连接错误/5xx/429/transient：兜底，记 WARN；</li>
 *   <li>{@link Kind#AUTH} —— 401/403：兜底，但记 <b>ERROR</b> 级告警
 *       （鉴权失败长期吃兜底会掩盖云 key 失效，D2 明确要求可感知）；</li>
 *   <li>{@link Kind#FATAL} —— 其它：不兜底，按既有降级处理。</li>
 * </ul>
 *
 * <p><b>Spring AI 通道（2026-11-08 断网彩排实证）</b>：1.0.0 的同步 call 路径把 4xx
 * 包装为 {@code NonTransientAiException}——状态码只在 message 文本里（如
 * {@code "HTTP 401 - {"error":{...}}"}），cause 链没有 RestClientResponseException；
 * transient 故障（重试耗尽）抛 {@code TransientAiException}。本类只按 message 前缀
 * {@code ^HTTP (\d{3})} 精确识别 <b>401/403/429</b> 三个状态码；400/404 等我方构造错误、
 * 无状态码文本一律维持 FATAL——宁可漏兜，不可把真 bug 吞进兜底。
 */
public final class CloudErrorClassifier {

    public enum Kind {
        UNAVAILABLE, AUTH, FATAL
    }

    /** Spring AI NonTransientAiException message 前缀中的状态码；只认前缀，防正文误命中。 */
    private static final java.util.regex.Pattern SPRING_AI_STATUS_PREFIX =
            java.util.regex.Pattern.compile("^HTTP (\\d{3})(?:\\s|$).*", java.util.regex.Pattern.DOTALL);

    private CloudErrorClassifier() {
    }

    public static Kind classify(Throwable error) {
        for (Throwable t = error; t != null; t = t.getCause()) {
            if (t instanceof org.springframework.web.client.RestClientResponseException rce) {
                return fromStatus(rce.getStatusCode().value());
            }
            if (t instanceof org.springframework.web.reactive.function.client.WebClientResponseException wce) {
                return fromStatus(wce.getStatusCode().value());
            }
            if (t instanceof org.springframework.ai.retry.NonTransientAiException ntae) {
                return fromSpringAiMessage(ntae.getMessage());
            }
            if (t instanceof org.springframework.ai.retry.TransientAiException) {
                // Spring AI 重试耗尽后抛出的 transient 故障，按云不可用处理
                return Kind.UNAVAILABLE;
            }
            if (t instanceof java.net.SocketTimeoutException
                    || t instanceof java.net.ConnectException
                    || t instanceof java.util.concurrent.TimeoutException
                    || t instanceof io.netty.handler.timeout.ReadTimeoutException
                    || t instanceof io.netty.handler.timeout.WriteTimeoutException) {
                return Kind.UNAVAILABLE;
            }
            if (t instanceof org.springframework.web.client.ResourceAccessException) {
                // RestTemplate/RestClient 的 I/O 包装（connect/read 失败）
                return Kind.UNAVAILABLE;
            }
        }
        return Kind.FATAL;
    }

    /** 窄口径：仅 401/403→AUTH、429→UNAVAILABLE；其余（400/404/5xx 文本/无状态码）一律 FATAL。 */
    private static Kind fromSpringAiMessage(String message) {
        if (message == null) {
            return Kind.FATAL;
        }
        var m = SPRING_AI_STATUS_PREFIX.matcher(message);
        if (!m.matches()) {
            return Kind.FATAL;
        }
        int status = Integer.parseInt(m.group(1));
        if (status == 401 || status == 403) {
            return Kind.AUTH;
        }
        if (status == 429) {
            return Kind.UNAVAILABLE;
        }
        return Kind.FATAL;
    }

    private static Kind fromStatus(int status) {
        if (status == 401 || status == 403) {
            return Kind.AUTH;
        }
        if (status == 429 || status >= 500) {
            return Kind.UNAVAILABLE;
        }
        return Kind.FATAL;
    }
}
