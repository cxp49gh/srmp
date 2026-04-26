package com.smartroad.srmp.assessment.controller;

import com.smartroad.srmp.assessment.dto.AssessmentResultQueryDTO;
import com.smartroad.srmp.assessment.dto.AssessmentResultSaveDTO;
import com.smartroad.srmp.assessment.service.AssessmentResultService;
import com.smartroad.srmp.assessment.vo.AssessmentResultVO;
import com.smartroad.srmp.assessment.vo.AssessmentSummaryVO;
import com.smartroad.srmp.common.core.PageResult;
import com.smartroad.srmp.common.core.R;
import org.springframework.web.bind.annotation.*;

import javax.annotation.Resource;

@RestController
@RequestMapping("/api/assessment-results")
public class AssessmentResultController {
    @Resource private AssessmentResultService service;

    @PostMapping("/page")
    public R<PageResult<AssessmentResultVO>> page(@RequestBody AssessmentResultQueryDTO query) { return R.ok(service.page(query)); }
    @GetMapping("/{id}")
    public R<AssessmentResultVO> get(@PathVariable String id) { return R.ok(service.getById(id)); }
    @PostMapping
    public R<String> create(@RequestBody AssessmentResultSaveDTO dto) { return R.ok(service.create(dto)); }
    @PutMapping("/{id}")
    public R<Void> update(@PathVariable String id, @RequestBody AssessmentResultSaveDTO dto) { service.update(id, dto); return R.ok(); }
    @DeleteMapping("/{id}")
    public R<Void> delete(@PathVariable String id) { service.delete(id); return R.ok(); }
    @PostMapping("/statistics")
    public R<AssessmentSummaryVO> statistics(@RequestBody AssessmentResultQueryDTO query) { return R.ok(service.summary(query)); }
}
