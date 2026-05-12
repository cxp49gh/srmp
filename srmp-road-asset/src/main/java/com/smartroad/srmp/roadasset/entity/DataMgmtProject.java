package com.smartroad.srmp.roadasset.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.smartroad.srmp.common.core.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("data_mgmt_project")
public class DataMgmtProject extends BaseEntity {
    @TableId(type = IdType.ASSIGN_UUID)
    private String id;
    private String name;
    private String remark;
}
