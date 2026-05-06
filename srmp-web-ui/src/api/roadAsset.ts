import { longRequest } from '../utils/request'
import type { ImportNetworkResultVO } from '../types/importNetwork'

/** 上传 .tar 路网包（multipart，勿手写 Content-Type，以便浏览器带 boundary） */
export function importRoadNetwork(file: File): Promise<ImportNetworkResultVO> {
  const fd = new FormData()
  fd.append('file', file)
  return longRequest.post('/api/road-routes/import-network', fd)
}
