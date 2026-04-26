package com.smartroad.srmp.disease.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.smartroad.srmp.common.core.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("disease_type_dict")
public class DiseaseTypeDict extends BaseEntity {
    @TableId(type = IdType.ASSIGN_UUID)
    private String id;
    private String diseaseCode;
    private String diseaseName;
    private String diseaseCategory;
    private String measureUnit;
    private String relatedIndex;
    private Boolean severityEnabled;
    private Boolean enabled;
    private Integer sortNo;
    private String remark;
}
