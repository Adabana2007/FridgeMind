package com.fridgemind.service;

import com.fridgemind.vo.CategoryVO;

import java.util.List;

/**
 * 食材分类服务（只读）
 */
public interface CategoryService {

    /** 家庭分类树（系统预置 + 家庭自定义） */
    List<CategoryVO> getTree(Long familyId);
}
