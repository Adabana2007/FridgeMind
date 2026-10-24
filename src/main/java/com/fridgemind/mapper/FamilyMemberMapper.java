package com.fridgemind.mapper;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.fridgemind.entity.FamilyMember;
import org.apache.ibatis.annotations.Mapper;

import java.util.List;

/**
 * 家庭成员 Mapper
 */
@Mapper
public interface FamilyMemberMapper extends BaseMapper<FamilyMember> {

    /** 查用户当前家庭关系（取一条，用于识别所属家庭）；按 id 升序保证取哪一条是确定的 */
    default FamilyMember selectOneByUserId(Long userId) {
        return selectOne(new LambdaQueryWrapper<FamilyMember>()
                .eq(FamilyMember::getUserId, userId)
                .orderByAsc(FamilyMember::getId)
                .last("LIMIT 1"));
    }

    default List<FamilyMember> selectListByUserId(Long userId) {
        return selectList(new LambdaQueryWrapper<FamilyMember>()
                .eq(FamilyMember::getUserId, userId));
    }

    /** 查用户在指定家庭中的成员关系（用于加入/权限判断） */
    default FamilyMember selectByFamilyAndUser(Long familyId, Long userId) {
        return selectOne(new LambdaQueryWrapper<FamilyMember>()
                .eq(FamilyMember::getFamilyId, familyId)
                .eq(FamilyMember::getUserId, userId));
    }

    default List<FamilyMember> selectByFamilyId(Long familyId) {
        return selectList(new LambdaQueryWrapper<FamilyMember>()
                .eq(FamilyMember::getFamilyId, familyId));
    }

    default Long countByFamilyId(Long familyId) {
        return selectCount(new LambdaQueryWrapper<FamilyMember>()
                .eq(FamilyMember::getFamilyId, familyId));
    }
}
