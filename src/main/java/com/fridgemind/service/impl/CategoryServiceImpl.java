package com.fridgemind.service.impl;

import com.fridgemind.entity.Category;
import com.fridgemind.mapper.CategoryMapper;
import com.fridgemind.service.CategoryService;
import com.fridgemind.vo.CategoryVO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 食材分类服务实现（只读：系统预置 + 家庭自定义分类）
 *
 * <p>用户端不再提供分类增删，分类维护统一走管理端 {@code /api/admin/categories}。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class CategoryServiceImpl implements CategoryService {

    private final CategoryMapper categoryMapper;

    @Override
    public List<CategoryVO> getTree(Long familyId) {
        // 系统预置分类（familyId IS NULL）
        List<Category> systemCategories = categoryMapper.selectSystemCategories();

        // 家庭自定义分类
        List<Category> familyCategories = categoryMapper.selectByFamilyId(familyId);

        List<Category> allCategories = new ArrayList<>();
        allCategories.addAll(systemCategories);
        allCategories.addAll(familyCategories);

        return buildTree(allCategories);
    }

    /** 构建分类树：parentId=0 为根节点 */
    private List<CategoryVO> buildTree(List<Category> categories) {
        // 按 parentId 分组（NULL 视为 0，表示根节点）
        Map<Integer, List<Category>> grouped = categories.stream()
                .collect(Collectors.groupingBy(c -> c.getParentId() != null ? c.getParentId() : 0));

        List<Category> roots = grouped.getOrDefault(0, new ArrayList<>());

        return roots.stream()
                .map(root -> {
                    List<Category> children = grouped.getOrDefault(root.getId(), new ArrayList<>());
                    return CategoryVO.builder()
                            .id(root.getId())
                            .name(root.getName())
                            .parentId(root.getParentId())
                            .isSystem(root.getIsSystem())
                            .sortOrder(root.getSortOrder())
                            .children(children.stream().map(this::toVO).collect(Collectors.toList()))
                            .build();
                })
                .collect(Collectors.toList());
    }

    private CategoryVO toVO(Category category) {
        return CategoryVO.builder()
                .id(category.getId())
                .name(category.getName())
                .parentId(category.getParentId())
                .isSystem(category.getIsSystem())
                .sortOrder(category.getSortOrder())
                .children(new ArrayList<>())
                .build();
    }
}
