package com.smartroad.srmp.importer.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import java.time.LocalDateTime;

@Data
@TableName("data_import_task")
public class DataImportTask {
    private String id;
    private String tenantId;
    private String importCode;
    private String importName;
    private String dataType;
    private String fileId;
    private String status;
    private Integer totalCount;
    private Integer successCount;
    private Integer failedCount;
    private String errorMessage;
    private String createdBy;
    private LocalDateTime startedAt;
    private LocalDateTime finishedAt;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private Boolean deleted;
}