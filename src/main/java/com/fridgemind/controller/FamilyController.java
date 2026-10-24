package com.fridgemind.controller;

import com.fridgemind.common.Result;
import com.fridgemind.common.ResultCode;
import com.fridgemind.dto.CreateFamilyRequest;
import com.fridgemind.dto.JoinFamilyRequest;
import com.fridgemind.dto.UpdateFamilyRequest;
import com.fridgemind.security.AuthContext;
import com.fridgemind.service.FamilyService;
import com.fridgemind.vo.FamilyVO;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.Collections;

/**
 * 家庭控制器
 */
@RestController
@RequestMapping("/api/family")
@RequiredArgsConstructor
public class FamilyController {

    private final FamilyService familyService;

    /** 创建新家庭（用户从原家庭退出后加入新家庭） */
    @PostMapping("/create")
    public Result<?> create(@Valid @RequestBody CreateFamilyRequest req) {
        FamilyVO family = familyService.createFamily(AuthContext.getUserId(), req.getName());
        return Result.success(family);
    }

    /** 获取当前用户所属的家庭信息 */
    @GetMapping("/my")
    public Result<?> getMyFamily() {
        FamilyVO family = familyService.getMyFamily(AuthContext.getUserId());
        return Result.success(family);
    }

    /** 生成家庭邀请二维码（需已加入家庭） */
    @GetMapping("/qr-code")
    public Result<?> getQrCode() {
        FamilyVO family = familyService.getMyFamily(AuthContext.getUserId());
        if (family == null) {
            // 未加入家庭，无二维码可生成
            return Result.error(ResultCode.FAMILY_NOT_FOUND);
        }
        return Result.success(familyService.generateQrCode(family.getId()));
    }

    /** 通过二维码 token 加入家庭 */
    @PostMapping("/join")
    public Result<?> join(@Valid @RequestBody JoinFamilyRequest req) {
        familyService.joinFamily(AuthContext.getUserId(), req.getQrToken());
        return Result.success();
    }

    /** 踢出家庭成员（仅 owner 可操作） */
    @DeleteMapping("/kick/{userId}")
    public Result<?> kickMember(@PathVariable Long userId) {
        familyService.kickMember(AuthContext.getUserId(), userId, AuthContext.getFamilyId());
        return Result.success();
    }

    /** 退出当前家庭（群主不可退出，退出后自动获得一个"我的家"） */
    @PostMapping("/leave")
    public Result<?> leave() {
        familyService.leaveFamily(AuthContext.getUserId(), AuthContext.getFamilyId());
        return Result.success();
    }

    /** 修改家庭名称（仅 owner 可操作） */
    @PutMapping("/rename")
    public Result<?> rename(@Valid @RequestBody UpdateFamilyRequest req) {
        familyService.renameFamily(AuthContext.getUserId(), AuthContext.getFamilyId(), req.getName());
        return Result.success();
    }

    /** 获取家庭成员列表 */
    @GetMapping("/members")
    public Result<?> getMembers() {
        // 不能依赖 AuthContext.getFamilyId()（该接口被 FamilyInterceptor 排除）
        FamilyVO family = familyService.getMyFamily(AuthContext.getUserId());
        if (family == null) {
            return Result.success(Collections.emptyList());
        }
        return Result.success(familyService.getMembers(family.getId()));
    }
}
