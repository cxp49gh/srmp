package com.smartroad.srmp.gis.service;
import com.smartroad.srmp.gis.vo.GeoJsonFeatureCollectionVO;
import com.smartroad.srmp.roadasset.dto.*;
public interface GisRoadAssetLayerService {
    GeoJsonFeatureCollectionVO roadRoutes(RoadRouteQueryDTO query);
    GeoJsonFeatureCollectionVO roadSections(RoadSectionQueryDTO query);
    GeoJsonFeatureCollectionVO evaluationUnits(EvaluationUnitQueryDTO query);
}
