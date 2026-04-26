package com.smartroad.srmp.importer.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import java.time.LocalDateTime;

@Data
@TableName("data_import_error_log")
public class DataImportErrorLog {
    private String id;
    private String tenantId;
    private String importTaskId;
    private Integer rowNo;
    private String fieldName;
    private String fieldValue;
    private String errorType;
    private String errorMessage;
    private LocalDateTime createdAt;
}