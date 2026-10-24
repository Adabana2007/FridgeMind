package com.fridgemind.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 审计日志实体
 */
@Data
@TableName("audit_log")
public class AuditLog {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long userId;

    private Long familyId;

    /** 操作类型（如：RECOGNIZE_INGREDIENT, ADD_INGREDIENT） */
    private String action;

    private String target;

    /** 级别：INFO/WARN/ERROR */
    private String level;

    /** 结果：SUCCESS/FAIL */
    private String result;

    private String ip;

    private LocalDateTime createdAt;
}
