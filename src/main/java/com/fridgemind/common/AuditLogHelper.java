package com.fridgemind.common;

import com.fridgemind.entity.AuditLog;
import com.fridgemind.mapper.AuditLogMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;

/**
 * 审计日志写入辅助：所有日志统一沉淀到 audit_log 表。
 * info/warn 记录成功（result=SUCCESS），error 记录失败（result=FAIL）。
 */
@Component
@RequiredArgsConstructor
public class AuditLogHelper {

    private final AuditLogMapper auditLogMapper;

    /** 通用落库：level/result/ip 等字段原样写入 audit_log 表 */
    public void log(Long userId, Long familyId, String action, String target,
                    String level, String result, String ip) {
        AuditLog log = new AuditLog();
        log.setUserId(userId);
        log.setFamilyId(familyId);
        log.setAction(action);
        log.setTarget(target);
        log.setLevel(level);
        log.setResult(result);
        log.setIp(ip);
        log.setCreatedAt(LocalDateTime.now());
        auditLogMapper.insert(log);
    }

    /** 记录成功消息（level=INFO，result=SUCCESS） */
    public void info(Long userId, Long familyId, String action, String target) {
        log(userId, familyId, action, target, "INFO", "SUCCESS", null);
    }

    /** 记录成功但需关注的告警（level=WARN，result=SUCCESS） */
    public void warn(Long userId, Long familyId, String action, String target) {
        log(userId, familyId, action, target, "WARN", "SUCCESS", null);
    }

    /** 记录失败日志（level=ERROR，result=FAIL） */
    public void error(Long userId, Long familyId, String action, String target) {
        log(userId, familyId, action, target, "ERROR", "FAIL", null);
    }
}
