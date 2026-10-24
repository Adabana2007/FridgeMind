package com.fridgemind.service.impl;

import cn.hutool.core.util.RandomUtil;
import com.fridgemind.common.BusinessException;
import com.fridgemind.common.ResultCode;
import com.fridgemind.entity.Family;
import com.fridgemind.mapper.FamilyMapper;
import com.fridgemind.service.QrCodeService;
import com.fridgemind.vo.QrCodeVO;
import com.google.zxing.BarcodeFormat;
import com.google.zxing.client.j2se.MatrixToImageWriter;
import com.google.zxing.common.BitMatrix;
import com.google.zxing.qrcode.QRCodeWriter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.time.LocalDateTime;
import java.util.Base64;
import java.util.concurrent.TimeUnit;

/** 家庭二维码服务实现（6位邀请码，DB持久化 + Redis缓存） */
@Slf4j
@Service
@RequiredArgsConstructor
public class QrCodeServiceImpl implements QrCodeService {

    private final FamilyMapper familyMapper;
    private final RedisTemplate<String, Object> redisTemplate;

    private static final String QR_CACHE_PREFIX = "qr:family:";
    private static final int QR_TTL_HOURS = 24;

    @Override
    @Transactional
    public QrCodeVO generate(Long familyId) {
        String code = generateUniqueCode();

        // 写入 DB（家庭表已有 qr_code_token + qr_token_expire 字段）
        Family family = familyMapper.selectById(familyId);
        family.setQrCodeToken(code);
        family.setQrTokenExpire(LocalDateTime.now().plusHours(QR_TTL_HOURS));
        familyMapper.updateById(family);

        // Redis 缓存加速校验（key=code, value=familyId）
        redisTemplate.opsForValue().set(
                QR_CACHE_PREFIX + code, familyId, QR_TTL_HOURS, TimeUnit.HOURS);

        // 生成二维码图片（内容为 6 位数字）
        String base64Image = generateQrCodeBase64(code);

        log.info("生成家庭邀请码 familyId={}, code={}", familyId, code);

        return QrCodeVO.builder()
                .token(code)
                .base64Image(base64Image)
                .expireSeconds(QR_TTL_HOURS * 3600)
                .build();
    }

    @Override
    public Long validate(String token) {
        if (token == null || token.isBlank()) {
            return null;
        }
        String code = token.trim();

        Object cached = redisTemplate.opsForValue().get(QR_CACHE_PREFIX + code);
        if (cached instanceof Number) {
            log.info("邀请码验证成功(Redis) code={}", code);
            return ((Number) cached).longValue();
        }

        // Redis 未命中则回源 DB，并校验过期时间
        Family family = familyMapper.selectByQrToken(code);
        if (family == null) {
            return null;
        }

        if (family.getQrTokenExpire() == null
                || family.getQrTokenExpire().isBefore(LocalDateTime.now())) {
            return null;
        }

        long remainSeconds = java.time.Duration.between(
                LocalDateTime.now(), family.getQrTokenExpire()).getSeconds();
        if (remainSeconds > 0) {
            redisTemplate.opsForValue().set(
                    QR_CACHE_PREFIX + code, family.getId(), remainSeconds, TimeUnit.SECONDS);
        }

        log.info("邀请码验证成功(DB) code={}, familyId={}", code, family.getId());
        return family.getId();
    }

    /** 生成不重复的 6 位数字邀请码（碰撞则重试） */
    private String generateUniqueCode() {
        for (int i = 0; i < 10; i++) {
            String code = RandomUtil.randomNumbers(6);
            Long exists = familyMapper.countByQrToken(code, LocalDateTime.now());
            if (exists == null || exists == 0) {
                return code;
            }
        }
        // 极小概率碰撞，兜底用7位
        return RandomUtil.randomNumbers(7);
    }

    /** 用 ZXing 生成二维码图片（200x200 像素）并转成 base64 的 data URI */
    private String generateQrCodeBase64(String content) {
        try {
            QRCodeWriter writer = new QRCodeWriter();
            BitMatrix matrix = writer.encode(content, BarcodeFormat.QR_CODE, 200, 200);
            BufferedImage image = MatrixToImageWriter.toBufferedImage(matrix);

            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            ImageIO.write(image, "PNG", baos);
            byte[] bytes = baos.toByteArray();

            return "data:image/png;base64," + Base64.getEncoder().encodeToString(bytes);
        } catch (Exception e) {
            log.error("生成二维码图片失败", e);
            throw new BusinessException(ResultCode.SERVER_ERROR, "生成二维码图片失败");
        }
    }
}
