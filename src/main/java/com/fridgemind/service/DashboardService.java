package com.fridgemind.service;

import com.fridgemind.vo.DashboardStatsVO;

/**
 * 看板聚合服务
 */
public interface DashboardService {

    /** 看板统计（fridgeId 不传则汇总全部冰箱） */
    DashboardStatsVO stats(Long familyId, Long fridgeId);

    /** 家庭展示缓存失效（看板统计 + AI 菜谱推荐），写入口变化后调用 */
    void evictFamilyCaches(Long familyId);
}
