package com.fridgemind.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 家庭实体
 */
@Data
@TableName("family")
public class Family {

    @TableId(type = IdType.AUTO)
    private Long id;

    private String name;

    private Long ownerId;

    /** 邀请二维码 token */
    private String qrCodeToken;

    private LocalDateTime qrTokenExpire;

    private LocalDateTime createdAt;
}
