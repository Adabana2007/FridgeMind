package com.fridgemind.vo;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class FridgeVO {
    private Long id;
    private String name;
    private String deviceSn;
    /** 在线状态：0=离线, 1=在线 */
    private Integer onlineStatus;
    private int ingredientCount;
    private int expiredCount;
}
