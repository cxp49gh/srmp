package com.smartroad.srmp.gis.service.impl;

import com.smartroad.srmp.gis.service.GisRoadAssetLayerService;
import com.smartroad.srmp.gis.util.GeoJsonParseUtils;
import com.smartroad.srmp.gis.vo.*;
import com.smartroad.srmp.roadasset.dto.*;
import com.smartroad.srmp.roadasset.service.*;
import com.smartroad.srmp.roadasset.vo.*;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.util.List;

@Service
public class GisRoadAssetLayerServiceImpl implements GisRoadAssetLayerService {
    @Resource private RoadRouteService roadRouteService;
    @Resource private RoadSectionService roadSectionService;
    @Resource private RoadEvaluationUnitService evaluationUnitService;

    public GeoJsonFeatureCollectionVO roadRoutes(RoadRouteQueryDTO query) {
        List<RoadRouteVO> list = roadRouteService.listForMap(query);
        GeoJsonFeatureCollectionVO fc = new GeoJsonFeatureCollectionVO();
        for (RoadRouteVO item : list) {
            GeoJsonFeatureVO f = new GeoJsonFeatureVO();
            f.setId(item.getId());
            f.setGeometry(GeoJsonParseUtils.parse(item.getGeomGeoJson()));
            f.getProperties().put("objectType", "ROAD_ROUTE");
            f.getProperties().put("routeCode", item.getRouteCode());
            f.getProperties().put("routeName", item.getRouteName());
            f.getProperties().put("routeType", item.getRouteType());
            f.getProperties().put("technicalGrade", item.getTechnicalGrade());
            f.getProperties().put("startStake", item.getStartStake());
            f.getProperties().put("endStake", item.getEndStake());
            f.getProperties().put("lengthKm", item.getLengthKm());
            fc.getFeatures().add(f);
        }
        return fc;
    }

    public GeoJsonFeatureCollectionVO roadSections(RoadSectionQueryDTO query) {
        List<RoadSectionVO> list = roadSectionService.listForMap(query);
        GeoJsonFeatureCollectionVO fc = new GeoJsonFeatureCollectionVO();
        for (RoadSectionVO item : list) {
            GeoJsonFeatureVO f = new GeoJsonFeatureVO();
            f.setId(item.getId());
            f.setGeometry(GeoJsonParseUtils.parse(item.getGeomGeoJson()));
            f.getProperties().put("objectType", "ROAD_SECTION");
            f.getProperties().put("routeCode", item.getRouteCode());
            f.getProperties().put("sectionCode", item.getSectionCode());
            f.getProperties().put("sectionName", item.getSectionName());
            f.getProperties().put("direction", item.getDirection());
            f.getProperties().put("startStake", item.getStartStake());
            f.getProperties().put("endStake", item.getEndStake());
            f.getProperties().put("pavementType", item.getPavementType());
            fc.getFeatures().add(f);
        }
        return fc;
    }

    public GeoJsonFeatureCollectionVO evaluationUnits(EvaluationUnitQueryDTO query) {
        List<RoadEvaluationUnitVO> list = evaluationUnitService.listForMap(query);
        GeoJsonFeatureCollectionVO fc = new GeoJsonFeatureCollectionVO();
        for (RoadEvaluationUnitVO item : list) {
            GeoJsonFeatureVO f = new GeoJsonFeatureVO();
            f.setId(item.getId());
            f.setGeometry(GeoJsonParseUtils.parse(item.getGeomGeoJson()));
            f.getProperties().put("objectType", "EVALUATION_UNIT");
            f.getProperties().put("routeCode", item.getRouteCode());
            f.getProperties().put("unitCode", item.getUnitCode());
            f.getProperties().put("direction", item.getDirection());
            f.getProperties().put("laneNo", item.getLaneNo());
            f.getProperties().put("startStake", item.getStartStake());
            f.getProperties().put("endStake", item.getEndStake());
            f.getProperties().put("lengthM", item.getLengthM());
            fc.getFeatures().add(f);
        }
        return fc;
    }
}
