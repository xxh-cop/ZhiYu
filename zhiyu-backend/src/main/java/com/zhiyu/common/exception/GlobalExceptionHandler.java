package com.zhiyu.common.exception;

import com.zhiyu.common.result.Result;
import com.zhiyu.common.result.ResultCode;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.MessageSourceResolvable;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.FieldError;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import org.springframework.web.servlet.NoHandlerFoundException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import java.util.Objects;

/**
 * 全局异常处理：把异常统一收敛成 {@link Result}，并按 AGENTS.md §3 给出对应 HTTP 状态。
 *
 * <p>错误码取值见 {@code AGENTS.md} §3，不新造、不改数值。
 *
 * <p><b>这里管不到 401/403</b>：未认证与权限不足发生在 DispatcherServlet 之前，
 * 由 {@code JwtAuthFilter} + {@code RestAuthenticationEntryPoint} /
 * {@code RestAccessDeniedHandler} 直接写响应。
 *
 * <p><b>关于 404/405</b>：T0.3 收尾批次新增了错误码 `1004 资源不存在 | 404` 与
 * `1005 请求方法不支持 | 405`（见 AGENTS.md §3），<b>不再用 5000 兜底</b>。
 * 这样客户端的路径/方法失误不会混进 5xx 监控告警，也不会被记成 ERROR 级系统异常
 * 与完整堆栈，只留一条 WARN。
 */
@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    /** 业务异常：用异常自带的 code 反查 HTTP 状态，再原样返回 code/message。 */
    @ExceptionHandler(BusinessException.class)
    public ResponseEntity<Result<Void>> handleBusinessException(BusinessException e) {
        ResultCode matched = ResultCode.fromCode(e.getCode());
        // code 不在 AGENTS.md §3 的表里时兜底为 200
        HttpStatus status = (matched == null) ? HttpStatus.OK : matched.getHttpStatus();
        log.warn("业务异常 code={}, httpStatus={}, message={}", e.getCode(), status.value(), e.getMessage());
        return ResponseEntity.status(status).body(Result.error(e.getCode(), e.getMessage()));
    }

    /** 请求体校验失败（@Valid @RequestBody，抛的是 MethodArgumentNotValidException）：固定 HTTP 400。 */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<Result<Void>> handleMethodArgumentNotValid(MethodArgumentNotValidException e) {
        FieldError fieldError = e.getBindingResult().getFieldError();
        String message = (fieldError == null)
                ? ResultCode.PARAM_ERROR.getMessage()
                : fieldError.getDefaultMessage();
        log.warn("请求体校验失败 field={}, message={}",
                fieldError == null ? "-" : fieldError.getField(), message);
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(Result.error(ResultCode.PARAM_ERROR.getCode(), message));
    }

    /**
     * 方法参数校验失败（{@code @RequestParam} / {@code @PathVariable} 上的约束注解）。
     *
     * <p>Spring Boot 3.2（Spring 6.1）起这类失败抛的是
     * {@link HandlerMethodValidationException}，<b>不再是</b>
     * {@code ConstraintViolationException}，所以必须单独接。
     */
    @ExceptionHandler(HandlerMethodValidationException.class)
    public ResponseEntity<Result<Void>> handleHandlerMethodValidation(HandlerMethodValidationException e) {
        String message = e.getAllValidationResults().stream()
                .flatMap(result -> result.getResolvableErrors().stream())
                .map(MessageSourceResolvable::getDefaultMessage)
                .filter(Objects::nonNull)
                .findFirst()
                .orElse(ResultCode.PARAM_ERROR.getMessage());
        log.warn("方法参数校验失败 message={}", message);
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(Result.error(ResultCode.PARAM_ERROR.getCode(), message));
    }

    /**
     * 请求体读不出来：JSON 语法错误、body 为空、Content-Type 不对。
     *
     * <p>属于调用方问题，固定 400 + 1001；不回显原始报文内容
     * （避免把解析器内部细节或用户输入原样抛回去）。
     */
    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<Result<Void>> handleHttpMessageNotReadable(HttpMessageNotReadableException e) {
        log.warn("请求体不可读：{}", e.getMessage());
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(Result.error(ResultCode.PARAM_ERROR.getCode(), "请求体格式错误或缺失"));
    }

    /**
     * 找不到处理该路径的 Controller：1004 / HTTP 404。
     *
     * <p>注意：Spring Boot 默认 {@code spring.mvc.throw-exception-if-no-handler-found=false}，
     * 未匹配的路径通常由下面的 {@link NoResourceFoundException} 接住；
     * 本 handler 是为显式开启该开关的场景保留的。
     */
    @ExceptionHandler(NoHandlerFoundException.class)
    public ResponseEntity<Result<Void>> handleNoHandlerFound(NoHandlerFoundException e) {
        log.warn("路径不存在：{} {}", e.getHttpMethod(), e.getRequestURL());
        return ResponseEntity.status(ResultCode.RESOURCE_NOT_FOUND.getHttpStatus())
                .body(Result.error(ResultCode.RESOURCE_NOT_FOUND));
    }

    /**
     * 路径匹配到了静态资源处理器但资源不存在（Spring 6.1+，Boot 3.2 默认行为下的 404）。
     *
     * <p><b>这是实际生效的 404 出口</b>：已认证用户访问未注册的路径时走这里，
     * 返回 1004 / HTTP 404。匿名访问未注册路径仍由 Spring Security 先拦成 401
     * （不泄露「哪些路径存在」，见 AGENTS.md §3 的 1002 说明）。
     */
    @ExceptionHandler(NoResourceFoundException.class)
    public ResponseEntity<Result<Void>> handleNoResourceFound(NoResourceFoundException e) {
        log.warn("资源不存在：{}", e.getResourcePath());
        return ResponseEntity.status(ResultCode.RESOURCE_NOT_FOUND.getHttpStatus())
                .body(Result.error(ResultCode.RESOURCE_NOT_FOUND));
    }

    /** HTTP 方法不支持（如对 POST 接口发 GET）：1005 / HTTP 405。 */
    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    public ResponseEntity<Result<Void>> handleMethodNotSupported(HttpRequestMethodNotSupportedException e) {
        log.warn("请求方法不支持：method={}, supported={}", e.getMethod(), e.getSupportedHttpMethods());
        return ResponseEntity.status(ResultCode.METHOD_NOT_ALLOWED.getHttpStatus())
                .body(Result.error(ResultCode.METHOD_NOT_ALLOWED));
    }

    /** 兜底：记录完整堆栈，只向调用方暴露通用提示，固定 HTTP 500，不泄漏内部细节。 */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<Result<Void>> handleException(Exception e) {
        log.error("系统异常", e);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(Result.error(ResultCode.SYSTEM_ERROR));
    }
}
