package com.fridgemind.mapper;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.fridgemind.entity.User;
import org.apache.ibatis.annotations.Mapper;

/**
 * 用户 Mapper
 */
@Mapper
public interface UserMapper extends BaseMapper<User> {

    default User selectByPhone(String phone) {
        return selectOne(new LambdaQueryWrapper<User>()
                .eq(User::getPhone, phone));
    }

    /** 分页查用户，keyword 同时模糊匹配手机号/昵称（复合条件），按注册时间倒序 */
    default Page<User> selectPageByKeyword(Page<User> page, String keyword) {
        LambdaQueryWrapper<User> wrapper = new LambdaQueryWrapper<User>()
                .orderByDesc(User::getCreatedAt);
        if (keyword != null && !keyword.isBlank()) {
            wrapper.and(w -> w.like(User::getPhone, keyword).or().like(User::getNickname, keyword));
        }
        return selectPage(page, wrapper);
    }
}
