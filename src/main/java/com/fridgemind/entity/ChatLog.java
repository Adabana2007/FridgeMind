package com.fridgemind.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/** AI 对话消息（单时间线，按用户存储） */
@Data
@TableName("chat_log")
public class ChatLog {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long userId;

    /** 角色：0=用户, 1=AI助手 */
    private Integer role;

    private String content;

    private LocalDateTime createdAt;
}
