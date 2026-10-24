package com.fridgemind.vo;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class FamilyVO {
    private Long id;
    private String name;
    private Long ownerId;
    private String ownerName;
    private int memberCount;
}
