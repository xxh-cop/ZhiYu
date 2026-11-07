package com.zhiyu.ai.resilience;

/**
 * 云端调用异常分类（2026-11-08 装配层批，D2）。
 *
 * <p>只有「云不可用」才允许本地兜底；请求自身构造错误（400/404/422 等）
 * 与未知异常不兜底——那多半是我方 bug，打本地也会败，且会掩盖问题。
 *
 * <p>词表：
 * <ul>
 *   <li>{@link Kind#UNAVAILABLE} —— 超时/连接错误/5xx/429：兜底，记 WARN；</li>
 *   <li>{@link Kind#AUTH} —— 401/403：兜底，但记 <b>ERROR</b> 级告警
 *       （鉴权失败长期吃兜底会掩盖云 key 失效，D2 明确要求可感知）；</li>
 *   <li>{@link Kind#FATAL} —— 其它：不兜底，按既有降级处理。</li>
 * </ul>
 */
public final class CloudErrorClassifier {

    public enum Kind {
        UNAVAILABLE, AUTH, FATAL
    }

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
