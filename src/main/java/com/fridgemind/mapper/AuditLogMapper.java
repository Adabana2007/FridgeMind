package com.fridgemind.mapper;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.fridgemind.entity.AuditLog;
import org.apache.ibatis.annotations.Mapper;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 审计日志 Mapper
 */
@Mapper
public interface AuditLogMapper extends BaseMapper<AuditLog> {

    /** 统计今日审计日志数（created_at >= 当天零点） */
    default Long countToday() {
        return selectCount(new LambdaQueryWrapper<AuditLog>()
                .ge(AuditLog::getCreatedAt, LocalDate.now().atStartOfDay()));
    }

    /** 按操作/级别/时间范围动态筛选审计日志，时间倒序 */
    default Page<AuditLog> selectPageByFilters(Page<AuditLog> page, String action,
            String level, LocalDateTime dateFrom, LocalDateTime dateTo) {
        LambdaQueryWrapper<AuditLog> wrapper = new LambdaQueryWrapper<AuditLog>()
                .orderByDesc(AuditLog::getCreatedAt);
        if (action != null && !action.isBlank()) {
            wrapper.eq(AuditLog::getAction, action);
        }
        if (level != null && !level.isBlank()) {
            wrapper.eq(AuditLog::getLevel, level);
        }
        if (dateFrom != null) {
            wrapper.ge(AuditLog::getCreatedAt, dateFrom);
        }
        if (dateTo != null) {
            wrapper.le(AuditLog::getCreatedAt, dateTo);
        }
        return selectPage(page, wrapper);
    }
}
