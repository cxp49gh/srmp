package com.smartroad.srmp.gis.service.impl;

import com.smartroad.srmp.assessment.dto.AssessmentResultQueryDTO;
import com.smartroad.srmp.assessment.service.AssessmentResultService;
import com.smartroad.srmp.assessment.vo.AssessmentResultVO;
import com.smartroad.srmp.gis.service.GisAssessmentLayerService;
import com.smartroad.srmp.gis.util.GeoJsonParseUtils;
import com.smartroad.srmp.gis.util.GisStyleUtils;
import com.smartroad.srmp.gis.vo.GeoJsonFeatureCollectionVO;
import com.smartroad.srmp.gis.vo.GeoJsonFeatureVO;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.util.List;

@Service
public class GisAssessmentLayerServiceImpl implements GisAssessmentLayerService {
    @Resource private AssessmentResultService assessmentResultService;

    public GeoJsonFeatureCollectionVO assessmentResults(AssessmentResultQueryDTO query) {
        List<AssessmentResultVO> list = assessmentResultService.listForMap(query);
        GeoJsonFeatureCollectionVO fc = new GeoJsonFeatureCollectionVO();
        for (AssessmentResultVO item : list) {
            GeoJsonFeatureVO f = new GeoJsonFeatureVO();
            f.setId(item.getId());
            f.setGeometry(GeoJsonParseUtils.parse(item.getGeomGeoJson()));
            f.getProperties().put("objectType", "ASSESSMENT_RESULT");
            f.getProperties().put("routeCode", item.getRouteCode());
            f.getProperties().put("direction", item.getDirection());
            f.getProperties().put("unitId", item.getUnitId());
            f.getProperties().put("startStake", item.getStartStake());
            f.getProperties().put("endStake", item.getEndStake());
            f.getProperties().put("year", item.getYear());
            f.getProperties().put("mqi", item.getMqi());
            f.getProperties().put("sci", item.getSci());
            f.getProperties().put("pqi", item.getPqi());
            f.getProperties().put("bci", item.getBci());
            f.getProperties().put("tci", item.getTci());
            f.getProperties().put("pci", item.getPci());
            f.getProperties().put("rqi", item.getRqi());
            f.getProperties().put("rdi", item.getRdi());
            f.getProperties().put("grade", item.getGrade());
            f.getProperties().put("color", GisStyleUtils.colorByGrade(item.getGrade()));
            fc.getFeatures().add(f);
        }
        return fc;
    }
}
