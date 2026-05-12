package com.smartroad.srmp.roadasset.controller;

import com.smartroad.srmp.common.core.PageResult;
import com.smartroad.srmp.common.core.R;
import com.smartroad.srmp.roadasset.dto.DataImportRecordQueryDTO;
import com.smartroad.srmp.roadasset.dto.DataMgmtClearDTO;
import com.smartroad.srmp.roadasset.dto.DataMgmtProjectQueryDTO;
import com.smartroad.srmp.roadasset.dto.DataMgmtProjectSaveDTO;
import com.smartroad.srmp.roadasset.service.DataImportRecordService;
import com.smartroad.srmp.roadasset.service.DataMgmtClearService;
import com.smartroad.srmp.roadasset.service.DataMgmtImportService;
import com.smartroad.srmp.roadasset.service.DataMgmtProjectService;
import com.smartroad.srmp.roadasset.vo.DataImportRecordVO;
import com.smartroad.srmp.roadasset.vo.DataMgmtClearResultVO;
import com.smartroad.srmp.roadasset.vo.DataMgmtProjectVO;
import com.smartroad.srmp.roadasset.vo.ImportDiseaseExcelResultVO;
import com.smartroad.srmp.roadasset.vo.ImportNetworkResultVO;
import com.smartroad.srmp.roadasset.vo.ImportSectionPackageResultVO;
import org.springframework.http.MediaType;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import javax.annotation.Resource;

@RestController
@RequestMapping("/api/data-mgmt")
public class DataMgmtController {

    @Resource
    private DataMgmtProjectService dataMgmtProjectService;
    @Resource
    private DataImportRecordService dataImportRecordService;
    @Resource
    private DataMgmtImportService dataMgmtImportService;
    @Resource
    private DataMgmtClearService dataMgmtClearService;

    @PostMapping("/projects/page")
    public R<PageResult<DataMgmtProjectVO>> projectPage(@RequestBody DataMgmtProjectQueryDTO query) {
        return R.ok(dataMgmtProjectService.page(query));
    }

    @PostMapping("/projects")
    public R<String> createProject(@Validated @RequestBody DataMgmtProjectSaveDTO dto) {
        return R.ok(dataMgmtProjectService.create(dto));
    }

    @GetMapping("/projects/{id}")
    public R<DataMgmtProjectVO> projectDetail(@PathVariable String id) {
        return R.ok(dataMgmtProjectService.getById(id));
    }

    /**
     * 删除项目：先物理清除本项目归属数据与导入流水（与 {@code POST .../clear} scope=ALL 一致），再软删项目主档。
     */
    @DeleteMapping("/projects/{id}")
    public R<Void> deleteProject(@PathVariable String id) {
        dataMgmtProjectService.delete(id);
        return R.ok();
    }

    @PostMapping("/projects/{projectId}/import-records/page")
    public R<PageResult<DataImportRecordVO>> importRecordPage(
            @PathVariable String projectId,
            @RequestBody DataImportRecordQueryDTO query) {
        dataMgmtProjectService.requireExists(projectId);
        return R.ok(dataImportRecordService.pageByProject(projectId, query));
    }

    @PostMapping(value = "/projects/{projectId}/imports/road-network", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public R<ImportNetworkResultVO> importRoadNetwork(
            @PathVariable String projectId,
            @RequestPart("file") MultipartFile file) {
        return R.ok(dataMgmtImportService.importRoadNetwork(projectId, file));
    }

    @PostMapping(value = "/projects/{projectId}/imports/section-package", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public R<ImportSectionPackageResultVO> importSectionPackage(
            @PathVariable String projectId,
            @RequestPart("file") MultipartFile file) {
        return R.ok(dataMgmtImportService.importSectionPackage(projectId, file));
    }

    @PostMapping(value = "/projects/{projectId}/imports/disease-excel", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public R<ImportDiseaseExcelResultVO> importDiseaseExcel(
            @PathVariable String projectId,
            @RequestPart("file") MultipartFile file) {
        return R.ok(dataMgmtImportService.importDiseaseExcel(projectId, file));
    }

    /**
     * 按项目物理删除已归属该项目的业务数据（匹配 {@code tenant_id + project_id}）；{@code scope=ALL} 时包含本项目导入流水。
     */
    @PostMapping("/projects/{projectId}/clear")
    public R<DataMgmtClearResultVO> clearProjectData(
            @PathVariable String projectId,
            @Validated @RequestBody DataMgmtClearDTO dto) {
        return R.ok(dataMgmtClearService.clearByProject(projectId, dto.getScope()));
    }
}
