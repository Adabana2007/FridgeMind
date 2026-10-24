package com.fridgemind.common;

import lombok.Data;

/**
 * 统一响应体
 */
@Data
public class Result<T> {

    private int code;
    private String message;
    private T data;

    /** 强制走静态工厂 */
    private Result(int code, String message, T data) {
        this.code = code;
        this.message = message;
        this.data = data;
    }

    /** 成功（带数据） */
    public static <T> Result<T> success(T data) {
        return new Result<>(ResultCode.SUCCESS.getCode(), ResultCode.SUCCESS.getMessage(), data);
    }

    /** 成功（无数据） */
    public static <T> Result<T> success() {
        return success(null);
    }

    /** 失败（枚举内置提示） */
    public static <T> Result<T> error(ResultCode code) {
        return new Result<>(code.getCode(), code.getMessage(), null);
    }

    /** 失败（覆盖提示） */
    public static <T> Result<T> error(ResultCode code, String message) {
        return new Result<>(code.getCode(), message, null);
    }

    /** 失败（非枚举错误） */
    public static <T> Result<T> error(int code, String message) {
        return new Result<>(code, message, null);
    }
}
