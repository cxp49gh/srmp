package com.smartroad.srmp.roadasset.service.impl;

import com.alibaba.excel.EasyExcel;
import com.alibaba.excel.context.AnalysisContext;
import com.alibaba.excel.event.AnalysisEventListener;
import com.alibaba.excel.exception.ExcelAnalysisException;
import com.smartroad.srmp.common.exception.BizException;
import com.smartroad.srmp.disease.dto.DiseaseSaveDTO;
import com.smartroad.srmp.disease.service.DiseaseRecordService;
import com.smartroad.srmp.disease.service.DiseaseTypeService;
import com.smartroad.srmp.disease.vo.DiseaseTypeVO;
import com.smartroad.srmp.roadasset.mapper.RoadRouteMapper;
import com.smartroad.srmp.roadasset.service.DiseaseExcelImportService;
import com.smartroad.srmp.roadasset.vo.ImportDiseaseExcelResultVO;
import com.smartroad.srmp.tenant.context.TenantContextHolder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import javax.annotation.Resource;
import java.io.InputStream;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 病害 Excel 导入：EasyExcel 按行读取（避免整簿 DOM）；表头在第 5 行、数据自第 6 行起；
 * 内存中每积累 {@link #PERSIST_BATCH_SIZE} 条即调用 {@code create} 落库，同一事务内直至读完。
 */
@Service
public class DiseaseExcelImportServiceImpl implements DiseaseExcelImportService {

    private static final Logger log = LoggerFactory.getLogger(DiseaseExcelImportServiceImpl.class);

    /** 每批持久化条数（仅控制内存与单次循环量，不要求用户拆文件）；解析进度日志与此对齐 */
    private static final int PERSIST_BATCH_SIZE = 5000;
    private static final String SOURCE_EXCEL = "EXCEL_IMPORT";
    private static final String DEFAULT_CATEGORY = "未分类";
    private static final String DEFAULT_TYPE = "EXCEL_IMPORT";

    /** Excel 表头所在物理行号（1-based） */
    private static final int HEADER_ROW_ONE_BASED = 5;
    /** 首条数据行物理行号（1-based） */
    private static final int FIRST_DATA_ROW_ONE_BASED = 6;
    private static final int HEADER_ROW_INDEX_0 = HEADER_ROW_ONE_BASED - 1;
    private static final int FIRST_DATA_ROW_INDEX_0 = FIRST_DATA_ROW_ONE_BASED - 1;

    /** 第 5 行表头列名（与 .feature/导入病害数据-需求说明.md 一致） */
    private static final String[] HEADER = {
            "序号", "路线编码", "方向", "桩号", "路面材质", "经度", "纬度", "高度", "病害名称",
            "长度(m)", "宽度(m)", "面积(㎡)", "备注", "图片地址"
    };

    /** 桩号区间分隔：～ ~ — （不含普通连字符，避免与 K 桩表达式混淆） */
    private static final Pattern RANGE_SPLIT = Pattern.compile("[\\uFF5E~\\u2014]");
    private static final Pattern K_STAKE = Pattern.compile("(?i)k?\\s*(\\d+)\\s*\\+\\s*(\\d+)");

    @Resource
    private RoadRouteMapper roadRouteMapper;
    @Resource
    private DiseaseRecordService diseaseRecordService;
    @Resource
    private DiseaseTypeService diseaseTypeService;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public ImportDiseaseExcelResultVO importExcel(MultipartFile file) {
        String tenantId = TenantContextHolder.getTenantId();
        String displayName = file != null ? file.getOriginalFilename() : null;
        long sizeBytes = file != null ? file.getSize() : 0;
        log.info("[disease-excel-import] 开始 tenant={} file={} size={} bytes", tenantId, displayName, sizeBytes);

        if (file == null || file.isEmpty()) {
            log.warn("[disease-excel-import] 拒绝 tenant={} reason=空文件", tenantId);
            throw new BizException("导入文件不能为空");
        }
        String original = file.getOriginalFilename() == null ? "" : file.getOriginalFilename().toLowerCase(Locale.ROOT);
        if (!original.endsWith(".xlsx")) {
            log.warn("[disease-excel-import] 拒绝 tenant={} file={} reason=非xlsx", tenantId, displayName);
            throw new BizException("仅支持 .xlsx 格式");
        }

        long t0 = System.currentTimeMillis();
        try {
            ImportDiseaseExcelResultVO vo = readAndPersistInBatches(file);
            log.info("[disease-excel-import] 完成 tenant={} file={} insertedCount={} skippedMissingRoute={} durationMs={}",
                    tenantId, displayName, vo.getInsertedCount(), vo.getSkippedMissingRouteCount(),
                    System.currentTimeMillis() - t0);
            return vo;
        } catch (BizException e) {
            int detailCount = e.getDetails() == null ? 0 : e.getDetails().size();
            log.warn("[disease-excel-import] 业务失败 tenant={} file={} msg={} detailCount={}",
                    tenantId, displayName, e.getMessage(), detailCount);
            throw e;
        } catch (Exception e) {
            log.error("[disease-excel-import] 失败 tenant={} file={}", tenantId, displayName, e);
            throw e;
        }
    }

    /**
     * 流式读行；每 {@link #PERSIST_BATCH_SIZE} 条调用一次 {@link DiseaseRecordService#createBatch}，
     * 与 {@link #importExcel} 同一事务；存在行级校验错误时整批回滚。
     *
     * @return 写入条数与因路网无此路线编码而跳过的行数
     */
    private ImportDiseaseExcelResultVO readAndPersistInBatches(MultipartFile file) {
        String tenantId = TenantContextHolder.getTenantId();
        String logFile = file.getOriginalFilename();
        List<DiseaseTypeVO> dict = diseaseTypeService.listEnabledForImport();
        List<DiseaseSaveDTO> batch = new ArrayList<>(PERSIST_BATCH_SIZE);
        List<String> rowErrors = new ArrayList<>();
        Set<String> missingRouteCodes = new HashSet<>();
        AtomicInteger skippedNoRouteRows = new AtomicInteger(0);
        Map<String, String> routeIdCache = new HashMap<>();
        AtomicInteger totalInserted = new AtomicInteger(0);
        AtomicInteger dataRowsTouched = new AtomicInteger(0);

        log.info("[disease-excel-import] 开始读表 tenant={} file={} 分批/进度间隔={} 行",
                tenantId, logFile, PERSIST_BATCH_SIZE);

        try (InputStream in = file.getInputStream()) {
            EasyExcel.read(in, new AnalysisEventListener<Map<Integer, String>>() {
                @Override
                public void invoke(Map<Integer, String> data, AnalysisContext context) {
                    int rowIndex = context.readRowHolder().getRowIndex();
                    int excelRow = rowIndex + 1;
                    if (rowIndex == HEADER_ROW_INDEX_0) {
                        validateHeaderRow(data, excelRow);
                        log.info("[disease-excel-import] 表头通过 tenant={} file={} 开始解析数据区（自第 {} 行）",
                                tenantId, logFile, FIRST_DATA_ROW_ONE_BASED);
                        return;
                    }
                    if (rowIndex < FIRST_DATA_ROW_INDEX_0) {
                        return;
                    }
                    if (isRowEmpty(data)) {
                        return;
                    }
                    int touched = dataRowsTouched.incrementAndGet();
                    if (touched % PERSIST_BATCH_SIZE == 0) {
                        log.info("[disease-excel-import] 进度 tenant={} file={} 已处理数据区非空行≈{} 当前已入库={}",
                                tenantId, logFile, touched, totalInserted.get());
                    }
                    try {
                        DiseaseSaveDTO dto = buildDto(data, dict, routeIdCache, missingRouteCodes, skippedNoRouteRows);
                        if (dto == null) {
                            return;
                        }
                        batch.add(dto);
                        if (batch.size() >= PERSIST_BATCH_SIZE) {
                            persistBatch(batch, totalInserted, tenantId, logFile);
                        }
                    } catch (BizException e) {
                        rowErrors.add("第" + excelRow + "行: " + e.getMessage());
                    }
                }

                @Override
                public void doAfterAllAnalysed(AnalysisContext context) {
                    persistBatch(batch, totalInserted, tenantId, logFile);
                    if (!rowErrors.isEmpty()) {
                        throw new BizException("病害 Excel 校验失败", rowErrors);
                    }
                    if (!missingRouteCodes.isEmpty()) {
                        List<String> sorted = new ArrayList<>(missingRouteCodes);
                        Collections.sort(sorted);
                        log.warn("[disease-excel-import] 路网中无此路线编码，已跳过 {} 行（去重编码 {} 个）codes={}",
                                skippedNoRouteRows.get(), missingRouteCodes.size(), sorted);
                    }
                    if (totalInserted.get() == 0 && dataRowsTouched.get() == 0) {
                        throw new BizException("未解析到有效数据行（请确认表头在第 "
                                + HEADER_ROW_ONE_BASED + " 行、数据从第 " + FIRST_DATA_ROW_ONE_BASED + " 行开始填写）");
                    }
                }
            }).sheet().headRowNumber(0).doRead();
        } catch (BizException e) {
            throw e;
        } catch (ExcelAnalysisException e) {
            Throwable c = e.getCause();
            if (c instanceof BizException) {
                throw (BizException) c;
            }
            throw new BizException(e.getMessage());
        } catch (Exception e) {
            Throwable c = e;
            while (c != null) {
                if (c instanceof BizException) {
                    throw (BizException) c;
                }
                c = c.getCause();
            }
            log.warn("[disease-excel-import] 读取异常 tenant={} msg={}", TenantContextHolder.getTenantId(), e.getMessage());
            throw new BizException("读取 Excel 失败：" + e.getMessage());
        }
        log.info("[disease-excel-import] 解析结束 tenant={} file={} 数据区非空行≈{} 已入库={} 因路网无路线跳过={}",
                tenantId, logFile, dataRowsTouched.get(), totalInserted.get(), skippedNoRouteRows.get());
        ImportDiseaseExcelResultVO result = new ImportDiseaseExcelResultVO();
        result.setInsertedCount(totalInserted.get());
        result.setSkippedMissingRouteCount(skippedNoRouteRows.get());
        return result;
    }

    private void persistBatch(List<DiseaseSaveDTO> batch, AtomicInteger totalInserted, String tenantId, String logFile) {
        if (batch.isEmpty()) {
            return;
        }
        int n = batch.size();
        // 批量 INSERT（Mapper 内再按块切分），避免逐条 create 导致 N 次数据库往返
        diseaseRecordService.createBatch(batch);
        totalInserted.addAndGet(n);
        log.info("[disease-excel-import] 进度 tenant={} file={} 本批入库={} 条 累计已入库={} 条",
                tenantId, logFile, n, totalInserted.get());
        batch.clear();
    }

    /**
     * 按 Excel 中的路线编码在路网中解析 {@code road_route.id}。部分导出在编码末尾附带上下行标识
     *（常见为 {@code AB}，或单侧 {@code A}/{@code B}，大小写不敏感），而路网表 {@code route_code} 通常不含该后缀，
     * 故在原文无命中时再按去后缀后的候选依次查询。
     */
    private String lookupRouteIdInNetwork(String tenantId, String routeCodeFromExcel) {
        for (String candidate : expandRouteCodeLookupKeys(routeCodeFromExcel)) {
            if (candidate.isEmpty()) {
                continue;
            }
            String id = roadRouteMapper.selectIdByTenantAndRouteCode(tenantId, candidate);
            if (id != null) {
                return id;
            }
        }
        return null;
    }

    /** 原文 + 至多两次「剥末尾上下行后缀」，保持顺序、去重 */
    private static List<String> expandRouteCodeLookupKeys(String routeCode) {
        List<String> keys = new ArrayList<>(3);
        String t = routeCode == null ? "" : routeCode.trim();
        if (!t.isEmpty()) {
            keys.add(t);
        }
        String s1 = stripOneLevelDirectionSuffix(t);
        if (!s1.isEmpty() && !s1.equals(t)) {
            keys.add(s1);
        }
        String s2 = stripOneLevelDirectionSuffix(s1);
        if (!s2.isEmpty() && !s2.equals(s1) && !keys.contains(s2)) {
            keys.add(s2);
        }
        return keys;
    }

    /**
     * 剥一层末尾上下行后缀：优先 {@code AB}，否则单独末尾 {@code A}/{@code B}（与「上行/下行」单字母习惯一致）。
     */
    private static String stripOneLevelDirectionSuffix(String routeCode) {
        if (routeCode == null) {
            return "";
        }
        String s = routeCode.trim();
        int n = s.length();
        if (n < 2) {
            return s;
        }
        String u = s.toUpperCase(Locale.ROOT);
        if (u.endsWith("AB")) {
            return s.substring(0, n - 2).trim();
        }
        if (u.endsWith("A") || u.endsWith("B")) {
            return s.substring(0, n - 1).trim();
        }
        return s;
    }

    /** 反复剥末尾 A/B/AB，得到与路网 {@code route_code} 一致的入库编码 */
    private static String stripAllDirectionSuffixes(String routeCode) {
        String s = routeCode == null ? "" : routeCode.trim();
        String t;
        while (!(t = stripOneLevelDirectionSuffix(s)).equals(s)) {
            s = t;
        }
        return s;
    }

    /**
     * 若路线编码末尾带上下行后缀，则映射为库内 {@code direction}：{@code A}→上行，{@code B}→下行，{@code AB}→双向；
     * 仅识别「与剥一层后缀后变短」的情况，避免误把正常以 A/B 结尾的编号当成上下行。
     */
    private static String inferDirectionFromRouteSuffixTail(String rawRouteCode) {
        if (rawRouteCode == null || rawRouteCode.trim().isEmpty()) {
            return null;
        }
        String s = rawRouteCode.trim();
        String stripped = stripOneLevelDirectionSuffix(s);
        if (stripped.equals(s)) {
            return null;
        }
        String u = s.toUpperCase(Locale.ROOT);
        if (u.endsWith("AB")) {
            return "BOTH";
        }
        if (u.endsWith("A")) {
            return "UP";
        }
        if (u.endsWith("B")) {
            return "DOWN";
        }
        return null;
    }

    private void validateHeaderRow(Map<Integer, String> data, int excelRow) {
        List<String> bad = new ArrayList<>();
        for (int i = 0; i < HEADER.length; i++) {
            String expected = normHeader(HEADER[i]);
            String actual = normHeader(cell(data, i));
            if (!expected.equals(actual)) {
                bad.add("第" + excelRow + "行第" + (i + 1) + "列: 期望「" + HEADER[i] + "」，实际「" + cell(data, i) + "」");
            }
        }
        if (!bad.isEmpty()) {
            throw new BizException("表头不匹配，请使用标准模板", bad);
        }
        log.debug("[disease-excel-import] 表头校验通过");
    }

    /**
     * @param missingRouteCodes 路网中不存在的路线编码（去重，仅用于结束时的 WARN 汇总）
     * @param skippedNoRouteRows 因路线不存在而跳过的行数累计
     * @return 可入库的 DTO；若路线在路网中不存在则返回 {@code null}（跳过该行，不视为整文件失败）
     */
    private DiseaseSaveDTO buildDto(Map<Integer, String> data, List<DiseaseTypeVO> dict,
                                    Map<String, String> routeIdCache, Set<String> missingRouteCodes,
                                    AtomicInteger skippedNoRouteRows) {
        String routeCode = cell(data, 1);
        if (routeCode.isEmpty()) {
            throw new BizException("路线编码不能为空");
        }
        String diseaseName = cell(data, 8);
        if (diseaseName.isEmpty()) {
            throw new BizException("病害名称不能为空");
        }

        String tenantId = TenantContextHolder.getTenantId();
        String routeId = routeIdCache.computeIfAbsent(routeCode, c -> lookupRouteIdInNetwork(tenantId, c));
        if (routeId == null) {
            missingRouteCodes.add(routeCode);
            skippedNoRouteRows.incrementAndGet();
            return null;
        }

        String lonStr = cell(data, 5);
        String latStr = cell(data, 6);
        if (lonStr.isEmpty() || latStr.isEmpty()) {
            throw new BizException("缺少有效经纬度");
        }
        BigDecimal lon = parseDecimal(lonStr, "经度");
        BigDecimal lat = parseDecimal(latStr, "纬度");
        if (lon.compareTo(new BigDecimal("-180")) < 0 || lon.compareTo(new BigDecimal("180")) > 0) {
            throw new BizException("经度超出范围");
        }
        if (lat.compareTo(new BigDecimal("-90")) < 0 || lat.compareTo(new BigDecimal("90")) > 0) {
            throw new BizException("纬度超出范围");
        }
        String geomWkt = "POINT(" + lon.stripTrailingZeros().toPlainString() + " " + lat.stripTrailingZeros().toPlainString() + ")";

        String directionFromSuffix = inferDirectionFromRouteSuffixTail(routeCode);
        String direction = directionFromSuffix != null
                ? directionFromSuffix
                : normalizeDirection(cell(data, 2));
        BigDecimal[] stakes = parseStake(cell(data, 3));

        String pavement = cell(data, 4);
        String remarkUser = cell(data, 12);
        String imageUrl = cell(data, 13);
        String remark = buildRemark(pavement, remarkUser, imageUrl);

        BigDecimal damageLength = parseOptionalDecimal(cell(data, 9), "长度(m)");
        BigDecimal damageWidth = parseOptionalDecimal(cell(data, 10), "宽度(m)");
        BigDecimal damageArea = parseOptionalDecimal(cell(data, 11), "面积(㎡)");
        BigDecimal damageDepth = parseOptionalDecimal(cell(data, 7), "高度");

        BigDecimal quantity = null;
        String measureUnit = null;
        if (damageArea != null) {
            quantity = damageArea;
            measureUnit = "㎡";
        } else if (damageLength != null) {
            quantity = damageLength;
            measureUnit = "m";
        }

        DiseaseCategoryType ct = matchDict(diseaseName, dict);

        DiseaseSaveDTO dto = new DiseaseSaveDTO();
        dto.setRouteId(routeId);
        dto.setRouteCode(stripAllDirectionSuffixes(routeCode));
        dto.setDirection(direction);
        dto.setStartStake(stakes[0]);
        dto.setEndStake(stakes[1]);
        dto.setDiseaseCategory(ct.category);
        dto.setDiseaseType(ct.type);
        dto.setDiseaseName(diseaseName);
        dto.setSeverity(null);
        dto.setQuantity(quantity);
        dto.setMeasureUnit(measureUnit);
        dto.setDamageArea(damageArea);
        dto.setDamageLength(damageLength);
        dto.setDamageWidth(damageWidth);
        dto.setDamageDepth(damageDepth);
        dto.setSource(SOURCE_EXCEL);
        dto.setConfidence(null);
        dto.setStatus("UNPROCESSED");
        dto.setGeomWkt(geomWkt);
        dto.setRemark(remark.isEmpty() ? null : remark);
        dto.setTaskId(null);
        dto.setSectionId(null);
        dto.setUnitId(null);
        dto.setLaneNo(null);
        return dto;
    }

    private static class DiseaseCategoryType {
        final String category;
        final String type;

        DiseaseCategoryType(String category, String type) {
            this.category = category;
            this.type = type;
        }
    }

    private DiseaseCategoryType matchDict(String diseaseName, List<DiseaseTypeVO> dict) {
        String n = diseaseName.trim();
        for (DiseaseTypeVO d : dict) {
            if (d.getDiseaseName() != null && n.equalsIgnoreCase(d.getDiseaseName().trim())) {
                return new DiseaseCategoryType(
                        safe(d.getDiseaseCategory(), DEFAULT_CATEGORY),
                        pickTypeCode(d));
            }
        }
        for (DiseaseTypeVO d : dict) {
            if (d.getDiseaseCode() != null && n.equalsIgnoreCase(d.getDiseaseCode().trim())) {
                return new DiseaseCategoryType(
                        safe(d.getDiseaseCategory(), DEFAULT_CATEGORY),
                        pickTypeCode(d));
            }
        }
        String lower = n.toLowerCase(Locale.ROOT);
        List<DiseaseTypeVO> fuzzy = new ArrayList<>();
        for (DiseaseTypeVO d : dict) {
            if (d.getDiseaseName() == null) {
                continue;
            }
            String dn = d.getDiseaseName().toLowerCase(Locale.ROOT);
            if (dn.contains(lower) || lower.contains(dn)) {
                fuzzy.add(d);
            }
        }
        if (fuzzy.size() == 1) {
            DiseaseTypeVO d = fuzzy.get(0);
            return new DiseaseCategoryType(safe(d.getDiseaseCategory(), DEFAULT_CATEGORY), pickTypeCode(d));
        }
        return new DiseaseCategoryType(DEFAULT_CATEGORY, DEFAULT_TYPE);
    }

    private static String pickTypeCode(DiseaseTypeVO d) {
        if (d.getDiseaseCode() != null && !d.getDiseaseCode().trim().isEmpty()) {
            return d.getDiseaseCode().trim();
        }
        if (d.getDiseaseName() != null && !d.getDiseaseName().trim().isEmpty()) {
            return d.getDiseaseName().trim();
        }
        return DEFAULT_TYPE;
    }

    private static String safe(String v, String dft) {
        return v == null || v.trim().isEmpty() ? dft : v.trim();
    }

    private static String buildRemark(String pavement, String remarkUser, String imageUrl) {
        StringBuilder sb = new StringBuilder();
        if (pavement != null && !pavement.trim().isEmpty()) {
            sb.append("路面材质:").append(pavement.trim()).append(";");
        }
        if (remarkUser != null && !remarkUser.trim().isEmpty()) {
            if (sb.length() > 0) {
                sb.append(" ");
            }
            sb.append(remarkUser.trim());
        }
        if (imageUrl != null && !imageUrl.trim().isEmpty()) {
            if (sb.length() > 0) {
                sb.append(" ");
            }
            sb.append("图片:").append(imageUrl.trim());
        }
        return sb.toString().trim();
    }

    private static String normalizeDirection(String raw) {
        if (raw == null || raw.trim().isEmpty()) {
            throw new BizException("方向不能为空");
        }
        String s = raw.trim();
        String u = s.toUpperCase(Locale.ROOT);
        if ("UP".equals(u) || "上行".equals(s)) {
            return "UP";
        }
        if ("DOWN".equals(u) || "下行".equals(s)) {
            return "DOWN";
        }
        if ("BOTH".equals(u) || "双向".equals(s) || "全幅".equals(s)) {
            return "BOTH";
        }
        if ("UP".equals(s) || "DOWN".equals(s) || "BOTH".equals(s)) {
            return s;
        }
        throw new BizException("方向无法识别: " + s);
    }

    private BigDecimal[] parseStake(String raw) {
        if (raw == null || raw.trim().isEmpty()) {
            throw new BizException("桩号不能为空");
        }
        String s = raw.trim();
        String[] parts = RANGE_SPLIT.split(s, 2);
        if (parts.length == 2) {
            BigDecimal a = parseStakeSingle(parts[0].trim());
            BigDecimal b = parseStakeSingle(parts[1].trim());
            if (a.compareTo(b) <= 0) {
                return new BigDecimal[]{a, b};
            }
            return new BigDecimal[]{b, a};
        }
        BigDecimal one = parseStakeSingle(s);
        return new BigDecimal[]{one, one};
    }

    private BigDecimal parseStakeSingle(String s) {
        Matcher km = K_STAKE.matcher(s);
        if (km.matches()) {
            BigDecimal kmPart = new BigDecimal(km.group(1));
            BigDecimal mPart = new BigDecimal(km.group(2));
            return kmPart.add(mPart.divide(new BigDecimal("1000"), 3, RoundingMode.HALF_UP));
        }
        try {
            return new BigDecimal(s.replace(",", "").trim());
        } catch (Exception e) {
            throw new BizException("桩号无法解析: " + s);
        }
    }

    private static BigDecimal parseDecimal(String s, String label) {
        try {
            BigDecimal v = new BigDecimal(s.replace(",", "").trim());
            if (!Double.isFinite(v.doubleValue())) {
                throw new BizException(label + " 不是有效数字");
            }
            return v;
        } catch (BizException e) {
            throw e;
        } catch (Exception e) {
            throw new BizException(label + " 不是有效数字");
        }
    }

    private static BigDecimal parseOptionalDecimal(String s, String label) {
        if (s == null || s.trim().isEmpty()) {
            return null;
        }
        try {
            return new BigDecimal(s.replace(",", "").trim());
        } catch (Exception e) {
            throw new BizException(label + " 不是有效数字");
        }
    }

    private static String cell(Map<Integer, String> data, int col) {
        String v = data.get(col);
        return v == null ? "" : v.trim();
    }

    private static boolean isRowEmpty(Map<Integer, String> data) {
        for (int i = 0; i < HEADER.length; i++) {
            if (!cell(data, i).isEmpty()) {
                return false;
            }
        }
        return true;
    }

    private static String normHeader(String s) {
        if (s == null) {
            return "";
        }
        String t = s.replace('\u3000', ' ')
                .replace('（', '(')
                .replace('）', ')')
                .trim()
                .toLowerCase(Locale.ROOT);
        return t.replaceAll("\\s+", "");
    }
}
