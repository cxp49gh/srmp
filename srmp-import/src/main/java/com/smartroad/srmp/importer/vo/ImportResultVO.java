package com.smartroad.srmp.importer.vo;

import lombok.Data;
import java.util.ArrayList;
import java.util.List;

@Data
public class ImportResultVO {
    private String importTaskId;
    private String importCode;
    private String dataType;
    private String status;
    private Integer totalCount = 0;
    private Integer successCount = 0;
    private Integer failedCount = 0;
    private List<String> errors = new ArrayList<>();
}