package com.fridgemind.vo;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class UserInfoVO {
    private Long id;
    private String phone;
    private String nickname;
    private String avatar;
    private Integer hasPassword;
    private Integer enabled;
}
