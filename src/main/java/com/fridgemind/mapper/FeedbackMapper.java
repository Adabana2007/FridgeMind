package com.fridgemind.mapper;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.fridgemind.entity.Feedback;
import org.apache.ibatis.annotations.Mapper;

import java.util.List;

@Mapper
public interface FeedbackMapper extends BaseMapper<Feedback> {

    /** 按用户查反馈，最新提交在前 */
    default List<Feedback> selectByUserIdOrderByCreatedDesc(Long userId) {
        return selectList(new LambdaQueryWrapper<Feedback>()
                .eq(Feedback::getUserId, userId)
                .orderByDesc(Feedback::getCreatedAt));
    }

    /** 分页查反馈，status 为空则不过滤状态，按时间倒序 */
    default Page<Feedback> selectPageByStatus(Page<Feedback> page, Integer status) {
        LambdaQueryWrapper<Feedback> wrapper = new LambdaQueryWrapper<Feedback>()
                .orderByDesc(Feedback::getCreatedAt);
        if (status != null) {
            wrapper.eq(Feedback::getStatus, status);
        }
        return selectPage(page, wrapper);
    }

    default Long countByStatus(Integer status) {
        return selectCount(new LambdaQueryWrapper<Feedback>()
                .eq(Feedback::getStatus, status));
    }
}
