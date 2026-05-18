package com.smartroad.srmp.gis.vo;
import lombok.Data;
import java.util.ArrayList;
import java.util.List;

@Data
public class GeoJsonFeatureCollectionVO {
    private String type = "FeatureCollection";
    /** 可选图层模式：summary / cluster / detail / too_many。普通 GeoJSON 消费方可忽略。 */
    private String mode;
    /** 当前查询范围内匹配总数，不等于 features.size()。 */
    private Long total;
    /** 当前模式下后端最大返回数量。 */
    private Integer limit;
    /** 给前端展示的轻量提示，例如“请放大地图查看”。 */
    private String message;
    private List<GeoJsonFeatureVO> features = new ArrayList<>();
}
