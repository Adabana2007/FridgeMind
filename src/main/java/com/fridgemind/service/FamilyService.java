package com.fridgemind.service;

import com.fridgemind.vo.FamilyMemberVO;
import com.fridgemind.vo.FamilyVO;
import com.fridgemind.vo.QrCodeVO;

import java.util.List;

/**
 * 家庭服务
 */
public interface FamilyService {

    /** 创建新家庭（自动退出原家庭） */
    FamilyVO createFamily(Long userId, String name);

    FamilyVO getMyFamily(Long userId);

    QrCodeVO generateQrCode(Long familyId);

    void joinFamily(Long userId, String qrToken);

    /** 踢出家庭成员（仅 owner 可操作） */
    void kickMember(Long operatorUserId, Long targetUserId, Long familyId);

    /** 主动退出家庭（群主不可退出，退出后自动获得一个"我的家"） */
    void leaveFamily(Long userId, Long familyId);

    /** 修改家庭名称（仅 owner 可操作） */
    void renameFamily(Long userId, Long familyId, String name);

    List<FamilyMemberVO> getMembers(Long familyId);
}
