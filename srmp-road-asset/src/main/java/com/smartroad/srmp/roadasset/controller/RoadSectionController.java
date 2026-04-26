package com.smartroad.srmp.roadasset.controller;
import com.smartroad.srmp.common.core.*;
import com.smartroad.srmp.roadasset.dto.*;
import com.smartroad.srmp.roadasset.service.RoadSectionService;
import com.smartroad.srmp.roadasset.vo.RoadSectionVO;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import javax.annotation.Resource;

@RestController
@RequestMapping("/api/road-sections")
public class RoadSectionController {
    @Resource private RoadSectionService service;
    @PostMapping("/page") public R<PageResult<RoadSectionVO>> page(@RequestBody RoadSectionQueryDTO query) { return R.ok(service.page(query)); }
    @GetMapping("/{id}") public R<RoadSectionVO> detail(@PathVariable String id) { return R.ok(service.getById(id)); }
    @PostMapping public R<String> create(@Validated @RequestBody RoadSectionSaveDTO dto) { return R.ok(service.create(dto)); }
    @PutMapping("/{id}") public R<Void> update(@PathVariable String id, @Validated @RequestBody RoadSectionSaveDTO dto) { service.update(id, dto); return R.ok(); }
    @DeleteMapping("/{id}") public R<Void> delete(@PathVariable String id) { service.delete(id); return R.ok(); }
}
