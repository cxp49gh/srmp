package com.smartroad.srmp.roadasset.entity;

import com.baomidou.mybatisplus.annotation.*;
import com.smartroad.srmp.common.core.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;
import java.math.BigDecimal;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("road_section_ledger")
public class RoadEvaluationUnit extends BaseEntity {
    @TableId(type = IdType.ASSIGN_UUID)
    private String id;
    private String routeId;
    /** 对应库列 line_id，挂接线路级路段 */
    @TableField("line_id")
    private String sectionId;
    private String routeCode;
    /** 对应库列 ledger_code */
    @TableField("ledger_code")
    private String unitCode;
    private String ledgerName;
    /** 数据管理项目冗余 */
    private String projectId;
    private String direction;
    private Integer laneNo;
    private BigDecimal startStake;
    private BigDecimal endStake;
    private Integer lengthM;
    private String pavementType;
    private String technicalGrade;
    private BigDecimal roadWidth;
    private String adcode;
    private String manageOrgId;
    @TableField(exist = false)
    private String geomWkt;
    @TableField(exist = false)
    private String centerPointWkt;
    @TableField(exist = false)
    private String geomGeoJson;
    @TableField(exist = false)
    private String centerPointGeoJson;
}
