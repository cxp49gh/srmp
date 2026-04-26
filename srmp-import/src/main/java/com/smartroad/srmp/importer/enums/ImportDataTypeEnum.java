package com.smartroad.srmp.importer.enums;

public enum ImportDataTypeEnum {
    ROAD_ROUTE,
    ROAD_SECTION,
    EVALUATION_UNIT,
    DISEASE,
    ASSESSMENT,
    INDEX_RESULT;

    public static ImportDataTypeEnum of(String code) {
        if (code == null) throw new IllegalArgumentException("dataType不能为空");
        return ImportDataTypeEnum.valueOf(code.trim().toUpperCase());
    }
}