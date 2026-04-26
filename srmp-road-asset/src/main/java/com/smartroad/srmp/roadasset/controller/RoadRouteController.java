package com.smartroad.srmp.roadasset.controller;
import com.smartroad.srmp.common.core.*;
import com.smartroad.srmp.roadasset.dto.*;
import com.smartroad.srmp.roadasset.service.RoadRouteService;
import com.smartroad.srmp.roadasset.vo.RoadRouteVO;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import javax.annotation.Resource;

@RestController
@RequestMapping("/api/road-routes")
public class RoadRouteController {
    @Resource private RoadRouteService service;
    @PostMapping("/page") public R<PageResult<RoadRouteVO>> page(@RequestBody RoadRouteQueryDTO query) { return R.ok(service.page(query)); }
    @GetMapping("/{id}") public R<RoadRouteVO> detail(@PathVariable String id) { return R.ok(service.getById(id)); }
    @PostMapping public R<String> create(@Validated @RequestBody RoadRouteSaveDTO dto) { return R.ok(service.create(dto)); }
    @PutMapping("/{id}") public R<Void> update(@PathVariable String id, @Validated @RequestBody RoadRouteSaveDTO dto) { service.update(id, dto); return R.ok(); }
    @DeleteMapping("/{id}") public R<Void> delete(@PathVariable String id) { service.delete(id); return R.ok(); }
}
