package com.smartroad.srmp.roadasset.vo;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;

@Data
public class ImportSectionPackageResultVO {
    private int routeSectionCount;
    private int evaluationUnitCount;
    private int assessmentRowCount;
    private List<String> ignoredShapefileGroups = new ArrayList<>();
    private long durationMs;
    private List<String> warnings = new ArrayList<>();
}
