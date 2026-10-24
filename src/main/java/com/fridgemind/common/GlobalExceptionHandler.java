package com.fridgemind.common;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.ConstraintViolationException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import java.util.stream.Collectors;

/**
 * 全局异常拦截，把所有异常统一包装为 Result 返回
 * 未知异常兜底返回 500，避免暴露内部细节。
 */
@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    /** 业务异常：把 ResultCode 和自定义 message 直接返回给前端 */
    @ExceptionHandler(BusinessException.class)
    public ResponseEntity<Result<Void>> handleBusinessException(BusinessException e) {
        log.warn("业务异常：{} - {}", e.getResultCode(), e.getMessage());
        // 业务错误用 HTTP 200 包装，前端通过 body.code 区分
        return ResponseEntity.ok(Result.error(e.getResultCode(), e.getMessage()));
    }

    /** @Valid 校验失败：把每个字段的错误信息拼接后返回 */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<Result<Void>> handleValidationException(MethodArgumentNotValidException e) {
        String detail = e.getBindingResult().getFieldErrors().stream()
                .map(fieldError -> fieldError.getField() + ": " + fieldError.getDefaultMessage())
                .collect(Collectors.joining("; "));
        log.warn("参数校验失败：{}", detail);
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(Result.error(ResultCode.PARAM_ERROR, detail));
    }

    /** 参数缺失/类型不符/请求体不是合法 JSON：都是客户端错误，按 400 返回（否则会伪装成 500 服务故障） */
    @ExceptionHandler({MissingServletRequestParameterException.class,
            MethodArgumentTypeMismatchException.class,
            HttpMessageNotReadableException.class})
    public ResponseEntity<Result<Void>> handleBadRequest(Exception e) {
        log.warn("请求参数不合法：{}", e.getMessage());
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(Result.error(ResultCode.PARAM_ERROR, "请求参数不合法"));
    }

    /** 方法参数上的校验（如分页 @Max）失败：属于客户端错误，必须转 400 而不是落进兜底 500 */
    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<Result<Void>> handleConstraintViolation(ConstraintViolationException e) {
        String detail = e.getConstraintViolations().stream()
                .map(ConstraintViolation::getMessage)
                .collect(Collectors.joining("; "));
        log.warn("参数校验失败：{}", detail);
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(Result.error(ResultCode.PARAM_ERROR, detail));
    }

    /** 路径不存在：也是客户端错误，必须早于兜底处理 —— 否则缺步骤图之类会按 500 + ERROR 刷满日志 */
    @ExceptionHandler(NoResourceFoundException.class)
    public ResponseEntity<Result<Void>> handleNotFound(NoResourceFoundException e) {
        log.debug("资源不存在：{}", e.getMessage());
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(Result.error(ResultCode.NOT_FOUND));
    }

    /** 兜底：未预期的异常，打好完整日志方便排查 */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<Result<Void>> handleException(Exception e) {
        log.error("系统异常", e);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(Result.error(ResultCode.SERVER_ERROR));
    }
}
