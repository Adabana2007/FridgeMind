package com.fridgemind.service;

import com.fridgemind.vo.FeedbackVO;

import java.util.List;

/**
 * 用户反馈服务
 */
public interface FeedbackService {

    void submit(Long userId, String content);

    List<FeedbackVO> listByUser(Long userId);
}
