package com.fridgemind.vo;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 设备监控 VO（含心跳和故障状态）
 */
@Data
@Builder
public class DeviceVO {
    private Long id;
    private String name;
    private String deviceSn;
    private Long familyId;
    /** 所属家庭名称：设备监控按家庭搜索时需要展示，否则搜出来不知道为什么命中 */
    private String familyName;
    /** 群主手机号：家庭名会重名（库里 9 个家庭都叫"我的家"），手机号才是唯一的区分依据 */
    private String ownerPhone;
    /** 群主昵称：与手机号一起展示，方便认人 */
    private String ownerNickname;
    /** 在线状态：0=离线, 1=在线 */
    private Integer onlineStatus;
    private LocalDateTime lastHeartbeat;
    /** 是否故障：0=正常, 1=故障 */
    private Integer isFault;
}
