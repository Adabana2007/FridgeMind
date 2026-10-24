package com.fridgemind.service.impl;

import com.fridgemind.entity.Feedback;
import com.fridgemind.mapper.FeedbackMapper;
import com.fridgemind.service.FeedbackService;
import com.fridgemind.vo.FeedbackVO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

/**
 * 用户反馈服务实现
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class FeedbackServiceImpl implements FeedbackService {

    private final FeedbackMapper feedbackMapper;

    @Override
    @Transactional
    public void submit(Long userId, String content) {
        Feedback feedback = new Feedback();
        feedback.setUserId(userId);
        feedback.setContent(content);
        feedback.setStatus(0); // 初始状态：待处理
        feedbackMapper.insert(feedback);
        log.info("用户提交反馈 userId={}", userId);
    }

    @Override
    public List<FeedbackVO> listByUser(Long userId) {
        List<Feedback> list = feedbackMapper.selectByUserIdOrderByCreatedDesc(userId);
        return list.stream()
                .map(f -> FeedbackVO.builder()
                        .id(f.getId())
                        .userId(f.getUserId())
                        .content(f.getContent())
                        .status(f.getStatus())
                        .reply(f.getReply())
                        .createdAt(f.getCreatedAt())
                        .build())
                .collect(Collectors.toList());
    }
}
