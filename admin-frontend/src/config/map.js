// 离线校园地图配置（Leaflet + 本地瓦片，无需任何地图服务 AK）
export const mapConfig = {
  // 默认地图中心点（广西大学校园中心，WGS84，来源 OSM 校园多边形）
  // 注意：如果后端地点数据是 BD-09 坐标，可开启下方坐标转换
  defaultCenter: { lng: 108.288, lat: 22.847 },
  // 默认缩放级别（校园南北约 2.6km，zoom15 可完整展示）
  defaultZoom: 15,
  // 最小/最大缩放级别（本地瓦片只下载了 13~18）
  minZoom: 13,
  maxZoom: 18,
  // 标记点是否显示标签
  showLabel: true,
  // 离线瓦片地址模板（public/map-tiles 下的本地瓦片）
  tileUrls: {
    // 普通地图：OpenStreetMap 官方镜像（WGS84）
    normal: '/map-tiles/osm/{z}/{x}/{y}.png',
    // 卫星图：Esri World Imagery（WGS84）
    satellite: '/map-tiles/sat/{z}/{x}/{y}.png',
  },
  // 离线路网与地点兜底数据
  dataUrls: {
    roadNetwork: '/map-data/road-network.json',
    locations: '/map-data/locations.json',
  },
  // 坐标转换：若地点数据为 BD-09，置为 true（默认数据为 WGS84，无需转换）
  convertFromBd09: false,
}

// 地点分类对应的颜色
export const categoryColors = {
  '教学': '#409eff',
  '生活': '#67c23a',
  '服务': '#e6a23c',
  '餐饮': '#f56c6c',
  '运动': '#909399',
}

export function getCategoryColor(category) {
  return categoryColors[category] || '#909399'
}

// ==================== BD-09 → WGS84 坐标转换（可选） ====================
// 国内常见坐标系统转换算法，供坐标纠偏使用
// 参考：https://github.com/wandergis/coordtransform

const x_pi = (Math.PI * 3000.0) / 180.0

function bd09ToGcj02(lng, lat) {
  const x = lng - 0.0065
  const y = lat - 0.006
  const z = Math.sqrt(x * x + y * y) - 0.00002 * Math.sin(y * x_pi)
  const theta = Math.atan2(y, x) - 0.000003 * Math.cos(x * x_pi)
  return {
    lng: z * Math.cos(theta),
    lat: z * Math.sin(theta),
  }
}

function outOfChina(lng, lat) {
  return lng < 72.004 || lng > 137.8347 || lat < 0.8293 || lat > 55.8271
}

function gcj02ToWgs84(lng, lat) {
  if (outOfChina(lng, lat)) return { lng, lat }
  let dlng = 0
  let dlat = 0
  let x = lng
  let y = lat
  for (let i = 0; i < 3; i++) {
    const { lng: glng, lat: glat } = gcj02Encrypt(x, y)
    dlng = glng - lng
    dlat = glat - lat
    x = lng - dlng
    y = lat - dlat
  }
  return { lng: x, lat: y }
}

function gcj02Encrypt(lng, lat) {
  const a = 6378245.0
  const ee = 0.00669342162296594323
  let dLat = transformLat(lng - 105.0, lat - 35.0)
  let dLng = transformLng(lng - 105.0, lat - 35.0)
  const radLat = (lat / 180.0) * Math.PI
  let magic = Math.sin(radLat)
  magic = 1 - ee * magic * magic
  const sqrtMagic = Math.sqrt(magic)
  dLat = (dLat * 180.0) / (((a * (1 - ee)) / (magic * sqrtMagic)) * Math.PI)
  dLng = (dLng * 180.0) / ((a / sqrtMagic) * Math.cos(radLat) * Math.PI)
  return { lng: lng + dLng, lat: lat + dLat }
}

function transformLat(x, y) {
  let ret =
    -100.0 + 2.0 * x + 3.0 * y + 0.2 * y * y + 0.1 * x * y + 0.2 * Math.sqrt(Math.abs(x))
  ret += ((20.0 * Math.sin(6.0 * x * Math.PI) + 20.0 * Math.sin(2.0 * x * Math.PI)) * 2.0) / 3.0
  ret += ((20.0 * Math.sin(y * Math.PI) + 40.0 * Math.sin((y / 3.0) * Math.PI)) * 2.0) / 3.0
  ret += ((160.0 * Math.sin((y / 12.0) * Math.PI) + 320 * Math.sin((y * Math.PI) / 30.0)) * 2.0) / 3.0
  return ret
}

function transformLng(x, y) {
  let ret = 300.0 + x + 2.0 * y + 0.1 * x * x + 0.1 * x * y + 0.1 * Math.sqrt(Math.abs(x))
  ret += ((20.0 * Math.sin(6.0 * x * Math.PI) + 20.0 * Math.sin(2.0 * x * Math.PI)) * 2.0) / 3.0
  ret += ((20.0 * Math.sin(x * Math.PI) + 40.0 * Math.sin((x / 3.0) * Math.PI)) * 2.0) / 3.0
  ret += ((150.0 * Math.sin((x / 12.0) * Math.PI) + 300.0 * Math.sin((x / 30.0) * Math.PI)) * 2.0) / 3.0
  return ret
}

/** 统一入口：把存储坐标转为 WGS84（Leaflet 使用） */
export function toWgs84(lng, lat, fromBd09 = mapConfig.convertFromBd09) {
  if (!fromBd09) return { lng, lat }
  const gcj = bd09ToGcj02(lng, lat)
  return gcj02ToWgs84(gcj.lng, gcj.lat)
}
