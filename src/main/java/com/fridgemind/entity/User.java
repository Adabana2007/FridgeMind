package com.fridgemind.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 用户实体
 */
@Data
@TableName("user")
public class User {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 手机号（登录账号） */
    private String phone;

    /** bcrypt 加密后的密码，无密码登录时可为空 */
    private String password;

    private String nickname;

    private String avatar;

    /** 是否已设置密码：0=未设置, 1=已设置 */
    private Integer hasPassword;

    /** 是否启用：0=禁用, 1=启用（管理员可禁用用户） */
    private Integer enabled;

    private LocalDateTime createdAt;
}
