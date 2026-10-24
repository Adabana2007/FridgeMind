package com.fridgemind.service.impl;

import com.fridgemind.common.BusinessException;
import com.fridgemind.common.ResultCode;
import com.fridgemind.common.AuditLogHelper;
import com.fridgemind.entity.Family;
import com.fridgemind.entity.FamilyMember;
import com.fridgemind.entity.User;
import com.fridgemind.mapper.FamilyMapper;
import com.fridgemind.mapper.FamilyMemberMapper;
import com.fridgemind.mapper.UserMapper;
import com.fridgemind.service.FamilyService;
import com.fridgemind.service.QrCodeService;
import com.fridgemind.vo.FamilyMemberVO;
import com.fridgemind.vo.FamilyVO;
import com.fridgemind.vo.QrCodeVO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

/** 家庭服务实现（一人一家，加入/被踢自动切换归属） */
@Slf4j
@Service
@RequiredArgsConstructor
public class FamilyServiceImpl implements FamilyService {

    private final FamilyMapper familyMapper;
    private final FamilyMemberMapper familyMemberMapper;
    private final UserMapper userMapper;
    private final QrCodeService qrCodeService;
    private final AuditLogHelper auditLogHelper;

    @Override
    @Transactional
    public FamilyVO createFamily(Long userId, String name) {
        // 群主不能借"创建新家庭"逃逸：原家庭会变成没有群主的孤儿，而当前没有转让群主功能
        FamilyMember current = getMemberRecord(userId);
        if (current != null && current.getRole() != null && current.getRole() == 0) {
            throw new BusinessException(ResultCode.ONLY_OWNER_CAN_OPERATE,
                    "你是当前家庭的群主，不能创建新家庭");
        }

        // 创建前先退出原家庭（一人一家）
        leaveCurrentFamily(userId);

        Family family = new Family();
        family.setName(name);
        family.setOwnerId(userId);
        family.setCreatedAt(LocalDateTime.now());
        familyMapper.insert(family);

        addMember(family.getId(), userId, 0); // 创建者为 owner

        log.info("创建新家庭 userId={}, familyId={}, name={}", userId, family.getId(), name);
        auditLogHelper.info(userId, family.getId(), "CREATE_FAMILY", name);

        return buildFamilyVO(family, userId);
    }

    @Override
    public FamilyVO getMyFamily(Long userId) {
        FamilyMember member = getMemberRecord(userId);
        if (member == null) {
            return null;
        }

        Family family = familyMapper.selectById(member.getFamilyId());
        if (family == null) {
            return null;
        }

        return buildFamilyVO(family, userId);
    }

    @Override
    public QrCodeVO generateQrCode(Long familyId) {
        return qrCodeService.generate(familyId);
    }

    @Override
    @Transactional
    public void joinFamily(Long userId, String qrToken) {
        Long familyId = qrCodeService.validate(qrToken);
        if (familyId == null) {
            throw new BusinessException(ResultCode.QR_EXPIRED);
        }

        Family family = familyMapper.selectById(familyId);
        if (family == null) {
            throw new BusinessException(ResultCode.FAMILY_NOT_FOUND);
        }

        FamilyMember existing = familyMemberMapper.selectByFamilyAndUser(familyId, userId);
        if (existing != null) {
            throw new BusinessException(ResultCode.ALREADY_IN_FAMILY);
        }

        // 一人一家：加入新家庭前先退出原家庭
        leaveCurrentFamily(userId);

        // 加入新家庭（普通成员）
        addMember(familyId, userId, 1);

        log.info("用户加入家庭 userId={}, familyId={}", userId, familyId);
        auditLogHelper.info(userId, familyId, "JOIN_FAMILY", "familyId=" + familyId);
    }

    @Override
    @Transactional
    public void kickMember(Long operatorUserId, Long targetUserId, Long familyId) {
        // 校验操作者是 owner
        FamilyMember operatorMember = familyMemberMapper.selectByFamilyAndUser(familyId, operatorUserId);
        if (operatorMember == null || operatorMember.getRole() != 0) {
            throw new BusinessException(ResultCode.ONLY_OWNER_CAN_KICK);
        }

        FamilyMember targetMember = familyMemberMapper.selectByFamilyAndUser(familyId, targetUserId);
        if (targetMember == null) {
            throw new BusinessException(ResultCode.NOT_FAMILY_MEMBER);
        }

        // 不能踢 owner
        if (targetMember.getRole() == 0) {
            throw new BusinessException(ResultCode.CANNOT_KICK_OWNER);
        }

        familyMemberMapper.deleteById(targetMember.getId());

        // 被踢用户自动创建新的默认家庭
        createDefaultFamilyForUser(targetUserId);

        auditLogHelper.warn(operatorUserId, familyId, "KICK_MEMBER", "userId=" + targetUserId);

        log.info("踢出家庭成员 operatorUserId={}, targetUserId={}, familyId={}", operatorUserId, targetUserId, familyId);
    }

    @Override
    @Transactional
    public void leaveFamily(Long userId, Long familyId) {
        FamilyMember member = familyMemberMapper.selectByFamilyAndUser(familyId, userId);
        if (member == null) {
            throw new BusinessException(ResultCode.NOT_FAMILY_MEMBER);
        }
        // 群主不能退出：退出后家庭变成没有群主的孤儿，而当前没有转让群主功能
        if (member.getRole() != null && member.getRole() == 0) {
            throw new BusinessException(ResultCode.ONLY_OWNER_CAN_OPERATE, "群主不能退出家庭");
        }

        familyMemberMapper.deleteById(member.getId());

        // 退出后给一个落脚点，与被踢成员的处理保持一致（不引入"无家庭"这个新状态）
        createDefaultFamilyForUser(userId);

        auditLogHelper.warn(userId, familyId, "LEAVE_FAMILY", "userId=" + userId);

        log.info("成员退出家庭 userId={}, familyId={}", userId, familyId);
    }

    @Override
    @Transactional
    public void renameFamily(Long userId, Long familyId, String name) {
        FamilyMember member = familyMemberMapper.selectByFamilyAndUser(familyId, userId);
        // 只有群主能改家庭名
        if (member == null || member.getRole() == null || member.getRole() != 0) {
            throw new BusinessException(ResultCode.ONLY_OWNER_CAN_OPERATE);
        }

        Family family = familyMapper.selectById(familyId);
        if (family == null) {
            throw new BusinessException(ResultCode.FAMILY_NOT_FOUND);
        }

        family.setName(name);
        familyMapper.updateById(family);

        auditLogHelper.info(userId, familyId, "RENAME_FAMILY", name);

        log.info("群主修改家庭名 userId={}, familyId={}, name={}", userId, familyId, name);
    }

    @Override
    public List<FamilyMemberVO> getMembers(Long familyId) {
        List<FamilyMember> members = familyMemberMapper.selectByFamilyId(familyId);

        return members.stream().map(member -> {
            User user = userMapper.selectById(member.getUserId());
            return FamilyMemberVO.builder()
                    .userId(member.getUserId())
                    .nickname(user != null ? user.getNickname() : "未知用户")
                    .phone(user != null ? user.getPhone() : null)
                    .avatar(user != null ? user.getAvatar() : null)
                    .role(member.getRole())
                    .joinedAt(member.getJoinedAt())
                    .build();
        }).collect(Collectors.toList());
    }

    /** 用户从当前所有家庭退出（兜底清理） */
    private void leaveCurrentFamily(Long userId) {
        List<FamilyMember> records = familyMemberMapper.selectListByUserId(userId);
        for (FamilyMember record : records) {
            familyMemberMapper.deleteById(record.getId());
        }
    }

    private void addMember(Long familyId, Long userId, int role) {
        FamilyMember member = new FamilyMember();
        member.setFamilyId(familyId);
        member.setUserId(userId);
        member.setRole(role);
        member.setJoinedAt(LocalDateTime.now());
        familyMemberMapper.insert(member);
    }

    private FamilyMember getMemberRecord(Long userId) {
        List<FamilyMember> list = familyMemberMapper.selectListByUserId(userId);
        return list.isEmpty() ? null : list.get(0);
    }

    /** 被踢用户自动创建 "我的家" 默认家庭 */
    private void createDefaultFamilyForUser(Long userId) {
        Family family = new Family();
        family.setName("我的家");
        family.setOwnerId(userId);
        family.setCreatedAt(LocalDateTime.now());
        familyMapper.insert(family);

        addMember(family.getId(), userId, 0);
        log.info("为被踢用户创建默认家庭 userId={}, familyId={}", userId, family.getId());
    }

    private FamilyVO buildFamilyVO(Family family, Long userId) {
        User owner = userMapper.selectById(family.getOwnerId());
        Long memberCount = familyMemberMapper.countByFamilyId(family.getId());

        return FamilyVO.builder()
                .id(family.getId())
                .name(family.getName())
                .ownerId(family.getOwnerId())
                .ownerName(owner != null ? owner.getNickname() : "未知")
                .memberCount(memberCount != null ? memberCount.intValue() : 0)
                .build();
    }
}
