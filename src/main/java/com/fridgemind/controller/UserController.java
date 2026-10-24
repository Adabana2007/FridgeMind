package com.fridgemind.controller;

import com.fridgemind.common.Result;
import com.fridgemind.dto.LoginByCodeRequest;
import com.fridgemind.dto.LoginByPwdRequest;
import com.fridgemind.dto.SendCodeRequest;
import com.fridgemind.dto.SetPasswordRequest;
import com.fridgemind.dto.SubmitFeedbackRequest;
import com.fridgemind.dto.UpdateNicknameRequest;
import com.fridgemind.security.AuthContext;
import com.fridgemind.service.FeedbackService;
import com.fridgemind.service.SmsService;
import com.fridgemind.service.UserService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

/**
 * 用户控制器
 * send-code / login-by-code / login-by-pwd 不做登录拦截（拦截器已排除）。
 */
@RestController
@RequestMapping("/api/user")
@RequiredArgsConstructor
public class UserController {

    private final SmsService smsService;
    private final UserService userService;
    private final FeedbackService feedbackService;

    /** 发送短信验证码 */
    @PostMapping("/send-code")
    public Result<?> sendCode(@Valid @RequestBody SendCodeRequest req) {
        smsService.sendCode(req.getPhone());
        return Result.success();
    }

    /** 短信验证码登录（无账号自动注册） */
    @PostMapping("/login-by-code")
    public Result<?> loginByCode(@Valid @RequestBody LoginByCodeRequest req) {
        return Result.success(userService.loginByCode(req.getPhone(), req.getCode()));
    }

    /** 密码登录 */
    @PostMapping("/login-by-pwd")
    public Result<?> loginByPwd(@Valid @RequestBody LoginByPwdRequest req) {
        return Result.success(userService.loginByPwd(req.getPhone(), req.getPassword()));
    }

    /** 设置/修改登录密码（需已登录；已设过密码时必须带 oldPassword） */
    @PostMapping("/set-password")
    public Result<?> setPassword(@Valid @RequestBody SetPasswordRequest req) {
        userService.changePassword(AuthContext.getUserId(), req.getOldPassword(), req.getPassword());
        return Result.success();
    }

    /** 获取当前登录用户信息 */
    @GetMapping("/info")
    public Result<?> getUserInfo() {
        return Result.success(userService.getUserInfo(AuthContext.getUserId()));
    }

    /** 修改昵称（需已登录） */
    @PutMapping("/nickname")
    public Result<?> updateNickname(@Valid @RequestBody UpdateNicknameRequest req) {
        userService.updateNickname(AuthContext.getUserId(), req.getNickname());
        return Result.success();
    }

    /** 提交用户反馈 */
    @PostMapping("/feedback")
    public Result<?> submitFeedback(@Valid @RequestBody SubmitFeedbackRequest req) {
        feedbackService.submit(AuthContext.getUserId(), req.getContent());
        return Result.success();
    }

    /** 查询当前用户的反馈列表（含管理员回复） */
    @GetMapping("/feedback")
    public Result<?> myFeedbacks() {
        return Result.success(feedbackService.listByUser(AuthContext.getUserId()));
    }
}
