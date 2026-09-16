/**
 * 离线校园地图工具库
 * - 路网加载与建图（按出行方式：步行 / 骑行 / 驾车）
 * - Dijkstra 最短路径（按出行方式使用不同的可通行道路与代价系数）
 * - 坐标距离 / 方位角 / 导航步骤生成
 * 完全本地运行，不依赖任何地图服务 AK。
 */
import { ref } from 'vue'

// ==================== 出行方式配置 ====================

/**
 * 三种出行方式的路网可达规则与速度模型。
 * allowed: 可通行的 OSM highway 类型集合
 * speed:   平均速度（米/分钟），用于估算耗时
 * cost():  代价系数，用于影响选路偏好（越小越优先）
 */
export const MODE_PROFILES = {
  walking: {
    key: 'walking',
    label: '步行',
    unit: '步行',
    verb: '步行',
    color: '#409eff',
    speed: 80, // ≈4.8 km/h
    allowed: new Set([
      'footway', 'steps', 'path', 'pedestrian', 'service', 'residential',
      'living_street', 'unclassified', 'tertiary', 'secondary', 'primary', 'trunk',
    ]),
    cost(highway) {
      // 步行优先走人行道与小路，主干道绕行代价更高
      if (highway === 'footway' || highway === 'path' || highway === 'pedestrian') return 0.92
      if (highway === 'steps') return 1.05
      if (highway === 'primary' || highway === 'trunk') return 1.25
      return 1
    },
  },
  cycling: {
    key: 'cycling',
    label: '骑行',
    unit: '骑行',
    verb: '骑行',
    color: '#67c23a',
    speed: 250, // ≈15 km/h
    allowed: new Set([
      'path', 'pedestrian', 'service', 'residential', 'living_street',
      'unclassified', 'tertiary', 'secondary', 'primary', 'trunk',
    ]),
    cost(highway) {
      // 骑行绕开台阶；主干道路面更好、更通畅
      if (highway === 'primary' || highway === 'trunk' || highway === 'secondary') return 0.95
      if (highway === 'service' || highway === 'residential') return 1.08
      return 1
    },
  },
  driving: {
    key: 'driving',
    label: '驾车',
    unit: '驾车',
    verb: '行驶',
    color: '#e6a23c',
    speed: 400, // ≈24 km/h（校园限速）
    allowed: new Set([
      'service', 'residential', 'living_street',
      'unclassified', 'tertiary', 'secondary', 'primary', 'trunk',
    ]),
    cost(highway) {
      // 驾车优先走等级更高的道路，服务性小路代价更高
      if (highway === 'primary' || highway === 'trunk') return 0.88
      if (highway === 'secondary' || highway === 'tertiary') return 0.96
      if (highway === 'service' || highway === 'living_street') return 1.2
      return 1.05
    },
  },
}

export const MODE_KEYS = ['walking', 'cycling', 'driving']

/** 取出行方式配置，未知方式回退为步行 */
export function getModeProfile(mode) {
  return MODE_PROFILES[mode] || MODE_PROFILES.walking
}

// ==================== 坐标工具 ====================

/** 球面距离（米） */
export function haversine(lat1, lon1, lat2, lon2) {
  const R = 6371000
  const toRad = (d) => (d * Math.PI) / 180
  const dLat = toRad(lat2 - lat1)
  const dLon = toRad(lon2 - lon1)
  const a =
    Math.sin(dLat / 2) ** 2 +
    Math.cos(toRad(lat1)) * Math.cos(toRad(lat2)) * Math.sin(dLon / 2) ** 2
  return 2 * R * Math.asin(Math.sqrt(a))
}

/** 两点方位角（0=北，顺时针） */
export function bearing(lat1, lon1, lat2, lon2) {
  const toRad = (d) => (d * Math.PI) / 180
  const toDeg = (d) => (d * 180) / Math.PI
  const φ1 = toRad(lat1)
  const φ2 = toRad(lat2)
  const Δλ = toRad(lon2 - lon1)
  const y = Math.sin(Δλ) * Math.cos(φ2)
  const x = Math.cos(φ1) * Math.sin(φ2) - Math.sin(φ1) * Math.cos(φ2) * Math.cos(Δλ)
  return (toDeg(Math.atan2(y, x)) + 360) % 360
}

/** 方位角转中文方向 */
export function bearingToText(b) {
  const dirs = ['北', '东北', '东', '东南', '南', '西南', '西', '西北']
  return dirs[Math.round(b / 45) % 8]
}

/** 格式化距离 */
export function fmtDistance(m) {
  return m >= 1000 ? (m / 1000).toFixed(1) + ' 公里' : Math.round(m) + ' 米'
}

/** 格式化时长（按出行方式速度换算） */
export function fmtDuration(m, mode = 'walking') {
  const speed = getModeProfile(mode).speed
  const minutes = Math.max(1, Math.round(m / speed))
  if (minutes < 60) return minutes + ' 分钟'
  const h = Math.floor(minutes / 60)
  const mm = minutes % 60
  return mm ? `${h} 小时 ${mm} 分钟` : `${h} 小时`
}

/** 生成一条导航分步文案（不同出行方式动词不同） */
export function stepText(step, index, mode = 'walking') {
  const profile = getModeProfile(mode)
  const dir = step.bearing !== null && step.bearing !== undefined ? `向${bearingToText(step.bearing)}` : ''
  const dist = fmtDistance(step.distance)
  if (step.highway === 'steps') {
    return `${index + 1}. 经台阶步道${dir}上行约 ${dist}`
  }
  const road = step.name && step.name !== '校园道路' ? `沿${step.name}` : '沿校园道路'
  return `${index + 1}. ${road}${dir}${profile.verb}约 ${dist}`
}

// ==================== 路网 ====================

let rawNetwork = null
const graphCache = new Map()
const networkLoading = ref(false)
const networkError = ref('')

/** 加载路网原始数据（只加载一次） */
export async function loadRoadNetwork() {
  if (rawNetwork) return rawNetwork
  networkLoading.value = true
  networkError.value = ''
  try {
    // 路网已服务化：由后端 /api/map/road-network 提供（内存缓存 + 按需下发），
    // 不再随前端打包。响应结构为 {code,message,data}，取 data 才是路网本体。
    const res = await fetch('/api/map/road-network', { cache: 'force-cache' })
    if (!res.ok) throw new Error(`HTTP ${res.status}`)
    const body = await res.json()
    rawNetwork = body && body.data ? body.data : body
    return rawNetwork
  } catch (e) {
    networkError.value = '路网数据加载失败，导航将降级为直线距离'
    console.error('路网加载失败:', e)
    return null
  } finally {
    networkLoading.value = false
  }
}

/**
 * 取指定出行方式的路网图（懒构建 + 缓存）。
 * 若原始数据尚未加载，会先尝试加载。
 */
export async function getGraph(mode = 'walking') {
  const key = getModeProfile(mode).key
  if (graphCache.has(key)) return graphCache.get(key)
  const data = await loadRoadNetwork()
  if (!data) return null
  const graph = buildGraph(data, key)
  graphCache.set(key, graph)
  return graph
}

/** 关闭/开启某出行方式下不可通行的道路后，是否还有可走的路 */
export function isEdgeAllowed(highway, mode) {
  return getModeProfile(mode).allowed.has(highway || '')
}

/**
 * 构建邻接表图，并只保留最大连通分量（去掉边缘孤立道路，
 * 保证图内任意两点都可达，导航更可靠）
 * graph: { mode, nodes: Map<id,{id,lat,lon}>, adj: Map<id, [{to, dist, name, highway}]> }
 */
export function buildGraph(data, mode = 'walking') {
  const profile = getModeProfile(mode)
  const allNodes = new Map()
  data.nodes.forEach((n) => allNodes.set(n.id, n))
  const adj = new Map()
  for (const e of data.edges) {
    const highway = e.highway || ''
    // 关键：按出行方式过滤不可通行的道路（如骑行/驾车不走台阶）
    if (!profile.allowed.has(highway)) continue
    const a = allNodes.get(e.a)
    const b = allNodes.get(e.b)
    if (!a || !b) continue
    const dist = haversine(a.lat, a.lon, b.lat, b.lon)
    if (dist > 300) continue // 过滤异常长边
    if (!adj.has(e.a)) adj.set(e.a, [])
    if (!adj.has(e.b)) adj.set(e.b, [])
    adj.get(e.a).push({ to: e.b, dist, name: e.name || '', highway })
    adj.get(e.b).push({ to: e.a, dist, name: e.name || '', highway })
  }

  // 找最大连通分量
  const seen = new Set()
  let bestComp = []
  for (const id of adj.keys()) {
    if (seen.has(id)) continue
    const comp = []
    const q = [id]
    seen.add(id)
    while (q.length) {
      const u = q.pop()
      comp.push(u)
      for (const v of adj.get(u) || []) {
        if (!seen.has(v.to)) {
          seen.add(v.to)
          q.push(v.to)
        }
      }
    }
    if (comp.length > bestComp.length) bestComp = comp
  }

  const keep = new Set(bestComp)
  const nodes = new Map()
  for (const id of bestComp) nodes.set(id, allNodes.get(id))
  const adj2 = new Map()
  for (const id of bestComp) {
    adj2.set(id, (adj.get(id) || []).filter((e) => keep.has(e.to)))
  }
  return { mode: profile.key, nodes, adj: adj2 }
}

/** 找到距离坐标最近的路网节点（超过 maxDistance 米视为路网外，返回 null） */
export function nearestNode(graph, lat, lon, maxDistance = 500) {
  if (!graph) return null
  let best = null
  let bestDist = Infinity
  for (const n of graph.nodes.values()) {
    const d = haversine(lat, lon, n.lat, n.lon)
    if (d < bestDist) {
      bestDist = d
      best = n
    }
  }
  if (!best || bestDist > maxDistance) return null
  return best
}

/**
 * Dijkstra 最短路径
 * @param mode 出行方式：影响道路代价系数（可通行道路已在建图时过滤）
 * @returns { nodeIds: number[], distance: number, cost: number, steps: [{name,distance,bearing,count,highway}] } | null
 */
export function findShortestPath(graph, startId, endId, mode = 'walking') {
  if (!graph || !graph.nodes.has(startId) || !graph.nodes.has(endId)) return null
  const profile = getModeProfile(mode)

  const dist = new Map() // 实际里程（米）
  const cost = new Map() // 加权代价
  const prev = new Map()
  const prevEdge = new Map()
  const visited = new Set()
  const pq = [[0, startId]]
  dist.set(startId, 0)
  cost.set(startId, 0)

  while (pq.length) {
    pq.sort((a, b) => a[0] - b[0])
    const [c, u] = pq.shift()
    if (visited.has(u)) continue
    visited.add(u)
    if (u === endId) break
    const edges = graph.adj.get(u) || []
    for (const e of edges) {
      if (visited.has(e.to)) continue
      const nc = c + e.dist * profile.cost(e.highway)
      if (nc < (cost.get(e.to) ?? Infinity)) {
        cost.set(e.to, nc)
        dist.set(e.to, (dist.get(u) ?? 0) + e.dist)
        prev.set(e.to, u)
        prevEdge.set(e.to, e)
        pq.push([nc, e.to])
      }
    }
  }

  if (!dist.has(endId)) return null

  // 还原路径
  const nodeIds = []
  let cur = endId
  while (cur !== undefined) {
    nodeIds.unshift(cur)
    cur = prev.get(cur)
  }

  // 生成步骤：合并同名且同类型的连续路段
  const steps = []
  let curName = null
  let curHighway = null
  let segDist = 0
  let segCount = 0
  let segFrom = null
  let segTo = null

  const flush = () => {
    if (curName !== null && segCount > 0) {
      const b = segFrom && segTo
        ? bearing(graph.nodes.get(segFrom).lat, graph.nodes.get(segFrom).lon, graph.nodes.get(segTo).lat, graph.nodes.get(segTo).lon)
        : null
      steps.push({ name: curName, highway: curHighway, distance: segDist, bearing: b, count: segCount })
    }
  }

  for (let i = 0; i < nodeIds.length - 1; i++) {
    const e = prevEdge.get(nodeIds[i + 1])
    if (!e) continue
    const name = e.name || '校园道路'
    if (name !== curName || e.highway !== curHighway) {
      flush()
      curName = name
      curHighway = e.highway
      segDist = 0
      segCount = 0
      segFrom = null
    }
    segDist += e.dist
    segCount++
    segFrom = segFrom ?? nodeIds[i]
    segTo = nodeIds[i + 1]
  }
  flush()

  return { nodeIds, distance: dist.get(endId), cost: cost.get(endId), steps }
}

/** 由节点序列生成 Leaflet 经纬度点数组 */
export function pathToLatLngs(graph, nodeIds) {
  return nodeIds.map((id) => {
    const n = graph.nodes.get(id)
    return [n.lat, n.lon]
  })
}
