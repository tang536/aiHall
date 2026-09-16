<template>
  <AdminLayout>
    <div class="manage-page">
      <el-tabs v-model="activeTab">
        <!-- ==================== 商品管理 ==================== -->
        <el-tab-pane label="商品管理" name="items">
          <div class="toolbar">
            <el-radio-group v-model="statusFilter" @change="loadItems">
              <el-radio-button value="">全部</el-radio-button>
              <el-radio-button value="ON_SALE">在售</el-radio-button>
              <el-radio-button value="OFF_SHELF">已下架</el-radio-button>
              <el-radio-button value="SOLD">已售出</el-radio-button>
            </el-radio-group>
            <el-input
              v-model="keyword"
              placeholder="搜索商品标题 / 描述"
              clearable
              style="width: 240px"
              @keyup.enter="loadItems"
              @clear="loadItems"
            >
              <template #prefix><el-icon><Search /></el-icon></template>
              <template #append><el-button @click="loadItems">搜索</el-button></template>
            </el-input>
            <el-button @click="loadItems"><el-icon><Refresh /></el-icon> 刷新</el-button>
          </div>

          <el-table :data="items" stripe style="width: 100%">
            <el-table-column label="图片" width="90">
              <template #default="{ row }">
                <el-image
                  v-if="firstImage(row.images)"
                  :src="firstImage(row.images)"
                  :preview-src-list="imageList(row.images)"
                  fit="cover"
                  class="thumb"
                />
                <div v-else class="thumb thumb-empty"><el-icon><Picture /></el-icon></div>
              </template>
            </el-table-column>
            <el-table-column prop="title" label="商品标题" min-width="180" show-overflow-tooltip />
            <el-table-column label="价格" width="100" sortable :sort-by="(r) => Number(r.price)">
              <template #default="{ row }">
                <span class="price">{{ formatMoney(row.price) }}</span>
              </template>
            </el-table-column>
            <el-table-column label="分类" width="100">
              <template #default="{ row }">{{ categoryText(row.category) }}</template>
            </el-table-column>
            <el-table-column label="卖家" width="130" show-overflow-tooltip>
              <template #default="{ row }">
                {{ row.seller?.displayName || ('用户 #' + row.sellerId) }}
              </template>
            </el-table-column>
            <el-table-column label="状态" width="100">
              <template #default="{ row }">
                <el-tag size="small" :type="marketStatusTypeMap[row.status] || 'info'">
                  {{ marketStatusMap[row.status] || row.status }}
                </el-tag>
              </template>
            </el-table-column>
            <el-table-column label="上线时间" width="160">
              <template #default="{ row }">{{ formatTime(row.onlineTime || row.createTime) }}</template>
            </el-table-column>
            <el-table-column label="浏览/分享" width="100">
              <template #default="{ row }">{{ row.viewCount || 0 }} / {{ row.shareCount || 0 }}</template>
            </el-table-column>
            <el-table-column label="操作" width="200" fixed="right">
              <template #default="{ row }">
                <el-button text type="primary" size="small" @click="viewDetail(row)">详情</el-button>
                <el-button
                  v-if="row.status === 'ON_SALE'"
                  text
                  type="warning"
                  size="small"
                  @click="offShelf(row)"
                >下架</el-button>
                <el-button
                  v-else-if="row.status === 'OFF_SHELF'"
                  text
                  type="success"
                  size="small"
                  @click="onShelf(row)"
                >上架</el-button>
                <el-button text type="danger" size="small" @click="removeItem(row)">删除</el-button>
              </template>
            </el-table-column>
          </el-table>
          <el-empty v-if="items.length === 0" description="暂无商品" :image-size="80" />

          <div style="display: flex; justify-content: flex-end; margin-top: 16px">
            <el-pagination
              v-model:current-page="itemPage"
              v-model:page-size="itemSize"
              :total="itemTotal"
              :page-sizes="[10, 20, 50]"
              layout="total, sizes, prev, pager, next"
              @current-change="loadItems"
              @size-change="onItemSizeChange"
            />
          </div>
        </el-tab-pane>

        <!-- ==================== 订单记录 ==================== -->
        <el-tab-pane label="交易订单" name="orders">
          <div class="toolbar">
            <el-button @click="loadOrders"><el-icon><Refresh /></el-icon> 刷新订单</el-button>
            <div class="toolbar-tip">共 {{ orderTotal }} 笔 · 本页成交额 {{ formatMoney(totalAmount) }}</div>
          </div>
          <el-table :data="orders" stripe style="width: 100%">
            <el-table-column prop="orderNo" label="订单号" width="230" />
            <el-table-column prop="itemTitle" label="商品" min-width="180" show-overflow-tooltip />
            <el-table-column label="买家" width="110">
              <template #default="{ row }">用户 #{{ row.buyerId }}</template>
            </el-table-column>
            <el-table-column label="卖家" width="110">
              <template #default="{ row }">用户 #{{ row.sellerId }}</template>
            </el-table-column>
            <el-table-column label="金额" width="110">
              <template #default="{ row }"><span class="price">{{ formatMoney(row.amount) }}</span></template>
            </el-table-column>
            <el-table-column label="状态" width="100">
              <template #default="{ row }">
                <el-tag size="small" effect="plain">{{ orderStatusText(row.status) }}</el-tag>
              </template>
            </el-table-column>
            <el-table-column label="成交时间" width="170">
              <template #default="{ row }">{{ formatTime(row.createTime) }}</template>
            </el-table-column>
          </el-table>
          <el-empty v-if="orders.length === 0" description="暂无交易订单" :image-size="80" />

          <div style="display: flex; justify-content: flex-end; margin-top: 16px">
            <el-pagination
              v-model:current-page="orderPage"
              v-model:page-size="orderSize"
              :total="orderTotal"
              :page-sizes="[10, 20, 50]"
              layout="total, sizes, prev, pager, next"
              @current-change="loadOrders"
              @size-change="onOrderSizeChange"
            />
          </div>
        </el-tab-pane>
      </el-tabs>

      <!-- 商品详情 -->
      <el-dialog v-model="detailVisible" title="商品详情" width="620px">
        <template v-if="current">
          <el-descriptions :column="2" border>
            <el-descriptions-item label="标题" :span="2">{{ current.title }}</el-descriptions-item>
            <el-descriptions-item label="价格">{{ formatMoney(current.price) }}</el-descriptions-item>
            <el-descriptions-item label="原价">
              {{ current.originalPrice ? formatMoney(current.originalPrice) : '-' }}
            </el-descriptions-item>
            <el-descriptions-item label="分类">{{ categoryText(current.category) }}</el-descriptions-item>
            <el-descriptions-item label="成色">{{ conditionText(current.itemCondition) }}</el-descriptions-item>
            <el-descriptions-item label="状态">
              <el-tag size="small" :type="marketStatusTypeMap[current.status] || 'info'">
                {{ marketStatusMap[current.status] || current.status }}
              </el-tag>
            </el-descriptions-item>
            <el-descriptions-item label="交易地点">{{ current.tradeLocation || '-' }}</el-descriptions-item>
            <el-descriptions-item label="卖家" :span="2">
              {{ current.seller?.displayName || ('用户 #' + current.sellerId) }}
              <span v-if="current.seller?.college" class="muted"> · {{ current.seller.college }}</span>
            </el-descriptions-item>
            <el-descriptions-item label="描述" :span="2">
              <div class="desc-text">{{ current.description }}</div>
            </el-descriptions-item>
            <el-descriptions-item label="上线时间" :span="2">
              {{ formatTime(current.onlineTime || current.createTime) }}
            </el-descriptions-item>
          </el-descriptions>
          <div v-if="imageList(current.images).length" class="detail-images">
            <el-image
              v-for="(url, idx) in imageList(current.images)"
              :key="idx"
              :src="url"
              :preview-src-list="imageList(current.images)"
              :initial-index="idx"
              fit="cover"
              class="detail-image"
            />
          </div>
        </template>
      </el-dialog>
    </div>
  </AdminLayout>
</template>

<script setup>
import { computed, onMounted, ref, watch } from 'vue'
import AdminLayout from '@/components/AdminLayout.vue'
import {
  adminGetMarketItems, adminOffShelfMarketItem, adminOnShelfMarketItem,
  adminDeleteMarketItem, adminGetMarketOrders
} from '@/api'
import { ElMessage, ElMessageBox } from 'element-plus'
import {
  formatTime, formatMoney, marketStatusMap, marketStatusTypeMap
} from '@/utils/format'

const MARKET_CATEGORY_LABEL = {
  TEXTBOOK: '教材书籍',
  ELECTRONIC: '电子数码',
  DAILY: '生活用品',
  SPORTS: '运动户外',
  OTHER: '其他'
}
const CONDITION_LABEL = {
  NEW: '全新',
  LIKE_NEW: '几乎全新',
  GOOD: '轻微使用痕迹',
  FAIR: '明显使用痕迹'
}

const activeTab = ref('items')
const statusFilter = ref('')
const keyword = ref('')
const items = ref([])
const orders = ref([])
// 商品与订单各自独立分页，均走服务端分页（只加载当前页）
const itemPage = ref(1)
const itemSize = ref(20)
const itemTotal = ref(0)
const itemsLoading = ref(false)
const itemsError = ref('')
const orderPage = ref(1)
const orderSize = ref(20)
const orderTotal = ref(0)
const detailVisible = ref(false)
const current = ref(null)

const totalAmount = computed(() =>
  orders.value.reduce((sum, o) => sum + Number(o.amount || 0), 0)
)

function categoryText(c) {
  return MARKET_CATEGORY_LABEL[c] || c || '-'
}
function conditionText(c) {
  return CONDITION_LABEL[c] || c || '-'
}
function orderStatusText(s) {
  return { PAID: '已支付', COMPLETED: '已完成', CANCELLED: '已取消' }[s] || s
}
function imageList(images) {
  return (images || '').split(',').map((s) => s.trim()).filter(Boolean)
}
function firstImage(images) {
  return imageList(images)[0] || ''
}

async function loadItems() {
  itemsLoading.value = true
  itemsError.value = ''
  try {
    const res = await adminGetMarketItems({
      status: statusFilter.value || undefined,
      keyword: keyword.value || undefined,
      page: itemPage.value,
      size: itemSize.value
    })
    items.value = res.data?.list || []
    itemTotal.value = res.data?.total || 0
  } catch (e) {
    itemsError.value = (e && e.message) ? e.message : '商品列表加载失败，请稍后重试'
  } finally {
    itemsLoading.value = false
  }
}

function onItemSizeChange() {
  itemPage.value = 1
  loadItems()
}

async function loadOrders() {
  try {
    const res = await adminGetMarketOrders({
      page: orderPage.value,
      size: orderSize.value
    })
    orders.value = res.data?.list || []
    orderTotal.value = res.data?.total || 0
  } catch {
    /* 拦截器已提示 */
  }
}

function onOrderSizeChange() {
  orderPage.value = 1
  loadOrders()
}

function viewDetail(row) {
  current.value = row
  detailVisible.value = true
}

async function offShelf(row) {
  try {
    await ElMessageBox.confirm(`确定强制下架「${row.title}」吗？`, '提示', { type: 'warning' })
  } catch {
    return
  }
  try {
    await adminOffShelfMarketItem(row.id)
    ElMessage.success('已强制下架')
    loadItems()
  } catch {
    /* 拦截器已提示 */
  }
}

async function onShelf(row) {
  try {
    await adminOnShelfMarketItem(row.id)
    ElMessage.success('已恢复上架')
    loadItems()
  } catch {
    /* 拦截器已提示 */
  }
}

async function removeItem(row) {
  try {
    await ElMessageBox.confirm(`确定删除商品「${row.title}」吗？删除后不可恢复。`, '提示', { type: 'warning' })
  } catch {
    return
  }
  try {
    await adminDeleteMarketItem(row.id)
    ElMessage.success('已删除')
    loadItems()
  } catch {
    /* 拦截器已提示 */
  }
}

watch(activeTab, (tab) => {
  if (tab === 'orders') loadOrders()
})

onMounted(loadItems)
</script>

<style scoped>
.manage-page {
  display: flex;
  flex-direction: column;
  gap: 12px;
}
.toolbar {
  display: flex;
  gap: 12px;
  align-items: center;
  flex-wrap: wrap;
  margin-bottom: 12px;
}
.toolbar-tip {
  margin-left: auto;
  font-size: 13px;
  color: #909399;
}
.thumb {
  width: 56px;
  height: 56px;
  border-radius: 6px;
}
.thumb-empty {
  display: flex;
  align-items: center;
  justify-content: center;
  background: #f5f7fa;
  color: #c0c4cc;
}
.price {
  color: #f56c6c;
  font-weight: 600;
}
.muted {
  color: #909399;
}
.desc-text {
  white-space: pre-wrap;
  line-height: 1.7;
}
.detail-images {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
  margin-top: 14px;
}
.detail-image {
  width: 100px;
  height: 100px;
  border-radius: 8px;
}
</style>
