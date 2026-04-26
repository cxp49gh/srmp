package com.smartroad.srmp.gis.controller;

import com.smartroad.srmp.assessment.dto.AssessmentResultQueryDTO;
import com.smartroad.srmp.common.core.R;
import com.smartroad.srmp.disease.dto.DiseaseQueryDTO;
import com.smartroad.srmp.gis.service.GisAssessmentLayerService;
import com.smartroad.srmp.gis.service.GisDiseaseLayerService;
import com.smartroad.srmp.gis.service.GisMapSupportService;
import com.smartroad.srmp.gis.service.GisRoadAssetLayerService;
import com.smartroad.srmp.gis.vo.GeoJsonFeatureCollectionVO;
import com.smartroad.srmp.roadasset.dto.EvaluationUnitQueryDTO;
import com.smartroad.srmp.roadasset.dto.RoadRouteQueryDTO;
import com.smartroad.srmp.roadasset.dto.RoadSectionQueryDTO;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import javax.annotation.Resource;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/gis")
public class GisMapController {

    @Resource
    private GisRoadAssetLayerService roadAssetLayerService;

    @Resource
    private GisDiseaseLayerService diseaseLayerService;

    @Resource
    private GisAssessmentLayerService assessmentLayerService;

    @Resource
    private GisMapSupportService gisMapSupportService;

    @GetMapping("/layers")
    public R<List<String>> layers() {
        return R.ok(Arrays.asList(
                "ROAD_ROUTE",
                "ROAD_SECTION",
                "EVALUATION_UNIT",
                "DISEASE",
                "ASSESSMENT",
                "INSPECTION_TRACK"
        ));
    }

    @GetMapping("/road-routes")
    public R<GeoJsonFeatureCollectionVO> roadRoutes(RoadRouteQueryDTO query) {
        return R.ok(roadAssetLayerService.roadRoutes(query));
    }

    @GetMapping("/road-sections")
    public R<GeoJsonFeatureCollectionVO> roadSections(RoadSectionQueryDTO query) {
        return R.ok(roadAssetLayerService.roadSections(query));
    }

    @GetMapping("/evaluation-units")
    public R<GeoJsonFeatureCollectionVO> evaluationUnits(EvaluationUnitQueryDTO query) {
        return R.ok(roadAssetLayerService.evaluationUnits(query));
    }

    @GetMapping("/diseases")
    public R<GeoJsonFeatureCollectionVO> diseases(DiseaseQueryDTO query) {
        return R.ok(diseaseLayerService.diseases(query));
    }

    @GetMapping("/assessment-results")
    public R<GeoJsonFeatureCollectionVO> assessmentResults(AssessmentResultQueryDTO query) {
        return R.ok(assessmentLayerService.assessmentResults(query));
    }

    @PostMapping("/spatial-query")
    public R<GeoJsonFeatureCollectionVO> spatialQuery(@RequestBody(required = false) Map<String, Object> query) {
        return R.ok(gisMapSupportService.spatialQuery(query));
    }

    @PostMapping("/map-statistics")
    public R<Map<String, Object>> mapStatistics(@RequestBody(required = false) Map<String, Object> request) {
        return R.ok(gisMapSupportService.mapStatistics(request));
    }

    @GetMapping("/object-detail")
    public R<Map<String, Object>> objectDetail(@RequestParam String objectType, @RequestParam String id) {
        return R.ok(gisMapSupportService.objectDetail(objectType, id));
    }
}
