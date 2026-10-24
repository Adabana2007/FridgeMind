package com.fridgemind.service;

import com.fridgemind.vo.LoginVO;
import com.fridgemind.vo.UserInfoVO;

/**
 * 用户服务
 */
public interface UserService {

    /** 短信验证码登录（无账号自动注册） */
    LoginVO loginByCode(String phone, String code);

    LoginVO loginByPwd(String phone, String password);

    /** 设置或修改登录密码；已设过密码时必须提供正确的原密码 */
    void changePassword(Long userId, String oldPassword, String password);

    UserInfoVO getUserInfo(Long userId);

    /** 修改昵称：注册时分配的是随机昵称，改掉后家庭/日志各处展示同步更新 */
    void updateNickname(Long userId, String nickname);
}
