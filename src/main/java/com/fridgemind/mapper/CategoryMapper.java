package com.fridgemind.mapper;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.fridgemind.entity.Category;
import org.apache.ibatis.annotations.Mapper;

import java.util.List;

/**
 * 分类 Mapper
 */
@Mapper
public interface CategoryMapper extends BaseMapper<Category> {

    /** 查系统内置分类（family_id 为 null） */
    default List<Category> selectSystemCategories() {
        return selectList(new LambdaQueryWrapper<Category>()
                .isNull(Category::getFamilyId));
    }

    default List<Category> selectByFamilyId(Long familyId) {
        return selectList(new LambdaQueryWrapper<Category>()
                .eq(Category::getFamilyId, familyId));
    }

    /** 查全部分类，按 sort_order 升序 */
    default List<Category> selectAllOrderBySort() {
        return selectList(new LambdaQueryWrapper<Category>()
                .orderByAsc(Category::getSortOrder));
    }
}
