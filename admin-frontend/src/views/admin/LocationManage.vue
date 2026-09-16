<template>
  <AdminLayout>
    <div class="manage-page">
      <div class="page-header">
        <h2>地图地点管理</h2>
        <p>管理校园地图上的建筑和地点标记，学生端地图页面会实时显示这些地点</p>
      </div>

      <div class="toolbar">
        <el-input v-model="searchKeyword" placeholder="搜索地点名称..." clearable style="width: 240px" @clear="loadData" @keyup.enter="loadData">
          <template #prefix><el-icon><Search /></el-icon></template>
        </el-input>
        <el-select v-model="filterCategory" placeholder="全部分类" clearable style="width: 140px" @change="loadData">
          <el-option v-for="cat in categories" :key="cat" :label="cat" :value="cat" />
        </el-select>
        <el-button type="primary" @click="showCreate"><el-icon><Plus /></el-icon> 添加地点</el-button>
      </div>

      <el-table :data="locations" stripe style="width: 100%" v-loading="loading">
        <el-table-column type="index" label="#" width="60" />
        <el-table-column prop="name" label="地点名称" min-width="160" show-overflow-tooltip />
        <el-table-column label="分类" width="90">
          <template #default="{ row }">
            <el-tag size="small" :color="getCategoryColor(row.category)" style="color:#fff;border:none">{{ row.category }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="address" label="地址" min-width="180" show-overflow-tooltip />
        <el-table-column label="坐标" width="180">
          <template #default="{ row }">
            <span class="coord-text">{{ row.longitude?.toFixed(6) }}, {{ row.latitude?.toFixed(6) }}</span>
          </template>
        </el-table-column>
        <el-table-column prop="openHours" label="开放时间" width="140" show-overflow-tooltip />
        <el-table-column prop="phone" label="电话" width="120" />
        <el-table-column prop="sortOrder" label="排序" width="70" />
        <el-table-column label="操作" width="160" fixed="right">
          <template #default="{ row }">
            <el-button text type="primary" size="small" @click="showEdit(row)">编辑</el-button>
            <el-button text type="danger" size="small" @click="deleteItem(row)">删除</el-button>
          </template>
        </el-table-column>
      </el-table>

      <div class="pagination">
        <el-pagination
          v-model:current-page="page"
          v-model:page-size="size"
          :total="total"
          :page-sizes="[10, 20, 50]"
          layout="total, sizes, prev, pager, next"
          @size-change="loadData"
          @current-change="loadData"
        />
      </div>

      <!-- 编辑弹窗 -->
      <el-dialog v-model="dialogVisible" :title="form.id ? '编辑地点' : '添加地点'" width="680px" top="3vh">
        <el-form :model="form" label-width="90px">
          <el-form-item label="地点名称" required>
            <el-input v-model="form.name" placeholder="如：图书馆、第一教学楼" />
          </el-form-item>
          <el-form-item label="分类" required>
            <el-select v-model="form.category" style="width: 100%">
              <el-option v-for="cat in categories" :key="cat" :label="cat" :value="cat" />
            </el-select>
          </el-form-item>
          <el-form-item label="地址">
            <el-input v-model="form.address" placeholder="详细地址" />
          </el-form-item>
          <el-row :gutter="16">
            <el-col :span="12">
              <el-form-item label="经度" required>
                <el-input v-model.number="form.longitude" placeholder="如：108.290700">
                  <template #append>°E</template>
                </el-input>
              </el-form-item>
            </el-col>
            <el-col :span="12">
              <el-form-item label="纬度" required>
                <el-input v-model.number="form.latitude" placeholder="如：22.841200">
                  <template #append>°N</template>
                </el-input>
              </el-form-item>
            </el-col>
          </el-row>
          <el-form-item label="坐标获取">
            <el-button size="small" @click="showMapPicker">
              <el-icon><Location /></el-icon> 在地图上选取坐标
            </el-button>
            <span class="form-tip">点击按钮可在地图上点击选取经纬度</span>
          </el-form-item>
          <el-form-item label="开放时间">
            <el-input v-model="form.openHours" placeholder="如：周一至周日 8:00-22:00" />
          </el-form-item>
          <el-form-item label="联系电话">
            <el-input v-model="form.phone" placeholder="联系电话（选填）" />
          </el-form-item>
          <el-row :gutter="16">
            <el-col :span="12">
              <el-form-item label="所在楼栋">
                <el-input v-model="form.building" placeholder="如：图书馆" />
              </el-form-item>
            </el-col>
            <el-col :span="12">
              <el-form-item label="楼层">
                <el-input-number v-model="form.floor" :min="1" :max="99" style="width: 100%" />
              </el-form-item>
            </el-col>
          </el-row>
          <el-form-item label="排序权重">
            <el-input-number v-model="form.sortOrder" :min="0" :max="999" style="width: 120px" />
            <span class="form-tip">数字越小越靠前</span>
          </el-form-item>
          <el-form-item label="地点照片">
            <div class="image-upload-area">
              <el-upload
                :action="uploadUrl"
                :headers="uploadHeaders"
                :show-file-list="false"
                accept="image/*"
                multiple
                :on-success="onImageSuccess"
                :before-upload="beforeImageUpload"
              >
                <el-button size="small">
                  <el-icon><Picture /></el-icon> 上传照片
                </el-button>
              </el-upload>
              <span class="form-tip">可多选，弹窗内按 Ctrl+V 可直接粘贴剪贴板图片</span>
            </div>
            <div v-if="form.imageList.length" class="image-list">
              <div v-for="(url, idx) in form.imageList" :key="idx" class="image-item">
                <el-image :src="url" :preview-src-list="form.imageList" :initial-index="idx" fit="cover" class="image-thumb" />
                <el-button text type="danger" size="small" class="image-remove" @click="removeImage(idx)">删除</el-button>
              </div>
            </div>
            <span class="form-tip">支持 jpg/png/gif/webp，单张最大 5MB</span>
          </el-form-item>
          <el-form-item label="描述">
            <el-input v-model="form.description" type="textarea" :rows="3" placeholder="地点描述（选填）" />
          </el-form-item>
        </el-form>
        <template #footer>
          <el-button @click="dialogVisible = false">取消</el-button>
          <el-button type="primary" @click="submit" :loading="submitting">保存</el-button>
        </template>
      </el-dialog>

      <!-- 地图选点弹窗 -->
      <el-dialog v-model="mapPickerVisible" title="在地图上选取坐标" width="800px" top="5vh">
        <div class="map-picker-container">
          <div id="picker-map" class="picker-map"></div>
          <div class="picker-info">
            <el-icon><Location /></el-icon>
            <span>点击地图任意位置选取坐标</span>
            <span v-if="pickedLng" class="picked-coord">
              经度: {{ pickedLng.toFixed(6) }}，纬度: {{ pickedLat.toFixed(6) }}
            </span>
          </div>
        </div>
        <template #footer>
          <el-button @click="mapPickerVisible = false">取消</el-button>
          <el-button type="primary" @click="confirmPick" :disabled="!pickedLng">确认选取</el-button>
        </template>
      </el-dialog>
    </div>
  </AdminLayout>
</template>

<script setup>
import { ref, reactive, onMounted, onBeforeUnmount, nextTick, computed, watch } from 'vue'
import L from 'leaflet'
import 'leaflet/dist/leaflet.css'
import AdminLayout from '@/components/AdminLayout.vue'
import { adminGetLocations, adminCreateLocation, adminUpdateLocation, adminDeleteLocation } from '@/api'
import { ElMessage, ElMessageBox } from 'element-plus'
import { getCategoryColor, mapConfig, toWgs84 } from '@/config/map'
import { Picture } from '@element-plus/icons-vue'

const categories = ['教学', '生活', '服务', '餐饮', '运动', '其他']
const locations = ref([])
const loading = ref(false)
const submitting = ref(false)
const searchKeyword = ref('')
const filterCategory = ref('')
const page = ref(1)
const size = ref(20)
const total = ref(0)
const dialogVisible = ref(false)
const mapPickerVisible = ref(false)
const pickedLng = ref(null)
const pickedLat = ref(null)
let pickerMap = null
let pickerMark = null

const form = reactive({
  id: null, name: '', category: '教学', address: '',
  longitude: null, latitude: null, description: '',
  openHours: '', phone: '', imageList: [],
  floor: null, building: '', sortOrder: 0,
})

// 图片上传配置
const uploadUrl = '/api/admin/upload/image'
const uploadHeaders = computed(() => ({
  Authorization: `Bearer ${localStorage.getItem('token') || ''}`
}))

function beforeImageUpload(file) {
  if (file.size > 5 * 1024 * 1024) {
    ElMessage.warning('图片大小不能超过 5MB')
    return false
  }
  return true
}

function onImageSuccess(res) {
  if (res.code === 200 && res.data?.url) {
    if (!form.imageList.includes(res.data.url)) {
      form.imageList.push(res.data.url)
    }
    ElMessage.success('照片上传成功')
  } else {
    ElMessage.error(res.message || '上传失败')
  }
}

function removeImage(idx) {
  form.imageList.splice(idx, 1)
}

// 粘贴上传：从剪贴板读取图片文件并上传
async function handlePaste(e) {
  const items = e.clipboardData?.items
  if (!items) return
  for (const item of items) {
    if (item.type.startsWith('image/')) {
      const file = item.getAsFile()
      if (!file) continue
      if (file.size > 5 * 1024 * 1024) {
        ElMessage.warning('粘贴的图片大小不能超过 5MB')
        continue
      }
      const formData = new FormData()
      formData.append('file', file)
      try {
        const res = await fetch(uploadUrl, {
          method: 'POST',
          headers: { Authorization: `Bearer ${localStorage.getItem('token') || ''}` },
          body: formData
        }).then(r => r.json())
        onImageSuccess(res)
      } catch {
        ElMessage.error('粘贴图片上传失败')
      }
    }
  }
}

// 弹窗打开时注册全局粘贴监听，关闭时移除
watch(dialogVisible, (val) => {
  if (val) {
    window.addEventListener('paste', handlePaste)
  } else {
    window.removeEventListener('paste', handlePaste)
  }
})

onBeforeUnmount(() => {
  window.removeEventListener('paste', handlePaste)
})

async function loadData() {
  loading.value = true
  try {
    const params = { page: page.value, size: size.value }
    if (searchKeyword.value) params.keyword = searchKeyword.value
    const res = await adminGetLocations(params)
    let list = res.data.list || res.data || []
    if (filterCategory.value) {
      list = list.filter(l => l.category === filterCategory.value)
    }
    locations.value = list
    total.value = res.data.total || list.length
  } catch (e) {
    ElMessage.error('加载地点列表失败')
  } finally {
    loading.value = false
  }
}

function showCreate() {
  Object.assign(form, {
    id: null, name: '', category: '教学', address: '',
    longitude: null, latitude: null, description: '',
    openHours: '', phone: '', imageList: [],
    floor: null, building: '', sortOrder: locations.value.length + 1,
  })
  dialogVisible.value = true
}

function showEdit(row) {
  const imageList = row.imageUrl ? row.imageUrl.split(',').filter(Boolean) : []
  Object.assign(form, { ...row, imageList })
  dialogVisible.value = true
}

async function submit() {
  if (!form.name || !form.category) {
    ElMessage.warning('请填写地点名称和分类')
    return
  }
  if (form.longitude == null || form.latitude == null) {
    ElMessage.warning('请填写经纬度坐标，或点击地图选取')
    return
  }
  submitting.value = true
  try {
    const payload = { ...form, imageUrl: form.imageList.join(',') }
    delete payload.imageList
    if (form.id) {
      await adminUpdateLocation(form.id, payload)
      ElMessage.success('更新成功')
    } else {
      await adminCreateLocation(payload)
      ElMessage.success('添加成功')
    }
    dialogVisible.value = false
    loadData()
  } catch (e) {
    ElMessage.error('保存失败')
  } finally {
    submitting.value = false
  }
}

async function deleteItem(row) {
  try {
    await ElMessageBox.confirm(`确定要删除地点「${row.name}」吗？`, '确认删除', { type: 'warning' })
    await adminDeleteLocation(row.id)
    ElMessage.success('删除成功')
    loadData()
  } catch (e) {
    if (e !== 'cancel') ElMessage.error('删除失败')
  }
}

// 地图选点（Leaflet 离线地图，无需 AK）
async function showMapPicker() {
  mapPickerVisible.value = true
  pickedLng.value = form.longitude
  pickedLat.value = form.latitude
  await nextTick()
  initPickerMap()
}

function initPickerMap() {
  const container = document.getElementById('picker-map')
  if (!container) return

  // 重复打开时先销毁旧地图
  if (pickerMap) {
    pickerMap.remove()
    pickerMap = null
    pickerMark = null
  }

  const center = toWgs84(
    pickedLng.value || mapConfig.defaultCenter.lng,
    pickedLat.value || mapConfig.defaultCenter.lat
  )

  pickerMap = L.map('picker-map', {
    center: [center.lat, center.lng],
    zoom: 17,
    minZoom: mapConfig.minZoom,
    maxZoom: mapConfig.maxZoom,
    zoomControl: true,
    attributionControl: false,
  })

  L.tileLayer(mapConfig.tileUrls.normal, {
    minZoom: mapConfig.minZoom,
    maxZoom: mapConfig.maxZoom,
  }).addTo(pickerMap)

  // 如果已有坐标，显示标记
  if (pickedLng.value && pickedLat.value) {
    pickerMark = L.circleMarker([center.lat, center.lng], {
      radius: 9, color: '#f56c6c', fillColor: '#f56c6c', fillOpacity: 0.85, weight: 2,
    }).addTo(pickerMap)
  }

  // 点击地图选取坐标
  pickerMap.on('click', (e) => {
    if (pickerMark) pickerMap.removeLayer(pickerMark)
    pickedLng.value = e.latlng.lng
    pickedLat.value = e.latlng.lat
    pickerMark = L.circleMarker([e.latlng.lat, e.latlng.lng], {
      radius: 9, color: '#f56c6c', fillColor: '#f56c6c', fillOpacity: 0.85, weight: 2,
    }).addTo(pickerMap)
  })
}

function confirmPick() {
  form.longitude = pickedLng.value
  form.latitude = pickedLat.value
  mapPickerVisible.value = false
  ElMessage.success('坐标已选取')
}

onMounted(() => {
  loadData()
})
</script>

<style scoped>
.manage-page {
  padding: 0;
}

.page-header {
  margin-bottom: 16px;
}

.page-header h2 {
  margin: 0 0 4px 0;
  font-size: 20px;
}

.page-header p {
  margin: 0;
  color: #909399;
  font-size: 13px;
}

.toolbar {
  display: flex;
  gap: 12px;
  margin-bottom: 16px;
  align-items: center;
}

.coord-text {
  font-family: monospace;
  font-size: 12px;
  color: #606266;
}

.pagination {
  margin-top: 16px;
  display: flex;
  justify-content: flex-end;
}

.form-tip {
  margin-left: 8px;
  color: #909399;
  font-size: 12px;
}

.map-picker-container {
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.picker-map {
  width: 100%;
  height: 400px;
  border-radius: 8px;
  overflow: hidden;
}

.picker-info {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 10px 14px;
  background: #ecf5ff;
  border-radius: 8px;
  font-size: 13px;
  color: #606266;
}

.picked-coord {
  margin-left: auto;
  font-weight: 600;
  color: #409eff;
  font-family: monospace;
}

.image-upload-area {
  display: flex;
  align-items: center;
  gap: 10px;
  outline: none;
}

.image-list {
  margin-top: 10px;
  display: flex;
  flex-wrap: wrap;
  gap: 10px;
}

.image-item {
  position: relative;
  width: 120px;
}

.image-thumb {
  width: 120px;
  height: 90px;
  border-radius: 6px;
  border: 1px solid #dcdfe6;
  cursor: pointer;
}

.image-remove {
  display: block;
  text-align: center;
  margin-top: 2px;
  padding: 0;
}
</style>

<style>
/* Leaflet 容器在管理后台选点弹窗中的样式 */
.leaflet-container {
  width: 100%;
  height: 100%;
  font-family: inherit;
}
</style>
