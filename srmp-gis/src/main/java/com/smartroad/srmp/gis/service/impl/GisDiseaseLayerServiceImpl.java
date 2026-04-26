package com.smartroad.srmp.gis.service.impl;

import com.smartroad.srmp.disease.dto.DiseaseQueryDTO;
import com.smartroad.srmp.disease.service.DiseaseRecordService;
import com.smartroad.srmp.disease.vo.DiseaseRecordVO;
import com.smartroad.srmp.gis.service.GisDiseaseLayerService;
import com.smartroad.srmp.gis.util.GeoJsonParseUtils;
import com.smartroad.srmp.gis.util.GisStyleUtils;
import com.smartroad.srmp.gis.vo.GeoJsonFeatureCollectionVO;
import com.smartroad.srmp.gis.vo.GeoJsonFeatureVO;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.util.List;

@Service
public class GisDiseaseLayerServiceImpl implements GisDiseaseLayerService {
    @Resource private DiseaseRecordService diseaseRecordService;

    public GeoJsonFeatureCollectionVO diseases(DiseaseQueryDTO query) {
        List<DiseaseRecordVO> list = diseaseRecordService.listForMap(query);
        GeoJsonFeatureCollectionVO fc = new GeoJsonFeatureCollectionVO();
        for (DiseaseRecordVO item : list) {
            GeoJsonFeatureVO f = new GeoJsonFeatureVO();
            f.setId(item.getId());
            f.setGeometry(GeoJsonParseUtils.parse(item.getGeomGeoJson()));
            f.getProperties().put("objectType", "DISEASE");
            f.getProperties().put("routeCode", item.getRouteCode());
            f.getProperties().put("direction", item.getDirection());
            f.getProperties().put("laneNo", item.getLaneNo());
            f.getProperties().put("startStake", item.getStartStake());
            f.getProperties().put("endStake", item.getEndStake());
            f.getProperties().put("diseaseCategory", item.getDiseaseCategory());
            f.getProperties().put("diseaseType", item.getDiseaseType());
            f.getProperties().put("diseaseName", item.getDiseaseName());
            f.getProperties().put("severity", item.getSeverity());
            f.getProperties().put("quantity", item.getQuantity());
            f.getProperties().put("measureUnit", item.getMeasureUnit());
            f.getProperties().put("status", item.getStatus());
            f.getProperties().put("color", GisStyleUtils.colorBySeverity(item.getSeverity()));
            fc.getFeatures().add(f);
        }
        return fc;
    }
}
