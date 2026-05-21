package com.smartroad.srmp.roadasset.vo;

import lombok.Data;
import lombok.EqualsAndHashCode;

import java.util.ArrayList;
import java.util.List;

@Data
@EqualsAndHashCode(callSuper = true)
public class DataImportRecordDetailVO extends DataImportRecordVO {
    private String projectName;
    private String uploadedBy;
    private Object resultStats;
    private List<FailureDetailVO> failureDetails = new ArrayList<>();
    private List<String> warnings = new ArrayList<>();
    private String technicalInfo;

    @Data
    public static class FailureDetailVO {
        private Integer rowNo;
        private String field;
        private String reason;
        private String fileName;
    }
}
