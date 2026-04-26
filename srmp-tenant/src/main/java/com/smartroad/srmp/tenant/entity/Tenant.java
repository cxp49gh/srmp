package com.smartroad.srmp.tenant.entity;

import com.smartroad.srmp.common.core.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
public class Tenant extends BaseEntity {
    private String tenantCode;
    private String tenantName;
    private String tenantType;
    private String contactName;
    private String contactPhone;
    private String status;
    private String remark;
}
