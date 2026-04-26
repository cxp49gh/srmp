package com.smartroad.srmp.disease.controller;

import com.smartroad.srmp.common.core.PageResult;
import com.smartroad.srmp.common.core.R;
import com.smartroad.srmp.disease.dto.DiseaseQueryDTO;
import com.smartroad.srmp.disease.dto.DiseaseReviewDTO;
import com.smartroad.srmp.disease.dto.DiseaseSaveDTO;
import com.smartroad.srmp.disease.service.DiseaseRecordService;
import com.smartroad.srmp.disease.vo.DiseaseRecordVO;
import com.smartroad.srmp.disease.vo.DiseaseStatisticsVO;
import org.springframework.web.bind.annotation.*;

import javax.annotation.Resource;

@RestController
@RequestMapping("/api/diseases")
public class DiseaseRecordController {
    @Resource private DiseaseRecordService service;

    @PostMapping("/page")
    public R<PageResult<DiseaseRecordVO>> page(@RequestBody DiseaseQueryDTO query) { return R.ok(service.page(query)); }
    @GetMapping("/{id}")
    public R<DiseaseRecordVO> get(@PathVariable String id) { return R.ok(service.getById(id)); }
    @PostMapping
    public R<String> create(@RequestBody DiseaseSaveDTO dto) { return R.ok(service.create(dto)); }
    @PutMapping("/{id}")
    public R<Void> update(@PathVariable String id, @RequestBody DiseaseSaveDTO dto) { service.update(id, dto); return R.ok(); }
    @DeleteMapping("/{id}")
    public R<Void> delete(@PathVariable String id) { service.delete(id); return R.ok(); }
    @PostMapping("/review")
    public R<Void> review(@RequestBody DiseaseReviewDTO dto) { service.review(dto); return R.ok(); }
    @PostMapping("/statistics")
    public R<DiseaseStatisticsVO> statistics(@RequestBody DiseaseQueryDTO query) { return R.ok(service.statistics(query)); }
}
