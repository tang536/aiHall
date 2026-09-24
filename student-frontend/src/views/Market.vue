<template>
  <div class="market-page">
    <div class="page-header">
      <h2><el-icon><Goods /></el-icon> 二手交易平台</h2>
      <p>校园内二手好物，支持余额支付、商品分享与卖家私聊</p>
    </div>

    <!-- 钱包条 -->
    <div class="wallet-bar">
      <div class="wallet-left">
        <el-icon class="wallet-icon"><Wallet /></el-icon>
        <span>我的余额</span>
        <strong class="balance">{{ formatMoney(userStore.balance) }}</strong>
      </div>
      <el-button size="small" type="primary" plain @click="rechargeVisible = true">
        <el-icon><Plus /></el-icon> 充值
      </el-button>
    </div>

    <el-tabs v-model="activeTab" class="market-tabs">
      <!-- 全部商品 -->
      <el-tab-pane label="全部商品" name="all">
        <div class="toolbar">
          <el-input
            v-model="query.keyword"
            placeholder="搜索商品名称或描述"
            clearable
            class="search-input"
            @keyup.enter="loadList"
            @clear="loadList"
          >
            <template #prefix><el-icon><Search /></el-icon></template>
            <template #append><el-button @click="loadList">搜索</el-button></template>
          </el-input>
          <el-button type="primary" @click="openPublish">
            <el-icon><Sell /></el-icon> 发布闲置
          </el-button>
        </div>

        <div class="category-row">
          <el-button
            v-for="cat in MARKET_CATEGORIES"
            :key="cat.value"
            size="small"
            :type="query.category === cat.value ? 'primary' : ''"
            @click="switchCategory(cat.value)"
          >
            {{ cat.label }}
          </el-button>
        </div>

        <div v-loading="loading" class="item-grid">
          <div v-for="item in list" :key="item.id" class="item-card card-hover" @click="goDetail(item.id)">
            <div class="item-cover">
              <el-image :src="firstImage(item.images)" fit="cover" class="cover-img">
                <template #error>
                  <div class="cover-placeholder"><el-icon :size="28"><Picture /></el-icon></div>
                </template>
              </el-image>
              <el-tag class="cond-tag" size="small" effect="dark">
                {{ ITEM_CONDITION_LABEL[item.itemCondition] || '成色未知' }}
              </el-tag>
            </div>
            <div class="item-body">
              <h4 class="item-title">{{ item.title }}</h4>
              <div class="price-row">
                <span class="price">{{ formatMoney(item.price) }}</span>
                <span v-if="item.originalPrice" class="origin-price">{{ formatMoney(item.originalPrice) }}</span>
              </div>
              <div class="item-meta">
                <span class="seller-name clickable" @click.stop="goUserProfile(item.seller)"><el-icon><User /></el-icon> {{ item.seller?.displayName }}</span>
                <span>{{ MARKET_CATEGORY_LABEL[item.category] || '其他' }}</span>
              </div>
              <div class="item-foot">
                <span>{{ relativeTime(item.onlineTime || item.createTime) }}</span>
                <span>{{ item.viewCount || 0 }} 次浏览</span>
              </div>
            </div>
          </div>
        </div>
        <el-empty v-if="!loading && list.length === 0" description="暂无在售商品，快来发布第一件闲置吧" />

        <div v-if="total > query.size" class="pager">
          <el-pagination
            layout="prev, pager, next"
            :total="total"
            :page-size="query.size"
            :current-page="query.page"
            @current-change="onPageChange"
          />
        </div>
      </el-tab-pane>

      <!-- 我的商品 -->
      <el-tab-pane label="我的商品" name="mine">
        <el-table :data="mineList" v-loading="mineLoading" empty-text="还没有发布过商品">
          <el-table-column prop="title" label="商品" min-width="160" show-overflow-tooltip />
          <el-table-column label="价格" width="100">
            <template #default="{ row }">{{ formatMoney(row.price) }}</template>
          </el-table-column>
          <el-table-column label="状态" width="100">
            <template #default="{ row }">
              <el-tag size="small" :type="MARKET_STATUS_TYPE[row.status]">
                {{ MARKET_STATUS_LABEL[row.status] || row.status }}
              </el-tag>
            </template>
          </el-table-column>
          <el-table-column label="上线日期" width="120">
            <template #default="{ row }">{{ (row.onlineTime || '').slice(0, 10) }}</template>
          </el-table-column>
          <el-table-column label="操作" width="230" fixed="right">
            <template #default="{ row }">
              <el-button size="small" text type="primary" @click="goDetail(row.id)">详情</el-button>
              <el-button
                v-if="row.status === 'ON_SALE'"
                size="small"
                text
                type="warning"
                @click="doOffShelf(row)"
              >下架</el-button>
              <el-button
                v-else-if="row.status === 'OFF_SHELF'"
                size="small"
                text
                type="success"
                @click="doOnShelf(row)"
              >重新上架</el-button>
              <el-button size="small" text type="danger" @click="doDelete(row)">删除</el-button>
            </template>
          </el-table-column>
        </el-table>
      </el-tab-pane>

      <!-- 我买到的 -->
      <el-tab-pane label="我买到的" name="bought">
        <el-table :data="boughtList" v-loading="orderLoading" empty-text="还没有购买记录">
          <el-table-column prop="itemTitle" label="商品" min-width="160" show-overflow-tooltip />
          <el-table-column label="成交金额" width="110">
            <template #default="{ row }">{{ formatMoney(row.amount) }}</template>
          </el-table-column>
          <el-table-column prop="orderNo" label="订单号" min-width="180" show-overflow-tooltip />
          <el-table-column label="时间" width="170">
            <template #default="{ row }">{{ (row.createTime || '').replace('T', ' ').slice(0, 16) }}</template>
          </el-table-column>
        </el-table>
      </el-tab-pane>

      <!-- 我卖出的 -->
      <el-tab-pane label="我卖出的" name="sold">
        <el-table :data="soldList" v-loading="orderLoading" empty-text="还没有卖出记录">
          <el-table-column prop="itemTitle" label="商品" min-width="160" show-overflow-tooltip />
          <el-table-column label="成交金额" width="110">
            <template #default="{ row }">{{ formatMoney(row.amount) }}</template>
          </el-table-column>
          <el-table-column prop="orderNo" label="订单号" min-width="180" show-overflow-tooltip />
          <el-table-column label="时间" width="170">
            <template #default="{ row }">{{ (row.createTime || '').replace('T', ' ').slice(0, 16) }}</template>
          </el-table-column>
        </el-table>
      </el-tab-pane>

      <!-- 我的收藏 -->
      <el-tab-pane label="我的收藏" name="favorites">
        <div v-loading="favoriteLoading" class="favorite-grid">
          <div
            v-for="item in favoriteList"
            :key="item.id"
            class="item-card card-hover"
            @click="goDetail(item.id)"
          >
            <div class="item-cover">
              <el-image
                v-if="splitImages(item.images)[0]"
                :src="splitImages(item.images)[0]"
                fit="cover"
                class="cover-img"
              />
              <div v-else class="cover-placeholder">
                <el-icon><Goods /></el-icon>
              </div>
              <el-tag v-if="item.status !== 'ON_SALE'" size="small" class="status-tag"
                      :type="item.status === 'SOLD' ? 'danger' : 'info'">
                {{ MARKET_STATUS_LABEL[item.status] || item.status }}
              </el-tag>
            </div>
            <div class="item-body">
              <h4 class="item-title">{{ item.title }}</h4>
              <div class="price-row">
                <span class="price">{{ formatMoney(item.price) }}</span>
              </div>
              <div class="item-meta">
                <span class="seller-name clickable" @click.stop="goUserProfile(item.seller)"><el-icon><User /></el-icon> {{ item.seller?.displayName }}</span>
              </div>
            </div>
          </div>
          <el-empty v-if="!favoriteLoading && favoriteList.length === 0" description="还没有收藏商品，去逛逛吧" :image-size="80" />
        </div>
      </el-tab-pane>
    </el-tabs>

    <!-- 发布 / 编辑商品 -->
    <el-dialog v-model="publishVisible" :title="editingId ? '编辑商品' : '发布闲置' " width="620px" top="6vh">
      <el-form :model="form" label-width="86px" :rules="rules" ref="formRef">
        <el-form-item label="商品标题" prop="title">
          <el-input v-model="form.title" maxlength="100" show-word-limit placeholder="例如：九成新考研数学教材" />
        </el-form-item>
        <el-form-item label="出售价格" prop="price">
          <el-input-number v-model="form.price" :min="0.01" :max="999999" :precision="2" :step="1" />
          <span class="unit">元</span>
        </el-form-item>
        <el-form-item label="原价">
          <el-input-number v-model="form.originalPrice" :min="0" :max="999999" :precision="2" :step="1" />
          <span class="unit">元（选填）</span>
        </el-form-item>
        <el-form-item label="分类">
          <el-select v-model="form.category" style="width: 100%">
            <el-option v-for="c in MARKET_CATEGORIES.slice(1)" :key="c.value" :label="c.label" :value="c.value" />
          </el-select>
        </el-form-item>
        <el-form-item label="成色">
          <el-radio-group v-model="form.itemCondition">
            <el-radio v-for="c in ITEM_CONDITIONS" :key="c.value" :value="c.value">{{ c.label }}</el-radio>
          </el-radio-group>
        </el-form-item>
        <el-form-item label="商品图片">
          <ImageUpload v-model="form.images" :max="5" />
        </el-form-item>
        <el-form-item label="商品描述" prop="description">
          <el-input
            v-model="form.description"
            type="textarea"
            :rows="4"
            maxlength="2000"
            show-word-limit
            placeholder="描述一下商品的成色、使用情况、可议价空间等"
          />
        </el-form-item>
        <el-form-item label="交易地点">
          <el-input v-model="form.tradeLocation" maxlength="200" placeholder="例如：第一教学楼门口" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="publishVisible = false">取消</el-button>
        <el-button type="primary" :loading="submitting" @click="submitPublish">
          {{ editingId ? '保存修改' : '立即上架' }}
        </el-button>
      </template>
    </el-dialog>

    <!-- 充值 -->
    <el-dialog v-model="rechargeVisible" title="账户充值" width="420px">
      <el-alert
        type="info"
        :closable="false"
        title="演示环境：充值为模拟操作，不会发生真实支付。"
        style="margin-bottom: 14px"
      />
      <el-form label-width="70px">
        <el-form-item label="金额">
          <el-input-number v-model="rechargeAmount" :min="1" :max="10000" :precision="2" :step="50" />
          <span class="unit">元</span>
        </el-form-item>
      </el-form>
      <div class="quick-amounts">
        <el-button v-for="amt in [50, 100, 200, 500]" :key="amt" size="small" @click="rechargeAmount = amt">
          {{ amt }} 元
        </el-button>
      </div>
      <template #footer>
        <el-button @click="rechargeVisible = false">取消</el-button>
        <el-button type="primary" :loading="recharging" @click="doRecharge">确认充值</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { onMounted, reactive, ref, watch } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import ImageUpload from '@/components/ImageUpload.vue'
import { useUserStore } from '@/store/user'
import {
  listMarket, publishMarket, updateMarket, offShelfMarket, onShelfMarket, deleteMarket,
  myMarketItems, myBoughtOrders, mySoldOrders, recharge, getWallet, myFavorites
} from '@/api'
import {
  MARKET_CATEGORIES, MARKET_CATEGORY_LABEL, ITEM_CONDITIONS, ITEM_CONDITION_LABEL,
  MARKET_STATUS_LABEL, MARKET_STATUS_TYPE, splitImages, relativeTime, formatMoney
} from '@/utils/community'

const router = useRouter()
const userStore = useUserStore()

const activeTab = ref('all')
const loading = ref(false)
const list = ref([])
const total = ref(0)
const listError = ref('')
const query = reactive({ keyword: '', category: 'ALL', page: 1, size: 12 })

const mineList = ref([])
const mineLoading = ref(false)
const boughtList = ref([])
const soldList = ref([])
const orderLoading = ref(false)
const favoriteList = ref([])
const favoriteLoading = ref(false)

const publishVisible = ref(false)
const submitting = ref(false)
const editingId = ref(null)
const formRef = ref(null)
const form = reactive({
  title: '', price: 1, originalPrice: null, category: 'TEXTBOOK',
  itemCondition: 'GOOD', images: '', description: '', tradeLocation: ''
})
const rules = {
  title: [{ required: true, message: '请填写商品标题', trigger: 'blur' }],
  price: [{ required: true, message: '请填写出售价格', trigger: 'blur' }],
  description: [{ required: true, message: '请填写商品描述', trigger: 'blur' }]
}

const rechargeVisible = ref(false)
const rechargeAmount = ref(100)
const recharging = ref(false)

function firstImage(images) {
  const arr = splitImages(images)
  return arr[0] || ''
}

async function loadList() {
  loading.value = true
  listError.value = ''
  try {
    const res = await listMarket({ ...query })
    list.value = res?.data?.list || []
    total.value = res?.data?.total || 0
  } catch (e) {
    listError.value = (e && e.message) ? e.message : '商品列表加载失败，请稍后重试'
  } finally {
    loading.value = false
  }
}

function switchCategory(value) {
  query.category = value
  query.page = 1
  loadList()
}

function onPageChange(page) {
  query.page = page
  loadList()
}

async function loadMine() {
  mineLoading.value = true
  try {
    const res = await myMarketItems()
    mineList.value = res?.data || []
  } finally {
    mineLoading.value = false
  }
}

async function loadOrders() {
  orderLoading.value = true
  try {
    const [bought, sold] = await Promise.all([myBoughtOrders(), mySoldOrders()])
    boughtList.value = bought?.data || []
    soldList.value = sold?.data || []
  } finally {
    orderLoading.value = false
  }
}

async function loadFavorites() {
  favoriteLoading.value = true
  try {
    const res = await myFavorites()
    favoriteList.value = res?.data || []
  } catch {
    favoriteList.value = []
  } finally {
    favoriteLoading.value = false
  }
}

function goDetail(id) {
  router.push(`/market/${id}`)
}

function goUserProfile(user) {
  if (!user?.userId) return
  router.push(`/user/${user.userId}`)
}

function openPublish() {
  editingId.value = null
  Object.assign(form, {
    title: '', price: 1, originalPrice: null, category: 'TEXTBOOK',
    itemCondition: 'GOOD', images: '', description: '', tradeLocation: ''
  })
  publishVisible.value = true
}

async function submitPublish() {
  try {
    await formRef.value.validate()
  } catch {
    return
  }
  submitting.value = true
  try {
    const payload = { ...form }
    if (editingId.value) {
      await updateMarket(editingId.value, payload)
      ElMessage.success('商品已更新')
    } else {
      await publishMarket(payload)
      ElMessage.success('商品已上架')
    }
    publishVisible.value = false
    activeTab.value = 'all'
    loadList()
    loadMine()
  } finally {
    submitting.value = false
  }
}

async function doOffShelf(row) {
  try {
    await ElMessageBox.confirm(`确定下架「${row.title}」吗？下架后其他同学将看不到该商品。`, '下架确认', {
      confirmButtonText: '确定下架', cancelButtonText: '取消', type: 'warning'
    })
  } catch {
    return
  }
  await offShelfMarket(row.id)
  ElMessage.success('已下架')
  loadMine()
  loadList()
}

async function doOnShelf(row) {
  await onShelfMarket(row.id)
  ElMessage.success('已重新上架')
  loadMine()
  loadList()
}

async function doDelete(row) {
  try {
    await ElMessageBox.confirm(`确定删除「${row.title}」吗？删除后无法恢复。`, '删除确认', {
      confirmButtonText: '确定删除', cancelButtonText: '取消', type: 'warning'
    })
  } catch {
    return
  }
  await deleteMarket(row.id)
  ElMessage.success('已删除')
  loadMine()
  loadList()
}

async function doRecharge() {
  const amount = Number(rechargeAmount.value)
  if (!amount || amount <= 0) {
    ElMessage.warning('请输入正确的充值金额')
    return
  }
  recharging.value = true
  try {
    const res = await recharge(amount)
    userStore.setBalance(res?.data?.balance ?? amount)
    ElMessage.success(`充值成功，当前余额 ${formatMoney(userStore.balance)}`)
    rechargeVisible.value = false
  } finally {
    recharging.value = false
  }
}

watch(activeTab, (tab) => {
  if (tab === 'mine') loadMine()
  if (tab === 'bought' || tab === 'sold') loadOrders()
  if (tab === 'favorites') loadFavorites()
})

onMounted(() => {
  loadList()
  if (userStore.isLoggedIn) refreshBalance()
})

/** 从服务端同步最新余额（顶部余额展示） */
async function refreshBalance() {
  try {
    const res = await getWallet()
    userStore.setBalance(res?.data?.balance ?? userStore.balance)
  } catch {
    /* 静默失败 */
  }
}
</script>

<style scoped>
.wallet-bar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  padding: 12px 16px;
  border-radius: 10px;
  background: linear-gradient(135deg, #1a1a2e, #0f3460);
  color: #fff;
  margin-bottom: 16px;
}

.wallet-left {
  display: flex;
  align-items: center;
  gap: 8px;
  font-size: 14px;
}

.wallet-icon {
  font-size: 18px;
}

.balance {
  font-size: 20px;
  color: #ffd666;
}

.toolbar {
  display: flex;
  gap: 12px;
  align-items: center;
  margin-bottom: 12px;
}

.search-input {
  flex: 1;
  max-width: 460px;
}

.category-row {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
  margin-bottom: 16px;
}

.item-grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(216px, 1fr));
  gap: 14px;
  min-height: 120px;
}

.item-card {
  border: 1px solid #ebeef5;
  border-radius: 10px;
  overflow: hidden;
  background: #fff;
  cursor: pointer;
  transition: box-shadow 0.2s, transform 0.2s;
}

.item-card:hover {
  transform: translateY(-2px);
  box-shadow: 0 6px 18px rgba(0, 0, 0, 0.08);
}

.item-cover {
  position: relative;
  height: 150px;
  background: #f5f7fa;
}

.cover-img {
  width: 100%;
  height: 100%;
  display: block;
}

.cover-placeholder {
  height: 150px;
  display: flex;
  align-items: center;
  justify-content: center;
  color: #c0c4cc;
  background: #f5f7fa;
}

.cond-tag {
  position: absolute;
  left: 8px;
  top: 8px;
}

.item-body {
  padding: 10px 12px 12px;
}

.item-title {
  margin: 0 0 6px;
  font-size: 14px;
  font-weight: 600;
  color: #303133;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.price-row {
  display: flex;
  align-items: baseline;
  gap: 8px;
}

.price {
  color: #f56c6c;
  font-size: 18px;
  font-weight: 700;
}

.origin-price {
  color: #c0c4cc;
  font-size: 12px;
  text-decoration: line-through;
}

.item-meta {
  display: flex;
  justify-content: space-between;
  gap: 8px;
  margin-top: 6px;
  font-size: 12px;
  color: #909399;
}

.item-meta span {
  display: inline-flex;
  align-items: center;
  gap: 3px;
}

.seller-name {
  cursor: pointer;
}

.seller-name:hover {
  color: #409eff;
}

.clickable {
  cursor: pointer;
}

.item-foot {
  display: flex;
  justify-content: space-between;
  margin-top: 6px;
  font-size: 11px;
  color: #c0c4cc;
}

.pager {
  display: flex;
  justify-content: center;
  margin-top: 18px;
}

.unit {
  margin-left: 8px;
  color: #909399;
  font-size: 12px;
}

.quick-amounts {
  display: flex;
  gap: 8px;
  padding-left: 70px;
}

@media (max-width: 768px) {
  .toolbar {
    flex-direction: column;
    align-items: stretch;
  }
  .search-input {
    max-width: none;
  }
  .item-grid {
    grid-template-columns: repeat(auto-fill, minmax(150px, 1fr));
  }
}
</style>
