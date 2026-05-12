package com.smartroad.srmp.roadasset.vo;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class DataMgmtProjectVO {
    private String id;
    private String name;
    private String remark;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
