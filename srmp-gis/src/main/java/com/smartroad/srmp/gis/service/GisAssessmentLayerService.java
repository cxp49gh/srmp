package com.smartroad.srmp.gis.service;

import com.smartroad.srmp.assessment.dto.AssessmentResultQueryDTO;
import com.smartroad.srmp.gis.vo.GeoJsonFeatureCollectionVO;

public interface GisAssessmentLayerService {
    GeoJsonFeatureCollectionVO assessmentResults(AssessmentResultQueryDTO query);
}
