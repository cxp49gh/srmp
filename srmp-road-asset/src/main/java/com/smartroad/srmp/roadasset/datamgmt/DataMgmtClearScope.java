package com.smartroad.srmp.roadasset.datamgmt;

/**
 * 数据管理—按项目清除业务数据的范围，与 {@link DataImportType} 对齐并增加 {@link #ALL}；
 * 清除方式为物理删除表中匹配 {@code tenant_id + project_id} 的行。
 */
public final class DataMgmtClearScope {

    /** 路网 + 路段 + 病害 + 本项目导入流水（物理删除） */
    public static final String ALL = "ALL";

    private DataMgmtClearScope() {}

    public static boolean isKnown(String scope) {
        if (scope == null) {
            return false;
        }
        if (ALL.equals(scope)) {
            return true;
        }
        return DataImportType.ROAD_NETWORK.equals(scope)
                || DataImportType.SECTION_PACKAGE.equals(scope)
                || DataImportType.DISEASE_EXCEL.equals(scope);
    }
}
