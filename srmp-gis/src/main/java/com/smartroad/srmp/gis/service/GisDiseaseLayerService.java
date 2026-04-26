package com.smartroad.srmp.gis.service;

import com.smartroad.srmp.disease.dto.DiseaseQueryDTO;
import com.smartroad.srmp.gis.vo.GeoJsonFeatureCollectionVO;

public interface GisDiseaseLayerService {
    GeoJsonFeatureCollectionVO diseases(DiseaseQueryDTO query);
}
