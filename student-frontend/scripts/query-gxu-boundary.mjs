// 获取广西大学校园多边形顶点坐标（临时工具）
const q = `[out:json][timeout:60];
way(398115378);
out geom;`;

const url = 'https://overpass-api.de/api/interpreter?data=' + encodeURIComponent(q)
fetch(url, { headers: { Accept: 'application/json', 'User-Agent': 'aiEduDev-map/1.0' } })
  .then((r) => {
    if (!r.ok) throw new Error('HTTP ' + r.status)
    return r.json()
  })
  .then((d) => {
    const way = (d.elements || []).find((e) => e.type === 'way')
    if (!way || !way.geometry) {
      console.log('未找到几何')
      return
    }
    const lats = way.geometry.map((g) => g.lat)
    const lons = way.geometry.map((g) => g.lon)
    console.log('顶点数:', way.geometry.length)
    console.log('bbox:', Math.min(...lons), Math.min(...lats), Math.max(...lons), Math.max(...lats))
    console.log('宽(km):', ((Math.max(...lons) - Math.min(...lons)) * 111000 * Math.cos((Math.max(...lats) + Math.min(...lats)) / 2 / 180 * Math.PI)).toFixed(1))
    console.log('高(km):', ((Math.max(...lats) - Math.min(...lats)) * 111000).toFixed(1))
    console.log('coords:', JSON.stringify(way.geometry.map((g) => [g.lat, g.lon])))
  })
  .catch((e) => {
    console.error('FAIL:', e.message)
    process.exit(1)
  })
