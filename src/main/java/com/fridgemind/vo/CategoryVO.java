package com.fridgemind.vo;

import java.util.List;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class CategoryVO {
    private Integer id;
    private String name;
    private Integer parentId;
    /** 是否系统预置：0=自定义, 1=系统预置 */
    private Integer isSystem;
    private Integer sortOrder;
    private List<CategoryVO> children;
}
