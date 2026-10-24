package com.fridgemind.controller;

import com.fridgemind.common.Result;
import com.fridgemind.security.AuthContext;
import com.fridgemind.service.VoiceService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;

/**
 * 语音识别控制器（预留，后续接入语音引擎）
 */
@RestController
@RequestMapping("/api/voice")
@RequiredArgsConstructor
public class VoiceController {

    private final VoiceService voiceService;

    /** 上传语音文件并识别 */
    @PostMapping("/upload")
    public Result<?> upload(@RequestParam("file") MultipartFile file) throws IOException {
        return Result.success(voiceService.processVoice(file.getBytes(), AuthContext.getUserId()));
    }
}
