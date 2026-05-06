import type { Map as LeafletMap } from 'leaflet'
import L from 'leaflet'

/** 天地图球面墨卡托瓦片（tilematrixset=w，EPSG:3857），与 Leaflet 默认 CRS 一致。 */
export const MAP_TILE_CRS = 'EPSG:3857' as const

/** 业务 GeoJSON / bbox / 经纬度采用 WGS 84 地理坐标（EPSG:4326），与 PostGIS SRID 4326 对齐。 */
export const MAP_GEOGRAPHIC_CRS = 'EPSG:4326' as const

const TIANDITU_SUBDOMAINS = '01234567'

/**
 * 天地图 WMTS KVP（与官方示例一致）：底图 vec_w + 注记 cva_w。
 * Token 优先读 `import.meta.env.VITE_TIANDITU_TOKEN`，未配置时使用文档示例 key（生产环境请改为自己的 key）。
 *
 * @see https://lbs.tianditu.gov.cn/server/MapService.html
 */
function tiandituWmtsTemplate(path: string, layer: string, tk: string): string {
  // 勿用 URLSearchParams：会对 {z}/{x}/{y} 编码，Leaflet 无法替换瓦片占位符。
  return (
    `https://t{s}.tianditu.gov.cn/${path}/wmts?` +
    `SERVICE=WMTS&REQUEST=GetTile&VERSION=1.0.0&LAYER=${layer}&STYLE=default` +
    `&TILEMATRIXSET=w&FORMAT=tiles&TileMatrix={z}&TileCol={x}&TileRow={y}&tk=${encodeURIComponent(tk)}`
  )
}

/** 叠加天地图底图与注记；返回图层组便于后续扩展（如切换主题）。 */
export function addTiandituBasemap(map: LeafletMap): L.LayerGroup {
  const tk =
    (import.meta.env.VITE_TIANDITU_TOKEN as string | undefined)?.trim() ||
    'bb3a11452a810044c551e46541839c84'

  const group = L.layerGroup()

  L.tileLayer(tiandituWmtsTemplate('vec_w', 'vec', tk), {
    subdomains: TIANDITU_SUBDOMAINS,
    maxZoom: 22,
    maxNativeZoom: 18,
    attribution: '© 国家基础地理信息中心 天地图'
  }).addTo(group)

  L.tileLayer(tiandituWmtsTemplate('cva_w', 'cva', tk), {
    subdomains: TIANDITU_SUBDOMAINS,
    maxZoom: 22,
    maxNativeZoom: 18
  }).addTo(group)

  group.addTo(map)
  return group
}
