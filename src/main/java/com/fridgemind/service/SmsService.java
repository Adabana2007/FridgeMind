package com.fridgemind.service;

/**
 * 短信验证码服务
 */
public interface SmsService {

    void sendCode(String phone);

    boolean verifyCode(String phone, String code);

    /** 检查发送频率限制 */
    boolean checkRateLimit(String phone);
}
