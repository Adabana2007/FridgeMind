package com.fridgemind.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.fridgemind.entity.RecipeFavorite;
import org.apache.ibatis.annotations.Mapper;

/** 家庭食谱收藏 Mapper */
@Mapper
public interface RecipeFavoriteMapper extends BaseMapper<RecipeFavorite> {
}
