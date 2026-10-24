package com.fridgemind.service.impl;

import com.fridgemind.service.VoiceService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * 语音识别服务实现（占位）
 */
@Slf4j
@Service
public class VoiceServiceImpl implements VoiceService {

    @Override
    public String processVoice(byte[] audioData, Long userId) {
        log.info("语音识别请求 userId={}, audioSize={}", userId,
                audioData != null ? audioData.length : 0);
        return "语音识别开发中，敬请期待！";
    }
}
