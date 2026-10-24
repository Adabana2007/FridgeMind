package com.fridgemind.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.fridgemind.entity.ChatLog;

import java.util.List;

/** AI 对话消息 Mapper */
public interface ChatLogMapper extends BaseMapper<ChatLog> {

    /** 取某用户最近 N 条对话（按时间倒序） */
    default List<ChatLog> selectRecent(Long userId, int limit) {
        return selectList(Wrappers.<ChatLog>lambdaQuery()
                .eq(ChatLog::getUserId, userId)
                .orderByDesc(ChatLog::getId)
                .last("LIMIT " + limit));
    }
}
