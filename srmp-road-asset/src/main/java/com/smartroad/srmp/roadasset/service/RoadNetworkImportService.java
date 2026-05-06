package com.smartroad.srmp.roadasset.service;

import com.smartroad.srmp.roadasset.vo.ImportNetworkResultVO;
import org.springframework.web.multipart.MultipartFile;

public interface RoadNetworkImportService {

    ImportNetworkResultVO importNetwork(MultipartFile file);
}
