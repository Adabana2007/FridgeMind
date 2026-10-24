package com.fridgemind.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 冰箱实体
 */
@Data
@TableName("fridge")
public class Fridge {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long familyId;

    /** 冰箱名称（用户自定义） */
    private String name;

    /** 硬件设备序列号（可空，纯软件版不需要） */
    private String deviceSn;

    /** 设备接入密钥（设备 URL 携带，空表示不校验） */
    private String deviceKey;

    /** 在线状态：0=离线, 1=在线 */
    private Integer onlineStatus;

    /** 最后心跳时间（设备网关心跳更新） */
    private LocalDateTime lastHeartbeat;

    /** 是否故障：0=正常, 1=故障（管理员可手动标记） */
    private Integer isFault;

    private LocalDateTime createdAt;
}
