/**
 * 广西大学离线瓦片下载脚本（多源支持 + 并发）
 * - 普通底图: tile.openstreetmap.de（OSM 官方镜像，WGS84 坐标系）
 * - 卫星底图: Esri World Imagery
 *
 * 使用方式：
 *   node scripts/download-tiles.mjs osm     # 下载普通地图
 *   node scripts/download-tiles.mjs sat     # 下载卫星图
 *
 * 瓦片版权：
 *   OSM 底图 © OpenStreetMap contributors (ODbL)
 *   Esri World Imagery © Esri, Maxar, Earthstar Geographics
 */
import fs from 'node:fs'
import path from 'node:path'
import { fileURLToPath } from 'node:url'

const __dirname = path.dirname(fileURLToPath(import.meta.url))

// 广西大学主校区范围（以 OSM 校园多边形 way 398115378 为基准，外扩约 300~400m 留缓冲）
// 校园实际 bbox：lng 108.2793~108.2970，lat 22.8355~22.8588
const BBOX = {
  west: 108.276,
  east: 108.300,
  south: 22.832,
  north: 22.862,
}
const MIN_ZOOM = 13
const MAX_ZOOM = 18

const SOURCES = {
  osm: {
    name: 'osm',
    outDir: path.resolve(__dirname, '../public/map-tiles/osm'),
    url: (z, x, y) => `https://tile.openstreetmap.de/${z}/${x}/${y}.png`,
  },
  sat: {
    name: 'sat',
    outDir: path.resolve(__dirname, '../public/map-tiles/sat'),
    // 注意 Esri 瓦片 URL 顺序是 z/y/x
    url: (z, x, y) => `https://server.arcgisonline.com/ArcGIS/rest/services/World_Imagery/MapServer/tile/${z}/${y}/${x}`,
  },
}

const USER_AGENT = 'aiEduDev-offline-campus-map/1.0 (local development)'
const RETRY = 3
const CONCURRENCY = 3
const DELAY_MS = 80

function tileXY(lng, lat, z) {
  const n = 2 ** z
  const x = Math.floor(((lng + 180) / 360) * n)
  const latRad = (lat * Math.PI) / 180
  const y = Math.floor(
    ((1 - Math.log(Math.tan(latRad) + 1 / Math.cos(latRad)) / Math.PI) / 2) * n
  )
  return { x, y }
}

function tileRange(z) {
  const a = tileXY(BBOX.west, BBOX.north, z)
  const b = tileXY(BBOX.east, BBOX.south, z)
  return {
    xMin: Math.min(a.x, b.x), xMax: Math.max(a.x, b.x),
    yMin: Math.min(a.y, b.y), yMax: Math.max(a.y, b.y),
  }
}

async function fetchTile(url) {
  for (let attempt = 1; attempt <= RETRY; attempt++) {
    try {
      const res = await fetch(url, {
        headers: { 'User-Agent': USER_AGENT, Accept: 'image/png,*/*' },
        signal: AbortSignal.timeout(20000),
      })
      if (!res.ok) throw new Error(`HTTP ${res.status} ${url}`)
      const buf = Buffer.from(await res.arrayBuffer())
      if (buf.length < 100) throw new Error(`tile too small (${buf.length}B) ${url}`)
      return buf
    } catch (e) {
      if (attempt === RETRY) throw e
      await new Promise((r) => setTimeout(r, 600 * attempt))
    }
  }
  throw new Error('unreachable')
}

const sleep = (ms) => new Promise((r) => setTimeout(r, ms))

async function main() {
  const type = process.argv[2] || 'osm'
  const src = SOURCES[type]
  if (!src) {
    console.error('未知类型: ' + type + '，可选 osm / sat')
    process.exit(1)
  }

  const tasks = []
  for (let z = MIN_ZOOM; z <= MAX_ZOOM; z++) {
    const { xMin, xMax, yMin, yMax } = tileRange(z)
    for (let x = xMin; x <= xMax; x++) {
      for (let y = yMin; y <= yMax; y++) {
        tasks.push({ z, x, y })
      }
    }
  }
  console.log(`[${type}] 共 ${tasks.length} 张瓦片，并发 ${CONCURRENCY} 开始下载...`)

  let done = 0
  let skipped = 0
  const startTime = Date.now()
  const seen = new Set()

  async function worker() {
    while (true) {
      const task = tasks.pop()
      if (!task) return
      const key = `${task.z}/${task.x}/${task.y}`
      if (seen.has(key)) { skipped++; continue }
      seen.add(key)
      const { z, x, y } = task
      const dir = path.join(src.outDir, String(z), String(x))
      const file = path.join(dir, `${y}.png`)
      if (fs.existsSync(file) && fs.statSync(file).size > 100) {
        skipped++
        done++
        continue
      }
      try {
        fs.mkdirSync(dir, { recursive: true })
        const buf = await fetchTile(src.url(z, x, y))
        fs.writeFileSync(file, buf)
      } catch (e) {
        console.error(`[${type}] 下载失败 ${z}/${x}/${y}: ${e.message}`)
      }
      done++
      if (done % 50 === 0) {
        const el = ((Date.now() - startTime) / 1000).toFixed(0)
        console.log(`[${type}] 进度 ${done}/${tasks.length + done}（跳过 ${skipped}），已用 ${el}s`)
      }
      await sleep(DELAY_MS)
    }
  }

  const workers = Array.from({ length: CONCURRENCY }, () => worker())
  await Promise.all(workers)
  const el = ((Date.now() - startTime) / 1000).toFixed(0)
  console.log(`[${type}] 完成：${done} 张（跳过 ${skipped}），耗时 ${el}s -> ${src.outDir}`)
}

main().catch((e) => {
  console.error('脚本失败:', e.message)
  process.exit(1)
})
