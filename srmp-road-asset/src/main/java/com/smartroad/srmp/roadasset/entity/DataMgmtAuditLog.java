package com.smartroad.srmp.roadasset.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("data_mgmt_audit_log")
public class DataMgmtAuditLog {
    @TableId(type = IdType.ASSIGN_UUID)
    private String id;
    private String tenantId;
    private String projectId;
    private String projectName;
    private String operationType;
    private String operator;
    private LocalDateTime operatedAt;
    private String result;
    private String reason;
    private String snapshotBefore;
    private String snapshotAfter;
    private LocalDateTime createdAt;
    private Boolean deleted;
}
