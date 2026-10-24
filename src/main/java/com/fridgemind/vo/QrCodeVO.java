package com.fridgemind.vo;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class QrCodeVO {
    private String token;
    private String base64Image;
    private int expireSeconds;
}
