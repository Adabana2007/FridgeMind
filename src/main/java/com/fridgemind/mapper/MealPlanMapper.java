package com.fridgemind.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.fridgemind.entity.MealPlan;
import org.apache.ibatis.annotations.Mapper;

/** 家庭点餐表 Mapper */
@Mapper
public interface MealPlanMapper extends BaseMapper<MealPlan> {
}
