package com.fridgemind.vo;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/** 混合搜索结果（type 区分关键词命中/语义兜底，前端据此展示"语义相关推荐"标签） */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SearchResultVO {
    /** like=菜名关键词命中 / semantic=向量语义兜底 */
    private String type;
    private List<RecipeVO> records;
    private long total;
}
