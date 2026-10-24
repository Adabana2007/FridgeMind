package com.fridgemind.controller;

import com.fridgemind.common.Result;
import com.fridgemind.device.DeviceSyncService;
import com.fridgemind.dto.RemoteAddRequest;
import com.fridgemind.dto.RemoteRemoveRequest;
import com.fridgemind.entity.Fridge;
import com.fridgemind.security.AuthContext;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * 设备控制入口（小程序/Web → 冰箱设备）
 * WebSocket 细节完全封装在 device 包内。
 */
@RestController
@RequestMapping("/api/device")
@RequiredArgsConstructor
public class DeviceController {

    private final DeviceSyncService deviceSyncService;

    /** 设备状态（在线状态 + 名称），供管理端/小程序展示 */
    @GetMapping("/status")
    public Result<?> status(@RequestParam String deviceSn) {
        // 先校验设备归属当前家庭，再返回在线状态
        Fridge fridge = deviceSyncService.requireOwnedDevice(deviceSn, AuthContext.getFamilyId());
        return Result.success(Map.of(
                "deviceSn", fridge.getDeviceSn(),
                "name", fridge.getName(),
                "online", deviceSyncService.isOnline(fridge.getDeviceSn())
        ));
    }

    /** 远程添加食材到冰箱 */
    @PostMapping("/remote-add")
    public Result<?> remoteAdd(@Valid @RequestBody RemoteAddRequest req) {
        // 校验设备归属当前家庭后，再下发远程添加指令
        deviceSyncService.requireOwnedDevice(req.getDeviceSn(), AuthContext.getFamilyId());
        boolean ok = deviceSyncService.pushRemoteAdd(
                req.getDeviceSn(), req.getName(), req.getCount(), req.getCategory());
        return Result.success(Map.of("sent", ok));
    }

    /** 远程减少冰箱内食材（itemId 为设备端本地条目ID） */
    @PostMapping("/remote-remove")
    public Result<?> remoteRemove(@Valid @RequestBody RemoteRemoveRequest req) {
        // 校验设备归属当前家庭后，再下发远程减少指令
        deviceSyncService.requireOwnedDevice(req.getDeviceSn(), AuthContext.getFamilyId());
        boolean ok = deviceSyncService.pushRemoteRemove(
                req.getDeviceSn(), req.getItemId(), req.getCount());
        return Result.success(Map.of("sent", ok));
    }
}
