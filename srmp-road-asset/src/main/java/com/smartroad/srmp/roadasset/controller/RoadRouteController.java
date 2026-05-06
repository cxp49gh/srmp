package com.smartroad.srmp.roadasset.controller;
import com.smartroad.srmp.common.core.*;
import com.smartroad.srmp.roadasset.dto.*;
import com.smartroad.srmp.roadasset.service.RoadNetworkImportService;
import com.smartroad.srmp.roadasset.service.RoadRouteService;
import com.smartroad.srmp.roadasset.vo.ImportNetworkResultVO;
import com.smartroad.srmp.roadasset.vo.RoadRouteVO;
import org.springframework.http.MediaType;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import javax.annotation.Resource;

@RestController
@RequestMapping("/api/road-routes")
public class RoadRouteController {
    @Resource private RoadRouteService service;
    @Resource private RoadNetworkImportService roadNetworkImportService;
    @PostMapping("/page") public R<PageResult<RoadRouteVO>> page(@RequestBody RoadRouteQueryDTO query) { return R.ok(service.page(query)); }
    @GetMapping("/{id}") public R<RoadRouteVO> detail(@PathVariable String id) { return R.ok(service.getById(id)); }
    @PostMapping public R<String> create(@Validated @RequestBody RoadRouteSaveDTO dto) { return R.ok(service.create(dto)); }
    @PutMapping("/{id}") public R<Void> update(@PathVariable String id, @Validated @RequestBody RoadRouteSaveDTO dto) { service.update(id, dto); return R.ok(); }
    @DeleteMapping("/{id}") public R<Void> delete(@PathVariable String id) { service.delete(id); return R.ok(); }

    @PostMapping(value = "/import-network", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public R<ImportNetworkResultVO> importNetwork(@RequestPart("file") MultipartFile file) {
        return R.ok(roadNetworkImportService.importNetwork(file));
    }
}
