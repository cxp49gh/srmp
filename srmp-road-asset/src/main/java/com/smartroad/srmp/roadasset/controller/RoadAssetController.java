package com.smartroad.srmp.roadasset.controller;
import com.smartroad.srmp.common.core.R;
import com.smartroad.srmp.roadasset.dto.StakeLocationQueryDTO;
import com.smartroad.srmp.roadasset.service.RoadEvaluationUnitService;
import com.smartroad.srmp.roadasset.vo.StakeLocationVO;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import javax.annotation.Resource;

@RestController
@RequestMapping("/api/road-assets")
public class RoadAssetController {
    @Resource private RoadEvaluationUnitService evaluationUnitService;
    @GetMapping("/stake-location")
    public R<StakeLocationVO> locateByStake(@Validated StakeLocationQueryDTO query) {
        return R.ok(evaluationUnitService.locateByStake(query));
    }
}
