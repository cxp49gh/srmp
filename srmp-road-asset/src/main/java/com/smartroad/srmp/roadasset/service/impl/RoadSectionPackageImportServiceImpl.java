package com.smartroad.srmp.roadasset.service.impl;

import com.smartroad.srmp.assessment.dto.AssessmentResultSaveDTO;
import com.smartroad.srmp.assessment.service.AssessmentResultService;
import com.smartroad.srmp.common.exception.BizException;
import com.smartroad.srmp.roadasset.dto.HmCodeIdRow;
import com.smartroad.srmp.roadasset.dto.KmCodeIdRow;
import com.smartroad.srmp.roadasset.dto.LineCodeIdRow;
import com.smartroad.srmp.roadasset.dto.RouteNetworkMatch;
import com.smartroad.srmp.roadasset.dto.UnitCodeIdRow;
import com.smartroad.srmp.roadasset.sectionpkg.SectionPackageTier;
import com.smartroad.srmp.roadasset.entity.RoadEvaluationUnit;
import com.smartroad.srmp.roadasset.entity.RoadSection;
import com.smartroad.srmp.roadasset.entity.RoadSectionHm;
import com.smartroad.srmp.roadasset.entity.RoadSectionKm;
import com.smartroad.srmp.roadasset.mapper.RoadEvaluationUnitMapper;
import com.smartroad.srmp.roadasset.mapper.RoadRouteMapper;
import com.smartroad.srmp.roadasset.mapper.RoadSectionHmMapper;
import com.smartroad.srmp.roadasset.mapper.RoadSectionKmMapper;
import com.smartroad.srmp.roadasset.mapper.RoadSectionMapper;
import com.smartroad.srmp.roadasset.service.RoadSectionPackageImportService;
import com.smartroad.srmp.roadasset.util.RouteCodeNetworkLookup;
import com.smartroad.srmp.roadasset.vo.ImportSectionPackageResultVO;
import com.smartroad.srmp.common.util.IdUtils;
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
import org.springframework.dao.DataAccessException;
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
import java.sql.SQLException;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Stream;

@Service
public class RoadSectionPackageImportServiceImpl implements RoadSectionPackageImportService {

    private static final Logger log = LoggerFactory.getLogger(RoadSectionPackageImportServiceImpl.class);

    private static final long MAX_EXTRACT_BYTES = 500L * 1024 * 1024;
    private static final int SECTION_CODE_MAX = 100;
    private static final int UNIT_CODE_MAX = 100;
    private static final int KM_CODE_MAX = 1000;
    private static final int HM_CODE_MAX = 1000;

    @Resource
    private RoadRouteMapper roadRouteMapper;
    @Resource
    private RoadSectionMapper roadSectionMapper;
    @Resource
    private RoadEvaluationUnitMapper roadEvaluationUnitMapper;
    @Resource
    private RoadSectionKmMapper roadSectionKmMapper;
    @Resource
    private RoadSectionHmMapper roadSectionHmMapper;
    @Resource
    private AssessmentResultService assessmentResultService;

    @Value("${srmp.import.section.dbf-charset:UTF-8}")
    private String shapefileDbfCharset;

    /** 每批写入数据库的要素条数（批量 INSERT/UPSERT + 批量评定） */
    private static final int IMPORT_DB_BATCH_SIZE = 1000;
    private static final int SQL_IN_CHUNK = 1000;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public ImportSectionPackageResultVO importPackage(MultipartFile file) {
        return importPackage(file, null);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public ImportSectionPackageResultVO importPackage(MultipartFile file, String projectId) {
        long t0 = System.currentTimeMillis();
        if (file == null || file.isEmpty()) {
            throw new BizException("导入文件不能为空");
        }
        String original = file.getOriginalFilename();
        if (original == null || !original.toLowerCase(Locale.ROOT).endsWith(".tar")) {
            throw new BizException("仅支持 .tar 格式");
        }
        String tenantId = TenantContextHolder.getTenantId();
        log.info("[section-import] 开始 tenant={} file={} size={} bytes projectId={}", tenantId, original, file.getSize(), projectId);
        Path tempDir = null;
        try {
            tempDir = Files.createTempDirectory("srmp-section-import-");
            log.debug("[section-import] 解压目录 {}", tempDir.toAbsolutePath());
            try (InputStream in = new BufferedInputStream(file.getInputStream())) {
                safeUntar(in, tempDir);
            }
            ImportSectionPackageResultVO vo = importFromExtracted(tempDir, t0, projectId);
            log.info("[section-import] 完成 tenant={} line={} ledger={} km={} hm={} assessments={} durationMs={} warningCount={}",
                    tenantId,
                    vo.getLineCount(),
                    vo.getLedgerCount(),
                    vo.getKmCount(),
                    vo.getHmCount(),
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

    private ImportSectionPackageResultVO importFromExtracted(Path root, long t0, String projectId) throws Exception {
        ImportSectionPackageResultVO result = new ImportSectionPackageResultVO();
        List<String> warnings = new ArrayList<>();
        List<Path> lineShps = new ArrayList<>();
        List<Path> ledgerShps = new ArrayList<>();
        List<Path> kmShps = new ArrayList<>();
        List<Path> hmShps = new ArrayList<>();

        List<Path> allShp = new ArrayList<>();
        try (Stream<Path> walk = Files.walk(root)) {
            walk.filter(p -> p.getFileName().toString().toLowerCase(Locale.ROOT).endsWith(".shp")).forEach(allShp::add);
        }
        if (allShp.isEmpty()) {
            throw new BizException("压缩包内未找到 .shp 文件");
        }
        log.info("[section-import] 发现 .shp 共 {} 个", allShp.size());
        EnumSet<SectionPackageTier> tiersPresent = EnumSet.noneOf(SectionPackageTier.class);
        for (Path shp : allShp) {
            if (!isCompleteShapefileGroup(shp)) {
                throw new BizException("缺少 .dbf 或 .shx 或 .prj 文件，无法导入：" + shp.getFileName());
            }
            SectionPackageTier tier = classifyTier(shp, root);
            if (tier == SectionPackageTier.UNKNOWN) {
                throw new BizException("存在无法识别的 Shapefile 级别，请按命名约定放置线路级/台账级/公里级/百米级数据：" + relativize(root, shp));
            }
            tiersPresent.add(tier);
            if (tier == SectionPackageTier.LINE) {
                lineShps.add(shp);
            } else if (tier == SectionPackageTier.LEDGER) {
                ledgerShps.add(shp);
            } else if (tier == SectionPackageTier.KM) {
                kmShps.add(shp);
            } else {
                hmShps.add(shp);
            }
            log.debug("[section-import] 归类 {} -> {} ({})", relativize(root, shp), tier, shp.getFileName());
        }
        EnumSet<SectionPackageTier> required = EnumSet.of(SectionPackageTier.LINE, SectionPackageTier.LEDGER, SectionPackageTier.KM, SectionPackageTier.HM);
        if (!tiersPresent.containsAll(required)) {
            EnumSet<SectionPackageTier> missing = EnumSet.copyOf(required);
            missing.removeAll(tiersPresent);
            throw new BizException("压缩包缺少必须级别的 Shapefile，需同时包含：线路级、台账级、公里级、百米级。缺少：" + tierLabels(missing));
        }
        log.info("[section-import] 线路级 shp={} 台账级 shp={} 公里级 shp={} 百米级 shp={}",
                lineShps.size(), ledgerShps.size(), kmShps.size(), hmShps.size());
        result.setIgnoredShapefileGroups(new ArrayList<>());

        int lineCount = 0;
        int ledgerCount = 0;
        int kmCount = 0;
        int hmCount = 0;
        int arCount = 0;

        for (Path shp : lineShps) {
            log.info("[section-import] 处理线路级 {}", relativize(root, shp));
            Counts c = processTierShapefile(SectionPackageTier.LINE, shp, warnings, projectId);
            lineCount += c.entities;
            arCount += c.assessments;
        }
        for (Path shp : ledgerShps) {
            log.info("[section-import] 处理台账级 {}", relativize(root, shp));
            Counts c = processTierShapefile(SectionPackageTier.LEDGER, shp, warnings, projectId);
            ledgerCount += c.entities;
            arCount += c.assessments;
        }
        for (Path shp : kmShps) {
            log.info("[section-import] 处理公里级 {}", relativize(root, shp));
            Counts c = processTierShapefile(SectionPackageTier.KM, shp, warnings, projectId);
            kmCount += c.entities;
            arCount += c.assessments;
        }
        for (Path shp : hmShps) {
            log.info("[section-import] 处理百米级 {}", relativize(root, shp));
            Counts c = processTierShapefile(SectionPackageTier.HM, shp, warnings, projectId);
            hmCount += c.entities;
            arCount += c.assessments;
        }

        result.setLineCount(lineCount);
        result.setLedgerCount(ledgerCount);
        result.setKmCount(kmCount);
        result.setHmCount(hmCount);
        result.setRouteSectionCount(lineCount);
        result.setEvaluationUnitCount(ledgerCount);
        result.setAssessmentRowCount(arCount);
        result.setWarnings(warnings);
        result.setDurationMs(System.currentTimeMillis() - t0);
        return result;
    }

    private static String tierLabels(EnumSet<SectionPackageTier> missing) {
        List<String> labels = new ArrayList<>();
        for (SectionPackageTier t : missing) {
            if (t == SectionPackageTier.LINE) {
                labels.add("线路级");
            } else if (t == SectionPackageTier.LEDGER) {
                labels.add("台账级");
            } else if (t == SectionPackageTier.KM) {
                labels.add("公里级");
            } else if (t == SectionPackageTier.HM) {
                labels.add("百米级");
            }
        }
        return String.join("、", labels);
    }

    private static String relativize(Path root, Path shp) {
        try {
            return root.relativize(shp).toString().replace('\\', '/');
        } catch (Exception e) {
            return shp.toString();
        }
    }

    private static SectionPackageTier classifyTier(Path shp, Path root) {
        String rel = relativize(root, shp).toLowerCase(Locale.ROOT);
        String base = stripExtension(shp.getFileName().toString()).toLowerCase(Locale.ROOT);
        String hay = rel + "|" + base;
        if (containsAny(hay, "百米级", "百米", "road_section_hm", "hm_unit", "bmj")) {
            return SectionPackageTier.HM;
        }
        if (containsAny(hay, "公里级", "road_section_km", "km_unit", "glj")) {
            return SectionPackageTier.KM;
        }
        if (containsAny(hay, "台账级", "台账", "road_section_ledger", "ledger", "评定单元", "evaluation_unit", "eval_unit")) {
            return SectionPackageTier.LEDGER;
        }
        if (containsAny(hay, "线路级", "线路", "road_section_line", "section_line", "xlj", "路线级", "road_section", "路段")) {
            return SectionPackageTier.LINE;
        }
        return SectionPackageTier.UNKNOWN;
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

    /** 解析完成、待批量对齐路网 {@code route_code} 并生成业务编码 */
    private static class ParsedFeature {
        final int index;
        final ParsedRow row;

        ParsedFeature(int index, ParsedRow row) {
            this.index = index;
            this.row = row;
        }
    }

    /**
     * 四级共用的导入工作项：解析行 + 业务编码与路网命中结果。
     * 持久化时通过各实体上的 routeId / routeCode 与路网关联，不再写入 line_id、km_id、section_id 等层级外键。
     */
    private static final class ImportWorkItem {
        final int index;
        final ParsedRow row;
        final SectionPackageTier tier;
        final String segmentCode;
        final String routeCodeDb;
        final RouteNetworkMatch networkMatch;

        ImportWorkItem(int index, ParsedRow row, SectionPackageTier tier, String segmentCode, String routeCodeDb,
                       RouteNetworkMatch networkMatch) {
            this.index = index;
            this.row = row;
            this.tier = tier;
            this.segmentCode = segmentCode;
            this.routeCodeDb = routeCodeDb;
            this.networkMatch = networkMatch;
        }
    }

    private List<ImportWorkItem> buildWorkItems(SectionPackageTier tier, String tenantId, List<ParsedFeature> drafts,
                                                String projectId, Set<String> seenSegmentCodes) {
        if (drafts.isEmpty()) {
            return Collections.emptyList();
        }
        LinkedHashSet<String> importCodes = new LinkedHashSet<>();
        for (ParsedFeature d : drafts) {
            importCodes.add(d.row.routeCode);
        }
        Map<String, RouteNetworkMatch> net = RouteCodeNetworkLookup.resolveBatch(roadRouteMapper, tenantId, importCodes);
        List<ImportWorkItem> items = new ArrayList<>(drafts.size());
        for (ParsedFeature d : drafts) {
            RouteNetworkMatch m = net.get(d.row.routeCode);
            String rcDb = m != null && m.getCanonicalRouteCode() != null && !m.getCanonicalRouteCode().isEmpty()
                    ? m.getCanonicalRouteCode().trim()
                    : d.row.routeCode;
            String segment = segmentCodeForTier(tier, d.row, rcDb);
            ensureUniqueSegment(tier, segment, seenSegmentCodes);
            items.add(new ImportWorkItem(d.index, d.row, tier, segment, rcDb, m));
        }
        return items;
    }

    private static String segmentCodeForTier(SectionPackageTier tier, ParsedRow row, String routeCodeForDb) {
        switch (tier) {
            case LINE:
                return buildSectionCode(row, routeCodeForDb);
            case LEDGER:
                return buildUnitCode(row, routeCodeForDb);
            case KM:
                return buildKmCode(row, routeCodeForDb);
            case HM:
                return buildHmCode(row, routeCodeForDb);
            default:
                throw new BizException("未知路段级别");
        }
    }

    private static void ensureUniqueSegment(SectionPackageTier tier, String code, Set<String> seen) {
        if (seen.add(code)) {
            return;
        }
        switch (tier) {
            case LINE:
                throw new BizException("压缩包内路段编码重复：" + code);
            case LEDGER:
                throw new BizException("压缩包内评定单元编码重复：" + code);
            case KM:
                throw new BizException("压缩包内公里编码重复：" + code);
            case HM:
                throw new BizException("压缩包内百米编码重复：" + code);
            default:
                throw new BizException("编码重复：" + code);
        }
    }

    private Map<String, String> loadLineCodeIdMap(String tenantId, List<String> lineCodes) {
        if (lineCodes.isEmpty()) {
            return Collections.emptyMap();
        }
        Map<String, String> out = new HashMap<>();
        for (int i = 0; i < lineCodes.size(); i += SQL_IN_CHUNK) {
            int end = Math.min(i + SQL_IN_CHUNK, lineCodes.size());
            for (LineCodeIdRow r : roadSectionMapper.selectLineIdsByLineCodes(tenantId, lineCodes.subList(i, end))) {
                out.put(r.getLineCode(), r.getId());
            }
        }
        return out;
    }

    private Map<String, String> loadUnitCodeIdMap(String tenantId, List<String> unitCodes) {
        if (unitCodes.isEmpty()) {
            return Collections.emptyMap();
        }
        Map<String, String> out = new HashMap<>();
        for (int i = 0; i < unitCodes.size(); i += SQL_IN_CHUNK) {
            int end = Math.min(i + SQL_IN_CHUNK, unitCodes.size());
            for (UnitCodeIdRow r : roadEvaluationUnitMapper.selectLedgerIdsByUnitCodes(tenantId, unitCodes.subList(i, end))) {
                out.put(r.getUnitCode(), r.getId());
            }
        }
        return out;
    }

    private Map<String, String> loadKmCodeIdMap(String tenantId, List<String> kmCodes) {
        if (kmCodes.isEmpty()) {
            return Collections.emptyMap();
        }
        Map<String, String> out = new HashMap<>();
        for (int i = 0; i < kmCodes.size(); i += SQL_IN_CHUNK) {
            int end = Math.min(i + SQL_IN_CHUNK, kmCodes.size());
            for (KmCodeIdRow r : roadSectionKmMapper.selectKmIdsByKmCodes(tenantId, kmCodes.subList(i, end))) {
                out.put(r.getKmCode(), r.getId());
            }
        }
        return out;
    }

    private Map<String, String> loadHmCodeIdMap(String tenantId, List<String> hmCodes) {
        if (hmCodes.isEmpty()) {
            return Collections.emptyMap();
        }
        Map<String, String> out = new HashMap<>();
        for (int i = 0; i < hmCodes.size(); i += SQL_IN_CHUNK) {
            int end = Math.min(i + SQL_IN_CHUNK, hmCodes.size());
            for (HmCodeIdRow r : roadSectionHmMapper.selectHmIdsByHmCodes(tenantId, hmCodes.subList(i, end))) {
                out.put(r.getHmCode(), r.getId());
            }
        }
        return out;
    }

    private RoadSection buildLineSectionEntity(ParsedRow row, String sectionCode, String projectId, String routeCodeForDb) {
        RoadSection e = new RoadSection();
        e.setRouteCode(routeCodeForDb);
        e.setSectionCode(sectionCode);
        e.setSectionName(firstNonBlank(row.routeName, routeCodeForDb));
        e.setDirection(row.direction);
        e.setStartStake(row.startStake);
        e.setEndStake(row.endStake);
        e.setLengthKm(row.lengthM != null
                ? row.lengthM.divide(BigDecimal.valueOf(1000), 3, RoundingMode.HALF_UP)
                : null);
        e.setPavementType(row.pavementType);
        e.setTechnicalGrade(row.technicalGrade);
        e.setRoadWidth(row.roadWidth);
        e.setAdcode(row.adcode);
        e.setManageOrgId(null);
        e.setGeomWkt(row.geomWkt);
        if (projectId != null && !projectId.trim().isEmpty()) {
            e.setProjectId(projectId.trim());
        }
        return e;
    }

    private RoadEvaluationUnit buildLedgerUnitEntity(ParsedRow row, String unitCode, String projectId, String routeCodeForDb) {
        RoadEvaluationUnit u = new RoadEvaluationUnit();
        u.setRouteCode(routeCodeForDb);
        u.setUnitCode(unitCode);
        u.setLedgerName(firstNonBlank(row.routeName, routeCodeForDb));
        if (projectId != null && !projectId.trim().isEmpty()) {
            u.setProjectId(projectId.trim());
        }
        u.setDirection(row.direction);
        u.setLaneNo(null);
        u.setStartStake(row.startStake);
        u.setEndStake(row.endStake);
        u.setLengthM(row.lengthM != null ? row.lengthM.setScale(0, RoundingMode.HALF_UP).intValue() : 1000);
        u.setPavementType(row.pavementType);
        u.setTechnicalGrade(row.technicalGrade);
        u.setRoadWidth(row.roadWidth);
        u.setAdcode(row.adcode);
        u.setManageOrgId(null);
        u.setGeomWkt(row.geomWkt);
        u.setCenterPointWkt(midPointWkt(row.geomWkt));
        return u;
    }

    private RoadSectionKm buildKmEntity(ParsedRow row, String kmCode, String projectId, String routeCodeForDb) {
        RoadSectionKm km = new RoadSectionKm();
        km.setRouteCode(routeCodeForDb);
        km.setKmCode(kmCode);
        km.setDirection(row.direction);
        km.setStartStake(row.startStake);
        km.setEndStake(row.endStake);
        km.setLengthM(row.lengthM != null ? row.lengthM.setScale(0, RoundingMode.HALF_UP).intValue() : null);
        km.setPavementType(row.pavementType);
        km.setTechnicalGrade(row.technicalGrade);
        km.setRoadWidth(row.roadWidth);
        if (projectId != null && !projectId.trim().isEmpty()) {
            km.setProjectId(projectId.trim());
        }
        km.setGeomWkt(row.geomWkt);
        return km;
    }

    private RoadSectionHm buildHmEntity(ParsedRow row, String hmCode, String projectId, String routeCodeForDb) {
        RoadSectionHm hm = new RoadSectionHm();
        hm.setRouteCode(routeCodeForDb);
        hm.setHmCode(hmCode);
        hm.setDirection(row.direction);
        hm.setStartStake(row.startStake);
        hm.setEndStake(row.endStake);
        hm.setLengthM(row.lengthM != null ? row.lengthM.setScale(0, RoundingMode.HALF_UP).intValue() : null);
        if (projectId != null && !projectId.trim().isEmpty()) {
            hm.setProjectId(projectId.trim());
        }
        hm.setGeomWkt(row.geomWkt);
        return hm;
    }

    private int flushWorkBatch(SectionPackageTier tier, List<ImportWorkItem> batch, String tenantId, String projectId) {
        if (batch.isEmpty()) {
            return 0;
        }
        switch (tier) {
            case LINE:
                return flushLineTier(batch, tenantId, projectId);
            case LEDGER:
                return flushLedgerTier(batch, tenantId, projectId);
            case KM:
                return flushKmTier(batch, tenantId, projectId);
            case HM:
                return flushHmTier(batch, tenantId, projectId);
            default:
                throw new BizException("未知路段级别");
        }
    }

    private int flushLineTier(List<ImportWorkItem> batch, String tenantId, String projectId) {
        int orphan = 0;
        List<String> lineCodes = new ArrayList<>(batch.size());
        List<RoadSection> sections = new ArrayList<>(batch.size());
        for (ImportWorkItem it : batch) {
            String routeId = it.networkMatch != null ? it.networkMatch.getRouteId() : null;
            if (routeId == null) {
                orphan++;
            }
            RoadSection e = buildLineSectionEntity(it.row, it.segmentCode, projectId, it.routeCodeDb);
            e.setRouteId(routeId);
            String remark = it.row.remarkCn;
            if (it.row.detectionMethod != null && !it.row.detectionMethod.trim().isEmpty()) {
                String tag = "检测方式:" + it.row.detectionMethod.trim();
                remark = remark == null || remark.trim().isEmpty() ? tag : tag + " | " + remark;
            }
            if (routeId == null) {
                remark = mergeRemarkWithOrphanRouteHint(remark, it.row.routeCode);
            }
            e.setRemark(trimRemark(remark));
            lineCodes.add(e.getSectionCode());
            sections.add(e);
        }
        Map<String, String> existing = loadLineCodeIdMap(tenantId, lineCodes);
        LocalDateTime now = LocalDateTime.now();
        for (RoadSection e : sections) {
            String id = existing.get(e.getSectionCode());
            e.setId(id != null ? id : IdUtils.uuid());
            e.setTenantId(tenantId);
            e.setCreatedAt(now);
            e.setUpdatedAt(now);
            e.setDeleted(false);
        }
        roadSectionMapper.upsertBatchWithGeom(tenantId, sections, null);
        List<AssessmentResultSaveDTO> ars = new ArrayList<>(batch.size());
        for (int i = 0; i < batch.size(); i++) {
            RoadSection e = sections.get(i);
            ImportWorkItem it = batch.get(i);
            ars.add(buildAssessmentDto(it.row, "ROAD_SECTION_LINE", e.getId(), e.getId(), null, e.getRouteId(), e.getRouteCode()));
        }
        assessmentResultService.upsertBatchForImport(ars);
        return orphan;
    }

    private int flushLedgerTier(List<ImportWorkItem> batch, String tenantId, String projectId) {
        int orphan = 0;
        List<String> unitCodes = new ArrayList<>(batch.size());
        List<RoadEvaluationUnit> units = new ArrayList<>(batch.size());
        for (ImportWorkItem it : batch) {
            String routeId = it.networkMatch != null ? it.networkMatch.getRouteId() : null;
            if (routeId == null) {
                orphan++;
            }
            RoadEvaluationUnit u = buildLedgerUnitEntity(it.row, it.segmentCode, projectId, it.routeCodeDb);
            u.setRouteId(routeId);
            u.setSectionId(null);
            unitCodes.add(u.getUnitCode());
            units.add(u);
        }
        Map<String, String> existingUnits = loadUnitCodeIdMap(tenantId, unitCodes);
        LocalDateTime now = LocalDateTime.now();
        for (RoadEvaluationUnit u : units) {
            String id = existingUnits.get(u.getUnitCode());
            u.setId(id != null ? id : IdUtils.uuid());
            u.setTenantId(tenantId);
            u.setCreatedAt(now);
            u.setUpdatedAt(now);
            u.setDeleted(false);
        }
        roadEvaluationUnitMapper.upsertBatchWithGeom(tenantId, units, null);
        List<AssessmentResultSaveDTO> ars = new ArrayList<>(batch.size());
        for (int i = 0; i < batch.size(); i++) {
            RoadEvaluationUnit u = units.get(i);
            ImportWorkItem it = batch.get(i);
            ars.add(buildAssessmentDto(it.row, "ROAD_SECTION_LEDGER", u.getId(), null, u.getId(), u.getRouteId(), u.getRouteCode()));
        }
        assessmentResultService.upsertBatchForImport(ars);
        return orphan;
    }

    private int flushKmTier(List<ImportWorkItem> batch, String tenantId, String projectId) {
        int orphan = 0;
        List<String> kmCodes = new ArrayList<>(batch.size());
        List<RoadSectionKm> rows = new ArrayList<>(batch.size());
        for (ImportWorkItem it : batch) {
            String routeId = it.networkMatch != null ? it.networkMatch.getRouteId() : null;
            if (routeId == null) {
                orphan++;
            }
            RoadSectionKm km = buildKmEntity(it.row, it.segmentCode, projectId, it.routeCodeDb);
            km.setRouteId(routeId);
            km.setLineId(null);
            String kmRemark = it.row.remarkCn;
            if (routeId == null) {
                kmRemark = mergeRemarkWithOrphanRouteHint(kmRemark, it.row.routeCode);
            }
            km.setRemark(trimRemark(kmRemark));
            kmCodes.add(km.getKmCode());
            rows.add(km);
        }
        Map<String, String> existingKm = loadKmCodeIdMap(tenantId, kmCodes);
        LocalDateTime now = LocalDateTime.now();
        for (RoadSectionKm km : rows) {
            String id = existingKm.get(km.getKmCode());
            km.setId(id != null ? id : IdUtils.uuid());
            km.setTenantId(tenantId);
            km.setCreatedAt(now);
            km.setUpdatedAt(now);
            km.setDeleted(false);
        }
        roadSectionKmMapper.upsertBatchWithGeom(tenantId, rows, null);
        List<AssessmentResultSaveDTO> ars = new ArrayList<>(batch.size());
        for (int i = 0; i < batch.size(); i++) {
            RoadSectionKm km = rows.get(i);
            ImportWorkItem it = batch.get(i);
            ars.add(buildAssessmentDto(it.row, "ROAD_SECTION_KM", km.getId(), null, null, km.getRouteId(), km.getRouteCode()));
        }
        assessmentResultService.upsertBatchForImport(ars);
        return orphan;
    }

    private int flushHmTier(List<ImportWorkItem> batch, String tenantId, String projectId) {
        int orphan = 0;
        List<String> hmCodes = new ArrayList<>(batch.size());
        List<RoadSectionHm> rows = new ArrayList<>(batch.size());
        for (ImportWorkItem it : batch) {
            String routeId = it.networkMatch != null ? it.networkMatch.getRouteId() : null;
            if (routeId == null) {
                orphan++;
            }
            RoadSectionHm hm = buildHmEntity(it.row, it.segmentCode, projectId, it.routeCodeDb);
            hm.setRouteId(routeId);
            hm.setLineId(null);
            hm.setKmId(null);
            String hmRemark = it.row.remarkCn;
            if (routeId == null) {
                hmRemark = mergeRemarkWithOrphanRouteHint(hmRemark, it.row.routeCode);
            }
            hm.setRemark(trimRemark(hmRemark));
            hmCodes.add(hm.getHmCode());
            rows.add(hm);
        }
        Map<String, String> existingHm = loadHmCodeIdMap(tenantId, hmCodes);
        LocalDateTime now = LocalDateTime.now();
        for (RoadSectionHm hm : rows) {
            String id = existingHm.get(hm.getHmCode());
            hm.setId(id != null ? id : IdUtils.uuid());
            hm.setTenantId(tenantId);
            hm.setCreatedAt(now);
            hm.setUpdatedAt(now);
            hm.setDeleted(false);
        }
        roadSectionHmMapper.upsertBatchWithGeom(tenantId, rows, null);
        List<AssessmentResultSaveDTO> ars = new ArrayList<>(batch.size());
        for (int i = 0; i < batch.size(); i++) {
            RoadSectionHm hm = rows.get(i);
            ImportWorkItem it = batch.get(i);
            ars.add(buildAssessmentDto(it.row, "ROAD_SECTION_HM", hm.getId(), null, null, hm.getRouteId(), hm.getRouteCode()));
        }
        assessmentResultService.upsertBatchForImport(ars);
        return orphan;
    }

    private static String tierShortLabel(SectionPackageTier tier) {
        switch (tier) {
            case LINE:
                return "线路级";
            case LEDGER:
                return "台账级";
            case KM:
                return "公里级";
            case HM:
                return "百米级";
            default:
                return "路段包";
        }
    }

    private Counts processTierShapefile(SectionPackageTier tier, Path shpFile, List<String> warnings, String projectId) throws Exception {
        String tenantId = TenantContextHolder.getTenantId();
        String tierLabel = tierShortLabel(tier);
        Map<String, Object> params = new HashMap<>();
        params.put("url", shpFile.toUri().toURL());
        DataStore store = DataStoreFinder.getDataStore(params);
        if (store == null) {
            throw new BizException("无法读取 Shapefile：" + shpFile.getFileName());
        }
        int entities = 0;
        int assessments = 0;
        int orphanRouteFeatures = 0;
        Set<String> seenSegmentCodes = new HashSet<>();
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
            log.info("[section-import] {}打开 typeName={} dbfCharset={} crs={}",
                    tierLabel, typeName, dbfCs.name(), source2d.getName());

            SimpleFeatureCollection collection = source.getFeatures();
            int featureHint = collection.size();
            if (featureHint >= 0) {
                log.info("[section-import] {}要素数量(估算) {}", tierLabel, featureHint);
            }
            List<String> rowErrors = new ArrayList<>();
            int index = 0;
            List<ParsedFeature> drafts = new ArrayList<>(IMPORT_DB_BATCH_SIZE);
            try (SimpleFeatureIterator it = collection.features()) {
                while (it.hasNext()) {
                    index++;
                    SimpleFeature feature = it.next();
                    Map<String, Object> attrs = buildAttrMap(feature);
                    try {
                        ParsedRow row = parseRow(attrs, feature, toWgs84, index, shpFile.getFileName().toString());
                        drafts.add(new ParsedFeature(index, row));
                        if (drafts.size() >= IMPORT_DB_BATCH_SIZE) {
                            List<ImportWorkItem> workBatch = buildWorkItems(tier, tenantId, drafts, projectId, seenSegmentCodes);
                            orphanRouteFeatures += flushWorkBatch(tier, workBatch, tenantId, projectId);
                            entities += workBatch.size();
                            assessments += workBatch.size();
                            drafts.clear();
                        }
                    } catch (BizException ex) {
                        throw ex;
                    } catch (DataAccessException ex) {
                        throw ex;
                    } catch (Exception ex) {
                        rethrowIfDatabaseFailure(ex);
                        rowErrors.add(baseErr(shpFile, index) + ex.getMessage());
                    }
                }
            }
            if (!drafts.isEmpty()) {
                List<ImportWorkItem> workBatch = buildWorkItems(tier, tenantId, drafts, projectId, seenSegmentCodes);
                orphanRouteFeatures += flushWorkBatch(tier, workBatch, tenantId, projectId);
                entities += workBatch.size();
                assessments += workBatch.size();
            }
            if (!rowErrors.isEmpty()) {
                throw new BizException(joinDetails(tierLabel + "数据校验失败", rowErrors));
            }
            if (orphanRouteFeatures > 0) {
                warnings.add(tierLabel + " " + shpFile.getFileName() + "：共 " + orphanRouteFeatures
                        + " 条要素的路线编号在路网中未登记，已写入（route_id 为空、route_code 已保留），请补录路网后关联。");
            }
            log.info("[section-import] {}完成 file={} 扫描要素={} 写入要素={} 评定行={}",
                    tierLabel, shpFile.getFileName(), index, entities, assessments);
        } finally {
            store.dispose();
        }
        return new Counts(entities, assessments);
    }

    private static String baseErr(Path shp, int index) {
        return shp.getFileName() + " 第" + index + "条要素：";
    }

    /**
     * PostgreSQL 等：任一条 SQL 失败后事务即中止，若再捕获异常并继续循环，后续语句会报 25P02。
     * 凡带 {@link SQLException} 链或 Spring 数据访问异常，必须立刻抛出，保留首条真实错误。
     */
    private static void rethrowIfDatabaseFailure(Throwable ex) {
        for (Throwable t = ex; t != null; t = t.getCause()) {
            if (t instanceof SQLException) {
                if (ex instanceof RuntimeException) {
                    throw (RuntimeException) ex;
                }
                throw new RuntimeException(ex);
            }
        }
    }

    private AssessmentResultSaveDTO buildAssessmentDto(ParsedRow row, String objectType, String objectId,
                                                       String sectionId, String unitId, String routeId, String routeCodeForDb) {
        AssessmentResultSaveDTO ar = new AssessmentResultSaveDTO();
        ar.setObjectType(objectType);
        ar.setObjectId(objectId);
        ar.setRouteId(routeId);
        ar.setRouteCode(routeCodeForDb);
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
        String kmCodeAttr;
        String hmCodeAttr;
        String geomWkt;
    }

    /**
     * 路段包线路编码（与库表 {@code route_code}、路网对齐）仅取自属性 <strong>{@code linkCode}</strong>。
     * {@link #attr} 同时匹配原始列名与全大写，故 DBF 导出为 {@code LINKCODE} 亦可识别。
     * <p>
     * 其它：adCode→行政区划，linkName→路线名称，startMp/endMp→起止桩号，
     * upDown→检测方向，techLevel→技术等级，roadType→路面类型，length→路段长度(m)，
     * roadWidth→路面宽度(m)，detMethod→检测方式，year→检测年度，MQI…remark 同名列。
     */
    private ParsedRow parseRow(Map<String, Object> attrs, SimpleFeature feature, MathTransform toWgs84, int index, String shpLabel) throws Exception {
        ParsedRow r = new ParsedRow();
        r.routeCode = pickString(attrs, "linkCode");
        if (isBlank(r.routeCode)) {
            throw new BizException("路线编号为空：路段 Shapefile 必须提供 linkCode 列（与路网 road_route.route_code 同义；DBF 大写列名 LINKCODE 亦可）");
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
        r.sectionCodeAttr = pickString(attrs, "SECTION_CODE", "LDBM", "section_code", "路段编码", "线路编码");
        r.unitCodeAttr = pickString(attrs, "UNIT_CODE", "DYBM", "unit_code", "台账编码", "评定单元编码");
        r.kmCodeAttr = pickString(attrs, "KM_CODE", "GJBM", "km_code");
        r.hmCodeAttr = pickString(attrs, "HM_CODE", "BM_CODE", "hm_code");

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

    private static String buildSectionCode(ParsedRow row, String routeCodeForAutoSegment) {
        if (row.sectionCodeAttr != null && !row.sectionCodeAttr.trim().isEmpty()) {
            return truncateCode(sanitizeCode(row.sectionCodeAttr.trim()), SECTION_CODE_MAX);
        }
        String base = sanitizeCode(routeCodeForAutoSegment + "-L-K" + stakePlain(row.startStake) + "-K" + stakePlain(row.endStake) + "-" + row.direction);
        if (base.length() <= SECTION_CODE_MAX) {
            return base;
        }
        return truncateWithHash(base, SECTION_CODE_MAX, "S");
    }

    private static String buildUnitCode(ParsedRow row, String routeCodeForAutoSegment) {
        if (row.unitCodeAttr != null && !row.unitCodeAttr.trim().isEmpty()) {
            return truncateCode(sanitizeCode(row.unitCodeAttr.trim()), UNIT_CODE_MAX);
        }
        String base = sanitizeCode(routeCodeForAutoSegment + "-LD-K" + stakePlain(row.startStake) + "-K" + stakePlain(row.endStake) + "-" + row.direction);
        if (base.length() <= UNIT_CODE_MAX) {
            return base;
        }
        return truncateWithHash(base, UNIT_CODE_MAX, "U");
    }

    private static String buildKmCode(ParsedRow row, String routeCodeForAutoSegment) {
        if (row.kmCodeAttr != null && !row.kmCodeAttr.trim().isEmpty()) {
            return truncateCode(sanitizeCode(row.kmCodeAttr.trim()), KM_CODE_MAX);
        }
        String base = sanitizeCode(routeCodeForAutoSegment + "-KM-K" + stakePlain(row.startStake) + "-K" + stakePlain(row.endStake) + "-" + row.direction);
        if (base.length() <= KM_CODE_MAX) {
            return base;
        }
        return truncateWithHash(base, KM_CODE_MAX, "K");
    }

    private static String buildHmCode(ParsedRow row, String routeCodeForAutoSegment) {
        if (row.hmCodeAttr != null && !row.hmCodeAttr.trim().isEmpty()) {
            return truncateCode(sanitizeCode(row.hmCodeAttr.trim()), HM_CODE_MAX);
        }
        String base = sanitizeCode(routeCodeForAutoSegment + "-HM-K" + stakePlain(row.startStake) + "-K" + stakePlain(row.endStake) + "-" + row.direction);
        if (base.length() <= HM_CODE_MAX) {
            return base;
        }
        return truncateWithHash(base, HM_CODE_MAX, "H");
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

    /** 路网无该路线编号时写入备注前缀，便于后续排查与补录路网后关联 */
    private static String mergeRemarkWithOrphanRouteHint(String baseRemark, String routeCode) {
        String rc = routeCode == null ? "" : routeCode.trim();
        String tag = "[路网未登记路线编号:" + rc + "]";
        if (baseRemark == null || baseRemark.trim().isEmpty()) {
            return tag;
        }
        return tag + " " + baseRemark.trim();
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
