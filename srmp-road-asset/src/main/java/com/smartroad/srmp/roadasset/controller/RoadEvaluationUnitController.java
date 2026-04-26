package com.smartroad.srmp.roadasset.controller;
import com.smartroad.srmp.common.core.*;
import com.smartroad.srmp.roadasset.dto.*;
import com.smartroad.srmp.roadasset.service.RoadEvaluationUnitService;
import com.smartroad.srmp.roadasset.vo.*;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import javax.annotation.Resource;

@RestController
@RequestMapping("/api/evaluation-units")
public class RoadEvaluationUnitController {
    @Resource private RoadEvaluationUnitService service;
    @PostMapping("/page") public R<PageResult<RoadEvaluationUnitVO>> page(@RequestBody EvaluationUnitQueryDTO query) { return R.ok(service.page(query)); }
    @GetMapping("/{id}") public R<RoadEvaluationUnitVO> detail(@PathVariable String id) { return R.ok(service.getById(id)); }
    @PostMapping public R<String> create(@Validated @RequestBody EvaluationUnitSaveDTO dto) { return R.ok(service.create(dto)); }
    @PutMapping("/{id}") public R<Void> update(@PathVariable String id, @Validated @RequestBody EvaluationUnitSaveDTO dto) { service.update(id, dto); return R.ok(); }
    @DeleteMapping("/{id}") public R<Void> delete(@PathVariable String id) { service.delete(id); return R.ok(); }
    @GetMapping("/stake-location") public R<StakeLocationVO> locateByStake(@Validated StakeLocationQueryDTO query) { return R.ok(service.locateByStake(query)); }
}
