package com.fridgemind.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 家庭成员实体
 */
@Data
@TableName("family_member")
public class FamilyMember {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long familyId;

    private Long userId;

    /** 角色：0=创建者(owner), 1=普通成员(member) */
    private Integer role;

    private LocalDateTime joinedAt;
}
