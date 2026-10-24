package com.fridgemind.service;

import com.fridgemind.vo.QrCodeVO;

/**
 * 家庭二维码服务
 */
public interface QrCodeService {

    QrCodeVO generate(Long familyId);

    /** 校验二维码 token，返回家庭 ID（无效返回 null） */
    Long validate(String token);
}
