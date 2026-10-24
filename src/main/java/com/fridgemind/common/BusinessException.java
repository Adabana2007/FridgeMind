package com.fridgemind.common;

import lombok.Getter;

/**
 * 业务异常，service 层遇到预期错误时抛出，
 * 由 GlobalExceptionHandler 统一捕获并转为标准 Result 返回。
 */
@Getter
public class BusinessException extends RuntimeException {

    /** 关联的业务错误码，方便前端根据code做差异化处理 */
    private final ResultCode resultCode;

    /** 使用枚举内默认的 message */
    public BusinessException(ResultCode resultCode) {
        super(resultCode.getMessage());
        this.resultCode = resultCode;
    }

    /** 使用自定义 message，覆盖枚举默认提示 */
    public BusinessException(ResultCode resultCode, String message) {
        super(message);
        this.resultCode = resultCode;
    }
}
