package com.fridgemind.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 库存变更日志实体
 */
@Data
@TableName("inventory_log")
public class InventoryLog {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long ingredientId;

    /** 冰箱ID（冗余，方便按冰箱查询） */
    private Long fridgeId;

    private Long userId;

    /** 操作类型：1=新增, 2=移除, 3=过期, 4=修改 */
    private Integer type;

    /** 来源：1=手动录入, 2=自动检测 */
    private Integer source;

    private BigDecimal quantityChange;

    /** 操作后剩余数量 */
    private BigDecimal quantityAfter;

    /** 确认状态：NULL=不适用, 0=待确认, 1=已食用, 2=已丢弃, 3=已过期 */
    private Integer confirmed;

    private LocalDateTime confirmedAt;

    private String remark;

    private LocalDateTime createdAt;
}
