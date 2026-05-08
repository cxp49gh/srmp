package com.smartroad.srmp.roadasset.service.impl;

import com.smartroad.srmp.assessment.dto.AssessmentResultSaveDTO;
import com.smartroad.srmp.assessment.service.AssessmentResultService;
import com.smartroad.srmp.common.exception.BizException;
import com.smartroad.srmp.roadasset.dto.EvaluationUnitSaveDTO;
import com.smartroad.srmp.roadasset.dto.RoadSectionSaveDTO;
import com.smartroad.srmp.roadasset.mapper.RoadEvaluationUnitMapper;
import com.smartroad.srmp.roadasset.mapper.RoadRouteMapper;
import com.smartroad.srmp.roadasset.mapper.RoadSectionMapper;
import com.smartroad.srmp.roadasset.service.RoadEvaluationUnitService;
import com.smartroad.srmp.roadasset.service.RoadSectionPackageImportService;
import com.smartroad.srmp.roadasset.service.RoadSectionService;
import com.smartroad.srmp.roadasset.vo.ImportSectionPackageResultVO;
import com.smartroad.srmp.tenant.context.TenantContextHolder;
import org.apache.commons.compress.archivers.tar.TarArchiveEntry;
import org.apache.commons.compress.archivers.tar.TarArchiveInputStream;
import org.geotools.data.DataStore;
import org.geotools.data.DataStoreFinder;
import org.geotools.data.simple.SimpleFeatureCollection;
import org.geotools.data.simple.SimpleFeatureIterator;
import org.geotools.data.simple.SimpleFeatureSource;
import org.geotools.geometry.jts.JTS;
import org.geotools.referencing.CRS;
import org.geotools.referencing.crs.DefaultGeographicCRS;
import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.CoordinateSequence;
import org.locationtech.jts.geom.Geometry;
import org.locationtech.jts.geom.GeometryFactory;
import org.locationtech.jts.geom.LineString;
import org.locationtech.jts.geom.MultiLineString;
import org.locationtech.jts.linearref.LengthIndexedLine;
import org.opengis.feature.simple.SimpleFeature;
import org.opengis.feature.type.AttributeDescriptor;
import org.opengis.feature.type.GeometryDescriptor;
import org.opengis.referencing.crs.CoordinateReferenceSystem;
import org.opengis.referencing.crs.SingleCRS;
import org.opengis.referencing.operation.MathTransform;
import org.springframework.beans.factory.annotation.Value;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import javax.annotation.Resource;
import java.io.BufferedInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.security.MessageDigest;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.stream.Stream;

@Service
public class RoadSectionPackageImportServiceImpl implements RoadSectionPackageImportService {

    private static final Logger log = LoggerFactory.getLogger(RoadSectionPackageImportServiceImpl.class);

    private static final long MAX_EXTRACT_BYTES = 500L * 1024 * 1024;
    private static final int SECTION_CODE_MAX = 100;
    private static final int UNIT_CODE_MAX = 100;

    @Resource
    private RoadRouteMapper roadRouteMapper;
    @Resource
    private RoadSectionMapper roadSectionMapper;
    @Resource
    private RoadEvaluationUnitMapper roadEvaluationUnitMapper;
    @Resource
    private RoadSectionService roadSectionService;
    @Resource
    private RoadEvaluationUnitService roadEvaluationUnitService;
    @Resource
    private AssessmentResultService assessmentResultService;

    @Value("${srmp.import.section.dbf-charset:UTF-8}")
    private String shapefileDbfCharset;

    private enum Tier {
        SECTION, LEDGER, IGNORE, UNKNOWN
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public ImportSectionPackageResultVO importPackage(MultipartFile file) {
        long t0 = System.currentTimeMillis();
        if (file == null || file.isEmpty()) {
            throw new BizException("导入文件不能为空");
        }
        String original = file.getOriginalFilename();
        if (original == null || !original.toLowerCase(Locale.ROOT).endsWith(".tar")) {
            throw new BizException("仅支持 .tar 格式");
        }
        String tenantId = TenantContextHolder.getTenantId();
        log.info("[section-import] 开始 tenant={} file={} size={} bytes", tenantId, original, file.getSize());
        Path tempDir = null;
        try {
            tempDir = Files.createTempDirectory("srmp-section-import-");
            log.debug("[section-import] 解压目录 {}", tempDir.toAbsolutePath());
            try (InputStream in = new BufferedInputStream(file.getInputStream())) {
                safeUntar(in, tempDir);
            }
            ImportSectionPackageResultVO vo = importFromExtracted(tempDir, t0);
            log.info("[section-import] 完成 tenant={} sections={} units={} assessments={} durationMs={} warningCount={}",
                    tenantId,
                    vo.getRouteSectionCount(),
                    vo.getEvaluationUnitCount(),
                    vo.getAssessmentRowCount(),
                    vo.getDurationMs(),
                    vo.getWarnings() == null ? 0 : vo.getWarnings().size());
            return vo;
        } catch (BizException e) {
            log.warn("[section-import] 业务失败 tenant={} file={} msg={}", tenantId, original, e.getMessage());
            throw e;
        } catch (Exception e) {
            log.error("[section-import] 失败 tenant={} file={}", tenantId, original, e);
            throw new BizException("路段包导入失败：" + e.getMessage());
        } finally {
            if (tempDir != null) {
                deleteRecursivelyQuiet(tempDir);
            }
        }
    }

    private ImportSectionPackageResultVO importFromExtracted(Path root, long t0) throws Exception {
        ImportSectionPackageResultVO result = new ImportSectionPackageResultVO();
        List<String> warnings = new ArrayList<>();
        List<Path> sectionShps = new ArrayList<>();
        List<Path> ledgerShps = new ArrayList<>();
        List<String> ignored = new ArrayList<>();

        List<Path> allShp = new ArrayList<>();
        try (Stream<Path> walk = Files.walk(root)) {
            walk.filter(p -> p.getFileName().toString().toLowerCase(Locale.ROOT).endsWith(".shp")).forEach(allShp::add);
        }
        if (allShp.isEmpty()) {
            throw new BizException("压缩包内未找到 .shp 文件");
        }
        log.info("[section-import] 发现 .shp 共 {} 个", allShp.size());
        for (Path shp : allShp) {
            if (!isCompleteShapefileGroup(shp)) {
                throw new BizException("缺少 .dbf 或 .shx 或 .prj 文件，无法导入：" + shp.getFileName());
            }
            Tier tier = classifyTier(shp, root);
            if (tier == Tier.UNKNOWN) {
                throw new BizException("存在无法识别的 Shapefile 级别，请按命名约定放置路线级/台账级数据：" + relativize(root, shp));
            }
            if (tier == Tier.IGNORE) {
                ignored.add(relativize(root, shp));
                continue;
            }
            if (tier == Tier.SECTION) {
                sectionShps.add(shp);
            } else {
                ledgerShps.add(shp);
            }
            log.debug("[section-import] 归类 {} -> {} ({})", relativize(root, shp), tier, shp.getFileName());
        }
        log.info("[section-import] 路线级 shp={} 台账级 shp={} 忽略={}",
                sectionShps.size(), ledgerShps.size(), ignored.size());
        if (sectionShps.isEmpty() && ledgerShps.isEmpty()) {
            throw new BizException("压缩包内未找到路线级或台账级 Shapefile");
        }
        result.setIgnoredShapefileGroups(ignored);

        Map<String, String> stakeSectionId = new HashMap<>();
        int secCount = 0;
        int unitCount = 0;
        int arCount = 0;

        for (Path shp : sectionShps) {
            log.info("[section-import] 处理路线级 {}", relativize(root, shp));
            Counts c = processSectionShapefile(shp, stakeSectionId, warnings);
            secCount += c.entities;
            arCount += c.assessments;
        }
        for (Path shp : ledgerShps) {
            log.info("[section-import] 处理台账级 {}", relativize(root, shp));
            Counts c = processLedgerShapefile(shp, stakeSectionId, warnings);
            unitCount += c.entities;
            arCount += c.assessments;
        }

        result.setRouteSectionCount(secCount);
        result.setEvaluationUnitCount(unitCount);
        result.setAssessmentRowCount(arCount);
        result.setWarnings(warnings);
        result.setDurationMs(System.currentTimeMillis() - t0);
        return result;
    }

    private static String relativize(Path root, Path shp) {
        try {
            return root.relativize(shp).toString().replace('\\', '/');
        } catch (Exception e) {
            return shp.toString();
        }
    }

    private static Tier classifyTier(Path shp, Path root) {
        String rel = relativize(root, shp).toLowerCase(Locale.ROOT);
        String base = stripExtension(shp.getFileName().toString()).toLowerCase(Locale.ROOT);
        String hay = rel + "|" + base;
        if (containsAny(hay, "公里", "百米", "km_unit", "hm_unit")) {
            return Tier.IGNORE;
        }
        if (containsAny(hay, "台账级", "evaluation_unit", "eval_unit", "ledger", "评定单元")) {
            return Tier.LEDGER;
        }
        if (containsAny(hay, "路线级", "road_section", "路段")) {
            return Tier.SECTION;
        }
        return Tier.UNKNOWN;
    }

    private static boolean containsAny(String hay, String... needles) {
        for (String n : needles) {
            if (hay.contains(n.toLowerCase(Locale.ROOT))) {
                return true;
            }
        }
        return false;
    }

    private static class Counts {
        int entities;
        int assessments;

        Counts(int entities, int assessments) {
            this.entities = entities;
            this.assessments = assessments;
        }
    }

    private Counts processSectionShapefile(Path shpFile, Map<String, String> stakeSectionId, List<String> warnings) throws Exception {
        String tenantId = TenantContextHolder.getTenantId();
        Map<String, Object> params = new HashMap<>();
        params.put("url", shpFile.toUri().toURL());
        DataStore store = DataStoreFinder.getDataStore(params);
        if (store == null) {
            throw new BizException("无法读取 Shapefile：" + shpFile.getFileName());
        }
        int entities = 0;
        int assessments = 0;
        int skippedUnknownRoute = 0;
        Set<String> seenSectionCodes = new HashSet<>();
        try {
            if (store instanceof org.geotools.data.shapefile.ShapefileDataStore) {
                ((org.geotools.data.shapefile.ShapefileDataStore) store).setCharset(resolveDbfCharset());
            }
            Charset dbfCs = resolveDbfCharset();
            String typeName = store.getTypeNames()[0];
            SimpleFeatureSource source = store.getFeatureSource(typeName);
            CoordinateReferenceSystem sourceCrs = source.getSchema().getCoordinateReferenceSystem();
            if (sourceCrs == null) {
                throw new BizException("缺少 .prj 文件，无法确定坐标系");
            }
            SingleCRS horizontal = CRS.getHorizontalCRS(sourceCrs);
            CoordinateReferenceSystem source2d = horizontal != null ? horizontal : sourceCrs;
            MathTransform toWgs84 = CRS.findMathTransform(source2d, DefaultGeographicCRS.WGS84, true);
            log.info("[section-import] 路线级打开 typeName={} dbfCharset={} crs={}",
                    typeName, dbfCs.name(), source2d.getName());

            SimpleFeatureCollection collection = source.getFeatures();
            int featureHint = collection.size();
            if (featureHint >= 0) {
                log.info("[section-import] 路线级要素数量(估算) {}", featureHint);
            }
            List<String> rowErrors = new ArrayList<>();
            int index = 0;
            try (SimpleFeatureIterator it = collection.features()) {
                while (it.hasNext()) {
                    index++;
                    SimpleFeature feature = it.next();
                    Map<String, Object> attrs = buildAttrMap(feature);
                    try {
                        ParsedRow row = parseRow(attrs, feature, toWgs84, index, shpFile.getFileName().toString());
                        String routeId = roadRouteMapper.selectIdByTenantAndRouteCode(tenantId, row.routeCode);
                        if (routeId == null) {
                            skippedUnknownRoute++;
                            String warn = baseErr(shpFile, index) + "路线编号(linkCode) " + row.routeCode + " 在路网中不存在，已跳过";
                            warnings.add(warn);
                            log.debug("[section-import] {}", warn);
                            continue;
                        }
                        String sectionCode = buildSectionCode(row);
                        if (!seenSectionCodes.add(sectionCode)) {
                            throw new BizException("压缩包内路段编码重复：" + sectionCode);
                        }
                        RoadSectionSaveDTO dto = new RoadSectionSaveDTO();
                        dto.setRouteId(routeId);
                        dto.setRouteCode(row.routeCode);
                        dto.setSectionCode(sectionCode);
                        dto.setSectionName(firstNonBlank(row.routeName, row.routeCode));
                        dto.setDirection(row.direction);
                        dto.setStartStake(row.startStake);
                        dto.setEndStake(row.endStake);
                        dto.setLengthKm(row.lengthM != null
                                ? row.lengthM.divide(BigDecimal.valueOf(1000), 3, RoundingMode.HALF_UP)
                                : null);
                        dto.setPavementType(row.pavementType);
                        dto.setTechnicalGrade(row.technicalGrade);
                        dto.setRoadWidth(row.roadWidth);
                        dto.setAdcode(row.adcode);
                        dto.setManageOrgId(null);
                        dto.setGeomWkt(row.geomWkt);
                        String remark = row.remarkCn;
                        if (row.detectionMethod != null && !row.detectionMethod.trim().isEmpty()) {
                            String tag = "检测方式:" + row.detectionMethod.trim();
                            remark = remark == null || remark.trim().isEmpty() ? tag : tag + " | " + remark;
                        }
                        dto.setRemark(trimRemark(remark));

                        String existingId = roadSectionMapper.selectIdByTenantAndSectionCode(tenantId, sectionCode);
                        String sectionId;
                        if (existingId == null) {
                            sectionId = roadSectionService.create(dto);
                        } else {
                            roadSectionService.update(existingId, dto);
                            sectionId = existingId;
                        }
                        entities++;
                        stakeSectionId.put(stakeKey(row.routeCode, row.direction, row.startStake, row.endStake), sectionId);

                        AssessmentResultSaveDTO ar = buildAssessmentDto(row, "ROAD_SECTION", sectionId, sectionId, null, routeId);
                        assessmentResultService.upsertForImport(ar);
                        assessments++;
                    } catch (BizException ex) {
                        throw ex;
                    } catch (Exception ex) {
                        rowErrors.add(baseErr(shpFile, index) + ex.getMessage());
                    }
                }
            }
            if (!rowErrors.isEmpty()) {
                throw new BizException(joinDetails("路线级数据校验失败", rowErrors));
            }
            log.info("[section-import] 路线级完成 file={} 扫描要素={} 写入路段={} 评定行={} 跳过(无路线)={}",
                    shpFile.getFileName(), index, entities, assessments, skippedUnknownRoute);
            if (skippedUnknownRoute > 0) {
                log.warn("[section-import] 路线级 {} 因路网无对应路线共跳过 {} 条，详见返回 warnings", shpFile.getFileName(), skippedUnknownRoute);
            }
        } finally {
            store.dispose();
        }
        return new Counts(entities, assessments);
    }

    private Counts processLedgerShapefile(Path shpFile, Map<String, String> stakeSectionId, List<String> warnings) throws Exception {
        String tenantId = TenantContextHolder.getTenantId();
        Map<String, Object> params = new HashMap<>();
        params.put("url", shpFile.toUri().toURL());
        DataStore store = DataStoreFinder.getDataStore(params);
        if (store == null) {
            throw new BizException("无法读取 Shapefile：" + shpFile.getFileName());
        }
        int entities = 0;
        int assessments = 0;
        int skippedUnknownRoute = 0;
        Set<String> seenUnitCodes = new HashSet<>();
        try {
            if (store instanceof org.geotools.data.shapefile.ShapefileDataStore) {
                ((org.geotools.data.shapefile.ShapefileDataStore) store).setCharset(resolveDbfCharset());
            }
            Charset dbfCs = resolveDbfCharset();
            String typeName = store.getTypeNames()[0];
            SimpleFeatureSource source = store.getFeatureSource(typeName);
            CoordinateReferenceSystem sourceCrs = source.getSchema().getCoordinateReferenceSystem();
            if (sourceCrs == null) {
                throw new BizException("缺少 .prj 文件，无法确定坐标系");
            }
            SingleCRS horizontal = CRS.getHorizontalCRS(sourceCrs);
            CoordinateReferenceSystem source2d = horizontal != null ? horizontal : sourceCrs;
            MathTransform toWgs84 = CRS.findMathTransform(source2d, DefaultGeographicCRS.WGS84, true);
            log.info("[section-import] 台账级打开 typeName={} dbfCharset={} crs={}",
                    typeName, dbfCs.name(), source2d.getName());

            SimpleFeatureCollection collection = source.getFeatures();
            int featureHint = collection.size();
            if (featureHint >= 0) {
                log.info("[section-import] 台账级要素数量(估算) {}", featureHint);
            }
            List<String> rowErrors = new ArrayList<>();
            int index = 0;
            try (SimpleFeatureIterator it = collection.features()) {
                while (it.hasNext()) {
                    index++;
                    SimpleFeature feature = it.next();
                    Map<String, Object> attrs = buildAttrMap(feature);
                    try {
                        ParsedRow row = parseRow(attrs, feature, toWgs84, index, shpFile.getFileName().toString());
                        String routeId = roadRouteMapper.selectIdByTenantAndRouteCode(tenantId, row.routeCode);
                        if (routeId == null) {
                            skippedUnknownRoute++;
                            String warn = baseErr(shpFile, index) + "路线编号(linkCode) " + row.routeCode + " 在路网中不存在，已跳过";
                            warnings.add(warn);
                            log.debug("[section-import] {}", warn);
                            continue;
                        }
                        String unitCode = buildUnitCode(row);
                        if (!seenUnitCodes.add(unitCode)) {
                            throw new BizException("压缩包内评定单元编码重复：" + unitCode);
                        }
                        String sk = stakeKey(row.routeCode, row.direction, row.startStake, row.endStake);
                        String sectionId = stakeSectionId.get(sk);
                        if (sectionId == null) {
                            sectionId = roadSectionMapper.selectIdByRouteStake(tenantId, row.routeCode, row.direction, row.startStake, row.endStake);
                        }

                        EvaluationUnitSaveDTO dto = new EvaluationUnitSaveDTO();
                        dto.setRouteId(routeId);
                        dto.setSectionId(sectionId);
                        dto.setRouteCode(row.routeCode);
                        dto.setUnitCode(unitCode);
                        dto.setDirection(row.direction);
                        dto.setLaneNo(null);
                        dto.setStartStake(row.startStake);
                        dto.setEndStake(row.endStake);
                        dto.setLengthM(row.lengthM != null ? row.lengthM.setScale(0, RoundingMode.HALF_UP).intValue() : 1000);
                        dto.setPavementType(row.pavementType);
                        dto.setTechnicalGrade(row.technicalGrade);
                        dto.setRoadWidth(row.roadWidth);
                        dto.setAdcode(row.adcode);
                        dto.setManageOrgId(null);
                        dto.setGeomWkt(row.geomWkt);
                        dto.setCenterPointWkt(midPointWkt(row.geomWkt));

                        String existingId = roadEvaluationUnitMapper.selectIdByTenantAndUnitCode(tenantId, unitCode);
                        String unitId;
                        if (existingId == null) {
                            unitId = roadEvaluationUnitService.create(dto);
                        } else {
                            roadEvaluationUnitService.update(existingId, dto);
                            unitId = existingId;
                        }
                        entities++;

                        AssessmentResultSaveDTO ar = buildAssessmentDto(row, "EVALUATION_UNIT", unitId, sectionId, unitId, routeId);
                        assessmentResultService.upsertForImport(ar);
                        assessments++;
                    } catch (BizException ex) {
                        throw ex;
                    } catch (Exception ex) {
                        rowErrors.add(baseErr(shpFile, index) + ex.getMessage());
                    }
                }
            }
            if (!rowErrors.isEmpty()) {
                throw new BizException(joinDetails("台账级数据校验失败", rowErrors));
            }
            log.info("[section-import] 台账级完成 file={} 扫描要素={} 写入单元={} 评定行={} 跳过(无路线)={}",
                    shpFile.getFileName(), index, entities, assessments, skippedUnknownRoute);
            if (skippedUnknownRoute > 0) {
                log.warn("[section-import] 台账级 {} 因路网无对应路线共跳过 {} 条，详见返回 warnings", shpFile.getFileName(), skippedUnknownRoute);
            }
        } finally {
            store.dispose();
        }
        return new Counts(entities, assessments);
    }

    private static String baseErr(Path shp, int index) {
        return shp.getFileName() + " 第" + index + "条要素：";
    }

    private AssessmentResultSaveDTO buildAssessmentDto(ParsedRow row, String objectType, String objectId,
                                                       String sectionId, String unitId, String routeId) {
        AssessmentResultSaveDTO ar = new AssessmentResultSaveDTO();
        ar.setObjectType(objectType);
        ar.setObjectId(objectId);
        ar.setRouteId(routeId);
        ar.setRouteCode(row.routeCode);
        ar.setDirection(row.direction);
        ar.setStartStake(row.startStake);
        ar.setEndStake(row.endStake);
        ar.setYear(row.year);
        ar.setStandardCode("JTG_5210_2018");
        ar.setSectionId(sectionId);
        ar.setUnitId(unitId);
        ar.setMqi(row.mqi);
        ar.setPqi(row.pqi);
        ar.setSci(row.sci);
        ar.setBci(row.bci);
        ar.setTci(row.tci);
        ar.setPci(row.pci);
        ar.setRqi(row.rqi);
        ar.setRdi(row.rdi);
        ar.setPbi(row.pbi);
        ar.setPwi(row.pwi);
        ar.setSri(row.sri);
        ar.setPssi(row.pssi);
        ar.setGrade(row.grade);
        ar.setZeroReason(buildZeroReason(row));
        ar.setAssessedAt(LocalDateTime.now());
        return ar;
    }

    private static String buildZeroReason(ParsedRow row) {
        StringBuilder sb = new StringBuilder();
        if (row.detectionMethod != null && !row.detectionMethod.trim().isEmpty()) {
            sb.append("检测方式:").append(row.detectionMethod.trim()).append("; ");
        }
        if (row.dr != null) {
            sb.append("DR=").append(row.dr.stripTrailingZeros().toPlainString()).append(";");
        }
        if (row.iri != null) {
            sb.append("IRI=").append(row.iri.stripTrailingZeros().toPlainString()).append(";");
        }
        if (row.remarkCn != null && !row.remarkCn.trim().isEmpty()) {
            sb.append(row.remarkCn.trim());
        }
        String s = sb.toString().trim();
        return s.isEmpty() ? null : s;
    }

    private static class ParsedRow {
        String routeCode;
        String routeName;
        String adcode;
        BigDecimal startStake;
        BigDecimal endStake;
        String direction;
        String technicalGrade;
        String pavementType;
        BigDecimal lengthM;
        BigDecimal roadWidth;
        Integer year;
        BigDecimal mqi;
        BigDecimal pqi;
        BigDecimal sci;
        BigDecimal bci;
        BigDecimal tci;
        BigDecimal pci;
        BigDecimal rqi;
        BigDecimal rdi;
        BigDecimal pbi;
        BigDecimal pwi;
        BigDecimal sri;
        BigDecimal pssi;
        BigDecimal dr;
        BigDecimal iri;
        String grade;
        String detectionMethod;
        String remarkCn;
        String sectionCodeAttr;
        String unitCodeAttr;
        String geomWkt;
    }

    /**
     * SHP 属性名与业务字段一一对应（仅此一套）：
     * linkCode→路线编号，adCode→行政区划代码，linkName→路线名称，startMp/endMp→起止桩号，
     * upDown→检测方向，techLevel→技术等级，roadType→路面类型，length→路段长度(m)，
     * roadWidth→路面宽度(m)，detMethod→检测方式，year→检测年度，MQI…remark 同名列。
     * {@link #attr} 同时匹配原始列名与全大写，故 DBF 为 LINKCODE 等亦可识别。
     */
    private ParsedRow parseRow(Map<String, Object> attrs, SimpleFeature feature, MathTransform toWgs84, int index, String shpLabel) throws Exception {
        ParsedRow r = new ParsedRow();
        r.routeCode = pickString(attrs, "linkCode");
        if (isBlank(r.routeCode)) {
            throw new BizException("linkCode(路线编号)为空");
        }
        r.routeCode = r.routeCode.trim();
        r.routeName = pickString(attrs, "linkName");
        r.adcode = pickString(attrs, "adCode");
        r.startStake = pickDecimal(attrs, "startMp");
        r.endStake = pickDecimal(attrs, "endMp");
        if (r.startStake == null || r.endStake == null) {
            throw new BizException("startMp/endMp(起点桩号/终点桩号)为空");
        }
        if (r.startStake.compareTo(r.endStake) > 0) {
            BigDecimal t = r.startStake;
            r.startStake = r.endStake;
            r.endStake = t;
        }
        r.direction = normalizeDirection(pickString(attrs, "upDown"));
        r.technicalGrade = pickString(attrs, "techLevel");
        r.pavementType = normalizeRoadTypeToPavement(pickString(attrs, "roadType"));
        r.lengthM = pickDecimal(attrs, "length");
        r.roadWidth = pickDecimal(attrs, "roadWidth");
        r.year = pickYear(attrs);
        if (r.year == null) {
            throw new BizException("year(检测年度)为空或无法解析");
        }
        r.mqi = pickDecimal(attrs, "MQI");
        r.pqi = pickDecimal(attrs, "PQI");
        r.sci = pickDecimal(attrs, "SCI");
        r.bci = pickDecimal(attrs, "BCI");
        r.tci = pickDecimal(attrs, "TCI");
        r.pci = pickDecimal(attrs, "PCI");
        r.rqi = pickDecimal(attrs, "RQI");
        r.rdi = pickDecimal(attrs, "RDI");
        r.pbi = pickDecimal(attrs, "PBI");
        r.pwi = pickDecimal(attrs, "PWI");
        r.sri = pickDecimal(attrs, "SRI");
        r.pssi = pickDecimal(attrs, "PSSI");
        r.dr = pickDecimal(attrs, "DR");
        r.iri = pickDecimal(attrs, "IRI");
        r.detectionMethod = pickString(attrs, "detMethod");
        r.remarkCn = pickString(attrs, "remark");

        Geometry geom = (Geometry) feature.getDefaultGeometry();
        if (geom != null && !geom.isEmpty()) {
            LineString line = toLineString(geom);
            if (line != null) {
                LineString line2d = toXYLineString(line);
                Geometry transformed = JTS.transform(line2d, toWgs84);
                LineString line4326 = flattenToLineString2D(transformed);
                if (line4326 != null) {
                    r.geomWkt = lineStringToWktLonLat2D(line4326, 6);
                }
            }
        }
        return r;
    }

    private static String buildSectionCode(ParsedRow row) {
        if (row.sectionCodeAttr != null && !row.sectionCodeAttr.trim().isEmpty()) {
            return truncateCode(sanitizeCode(row.sectionCodeAttr.trim()), SECTION_CODE_MAX);
        }
        String base = sanitizeCode(row.routeCode + "-K" + stakePlain(row.startStake) + "-K" + stakePlain(row.endStake) + "-" + row.direction);
        if (base.length() <= SECTION_CODE_MAX) {
            return base;
        }
        return truncateWithHash(base, SECTION_CODE_MAX, "S");
    }

    private static String buildUnitCode(ParsedRow row) {
        if (row.unitCodeAttr != null && !row.unitCodeAttr.trim().isEmpty()) {
            return truncateCode(sanitizeCode(row.unitCodeAttr.trim()), UNIT_CODE_MAX);
        }
        String base = sanitizeCode(row.routeCode + "-U-K" + stakePlain(row.startStake) + "-K" + stakePlain(row.endStake) + "-" + row.direction);
        if (base.length() <= UNIT_CODE_MAX) {
            return base;
        }
        return truncateWithHash(base, UNIT_CODE_MAX, "U");
    }

    private static String stakePlain(BigDecimal v) {
        return v == null ? "" : v.stripTrailingZeros().toPlainString();
    }

    private static String sanitizeCode(String s) {
        return s.replaceAll("[^A-Za-z0-9_\\-\\u4e00-\\u9fa5]", "_");
    }

    private static String truncateCode(String s, int max) {
        if (s.length() <= max) {
            return s;
        }
        return s.substring(0, max);
    }

    private static String truncateWithHash(String base, int max, String prefix) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] h = md.digest(base.getBytes(StandardCharsets.UTF_8));
            String hex16 = toHex16(h);
            String tail = "_" + prefix + hex16;
            int keep = Math.max(1, max - tail.length());
            return base.substring(0, Math.min(keep, base.length())) + tail;
        } catch (Exception e) {
            throw new BizException("生成编码哈希失败");
        }
    }

    private static String toHex16(byte[] h) {
        StringBuilder sb = new StringBuilder(16);
        for (int i = 0; i < 8 && i < h.length; i++) {
            sb.append(String.format(Locale.ROOT, "%02x", h[i]));
        }
        while (sb.length() < 16) {
            sb.append('0');
        }
        return sb.substring(0, 16);
    }

    private static String stakeKey(String routeCode, String direction, BigDecimal start, BigDecimal end) {
        return routeCode + "\0" + direction + "\0" + normStakeKey(start) + "\0" + normStakeKey(end);
    }

    private static String normStakeKey(BigDecimal v) {
        return v == null ? "" : v.stripTrailingZeros().toPlainString();
    }

    private static String normalizeDirection(String raw) {
        if (raw == null || raw.trim().isEmpty()) {
            return "BOTH";
        }
        String t = raw.trim();
        String u = t.toUpperCase(Locale.ROOT);
        if ("UP".equals(u) || "DOWN".equals(u) || "BOTH".equals(u)) {
            return u;
        }
        // 常见编码：1 上行、2 下行、0/3 双向（与部分省厅导出一致）
        if ("1".equals(t) || "01".equals(t)) {
            return "UP";
        }
        if ("2".equals(t) || "02".equals(t)) {
            return "DOWN";
        }
        if ("0".equals(t) || "3".equals(t) || "9".equals(t)) {
            return "BOTH";
        }
        if ("上行".equals(t) || "上".equals(t)) {
            return "UP";
        }
        if ("下行".equals(t) || "下".equals(t)) {
            return "DOWN";
        }
        if ("双向".equals(t) || "全幅".equals(t) || "上下行".equals(t)) {
            return "BOTH";
        }
        throw new BizException("无法识别的检测方向 upDown：" + t + "（请使用 1/2/0、上行/下行/双向或 UP/DOWN/BOTH）");
    }

    private static boolean isBlank(String s) {
        return s == null || s.trim().isEmpty();
    }

    /**
     * roadType 常见取值映射到 pavement_type 字典码；未知则原样 trim 后大写回传。
     */
    private static String normalizeRoadTypeToPavement(String raw) {
        if (raw == null || raw.trim().isEmpty()) {
            return null;
        }
        String t = raw.trim();
        String u = t.toUpperCase(Locale.ROOT);
        if ("ASPHALT".equals(u) || "CONCRETE".equals(u) || "GRAVEL".equals(u) || "OTHER".equals(u)) {
            return u;
        }
        if ("沥青".equals(t) || "沥青砼".equals(t) || "沥青混凝土".equals(t)) {
            return "ASPHALT";
        }
        if ("水泥".equals(t) || "水泥混凝土".equals(t) || "水泥砼".equals(t) || "砼".equals(t)) {
            return "CONCRETE";
        }
        if ("砂石".equals(t) || "碎石".equals(t) || "粒料".equals(t)) {
            return "GRAVEL";
        }
        if ("1".equals(t)) {
            return "ASPHALT";
        }
        if ("2".equals(t)) {
            return "CONCRETE";
        }
        return u;
    }

    private static Integer pickYear(Map<String, Object> attrs) {
        Object v = firstAttr(attrs, "year");
        if (v == null) {
            return null;
        }
        if (v instanceof Number) {
            return ((Number) v).intValue();
        }
        String s = String.valueOf(v).trim();
        if (s.isEmpty()) {
            return null;
        }
        try {
            return new BigDecimal(s).intValue();
        } catch (Exception e) {
            return null;
        }
    }

    private static Object firstAttr(Map<String, Object> attrs, String... keys) {
        for (String k : keys) {
            Object v = attr(attrs, k);
            if (v != null) {
                return v;
            }
        }
        return null;
    }

    private static String pickString(Map<String, Object> attrs, String... keys) {
        Object v = firstAttr(attrs, keys);
        if (v == null) {
            return null;
        }
        String s = String.valueOf(v).trim();
        return s.isEmpty() ? null : s;
    }

    private static BigDecimal pickDecimal(Map<String, Object> attrs, String... keys) {
        Object v = firstAttr(attrs, keys);
        if (v == null) {
            return null;
        }
        if (v instanceof BigDecimal) {
            return (BigDecimal) v;
        }
        if (v instanceof Number) {
            return BigDecimal.valueOf(((Number) v).doubleValue());
        }
        String s = String.valueOf(v).trim();
        if (s.isEmpty()) {
            return null;
        }
        try {
            return new BigDecimal(s);
        } catch (Exception e) {
            return null;
        }
    }

    private static Object attr(Map<String, Object> m, String key) {
        if (key == null) {
            return null;
        }
        Object v = m.get(key);
        if (v != null) {
            return v;
        }
        v = m.get(key.toUpperCase(Locale.ROOT));
        if (v != null) {
            return v;
        }
        return null;
    }

    private static Map<String, Object> buildAttrMap(SimpleFeature feature) {
        Map<String, Object> map = new HashMap<>();
        for (AttributeDescriptor ad : feature.getFeatureType().getAttributeDescriptors()) {
            if (ad instanceof GeometryDescriptor) {
                continue;
            }
            String name = ad.getLocalName();
            if (name == null) {
                continue;
            }
            Object v = feature.getAttribute(name);
            if (v == null) {
                continue;
            }
            String trimmed = name.trim();
            map.put(trimmed.toUpperCase(Locale.ROOT), v);
            map.put(trimmed, v);
        }
        return map;
    }

    private Charset resolveDbfCharset() {
        if (shapefileDbfCharset == null || shapefileDbfCharset.trim().isEmpty()) {
            return StandardCharsets.UTF_8;
        }
        try {
            return Charset.forName(shapefileDbfCharset.trim());
        } catch (Exception e) {
            return StandardCharsets.UTF_8;
        }
    }

    private static String firstNonBlank(String a, String b) {
        if (a != null && !a.trim().isEmpty()) {
            return a.trim();
        }
        if (b != null && !b.trim().isEmpty()) {
            return b.trim();
        }
        return null;
    }

    private static String trimRemark(String remark) {
        if (remark == null) {
            return null;
        }
        if (remark.length() > 2000) {
            return remark.substring(0, 2000);
        }
        return remark;
    }

    private static String midPointWkt(String geomWkt) {
        if (geomWkt == null || !geomWkt.toUpperCase(Locale.ROOT).contains("LINESTRING")) {
            return null;
        }
        try {
            org.locationtech.jts.io.WKTReader reader = new org.locationtech.jts.io.WKTReader();
            Geometry g = reader.read(geomWkt);
            if (!(g instanceof LineString) || g.isEmpty()) {
                return null;
            }
            LineString ls = (LineString) g;
            LengthIndexedLine lil = new LengthIndexedLine(ls);
            double mid = lil.getEndIndex() / 2.0;
            Coordinate c = lil.extractPoint(mid);
            return "POINT (" + formatOrdinate(c.x, 6) + " " + formatOrdinate(c.y, 6) + ")";
        } catch (Exception e) {
            return null;
        }
    }

    private static LineString flattenToLineString2D(Geometry g) {
        if (g == null || g.isEmpty()) {
            return null;
        }
        if (g instanceof LineString) {
            return toXYLineString((LineString) g);
        }
        if (g instanceof MultiLineString) {
            LineString pick = toLineString(g);
            return pick == null ? null : toXYLineString(pick);
        }
        return null;
    }

    private static String lineStringToWktLonLat2D(LineString ls, int scale) {
        CoordinateSequence seq = ls.getCoordinateSequence();
        StringBuilder sb = new StringBuilder(seq.size() * 24);
        sb.append("LINESTRING (");
        for (int i = 0; i < seq.size(); i++) {
            if (i > 0) {
                sb.append(", ");
            }
            double x = seq.getOrdinate(i, CoordinateSequence.X);
            double y = seq.getOrdinate(i, CoordinateSequence.Y);
            sb.append(formatOrdinate(x, scale)).append(' ').append(formatOrdinate(y, scale));
        }
        sb.append(')');
        return sb.toString();
    }

    private static String formatOrdinate(double v, int scale) {
        return BigDecimal.valueOf(v).setScale(scale, RoundingMode.HALF_UP).stripTrailingZeros().toPlainString();
    }

    private static LineString toXYLineString(LineString line) {
        CoordinateSequence seq = line.getCoordinateSequence();
        GeometryFactory factory = line.getFactory();
        int n = seq.size();
        Coordinate[] coords = new Coordinate[n];
        for (int i = 0; i < n; i++) {
            coords[i] = new Coordinate(seq.getOrdinate(i, CoordinateSequence.X), seq.getOrdinate(i, CoordinateSequence.Y));
        }
        return factory.createLineString(coords);
    }

    private static LineString toLineString(Geometry g) {
        if (g instanceof LineString) {
            return (LineString) g;
        }
        if (g instanceof MultiLineString) {
            MultiLineString ml = (MultiLineString) g;
            LineString best = null;
            double bestLen = -1;
            for (int i = 0; i < ml.getNumGeometries(); i++) {
                Geometry gi = ml.getGeometryN(i);
                if (!(gi instanceof LineString)) {
                    continue;
                }
                LineString ls = (LineString) gi;
                if (ls.isEmpty()) {
                    continue;
                }
                double len = ls.getLength();
                if (len > bestLen) {
                    bestLen = len;
                    best = ls;
                }
            }
            return best;
        }
        return null;
    }

    private static String joinDetails(String prefix, List<String> details) {
        int max = Math.min(50, details.size());
        StringBuilder sb = new StringBuilder(prefix).append("：");
        for (int i = 0; i < max; i++) {
            if (i > 0) {
                sb.append("；");
            }
            sb.append(details.get(i));
        }
        if (details.size() > max) {
            sb.append("…(共").append(details.size()).append("条)");
        }
        return sb.toString();
    }

    private static boolean isCompleteShapefileGroup(Path shp) {
        Path dir = shp.getParent();
        String base = stripExtension(shp.getFileName().toString());
        return Files.isRegularFile(dir.resolve(base + ".dbf"))
                && Files.isRegularFile(dir.resolve(base + ".shx"))
                && Files.isRegularFile(dir.resolve(base + ".prj"));
    }

    private static String stripExtension(String name) {
        int i = name.lastIndexOf('.');
        return i > 0 ? name.substring(0, i) : name;
    }

    private void safeUntar(InputStream in, Path tempDir) throws IOException {
        Path root = tempDir.toAbsolutePath().normalize();
        long written = 0;
        try (TarArchiveInputStream tis = new TarArchiveInputStream(in)) {
            TarArchiveEntry entry;
            while ((entry = tis.getNextEntry()) != null) {
                if (entry.isDirectory()) {
                    continue;
                }
                String name = entry.getName().replace('\\', '/');
                if (name.startsWith("/") || name.contains("..")) {
                    throw new BizException("压缩包路径非法");
                }
                Path dest = root.resolve(name).normalize();
                if (!dest.startsWith(root)) {
                    throw new BizException("压缩包路径非法");
                }
                if (Files.notExists(dest.getParent())) {
                    Files.createDirectories(dest.getParent());
                }
                long size = entry.getSize();
                if (size > 0 && written + size > MAX_EXTRACT_BYTES) {
                    throw new BizException("解压后总大小超过上限");
                }
                try (OutputStream os = Files.newOutputStream(dest)) {
                    byte[] buf = new byte[8192];
                    int n;
                    while ((n = tis.read(buf)) >= 0) {
                        if (n == 0) {
                            continue;
                        }
                        written += n;
                        if (written > MAX_EXTRACT_BYTES) {
                            throw new BizException("解压后总大小超过上限");
                        }
                        os.write(buf, 0, n);
                    }
                }
            }
        }
    }

    private static void deleteRecursivelyQuiet(Path root) {
        if (root == null || !Files.exists(root)) {
            return;
        }
        try {
            Files.walkFileTree(root, new SimpleFileVisitor<Path>() {
                @Override
                public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                    Files.deleteIfExists(file);
                    return FileVisitResult.CONTINUE;
                }

                @Override
                public FileVisitResult postVisitDirectory(Path dir, IOException exc) throws IOException {
                    Files.deleteIfExists(dir);
                    return FileVisitResult.CONTINUE;
                }
            });
        } catch (IOException ignored) {
            // best-effort
        }
    }
}
