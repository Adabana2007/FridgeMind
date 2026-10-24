package com.fridgemind.vo;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class InventoryLogVO {
    private Long id;
    private Long ingredientId;
    private String ingredientName;
    /** 操作类型：1=新增, 2=移除, 3=过期, 4=修改 */
    private Integer type;
    /** 来源：1=手动录入, 2=自动检测 */
    private Integer source;
    private BigDecimal quantityChange;
    private BigDecimal quantityAfter;
    /** 确认状态：NULL=不适用, 0=待确认, 1=已食用, 2=已丢弃, 3=已过期 */
    private Integer confirmed;
    private String userName;
    private String remark;
    private LocalDateTime createdAt;
}
