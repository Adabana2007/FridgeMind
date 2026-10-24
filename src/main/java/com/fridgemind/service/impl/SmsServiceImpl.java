package com.fridgemind.service.impl;

import cn.hutool.core.util.RandomUtil;
import com.fridgemind.common.BusinessException;
import com.fridgemind.common.ResultCode;
import com.fridgemind.service.SmsService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.concurrent.TimeUnit;

/** 短信验证码服务实现（开发阶段可用固定验证码） */
@Slf4j
@Service
@RequiredArgsConstructor
public class SmsServiceImpl implements SmsService {

    private final RedisTemplate<String, Object> redisTemplate;

    @Value("${app.sms.mock:false}")
    private boolean mockSms;

    private static final String CODE_PREFIX = "sms:login:";
    private static final String RATE_PREFIX = "sms:rate:";
    private static final String DAILY_PREFIX = "sms:daily:";
    private static final int CODE_TTL_MINUTES = 5;
    private static final int RATE_TTL_SECONDS = 60;
    private static final int DAILY_MAX = 10;

    @Override
    public void sendCode(String phone) {
        // 检查频率限制（60 秒内一次）
        if (!checkRateLimit(phone)) {
            throw new BusinessException(ResultCode.SMS_RATE_LIMIT);
        }

        checkDailyLimit(phone);

        // 生成 6 位验证码（Mock 模式固定 123456）
        String code = mockSms ? "123456" : RandomUtil.randomNumbers(6);

        // 存入 Redis，5 分钟有效
        redisTemplate.opsForValue().set(CODE_PREFIX + phone, code, CODE_TTL_MINUTES, TimeUnit.MINUTES);

        // 记录发送时间（用于频率限制）
        redisTemplate.opsForValue().set(RATE_PREFIX + phone, "1", RATE_TTL_SECONDS, TimeUnit.SECONDS);

        // 递增每日发送计数，过期到当天结束
        String dailyKey = DAILY_PREFIX + phone;
        Long dailyCount = redisTemplate.opsForValue().increment(dailyKey);
        if (dailyCount != null && dailyCount == 1) {
            // 首次设置时，过期到当天23:59:59
            setExpireToEndOfDay(dailyKey);
        }

        log.info("短信验证码已发送 phone={}, mock={}", phone, mockSms);
    }

    @Override
    public boolean verifyCode(String phone, String code) {
        String key = CODE_PREFIX + phone;
        String storedCode = (String) redisTemplate.opsForValue().get(key);
        // 无存储验证码（未发送或已过期）视为校验失败
        if (storedCode == null) {
            return false;
        }
        // 验证通过后删除验证码，防止重复使用
        if (storedCode.equals(code)) {
            redisTemplate.delete(key);
            log.info("验证码校验成功 phone={}", phone);
            return true;
        }
        return false;
    }

    @Override
    public boolean checkRateLimit(String phone) {
        // 限频 key 不存在说明 60s 内未发送过，允许发送
        return redisTemplate.opsForValue().get(RATE_PREFIX + phone) == null;
    }

    /** 检查每日发送上限（最多 10 次） */
    private void checkDailyLimit(String phone) {
        String dailyKey = DAILY_PREFIX + phone;
        Object val = redisTemplate.opsForValue().get(dailyKey);
        int count = 0;
        if (val instanceof Integer) {
            count = (Integer) val;
        } else if (val instanceof Long) {
            count = ((Long) val).intValue();
        } else if (val instanceof String) {
            try {
                count = Integer.parseInt((String) val);
            } catch (NumberFormatException ignored) {
            }
        }
        if (count >= DAILY_MAX) {
            throw new BusinessException(ResultCode.SMS_DAILY_LIMIT);
        }
    }

    /** 设置 Key 过期时间为当天结束（23:59:59） */
    private void setExpireToEndOfDay(String key) {
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime endOfDay = LocalDateTime.of(now.toLocalDate(), LocalTime.MAX);
        long seconds = Duration.between(now, endOfDay).getSeconds();
        redisTemplate.expire(key, seconds, TimeUnit.SECONDS);
    }
}
