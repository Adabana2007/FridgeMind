package com.fridgemind.mapper;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.fridgemind.entity.Family;
import org.apache.ibatis.annotations.Mapper;

import java.time.LocalDateTime;

/**
 * 家庭 Mapper
 */
@Mapper
public interface FamilyMapper extends BaseMapper<Family> {

    default Family selectByQrToken(String qrCodeToken) {
        return selectOne(new LambdaQueryWrapper<Family>()
                .eq(Family::getQrCodeToken, qrCodeToken));
    }

    /** 校验二维码 token 仍有效：token 相同且未过期（应等于 1） */
    default Long countByQrToken(String qrCodeToken, LocalDateTime expireAfter) {
        return selectCount(new LambdaQueryWrapper<Family>()
                .eq(Family::getQrCodeToken, qrCodeToken)
                .gt(Family::getQrTokenExpire, expireAfter));
    }
}
