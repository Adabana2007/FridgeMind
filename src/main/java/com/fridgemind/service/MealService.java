package com.fridgemind.service;

import com.fridgemind.vo.MealVO;

import java.util.List;

/** 家庭点餐服务（一餐制：当前菜单，吃完清空重来） */
public interface MealService {

    /** 点一道菜（同菜重复由唯一键兜底，返回新增记录） */
    MealVO add(Long familyId, Long userId, String recipeCode, String note);

    /** 当前菜单（菜谱卡片 + 点餐人昵称 + 缺的食材） */
    List<MealVO> list(Long familyId);

    /** 改备注 */
    void updateNote(Long familyId, Long id, String note);

    /** 取消这道菜 */
    void remove(Long familyId, Long id);

    /** 这顿吃完啦：归档当前菜单（历史保留，可查） */
    void archive(Long familyId);

    /** 归档历史（按归档时间倒序，含菜谱卡片+点餐人） */
    List<MealVO> history(Long familyId);

    /** 聚合菜单缺的食材 → 加入购物清单（去重，返回加入条数） */
    int addMissingToShopping(Long familyId, Long userId);
}
