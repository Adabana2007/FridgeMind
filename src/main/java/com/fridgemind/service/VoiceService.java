package com.fridgemind.service;

/**
 * 语音识别服务（占位，后续接入语音引擎）
 */
public interface VoiceService {

    String processVoice(byte[] audioData, Long userId);
}
