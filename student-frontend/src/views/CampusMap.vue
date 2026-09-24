<template>
  <div class="map-page">
    <div class="page-header">
      <h2><el-icon><Location /></el-icon> 校园地图导航</h2>
      <p>广西大学离线地图：本地瓦片 + 本地路网寻路 + 浏览器定位，无需任何地图服务 AK</p>
    </div>

    <!-- 数据源提示（仅当使用离线兜底数据时显示） -->
    <el-alert v-if="usingFallbackData" type="info" :closable="false" class="ak-tip">
      <template #title>
        当前地图数据服务暂时不可用，已为您加载内置的校园地图数据，部分信息可能不是最新的。
      </template>
    </el-alert>

    <div class="map-container">
      <!-- 左侧：搜索和列表 -->
      <div class="map-sidebar">
        <el-input v-model="searchKeyword" placeholder="搜索地点名称..." size="large" clearable
                  @input="handleSearch">
          <template #prefix><el-icon><Search /></el-icon></template>
        </el-input>

        <div class="category-tabs">
          <el-button v-for="cat in categories" :key="cat.value"
                     :type="activeCategory === cat.value ? 'primary' : ''"
                     size="small"
                     @click="filterByCategory(cat.value)">
            <el-icon><component :is="cat.icon" /></el-icon>
            {{ cat.label }}
          </el-button>
        </div>

        <div class="marker-size-control">
          <span class="marker-size-label">标记大小</span>
          <el-slider v-model="markerSize" :min="8" :max="60" :step="2" @change="updateMarkers" style="flex:1" />
          <span class="marker-size-value">{{ markerSize }}px</span>
        </div>

        <div class="location-list">
          <div v-for="loc in filteredLocations" :key="loc.id"
               class="location-item card-hover"
               :class="{ active: selectedLocation?.id === loc.id }"
               @click="selectLocation(loc)">
            <div class="loc-icon" :style="{ background: getCategoryColor(loc.category) }">
              <el-icon><component :is="getCategoryIcon(loc.category)" /></el-icon>
            </div>
            <div class="loc-info">
              <h4>{{ loc.name }}</h4>
              <p>{{ loc.address }}</p>
              <p v-if="loc.openHours" class="loc-hours">
                <el-icon><Clock /></el-icon> {{ loc.openHours }}
              </p>
            </div>
          </div>
          <el-empty v-if="filteredLocations.length === 0" description="未找到相关地点" :image-size="60" />
        </div>
      </div>

      <!-- 右侧：离线地图区域 -->
      <div class="map-area">
        <div id="offline-map-container" ref="mapContainer" class="map-canvas">
          <div v-if="!mapReady" class="map-loading">
            <el-icon class="loading-icon"><Loading /></el-icon>
            <p>{{ loadingText }}</p>
          </div>
        </div>

        <!-- 出行方式切换（步行 / 骑行 / 驾车） -->
        <div v-if="mapReady" class="travel-mode-switch">
          <button
            v-for="m in modeOptions"
            :key="m.key"
            class="mode-btn"
            :class="{ active: travelMode === m.key }"
            :style="travelMode === m.key ? { background: m.color, borderColor: m.color } : {}"
            @click="switchTravelMode(m.key)"
          >
            <el-icon><component :is="m.icon" /></el-icon>
            <span>{{ m.label }}</span>
          </button>
        </div>

        <!-- 地图浮动工具栏 -->
        <div class="map-toolbar">
          <el-tooltip content="定位到我的位置" placement="right">
            <el-button circle size="large" :type="locating ? 'primary' : 'default'" @click="locateMe" :loading="locating" :disabled="!mapReady">
              <el-icon><Aim /></el-icon>
            </el-button>
          </el-tooltip>
          <div class="toolbar-sep"></div>
          <el-tooltip content="放大" placement="right">
            <el-button circle size="large" @click="zoomIn" :disabled="!mapReady">
              <el-icon><ZoomIn /></el-icon>
            </el-button>
          </el-tooltip>
          <el-tooltip content="缩小" placement="right">
            <el-button circle size="large" @click="zoomOut" :disabled="!mapReady">
              <el-icon><ZoomOut /></el-icon>
            </el-button>
          </el-tooltip>
          <el-tooltip :content="showSatellite ? '普通地图' : '卫星图'" placement="right">
            <el-button circle size="large" :type="showSatellite ? 'primary' : 'default'" @click="toggleMapType" :disabled="!mapReady">
              <el-icon><Picture /></el-icon>
            </el-button>
          </el-tooltip>
          <el-tooltip content="清除路线" placement="right">
            <el-button circle size="large" type="danger" plain @click="clearRoute()" :disabled="!mapReady || !routeResult">
              <el-icon><Delete /></el-icon>
            </el-button>
          </el-tooltip>
          <el-tooltip content="回到校园中心" placement="right">
            <el-button circle size="large" @click="resetView" :disabled="!mapReady">
              <el-icon><HomeFilled /></el-icon>
            </el-button>
          </el-tooltip>
        </div>

        <!-- 本地 POI 搜索框（在离线地点库中搜索） -->
        <div v-if="mapReady" class="poi-search-box">
          <el-input v-model="poiKeyword" placeholder="搜索校园地点（如：食堂、ATM、超市）" size="default" clearable @keyup.enter="poiSearch" @clear="clearPoiResults">
            <template #prefix><el-icon><Search /></el-icon></template>
            <template #append>
              <el-button @click="poiSearch" :loading="poiSearching">搜索</el-button>
            </template>
          </el-input>
        </div>

        <!-- POI 搜索结果 -->
        <div v-if="poiResults.length > 0" class="poi-results-panel">
          <div class="poi-results-header">
            <span>搜索结果 ({{ poiResults.length }})</span>
            <el-button text size="small" @click="clearPoiResults"><el-icon><Close /></el-icon></el-button>
          </div>
          <div class="poi-results-list">
            <div v-for="(poi, idx) in poiResults" :key="idx" class="poi-result-item" @click="selectPoi(poi)">
              <div class="poi-idx">{{ idx + 1 }}</div>
              <div class="poi-info">
                <div class="poi-name">{{ poi.name }}</div>
                <div class="poi-addr">{{ poi.address }} · {{ poi.category }}</div>
              </div>
            </div>
          </div>
        </div>

        <!-- 当前位置信息 -->
        <div v-if="currentPos && mapReady" class="current-pos-badge">
          <el-icon class="pulse-icon"><Aim /></el-icon>
          <span>{{ currentPosSource === 'geolocation' ? '我的位置' : '我的位置（手动设定）' }}</span>
        </div>

        <!-- 地点详情卡片 -->
        <div v-if="selectedLocation" class="location-detail">
          <div class="detail-header">
            <h3>{{ selectedLocation.name }}</h3>
            <el-button text @click="closeDetail"><el-icon><Close /></el-icon></el-button>
          </div>
          <div class="detail-body">
            <div v-if="detailImages.length" class="detail-images">
              <el-image v-for="(url, idx) in detailImages" :key="idx" :src="url" :preview-src-list="detailImages" :initial-index="idx" fit="cover" class="detail-image" alt="地点照片" />
            </div>
            <p><el-icon><Location /></el-icon> {{ selectedLocation.address }}</p>
            <p v-if="selectedLocation.openHours"><el-icon><Clock /></el-icon> {{ selectedLocation.openHours }}</p>
            <p v-if="selectedLocation.phone"><el-icon><Phone /></el-icon> {{ selectedLocation.phone }}</p>
            <p v-if="selectedLocation.description" class="detail-desc">{{ selectedLocation.description }}</p>
          </div>
          <div class="detail-actions">
            <el-button type="primary" @click="navigateToSelected" :disabled="!currentPos">
              <el-icon><Position /></el-icon> 导航
            </el-button>
            <el-button plain :disabled="!currentPos" @click="locateMe">
              <el-icon><Aim /></el-icon> 重新定位
            </el-button>
          </div>
        </div>

        <!-- 地图选点卡片 -->
        <div v-if="customPoint" class="location-detail custom-point-card">
          <div class="detail-header">
            <h3>地图选点</h3>
            <el-button text @click="clearCustomPoint"><el-icon><Close /></el-icon></el-button>
          </div>
          <div class="detail-body">
            <p><el-icon><Location /></el-icon> {{ customPoint.address || '未知位置' }}</p>
            <p class="detail-desc">经度: {{ customPoint.lng.toFixed(6) }} / 纬度: {{ customPoint.lat.toFixed(6) }}</p>
          </div>
          <div class="detail-actions">
            <el-button type="primary" :disabled="!currentPos" @click="navigateToCustomFromMe">
              <el-icon><Position /></el-icon> 导航
            </el-button>
            <el-button plain @click="setCustomAsMyLocation">设为我的位置</el-button>
          </div>
        </div>

      </div>
    </div>

    <!-- 路径规划结果（地图下方，可折叠） -->
    <div v-if="routeResult" class="route-panel">
      <div class="route-panel-header" @click="routePanelCollapsed = !routePanelCollapsed">
        <h3>
          <el-icon><Position /></el-icon>
          <span class="route-mode-tag" :style="{ background: modeProfile.color }">{{ modeProfile.label }}</span>
          导航方案
        </h3>
        <div class="route-panel-actions">
          <span v-if="routeSummary" class="route-panel-summary">
            全程 <strong>{{ routeSummary.distance }}</strong> · 约 <strong>{{ routeSummary.duration }}</strong>
          </span>
          <el-button text size="small" @click.stop="clearRoute()"><el-icon><Close /></el-icon></el-button>
          <el-icon class="collapse-icon" :class="{ rotated: routePanelCollapsed }"><ArrowDown /></el-icon>
        </div>
      </div>
      <div v-show="!routePanelCollapsed" class="route-panel-body">
        <!-- 面板内也可切换出行方式，切换后自动重新规划 -->
        <div class="route-mode-row">
          <span class="route-mode-label">出行方式</span>
          <el-radio-group :model-value="travelMode" size="small" @change="switchTravelMode">
            <el-radio-button v-for="m in modeOptions" :key="m.key" :value="m.key">
              {{ m.label }}
            </el-radio-button>
          </el-radio-group>
        </div>
        <el-alert
          v-if="routeNotice"
          :title="routeNotice"
          type="warning"
          :closable="false"
          show-icon
          class="route-notice"
        />
        <div class="route-steps">
          <div v-for="(step, idx) in routeSteps" :key="idx" class="route-step">
            <div class="step-num">{{ idx + 1 }}</div>
            <div class="step-text">{{ step }}</div>
          </div>
          <el-empty v-if="routeSteps.length === 0" :description="`暂无${modeProfile.label}指引`" :image-size="40" />
        </div>
      </div>
    </div>
  </div>
</template>

<script setup>
import { ref, computed, onMounted, onBeforeUnmount, nextTick } from 'vue'
import L from 'leaflet'
import 'leaflet/dist/leaflet.css'
import { getLocations } from '@/api'
import { ElMessage } from 'element-plus'
import { mapConfig, getCategoryColor, toWgs84 } from '@/config/map'
import {
  getGraph, findShortestPath, nearestNode, getModeProfile, stepText,
  MODE_PROFILES, haversine, fmtDistance, fmtDuration, pathToLatLngs,
} from '@/utils/offlineMap'

// 三种出行方式（图标 + 文案取自 MODE_PROFILES，保证与选路规则一致）
const modeOptions = [
  { key: 'walking', label: MODE_PROFILES.walking.label, color: MODE_PROFILES.walking.color, icon: 'Position' },
  { key: 'cycling', label: MODE_PROFILES.cycling.label, color: MODE_PROFILES.cycling.color, icon: 'Bicycle' },
  { key: 'driving', label: MODE_PROFILES.driving.label, color: MODE_PROFILES.driving.color, icon: 'Van' },
]
const travelMode = ref('walking')
const modeProfile = computed(() => getModeProfile(travelMode.value))
const routeNotice = ref('')
// 记录最近一次导航请求，便于切换出行方式后自动重算
const lastRoute = ref(null)

const searchKeyword = ref('')
const activeCategory = ref('')
const locations = ref([])
const selectedLocation = ref(null)
const detailImages = computed(() => {
  const url = selectedLocation.value?.imageUrl
  return url ? url.split(',').filter(Boolean) : []
})
const mapReady = ref(false)
const loadingText = ref('正在加载地图...')
const routeResult = ref(null)
const routePanelCollapsed = ref(false)
const routeSummary = ref(null)
const routeSteps = ref([])
const locating = ref(false)
const currentPos = ref(null)
const currentPosSource = ref('')
const showSatellite = ref(false)
const poiKeyword = ref('')
const poiResults = ref([])
const poiSearching = ref(false)
const customPoint = ref(null)
const usingFallbackData = ref(false)
const markerSize = ref(32)
let isUnmounted = false

let map = null
let markers = []
let routePolyline = null
let routeStartMarker = null
let routeEndMarker = null
let currentMarker = null
let customMarker = null
let tileLayers = {}
// 每种出行方式各自的路网图（可通行道路不同）；graph 指向当前方式
const graphs = { walking: null, cycling: null, driving: null }
let graph = null

const categories = [
  { label: '全部', value: '', icon: 'Grid' },
  { label: '教学', value: '教学', icon: 'Reading' },
  { label: '生活', value: '生活', icon: 'House' },
  { label: '服务', value: '服务', icon: 'Service' },
  { label: '餐饮', value: '餐饮', icon: 'Food' },
  { label: '运动', value: '运动', icon: 'Trophy' },
]

const filteredLocations = computed(() => {
  let list = locations.value
  if (activeCategory.value) {
    list = list.filter(l => l.category === activeCategory.value)
  }
  if (searchKeyword.value) {
    const kw = searchKeyword.value.toLowerCase()
    list = list.filter(l => l.name.toLowerCase().includes(kw) || l.address?.toLowerCase().includes(kw))
  }
  return list
})

function handleSearch() {
  updateMarkers()
}

function filterByCategory(cat) {
  activeCategory.value = cat
  updateMarkers()
}

function getCategoryIcon(cat) {
  const iconMap = { '教学': 'Reading', '生活': 'House', '服务': 'Service', '餐饮': 'Food', '运动': 'Trophy' }
  return iconMap[cat] || 'Location'
}

// ==================== 标记图标（divIcon，避免 Leaflet 默认图片路径问题） ====================

function createMarkerIcon(color, size = 32) {
  const html = `<svg xmlns="http://www.w3.org/2000/svg" width="${size}" height="${Math.round(size * 1.25)}" viewBox="0 0 32 40">
    <path d="M16 0C7.16 0 0 7.16 0 16c0 12 16 24 16 24s16-12 16-24C32 7.16 24.84 0 16 0z" fill="${color}" stroke="#fff" stroke-width="2"/>
    <circle cx="16" cy="16" r="6" fill="#fff"/>
  </svg>`
  return L.divIcon({
    className: 'campus-div-icon',
    html,
    iconSize: [size, Math.round(size * 1.25)],
    iconAnchor: [size / 2, Math.round(size * 1.25)],
  })
}

function createLabelIcon(color) {
  const html = `<div class="campus-label-icon">
    <svg xmlns="http://www.w3.org/2000/svg" width="28" height="35" viewBox="0 0 32 40">
      <path d="M16 0C7.16 0 0 7.16 0 16c0 12 16 24 16 24s16-12 16-24C32 7.16 24.84 0 16 0z" fill="${color}" stroke="#fff" stroke-width="2"/>
      <circle cx="16" cy="16" r="6" fill="#fff"/>
    </svg>
  </div>`
  return L.divIcon({
    className: 'campus-div-icon',
    html,
    iconSize: [28, 35],
    iconAnchor: [14, 35],
  })
}

// ==================== 地图初始化 ====================

function initMap() {
  const container = document.getElementById('offline-map-container')
  if (!container || typeof L === 'undefined') return

  const center = toWgs84(mapConfig.defaultCenter.lng, mapConfig.defaultCenter.lat)
  map = L.map('offline-map-container', {
    center: [center.lat, center.lng],
    zoom: mapConfig.defaultZoom,
    minZoom: mapConfig.minZoom,
    maxZoom: mapConfig.maxZoom,
    zoomControl: false, // 用自定义工具栏 + 右下角控件
    attributionControl: false,
  })

  // 普通底图（本地离线瓦片）
  tileLayers.normal = L.tileLayer(mapConfig.tileUrls.normal, {
    minZoom: mapConfig.minZoom,
    maxZoom: mapConfig.maxZoom,
    tileSize: 256,
  }).addTo(map)

  // 卫星底图（离线下载，默认不显示）
  tileLayers.satellite = L.tileLayer(mapConfig.tileUrls.satellite, {
    minZoom: mapConfig.minZoom,
    maxZoom: mapConfig.maxZoom,
    tileSize: 256,
  })

  // Leaflet 自带缩放控件（右下角，保留放大缩小）
  L.control.zoom({ position: 'bottomright' }).addTo(map)

  // 比例尺
  L.control.scale({ position: 'bottomleft', imperial: false }).addTo(map)

  map.on('click', handleMapClick)

  mapReady.value = true
  updateMarkers()
}

// ==================== 地点标记 ====================

function updateMarkers() {
  if (!map || !mapReady.value) return
  markers.forEach(m => map.removeLayer(m))
  markers = []

  filteredLocations.value.forEach(loc => {
    if (!loc.longitude || !loc.latitude) return
    const wgs = toWgs84(loc.longitude, loc.latitude)
    const color = getCategoryColor(loc.category)
    const marker = L.marker([wgs.lat, wgs.lng], { icon: createMarkerIcon(color, markerSize.value) })
    marker.on('click', () => selectLocation(loc))
    marker.bindTooltip(loc.name, { direction: 'top', offset: [0, -34], opacity: 0.95 })
    marker.addTo(map)
    markers.push(marker)
  })
}

function selectLocation(loc) {
  selectedLocation.value = loc
  customPoint.value = null
  if (map && loc.longitude && loc.latitude) {
    const wgs = toWgs84(loc.longitude, loc.latitude)
    map.flyTo([wgs.lat, wgs.lng], Math.max(map.getZoom(), 17))
    // 在地图上用选点标记高亮选中的地点
    if (customMarker) map.removeLayer(customMarker)
    customMarker = L.marker([wgs.lat, wgs.lng], { icon: createMarkerIcon('#e6a23c') }).addTo(map)
    customMarker.bindTooltip(loc.name, { direction: 'top', offset: [0, -34] }).openTooltip()
  }
}

function closeDetail() {
  selectedLocation.value = null
  if (customMarker && map && !customPoint.value) {
    map.removeLayer(customMarker)
    customMarker = null
  }
}

// ==================== 导航（本地路网 Dijkstra，起点统一为我的位置） ====================

/** 切换到指定出行方式；若有路线则按新方式自动重新规划 */
async function switchTravelMode(mode) {
  if (!mode || mode === travelMode.value) {
    if (mode) travelMode.value = mode
    return
  }
  travelMode.value = mode
  graph = graphs[mode] || (await getGraph(mode))
  if (lastRoute.value) {
    const { start, dest, startName, destName } = lastRoute.value
    doNavigation(start, dest, startName, destName)
  }
}

/** 从某起点到某终点的统一导航入口（按当前出行方式规划） */
async function doNavigation(start, dest, startName, destName) {
  if (!map || !start || !dest) return
  const startWgs = toWgs84(start.lng, start.lat)
  const endWgs = toWgs84(dest.lng, dest.lat)

  // 记住本次导航，便于切换出行方式后自动重算
  lastRoute.value = { start, dest, startName, destName }

  clearRoute(true)

  const startLatLng = [startWgs.lat, startWgs.lng]
  const endLatLng = [endWgs.lat, endWgs.lng]
  const profile = modeProfile.value

  // 取当前出行方式的路网（不同方式可通行道路不同）
  if (!graph || graph.mode !== profile.key) {
    graph = graphs[profile.key] || (await getGraph(profile.key))
  }

  // 路网可用：吸附最近节点 + Dijkstra（按方式加权选路）
  if (graph) {
    const sNode = nearestNode(graph, startWgs.lat, startWgs.lng)
    const eNode = nearestNode(graph, endWgs.lat, endWgs.lng)
    const path = sNode && eNode ? findShortestPath(graph, sNode.id, eNode.id, profile.key) : null

    if (path && path.nodeIds.length > 1) {
      const latLngs = pathToLatLngs(graph, path.nodeIds)
      // 在路径首尾拼上真实起终点，让折线从起点画到终点
      routePolyline = L.polyline([startLatLng, ...latLngs, endLatLng], {
        color: profile.color, weight: 6, opacity: 0.85,
      }).addTo(map)

      routeSummary.value = {
        distance: fmtDistance(path.distance),
        duration: fmtDuration(path.distance, profile.key),
      }
      routeSteps.value = path.steps.map((s, i) => stepText(s, i, profile.key))
      routeNotice.value = ''
      routeResult.value = true
      addStartEndMarkers(startLatLng, endLatLng, startName, destName)
      map.fitBounds(routePolyline.getBounds(), { padding: [60, 60] })
      ElMessage.success(`已按校园路网规划最短${profile.label}路线`)
      return
    }

    // 该出行方式下无可行路线：给出针对性的切换建议
    const reason = !sNode || !eNode
      ? `起点或终点附近没有可${profile.verb}的道路`
      : `起点与终点之间没有可${profile.verb}的连通道路`
    const suggest = profile.key === 'walking'
      ? '校园路网可能未覆盖该区域'
      : `可切换为「步行」或「骑行」再试`
    routeSummary.value = null
    routeResult.value = true
    routeNotice.value = `${reason}，${suggest}。下方为直线参考路线，${profile.verb}时请以校园道路为准。`
    drawStraightLine(startLatLng, endLatLng, startName, destName, routeNotice.value)
    return
  }

  // 路网加载失败：直线导航
  routeSummary.value = null
  routeResult.value = true
  routeNotice.value = `校园路网数据加载失败，当前为直线参考路线，${profile.verb}时请以校园道路为准`
  drawStraightLine(startLatLng, endLatLng, startName, destName, routeNotice.value)
}

/** 直线导航（降级路径，使用当前出行方式配色与速度） */
function drawStraightLine(startLatLng, endLatLng, startName, destName, tip) {
  const profile = modeProfile.value
  const dist = haversine(startLatLng[0], startLatLng[1], endLatLng[0], endLatLng[1])
  routePolyline = L.polyline([startLatLng, endLatLng], {
    color: profile.color, weight: 6, opacity: 0.85, dashArray: '8 6',
  }).addTo(map)
  routeSummary.value = {
    distance: fmtDistance(dist),
    duration: fmtDuration(dist, profile.key),
  }
  routeSteps.value = [tip]
  routeResult.value = true
  addStartEndMarkers(startLatLng, endLatLng, startName, destName)
  map.fitBounds(routePolyline.getBounds(), { padding: [60, 60] })
  ElMessage.warning(tip)
}

function addStartEndMarkers(startLatLng, endLatLng, startName, destName) {
  routeStartMarker = L.marker(startLatLng, { icon: createMarkerIcon('#67c23a') }).addTo(map)
  routeEndMarker = L.marker(endLatLng, { icon: createMarkerIcon('#f56c6c') }).addTo(map)
  routeStartMarker.bindTooltip(startName || '起点', { direction: 'top', offset: [0, -34] })
  routeEndMarker.bindTooltip(destName || '终点', { direction: 'top', offset: [0, -34] })
}

/** 导航到选中的地点（详情卡片"导航"按钮，从我的位置出发） */
function navigateToSelected() {
  if (!currentPos.value) {
    ElMessage.warning('请先点击定位按钮获取当前位置（或点击地图选点后"设为我的位置"）')
    return
  }
  if (!selectedLocation.value) {
    ElMessage.warning('请先在地图或列表中选择目标地点')
    return
  }
  const dest = selectedLocation.value
  if (!dest.longitude || !dest.latitude) {
    ElMessage.warning('目标地点缺少坐标信息')
    return
  }
  doNavigation(
    { lng: currentPos.value.lng, lat: currentPos.value.lat }, { lng: dest.longitude, lat: dest.latitude },
    '我的位置', dest.name
  )
}

/** 导航到地图选点（从我的位置出发） */
function navigateToCustomFromMe() {
  if (!customPoint.value) { ElMessage.warning('请先在地图上点击选择目的地'); return }
  if (!currentPos.value) { ElMessage.warning('请先定位或手动设定我的位置'); return }
  doNavigation(
    { lng: currentPos.value.lng, lat: currentPos.value.lat }, { lng: customPoint.value.lng, lat: customPoint.value.lat },
    '我的位置', '地图选点'
  )
  closeDetail()
}

/** 清除路线；keepRequest=true 时保留最近一次导航请求（供切换出行方式后重算） */
function clearRoute(keepRequest = false) {
  if (map) {
    if (routePolyline) { map.removeLayer(routePolyline); routePolyline = null }
    if (routeStartMarker) { map.removeLayer(routeStartMarker); routeStartMarker = null }
    if (routeEndMarker) { map.removeLayer(routeEndMarker); routeEndMarker = null }
  }
  routeResult.value = null
  routeSummary.value = null
  routeSteps.value = []
  routeNotice.value = ''
  routePanelCollapsed.value = false
  if (!keepRequest) lastRoute.value = null
}

// ==================== 地图点击选点 ====================

function handleMapClick(e) {
  if (!map) return
  const { lat, lng } = e.latlng
  customPoint.value = { lng, lat, address: `经度 ${lng.toFixed(6)}, 纬度 ${lat.toFixed(6)}` }
  selectedLocation.value = null

  if (customMarker) map.removeLayer(customMarker)
  customMarker = L.marker([lat, lng], { icon: createMarkerIcon('#e6a23c') }).addTo(map)
  customMarker.bindTooltip('地图选点', { direction: 'top', offset: [0, -34] }).openTooltip()
}

function clearCustomPoint() {
  if (customMarker && map) { map.removeLayer(customMarker); customMarker = null }
  customPoint.value = null
}

/** 把地图选点设为"我的位置"（浏览器定位失败时的兜底） */
function setCustomAsMyLocation() {
  if (!customPoint.value) return
  currentPos.value = { lng: customPoint.value.lng, lat: customPoint.value.lat }
  currentPosSource.value = 'manual'
  addCurrentMarker(customPoint.value.lng, customPoint.value.lat)
  ElMessage.success('已将选点设为我的位置，可用于导航')
}

// ==================== 定位 ====================

function addCurrentMarker(lng, lat) {
  if (!map) return
  if (currentMarker) map.removeLayer(currentMarker)
  const icon = L.divIcon({
    className: 'campus-div-icon',
    html: `<div class="current-pos-dot">
      <svg xmlns="http://www.w3.org/2000/svg" width="36" height="36" viewBox="0 0 36 36">
        <circle cx="18" cy="18" r="16" fill="rgba(148,30,35,0.2)" stroke="#941e23" stroke-width="2">
          <animate attributeName="r" values="10;16;10" dur="2s" repeatCount="indefinite"/>
          <animate attributeName="opacity" values="1;0.3;1" dur="2s" repeatCount="indefinite"/>
        </circle>
        <circle cx="18" cy="18" r="7" fill="#941e23" stroke="#fff" stroke-width="2"/>
      </svg>
    </div>`,
    iconSize: [36, 36],
    iconAnchor: [18, 18],
  })
  currentMarker = L.marker([lat, lng], { icon }).addTo(map)
}

function locateMe() {
  if (!mapReady.value) { ElMessage.warning('地图未加载完成'); return }

  if (!navigator.geolocation) {
    ElMessage.warning('当前浏览器不支持定位，可点击地图选点后"设为我的位置"')
    return
  }

  locating.value = true
  navigator.geolocation.getCurrentPosition(
    (pos) => {
      if (isUnmounted || !map) return
      locating.value = false
      const { latitude: lat, longitude: lng } = pos.coords
      currentPos.value = { lng, lat }
      currentPosSource.value = 'geolocation'
      addCurrentMarker(lng, lat)
      map.flyTo([lat, lng], Math.max(map.getZoom(), 17))
      ElMessage.success('已定位到当前位置')
    },
    (err) => {
      if (isUnmounted) return
      locating.value = false
      console.error('定位失败:', err.code, err.message)
      ElMessage.error('定位失败：' + (err.code === 1 ? '浏览器定位权限被拒绝' : '无法获取位置') +
        '。可点击地图选点后点"设为我的位置"继续导航')
    },
    { enableHighAccuracy: true, timeout: 10000, maximumAge: 0 }
  )
}

// ==================== 缩放 / 视图 ====================

function zoomIn() {
  if (map) map.zoomIn()
}

function zoomOut() {
  if (map) map.zoomOut()
}

function toggleMapType() {
  if (!map) return
  showSatellite.value = !showSatellite.value
  if (showSatellite.value) {
    map.removeLayer(tileLayers.normal)
    tileLayers.satellite.addTo(map)
  } else {
    map.removeLayer(tileLayers.satellite)
    tileLayers.normal.addTo(map)
  }
}

function resetView() {
  if (!map) return
  const c = toWgs84(mapConfig.defaultCenter.lng, mapConfig.defaultCenter.lat)
  map.flyTo([c.lat, c.lng], mapConfig.defaultZoom)
  clearPoiResults()
}

// ==================== 本地 POI 搜索（离线地点库） ====================

function poiSearch() {
  const kw = poiKeyword.value.trim().toLowerCase()
  if (!kw) { ElMessage.warning('请输入搜索关键词'); return }
  poiSearching.value = true
  const all = locations.value.filter(l =>
    (l.name || '').toLowerCase().includes(kw) ||
    (l.address || '').toLowerCase().includes(kw) ||
    (l.category || '').toLowerCase().includes(kw) ||
    (l.building || '').toLowerCase().includes(kw)
  )
  setTimeout(() => {
    poiSearching.value = false
    poiResults.value = all
    if (all.length === 0) ElMessage.info('未找到相关地点')
    else ElMessage.success(`找到 ${all.length} 个相关地点`)
  }, 200)
}

function selectPoi(poi) {
  if (!map || !poi.longitude || !poi.latitude) return
  const wgs = toWgs84(poi.longitude, poi.latitude)
  map.flyTo([wgs.lat, wgs.lng], Math.max(map.getZoom(), 18))
  selectLocation(poi)
}

function clearPoiResults() {
  poiResults.value = []
  poiKeyword.value = ''
}

// ==================== 生命周期 ====================

onMounted(async () => {
  isUnmounted = false

  // 1. 加载地点数据（后端优先，离线兜底）
  try {
    const res = await getLocations({ silent: true })
    if (isUnmounted) return
    locations.value = res.data || []
  } catch (e) {
    if (isUnmounted) return
    console.error('后端地点加载失败，使用离线数据:', e)
    usingFallbackData.value = true
    try {
      const res = await fetch(mapConfig.dataUrls.locations)
      locations.value = await res.json()
    } catch (e2) {
      console.error('离线地点数据加载失败:', e2)
    }
  }

  // 2. 预加载三种出行方式的路网（失败会自动降级为直线导航）
  try {
    for (const key of ['walking', 'cycling', 'driving']) {
      graphs[key] = await getGraph(key)
    }
    graph = graphs[travelMode.value]
  } catch (e) {
    console.error('路网预加载失败:', e)
  }

  // 3. 初始化地图
  await nextTick()
  if (isUnmounted) return
  initMap()

  // 4. 地图就绪后自动尝试定位一次（失败静默，可手动定位）
  setTimeout(() => {
    if (isUnmounted || !map || !navigator.geolocation) return
    navigator.geolocation.getCurrentPosition(
      (pos) => {
        if (isUnmounted || !map) return
        const { latitude: lat, longitude: lng } = pos.coords
        currentPos.value = { lng, lat }
        currentPosSource.value = 'geolocation'
        addCurrentMarker(lng, lat)
        // 自动定位后移动地图视图到当前位置
        map.flyTo([lat, lng], Math.max(map.getZoom(), 17))
      },
      () => { /* 静默忽略，用户可手动操作 */ },
      { enableHighAccuracy: true, timeout: 8000, maximumAge: 60000 }
    )
  }, 1000)
})

onBeforeUnmount(() => {
  isUnmounted = true
  if (map) {
    map.off('click', handleMapClick)
    map.remove()
    map = null
  }
  markers = []
})
</script>


.map-page {
  display: flex;
  flex-direction: column;
  gap: 16px;
}

.page-header h2 {
  font-size: 22px;
  font-weight: 700;
  display: flex;
  align-items: center;
  gap: 10px;
  margin: 0 0 6px 0;
}

.page-header p {
  color: var(--ct-muted);
  font-size: 14px;
  margin: 0;
}

.ak-tip {
  margin-bottom: 0;
}

.ak-tip code {
  background: var(--ct-paper);
  padding: 2px 6px;
  border-radius: 0;
  font-size: 12px;
}

.map-container {
  display: grid;
  grid-template-columns: 340px 1fr;
  gap: 20px;
  height: calc(100vh - 220px);
  min-height: 500px;
}

.map-sidebar {
  background: var(--ct-white);
  border-radius: 0;
  padding: 16px;
  display: flex;
  flex-direction: column;
  gap: 12px;
  overflow: hidden;
}

.category-tabs {
  display: flex;
  flex-wrap: wrap;
  gap: 6px;
}

.marker-size-control {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 8px 4px;
  border-top: 1px solid var(--ct-line);
  border-bottom: 1px solid var(--ct-line);
  margin: 8px 0;
}

.marker-size-label {
  font-size: 12px;
  color: var(--ct-muted);
  white-space: nowrap;
}

.marker-size-value {
  font-size: 12px;
  color: #4a4845;
  font-family: monospace;
  white-space: nowrap;
  min-width: 36px;
  text-align: right;
}

.location-list {
  flex: 1;
  overflow-y: auto;
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.location-item {
  display: flex;
  gap: 12px;
  padding: 12px;
  border-radius: 0;
  cursor: pointer;
  border: 1px solid var(--ct-line);
  transition: all 0.2s;
}

.location-item:hover {
  border-color: var(--ct-red);
  background: var(--ct-red-soft);
}

.location-item.active {
  border-color: var(--ct-red);
  background: var(--ct-red-soft);
}

.loc-icon {
  width: 40px;
  height: 40px;
  border-radius: 0;
  display: flex;
  align-items: center;
  justify-content: center;
  color: #fff;
  flex-shrink: 0;
}

.loc-info h4 {
  font-size: 14px;
  font-weight: 600;
  margin: 0 0 2px 0;
}

.loc-info p {
  font-size: 12px;
  color: var(--ct-muted);
  margin: 0;
}

.loc-hours {
  display: flex;
  align-items: center;
  gap: 4px;
}

.map-area {
  position: relative;
  background: var(--ct-white);
  border-radius: 0;
  overflow: hidden;
}

.map-canvas {
  width: 100%;
  height: 100%;
  position: relative;
  z-index: 0;
}

.map-toolbar {
  position: absolute;
  top: 16px;
  left: 16px;
  z-index: 500;
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 8px;
  background: rgba(255,255,255,0.95);
  padding: 10px 8px;
  border-radius: 0;
  box-shadow: 0 2px 12px rgba(0,0,0,0.15);
  backdrop-filter: blur(8px);
}

.toolbar-sep {
  width: 28px;
  height: 1px;
  background: #e0d9cc;
  margin: 2px 0;
}

.map-toolbar :deep(.el-button) {
  box-shadow: none;
  margin: 0 !important;
}

.poi-search-box {
  position: absolute;
  top: 16px;
  left: 80px;
  z-index: 500;
  width: 320px;
}

/* 出行方式切换（地图右上角） */
.travel-mode-switch {
  position: absolute;
  top: 16px;
  right: 16px;
  z-index: 500;
  display: flex;
  gap: 6px;
  padding: 5px;
  background: rgba(255, 255, 255, 0.95);
  border-radius: 0;
  box-shadow: 0 2px 12px rgba(0, 0, 0, 0.15);
}

.mode-btn {
  display: flex;
  align-items: center;
  gap: 4px;
  padding: 6px 12px;
  border: 1px solid var(--ct-line);
  border-radius: 0;
  background: var(--ct-white);
  color: #4a4845;
  font-size: 13px;
  cursor: pointer;
  transition: all 0.2s;
}

.mode-btn:hover {
  border-color: var(--ct-red);
  color: var(--ct-red);
}

.mode-btn.active {
  color: #fff;
  border-color: var(--ct-red);
  background: var(--ct-red);
}

.poi-results-panel {
  position: absolute;
  top: 64px;
  left: 80px;
  z-index: 500;
  width: 320px;
  max-height: 400px;
  background: var(--ct-white);
  border-radius: 0;
  box-shadow: 0 4px 20px rgba(0,0,0,0.15);
  overflow: hidden;
  display: flex;
  flex-direction: column;
}

.poi-results-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 10px 14px;
  border-bottom: 1px solid var(--ct-line);
  font-size: 13px;
  font-weight: 600;
  color: var(--ct-ink);
  flex-shrink: 0;
}

.poi-results-list {
  flex: 1;
  overflow-y: auto;
}

.poi-result-item {
  display: flex;
  gap: 10px;
  padding: 10px 14px;
  cursor: pointer;
  border-bottom: 1px solid var(--ct-line);
  transition: background 0.2s;
}

.poi-result-item:hover {
  background: var(--ct-red-soft);
}

.poi-idx {
  width: 22px;
  height: 22px;
  border-radius: 50%;
  background: var(--ct-red);
  color: #fff;
  font-size: 12px;
  display: flex;
  align-items: center;
  justify-content: center;
  flex-shrink: 0;
}

.poi-info {
  flex: 1;
  min-width: 0;
}

.poi-name {
  font-size: 13px;
  font-weight: 600;
  color: var(--ct-ink);
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}

.poi-addr {
  font-size: 12px;
  color: var(--ct-muted);
  margin-top: 2px;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}

.current-pos-badge {
  position: absolute;
  bottom: 16px;
  left: 16px;
  z-index: 400;
  display: flex;
  align-items: center;
  gap: 6px;
  padding: 6px 12px;
  background: var(--ct-white);
  border-radius: 0;
  box-shadow: 0 2px 12px rgba(0,0,0,0.15);
  font-size: 12px;
  color: var(--ct-red);
  font-weight: 600;
}

.pulse-icon {
  animation: pulse 1.5s ease-in-out infinite;
}

@keyframes pulse {
  0%, 100% { transform: scale(1); opacity: 1; }
  50% { transform: scale(1.2); opacity: 0.7; }
}

.map-loading {
  position: absolute;
  top: 0;
  left: 0;
  right: 0;
  bottom: 0;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  background: var(--ct-paper);
  color: var(--ct-muted);
  gap: 12px;
  z-index: 600;
}

.loading-icon {
  font-size: 32px;
  animation: rotate 1.5s linear infinite;
}

@keyframes rotate {
  from { transform: rotate(0deg); }
  to { transform: rotate(360deg); }
}

.location-detail {
  position: absolute;
  bottom: 16px;
  right: 16px;
  background: var(--ct-white);
  border-radius: 0;
  padding: 16px;
  box-shadow: 0 4px 20px rgba(0,0,0,0.15);
  z-index: 400;
  width: 360px;
  max-width: calc(100% - 32px);
}

.detail-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 10px;
}

.detail-header h3 {
  margin: 0;
  font-size: 18px;
}

.detail-images {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
  margin-bottom: 10px;
}

.detail-image {
  width: calc(50% - 4px);
  height: 120px;
  border-radius: 0;
  cursor: pointer;
}

.detail-body p {
  display: flex;
  align-items: center;
  gap: 6px;
  font-size: 13px;
  color: #4a4845;
  margin: 4px 0;
}

.detail-desc {
  color: var(--ct-muted) !important;
  margin-top: 8px !important;
}

.detail-actions {
  margin-top: 12px;
  display: flex;
  gap: 8px;
  flex-wrap: wrap;
}

.custom-point-card {
  bottom: 16px;
  left: 16px;
}

.route-panel {
  margin-top: 16px;
  background: var(--ct-white);
  border-radius: 0;
  box-shadow: 0 2px 12px rgba(0,0,0,0.08);
  overflow: hidden;
}

.route-panel-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 12px 16px;
  cursor: pointer;
  user-select: none;
  border-bottom: 1px solid var(--ct-line);
}

.route-panel-header h3 {
  margin: 0;
  font-size: 16px;
  display: flex;
  align-items: center;
  gap: 6px;
}

/* 出行方式标签（标题内）+ 面板内切换 */
.route-mode-tag {
  display: inline-block;
  padding: 1px 10px;
  border-radius: 0;
  font-size: 12px;
  font-weight: 500;
  color: #fff;
}

.route-mode-row {
  display: flex;
  align-items: center;
  gap: 12px;
  margin-bottom: 12px;
  flex-wrap: wrap;
}

.route-mode-label {
  font-size: 13px;
  color: var(--ct-muted);
}

.route-notice {
  margin-bottom: 12px;
}

.route-panel-actions {
  display: flex;
  align-items: center;
  gap: 12px;
}

.route-panel-summary {
  font-size: 13px;
  color: #4a4845;
}

.route-panel-summary strong {
  color: var(--ct-red);
}

.collapse-icon {
  transition: transform 0.3s;
  font-size: 14px;
  color: var(--ct-muted);
}

.collapse-icon.rotated {
  transform: rotate(-180deg);
}

.route-panel-body {
  padding: 12px 16px;
  max-height: 300px;
  overflow-y: auto;
}

.route-steps {
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.route-step {
  display: flex;
  gap: 10px;
  padding: 8px;
  border-radius: 0;
  background: #faf7f2;
}

.step-num {
  width: 22px;
  height: 22px;
  border-radius: 50%;
  background: var(--ct-red);
  color: #fff;
  font-size: 12px;
  display: flex;
  align-items: center;
  justify-content: center;
  flex-shrink: 0;
}

.step-text {
  font-size: 12px;
  color: #4a4845;
  line-height: 1.5;
}


<style>
/* 非 scoped：Leaflet 容器与 divIcon 样式 */
.leaflet-container {
  width: 100%;
  height: 100%;
  background: #e8ecf1;
  font-family: inherit;
}

.campus-div-icon {
  background: transparent;
  border: none;
}

.campus-div-icon svg {
  display: block;
  filter: drop-shadow(0 1px 2px rgba(0,0,0,0.3));
}

.leaflet-tooltip {
  font-size: 12px;
  border-radius: 6px;
}
</style>
