package com.smartroad.srmp.assessment.controller;

import com.smartroad.srmp.assessment.dto.IndexResultQueryDTO;
import com.smartroad.srmp.assessment.dto.IndexResultSaveDTO;
import com.smartroad.srmp.assessment.service.IndexResultService;
import com.smartroad.srmp.assessment.vo.IndexResultVO;
import com.smartroad.srmp.common.core.PageResult;
import com.smartroad.srmp.common.core.R;
import org.springframework.web.bind.annotation.*;

import javax.annotation.Resource;

@RestController
@RequestMapping("/api/index-results")
public class IndexResultController {
    @Resource private IndexResultService service;

    @PostMapping("/page")
    public R<PageResult<IndexResultVO>> page(@RequestBody IndexResultQueryDTO query) { return R.ok(service.page(query)); }
    @GetMapping("/{id}")
    public R<IndexResultVO> get(@PathVariable String id) { return R.ok(service.getById(id)); }
    @PostMapping
    public R<String> create(@RequestBody IndexResultSaveDTO dto) { return R.ok(service.create(dto)); }
    @PutMapping("/{id}")
    public R<Void> update(@PathVariable String id, @RequestBody IndexResultSaveDTO dto) { service.update(id, dto); return R.ok(); }
    @DeleteMapping("/{id}")
    public R<Void> delete(@PathVariable String id) { service.delete(id); return R.ok(); }
}
