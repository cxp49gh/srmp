package com.smartroad.srmp.roadasset.vo;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;

@Data
public class ImportNetworkResultVO {
    private int insertedCount;
    private int updatedCount;
    private int skippedCount;
    private List<String> warnings = new ArrayList<>();
}
