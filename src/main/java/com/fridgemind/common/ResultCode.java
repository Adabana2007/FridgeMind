package com.fridgemind.common;

import lombok.Getter;

/**
 * 统一错误码枚举
 * 按模块分段：1xxx-家庭，2xxx-食材，3xxx-冰箱，4xxx-分类，5xxx-短信，6xxx-密码。
 */
@Getter
public enum ResultCode {

    // 通用状态码（对齐 HTTP）：200 成功 / 400 参数错 / 401 未登录 / 403 无权限 / 404 资源不存在 / 500 服务错
    SUCCESS(200, "success"),
    PARAM_ERROR(400, "参数错误"),
    UNAUTHORIZED(401, "未登录或token已过期"),
    FORBIDDEN(403, "无权限访问"),
    NOT_FOUND(404, "资源不存在"),
    SERVER_ERROR(500, "服务器内部错误"),

    QR_EXPIRED(1001, "二维码已过期"),
    QR_INVALID(1002, "二维码无效"),
    ALREADY_IN_FAMILY(1003, "已加入该家庭，无需重复加入"),
    NOT_FAMILY_MEMBER(1004, "不是该家庭成员"),
    ONLY_OWNER_CAN_KICK(1005, "只有家庭创建者才能移除成员"),
    CANNOT_KICK_OWNER(1006, "不能移除家庭创建者"),
    FAMILY_NOT_FOUND(1007, "家庭不存在"),
    ONLY_OWNER_CAN_OPERATE(1008, "只有群主可以执行该操作"),

    INGREDIENT_NOT_FOUND(2001, "食材不存在"),
    INGREDIENT_PERMISSION_DENIED(2002, "无权操作该食材"),

    FRIDGE_NOT_FOUND(3001, "冰箱不存在"),
    FRIDGE_PERMISSION_DENIED(3002, "无权操作该冰箱"),
    FRIDGE_HAS_INGREDIENTS(3003, "冰箱下还有食材，请先清空后再删除"),

    CATEGORY_NOT_FOUND(4001, "分类不存在"),
    CATEGORY_HAS_INGREDIENTS(4002, "分类下还有食材，无法删除"),
    CATEGORY_IS_SYSTEM(4003, "系统默认分类不可删除"),

    SMS_RATE_LIMIT(5001, "发送过于频繁，请稍后再试"),
    SMS_DAILY_LIMIT(5002, "今日发送次数已达上限"),

    PASSWORD_LOCKED(6001, "密码输入错误次数过多，账号已锁定"),
    PASSWORD_WRONG(6002, "密码错误"),

    RECIPE_NOT_FOUND(7001, "食谱不存在");

    /** 业务状态码 */
    private final int code;
    /** 默认错误提示信息 */
    private final String message;

    ResultCode(int code, String message) {
        this.code = code;
        this.message = message;
    }
}
