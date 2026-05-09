import { longRequest } from '../utils/request'
import type {
  ImportDiseaseExcelResultVO,
  ImportNetworkResultVO,
  ImportSectionPackageResultVO
} from '../types/importNetwork'

/** 上传 .tar 路网包（multipart，勿手写 Content-Type，以便浏览器带 boundary） */
export function importRoadNetwork(file: File): Promise<ImportNetworkResultVO> {
  const fd = new FormData()
  fd.append('file', file)
  return longRequest.post('/api/road-routes/import-network', fd)
}

/** 上传 .tar 路段包（路线级 + 台账级 Shapefile，multipart） */
export function importSectionPackage(file: File): Promise<ImportSectionPackageResultVO> {
  const fd = new FormData()
  fd.append('file', file)
  return longRequest.post('/api/road-sections/import-package', fd)
}

/** 上传 .xlsx 病害台账（multipart） */
export function importDiseaseExcel(file: File): Promise<ImportDiseaseExcelResultVO> {
  const fd = new FormData()
  fd.append('file', file)
  return longRequest.post('/api/road-assets/diseases/import-excel', fd)
}
