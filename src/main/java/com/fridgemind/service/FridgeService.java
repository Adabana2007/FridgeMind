package com.fridgemind.service;

import com.fridgemind.vo.FridgeVO;

import java.util.List;

/**
 * 冰箱服务
 */
public interface FridgeService {

    FridgeVO add(Long familyId, String name, String deviceSn);

    List<FridgeVO> list(Long familyId);

    void update(Long fridgeId, Long familyId, String name);

    void delete(Long fridgeId, Long familyId);
}
