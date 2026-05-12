package com.smartroad.srmp.roadasset.service.impl;

import com.smartroad.srmp.common.exception.BizException;
import com.smartroad.srmp.roadasset.dto.RoadRouteSaveDTO;
import com.smartroad.srmp.roadasset.mapper.RoadRouteMapper;
import com.smartroad.srmp.roadasset.service.RoadNetworkImportService;
import com.smartroad.srmp.roadasset.service.RoadRouteService;
import com.smartroad.srmp.roadasset.vo.ImportNetworkResultVO;
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
import org.opengis.feature.simple.SimpleFeature;
import org.opengis.feature.simple.SimpleFeatureType;
import org.opengis.feature.type.AttributeDescriptor;
import org.opengis.feature.type.GeometryDescriptor;
import org.opengis.referencing.crs.CoordinateReferenceSystem;
import org.opengis.referencing.crs.SingleCRS;
import org.opengis.referencing.operation.MathTransform;
import org.springframework.beans.factory.annotation.Value;
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
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.stream.Stream;

@Service
public class RoadNetworkImportServiceImpl implements RoadNetworkImportService {

    private static final long MAX_EXTRACT_BYTES = 500L * 1024 * 1024;
    private static final int REMARK_MAX = 2000;
    private static final String ROUTE_TYPE_IMPORT = "OTHER";

    @Resource
    private RoadRouteMapper roadRouteMapper;
    @Resource
    private RoadRouteService roadRouteService;

    /** Shapefile .dbf 文本字段编码；UTF-8 与 ArcGIS Pro / QGIS 新版导出一致，老数据可改为 GBK 或 GB18030。 */
    @Value("${srmp.import.network.dbf-charset:UTF-8}")
    private String shapefileDbfCharset;

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

    @Override
    @Transactional(rollbackFor = Exception.class)
    public ImportNetworkResultVO importNetwork(MultipartFile file) {
        return importNetwork(file, null);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public ImportNetworkResultVO importNetwork(MultipartFile file, String projectId) {
        if (file == null || file.isEmpty()) {
            throw new BizException("导入文件不能为空");
        }
        String original = file.getOriginalFilename();
        if (original == null || !original.toLowerCase(Locale.ROOT).endsWith(".tar")) {
            throw new BizException("仅支持 .tar 格式");
        }
        Path tempDir = null;
        try {
            tempDir = Files.createTempDirectory("srmp-network-import-");
            try (InputStream in = new BufferedInputStream(file.getInputStream())) {
                safeUntar(in, tempDir);
            }
            Path shpFile = findSingleShapefileGroup(tempDir);
            return importFromShapefile(shpFile, projectId);
        } catch (BizException e) {
            throw e;
        } catch (Exception e) {
            throw new BizException("路网导入失败：" + e.getMessage());
        } finally {
            if (tempDir != null) {
                deleteRecursivelyQuiet(tempDir);
            }
        }
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

    private Path findSingleShapefileGroup(Path root) throws IOException {
        List<Path> shpFiles = new ArrayList<>();
        try (Stream<Path> walk = Files.walk(root)) {
            walk.filter(p -> p.getFileName().toString().toLowerCase(Locale.ROOT).endsWith(".shp")).forEach(shpFiles::add);
        }
        if (shpFiles.isEmpty()) {
            throw new BizException("压缩包内未找到 .shp 文件");
        }
        List<Path> complete = new ArrayList<>();
        for (Path shp : shpFiles) {
            if (isCompleteShapefileGroup(shp)) {
                complete.add(shp);
            } else {
                throw new BizException("缺少 .dbf 或 .shx 或 .prj 文件，无法导入");
            }
        }
        if (complete.size() > 1) {
            throw new BizException("压缩包内存在多组 Shapefile，请只保留一组路网数据");
        }
        return complete.get(0);
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

    private ImportNetworkResultVO importFromShapefile(Path shpFile, String projectId) throws Exception {
        Map<String, Object> params = new HashMap<>();
        params.put("url", shpFile.toUri().toURL());
        DataStore store = DataStoreFinder.getDataStore(params);
        if (store == null) {
            throw new BizException("无法读取 Shapefile");
        }
        ImportNetworkResultVO result = new ImportNetworkResultVO();
        List<String> warnings = new ArrayList<>();
        try {
            if (store instanceof org.geotools.data.shapefile.ShapefileDataStore) {
                ((org.geotools.data.shapefile.ShapefileDataStore) store).setCharset(resolveDbfCharset());
            }
            String typeName = store.getTypeNames()[0];
            SimpleFeatureSource source = store.getFeatureSource(typeName);
            SimpleFeatureType schema = source.getSchema();
            if (!hasAttributeIgnoreCase(schema, "ROUTE_NO")) {
                throw new BizException("缺少必填属性列 ROUTE_NO");
            }
            CoordinateReferenceSystem sourceCrs = schema.getCoordinateReferenceSystem();
            if (sourceCrs == null) {
                throw new BizException("缺少 .prj 文件，无法确定坐标系");
            }
            SingleCRS horizontal = CRS.getHorizontalCRS(sourceCrs);
            CoordinateReferenceSystem source2d = horizontal != null ? horizontal : sourceCrs;
            MathTransform toWgs84 = CRS.findMathTransform(source2d, DefaultGeographicCRS.WGS84, true);

            SimpleFeatureCollection collection = source.getFeatures();
            List<String> rowErrors = new ArrayList<>();
            Set<String> seenImportKeys = new HashSet<>();
            List<RoadRouteSaveDTO> dtos = new ArrayList<>();
            int skipped = 0;
            int index = 0;
            try (SimpleFeatureIterator it = collection.features()) {
                while (it.hasNext()) {
                    index++;
                    SimpleFeature feature = it.next();
                    Map<String, Object> attrs = normalizeAttributes(feature);
                    String routeNo = stringAttr(attrs, "ROUTE_NO");
                    if (routeNo == null || routeNo.trim().isEmpty()) {
                        rowErrors.add("第" + index + "条要素：路线编号 ROUTE_NO 为空");
                        continue;
                    }
                    String routeCode = routeNo.trim();

                    String nameChn = stringAttr(attrs, "NAME_CHN");
                    String nameEng = stringAttr(attrs, "NAME_ENG");
                    String routeName = firstNonBlank(nameChn, nameEng);
                    if (routeName == null) {
                        rowErrors.add("第" + index + "条要素：要素缺少 NAME_CHN 与 NAME_ENG");
                        continue;
                    }

                    Geometry geom = (Geometry) feature.getDefaultGeometry();
                    if (geom == null || geom.isEmpty()) {
                        rowErrors.add("第" + index + "条要素：几何为空");
                        continue;
                    }
                    LineString line = toLineString(geom);
                    if (line == null) {
                        skipped++;
                        continue;
                    }
                    LineString line2d = toXYLineString(line);
                    Geometry transformed;
                    try {
                        transformed = JTS.transform(line2d, toWgs84);
                    } catch (Exception ex) {
                        rowErrors.add("第" + index + "条要素：坐标变换失败：" + ex.getMessage());
                        continue;
                    }
                    LineString line4326 = flattenToLineString2D(transformed);
                    if (line4326 == null) {
                        rowErrors.add("第" + index + "条要素：坐标变换后非线要素");
                        continue;
                    }
                    String wkt = lineStringToWktLonLat2D(line4326, 6);

                    RoadRouteSaveDTO dto = new RoadRouteSaveDTO();
                    dto.setRouteCode(routeCode);
                    dto.setRouteName(routeName.trim());
                    dto.setRouteType(ROUTE_TYPE_IMPORT);
                    dto.setAdminGrade(null);
                    dto.setTechnicalGrade(null);
                    dto.setAdcode(null);
                    dto.setManageOrgId(null);
                    dto.setGeomWkt(wkt);

                    BigDecimal startStake = decimalAttr(attrs, "START_MP");
                    BigDecimal endStake = decimalAttr(attrs, "END_MP");
                    if (startStake != null && endStake != null && startStake.compareTo(endStake) > 0) {
                        dto.setStartStake(endStake);
                        dto.setEndStake(startStake);
                        warnings.add("路线 " + routeCode + "：起止桩号已按从小到大交换");
                    } else {
                        dto.setStartStake(startStake);
                        dto.setEndStake(endStake);
                    }

                    BigDecimal lengthKm = lengthKmFromDbf(attrs, line4326);
                    dto.setLengthKm(lengthKm);

                    BigDecimal lengthAttrM = decimalAttr(attrs, "LENGTH");
                    if (lengthAttrM != null && lengthKm != null) {
                        double geomM = lengthKm.doubleValue() * 1000.0;
                        double attrM = lengthAttrM.doubleValue();
                        if (geomM > 1 && attrM > 1) {
                            double diff = Math.abs(geomM - attrM) / Math.max(geomM, attrM);
                            if (diff > 0.05) {
                                warnings.add("路线 " + routeCode + "：几何估算长度与 LENGTH 属性偏差超过 5%");
                            }
                        }
                    }

                    dto.setRemark(buildRemark(attrs));
                    if (projectId != null && !projectId.trim().isEmpty()) {
                        dto.setProjectId(projectId.trim());
                    }

                    String importKey = importDuplicateKey(routeCode, dto.getStartStake(), dto.getEndStake());
                    if (!seenImportKeys.add(importKey)) {
                        throw new BizException(
                                "压缩包内存在重复路线（路线编号+起桩+止桩）："
                                        + routeCode
                                        + "，起桩 "
                                        + stakeLabel(dto.getStartStake())
                                        + "，止桩 "
                                        + stakeLabel(dto.getEndStake()));
                    }
                    dtos.add(dto);
                }
            }
            if (!rowErrors.isEmpty()) {
                throw new BizException(joinDetails("数据校验失败", rowErrors));
            }
            if (dtos.isEmpty()) {
                throw new BizException("没有可导入的路线要素");
            }

            String tenantId = TenantContextHolder.getTenantId();
            int inserted = 0;
            int updated = 0;
            for (RoadRouteSaveDTO dto : dtos) {
                String id = roadRouteMapper.selectIdByTenantAndRouteCode(tenantId, dto.getRouteCode());
                if (id == null) {
                    roadRouteService.create(dto);
                    inserted++;
                } else {
                    roadRouteService.update(id, dto);
                    updated++;
                }
            }
            result.setInsertedCount(inserted);
            result.setUpdatedCount(updated);
            result.setSkippedCount(skipped);
            result.setWarnings(warnings);
            return result;
        } finally {
            store.dispose();
        }
    }

    /** 与入库桩号一致：起止均非空且起点桩大于止点桩时交换后再组键。 */
    private static String importDuplicateKey(String routeCode, BigDecimal startStake, BigDecimal endStake) {
        BigDecimal s = startStake;
        BigDecimal e = endStake;
        if (s != null && e != null && s.compareTo(e) > 0) {
            BigDecimal t = s;
            s = e;
            e = t;
        }
        return routeCode + "\0" + normStakeKey(s) + "\0" + normStakeKey(e);
    }

    private static String normStakeKey(BigDecimal v) {
        return v == null ? "" : v.stripTrailingZeros().toPlainString();
    }

    private static String stakeLabel(BigDecimal v) {
        return v == null ? "空" : v.stripTrailingZeros().toPlainString();
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

    private static Map<String, Object> normalizeAttributes(SimpleFeature feature) {
        Map<String, Object> map = new LinkedHashMap<>();
        for (AttributeDescriptor ad : feature.getFeatureType().getAttributeDescriptors()) {
            String name = ad.getLocalName();
            if (name == null) {
                continue;
            }
            if (ad instanceof GeometryDescriptor) {
                continue;
            }
            Object v = feature.getAttribute(name);
            map.put(name.trim().toUpperCase(Locale.ROOT), v);
        }
        return map;
    }

    private static String stringAttr(Map<String, Object> attrs, String key) {
        Object v = attrs.get(key.toUpperCase(Locale.ROOT));
        if (v == null) {
            return null;
        }
        String s = String.valueOf(v).trim();
        return s.isEmpty() ? null : s;
    }

    private static BigDecimal decimalAttr(Map<String, Object> attrs, String key) {
        Object v = attrs.get(key.toUpperCase(Locale.ROOT));
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
        } catch (NumberFormatException e) {
            return null;
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

    private static boolean hasAttributeIgnoreCase(SimpleFeatureType schema, String want) {
        String u = want.toUpperCase(Locale.ROOT);
        for (AttributeDescriptor ad : schema.getAttributeDescriptors()) {
            if (ad instanceof GeometryDescriptor) {
                continue;
            }
            if (u.equals(ad.getLocalName().toUpperCase(Locale.ROOT))) {
                return true;
            }
        }
        return false;
    }

    /**
     * 库表为 {@code GEOMETRY(LineString,4326)}，仅接受二维线；变换结果再压成 2D 折线。
     */
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

    /** 手写 2D WKT，避免 {@link org.locationtech.jts.io.WKTWriter} 对带 Z/M 序列输出维数与 PostGIS typmod 不一致。 */
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

    /**
     * Shapefile 常为 3D/带测度坐标序列；与 {@link JTS#transform} 组合时易触发
     * {@code Invalid output dimension (must be 2 to 4)}。仅保留 X/Y 再变换。
     */
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

    private static BigDecimal lengthKmFromDbf(Map<String, Object> attrs, LineString line4326) {
        BigDecimal lengthM = decimalAttr(attrs, "LENGTH");
        if (lengthM != null && lengthM.compareTo(BigDecimal.ZERO) > 0) {
            return lengthM.divide(BigDecimal.valueOf(1000), 3, RoundingMode.HALF_UP);
        }
        double meters = lengthMetersHaversine(line4326);
        return BigDecimal.valueOf(meters / 1000.0).setScale(3, RoundingMode.HALF_UP);
    }

    private static double lengthMetersHaversine(LineString ls) {
        double sum = 0;
        for (int i = 1; i < ls.getNumPoints(); i++) {
            sum += haversineMeters(
                    ls.getCoordinateN(i - 1).y, ls.getCoordinateN(i - 1).x,
                    ls.getCoordinateN(i).y, ls.getCoordinateN(i).x);
        }
        return sum;
    }

    private static double haversineMeters(double lat1, double lon1, double lat2, double lon2) {
        final double R = 6371000.0;
        double p1 = Math.toRadians(lat1);
        double p2 = Math.toRadians(lat2);
        double dp = Math.toRadians(lat2 - lat1);
        double dl = Math.toRadians(lon2 - lon1);
        double a = Math.sin(dp / 2) * Math.sin(dp / 2)
                + Math.cos(p1) * Math.cos(p2) * Math.sin(dl / 2) * Math.sin(dl / 2);
        double c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
        return R * c;
    }

    private static String buildRemark(Map<String, Object> attrs) {
        List<String> parts = new ArrayList<>();
        Object id = attrs.get("ID");
        if (id != null && String.valueOf(id).trim().length() > 0) {
            parts.add("sourceId=" + String.valueOf(id).trim());
        }
        String dir = stringAttr(attrs, "DIRECTION");
        if (dir != null) {
            parts.add("direction=" + dir);
        }
        String ig = stringAttr(attrs, "IG_DIR");
        if (ig != null) {
            parts.add("igDir=" + ig);
        }
        if (parts.isEmpty()) {
            return null;
        }
        String s = String.join(" | ", parts);
        if (s.length() > REMARK_MAX) {
            return s.substring(0, REMARK_MAX);
        }
        return s;
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
            // best-effort cleanup
        }
    }
}
