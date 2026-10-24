package com.fridgemind.vo;

import java.time.LocalDateTime;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class FamilyMemberVO {
    private Long userId;
    private String nickname;
    /** 手机号：同家庭成员之间可见，用于认人 */
    private String phone;
    private String avatar;
    /** 角色：0=创建者(owner), 1=普通成员(member) */
    private Integer role;
    private LocalDateTime joinedAt;
}
