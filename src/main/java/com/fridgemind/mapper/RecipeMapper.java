package com.fridgemind.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.fridgemind.entity.Recipe;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;

import java.util.List;
import java.util.Map;

/** 食谱表 Mapper（检索走 MySQL 权威层；向量库只负责算相似度） */
@Mapper
public interface RecipeMapper extends BaseMapper<Recipe> {

    /** 各分类数量（分类 Tab 展示，按数量降序） */
    @Select("SELECT category AS category, COUNT(*) AS cnt FROM recipe GROUP BY category ORDER BY cnt DESC")
    List<Map<String, Object>> countByCategory();
}
