<template>
  <div class="lostfound-page">
    <div class="page-header">
      <h2><el-icon><Search /></el-icon> 失物招领</h2>
      <p>发布寻物启事与招领信息，系统智能匹配</p>
    </div>

    <div class="action-bar">
      <el-radio-group v-model="activeType" @change="loadData">
        <el-radio-button value="">全部</el-radio-button>
        <el-radio-button value="LOST">寻物启事</el-radio-button>
        <el-radio-button value="FOUND">招领信息</el-radio-button>
      </el-radio-group>
      <el-select v-model="filterCategory" placeholder="物品类型" clearable style="width: 140px" @change="loadData">
        <el-option label="证件" value="证件" />
        <el-option label="电子产品" value="电子产品" />
        <el-option label="钥匙" value="钥匙" />
        <el-option label="衣物" value="衣物" />
        <el-option label="书籍" value="书籍" />
        <el-option label="其他" value="其他" />
      </el-select>
      <el-button type="primary" @click="showPublishDialog">
        <el-icon><Plus /></el-icon> 发布信息
      </el-button>
    </div>

    <div class="item-grid">
      <div v-for="item in filteredItems" :key="item.id"
           class="item-card card-hover"
           @click="showDetail(item)">
        <div class="item-type-tag" :class="item.type">
          {{ item.type === 'LOST' ? '寻物' : '招领' }}
        </div>
        <div class="item-icon">
          <el-icon :size="40"><component :is="getCategoryIcon(item.itemCategory)" /></el-icon>
        </div>
        <h4>{{ item.itemName }}</h4>
        <p class="item-desc">{{ item.description }}</p>
        <div class="item-meta">
          <span><el-icon><Location /></el-icon> {{ item.lostLocation }}</span>
          <span><el-icon><Clock /></el-icon> {{ formatTime(item.lostTime) }}</span>
        </div>
        <el-tag size="small" :type="getStatusType(item.status)">{{ getStatusText(item.status) }}</el-tag>
      </div>
      <el-empty v-if="filteredItems.length === 0" description="暂无相关信息" />
    </div>

    <!-- 发布弹窗 -->
    <el-dialog v-model="publishVisible" title="发布失物招领信息" width="560px">
      <el-form :model="publishForm" label-width="90px">
        <el-form-item label="类型" required>
          <el-radio-group v-model="publishForm.type">
            <el-radio value="LOST">我丢了东西（寻物）</el-radio>
            <el-radio value="FOUND">我捡到东西（招领）</el-radio>
          </el-radio-group>
        </el-form-item>
        <el-form-item label="物品名称" required>
          <el-input v-model="publishForm.itemName" placeholder="请输入物品名称" />
        </el-form-item>
        <el-form-item label="物品类型">
          <el-select v-model="publishForm.itemCategory" placeholder="请选择" style="width: 100%">
            <el-option label="证件" value="证件" />
            <el-option label="电子产品" value="电子产品" />
            <el-option label="钥匙" value="钥匙" />
            <el-option label="衣物" value="衣物" />
            <el-option label="书籍" value="书籍" />
            <el-option label="其他" value="其他" />
          </el-select>
        </el-form-item>
        <el-form-item label="详细描述">
          <el-input v-model="publishForm.description" type="textarea" :rows="3" placeholder="请描述物品特征、颜色、品牌等" />
        </el-form-item>
        <el-form-item label="丢失/拾获地点">
          <el-input v-model="publishForm.lostLocation" placeholder="请输入地点" />
        </el-form-item>
        <el-form-item label="时间">
          <el-date-picker v-model="publishForm.lostTime" type="datetime" placeholder="选择时间" style="width: 100%" />
        </el-form-item>
        <el-form-item label="联系人">
          <el-input v-model="publishForm.contactName" placeholder="您的姓名" />
        </el-form-item>
        <el-form-item label="联系电话">
          <el-input v-model="publishForm.contactPhone" placeholder="联系电话" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="publishVisible = false">取消</el-button>
        <el-button type="primary" :loading="publishing" @click="submitPublish">发布并智能匹配</el-button>
      </template>
    </el-dialog>

    <!-- 详情弹窗 -->
    <el-dialog v-model="detailVisible" title="信息详情" width="500px">
      <div v-if="currentItem" class="detail-content">
        <el-descriptions :column="1" border>
          <el-descriptions-item label="类型">
            <el-tag :type="currentItem.type === 'LOST' ? 'danger' : 'success'">
              {{ currentItem.type === 'LOST' ? '寻物启事' : '招领信息' }}
            </el-tag>
          </el-descriptions-item>
          <el-descriptions-item label="物品名称">{{ currentItem.itemName }}</el-descriptions-item>
          <el-descriptions-item label="物品类型">{{ currentItem.itemCategory }}</el-descriptions-item>
          <el-descriptions-item label="详细描述">{{ currentItem.description }}</el-descriptions-item>
          <el-descriptions-item label="地点">{{ currentItem.lostLocation }}</el-descriptions-item>
          <el-descriptions-item label="时间">{{ formatTime(currentItem.lostTime) }}</el-descriptions-item>
          <el-descriptions-item label="联系人">{{ currentItem.contactName }}</el-descriptions-item>
          <el-descriptions-item label="联系电话">{{ currentItem.contactPhone }}</el-descriptions-item>
          <el-descriptions-item label="状态">
            <el-tag :type="getStatusType(currentItem.status)">{{ getStatusText(currentItem.status) }}</el-tag>
          </el-descriptions-item>
        </el-descriptions>
        <div v-if="matchedItems.length > 0" class="match-result">
          <el-alert title="智能匹配到以下可能相关的信息" type="success" :closable="false" show-icon />
          <div v-for="m in matchedItems" :key="m.id" class="matched-item" @click="showDetail(m)">
            <el-tag size="small" :type="m.type === 'LOST' ? 'danger' : 'success'">
              {{ m.type === 'LOST' ? '寻物' : '招领' }}
            </el-tag>
            <span>{{ m.itemName }}</span>
            <span class="matched-loc">{{ m.lostLocation }}</span>
          </div>
        </div>
      </div>
      <template #footer>
        <el-tooltip v-if="!canClaim" content="仅发布者本人或管理员可操作" placement="top">
          <el-button type="success" disabled>确认认领/找到</el-button>
        </el-tooltip>
        <el-button v-else-if="currentItem?.status === 'PENDING'" type="success" @click="claimItem">
          确认认领/找到
        </el-button>
        <el-button @click="detailVisible = false">关闭</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, computed, onMounted } from 'vue'
import { getLostFound, publishLostFound, claimLostFound, smartMatch } from '@/api'
import { ElMessage, ElMessageBox } from 'element-plus'
import { useUserStore } from '@/store/user'

const userStore = useUserStore()
const activeType = ref('')
const filterCategory = ref('')
const items = ref([])
const publishVisible = ref(false)
const publishing = ref(false)
const detailVisible = ref(false)
const currentItem = ref(null)
const matchedItems = ref([])

const publishForm = ref({
  type: 'LOST',
  itemName: '',
  itemCategory: '',
  description: '',
  lostLocation: '',
  lostTime: '',
  contactName: '',
  contactPhone: ''
})

const filteredItems = computed(() => {
  let list = items.value
  if (activeType.value) list = list.filter(i => i.type === activeType.value)
  if (filterCategory.value) list = list.filter(i => i.itemCategory === filterCategory.value)
  return list
})

// 仅发布者本人或管理员可标记已认领/已找回
const canClaim = computed(() => {
  if (!currentItem.value) return false
  if (userStore.isAdmin) return true
  return userStore.isLoggedIn && currentItem.value.publisherId === userStore.userInfo?.userId
})

function getCategoryIcon(cat) {
  const map = { '证件': 'Postcard', '电子产品': 'Iphone', '钥匙': 'Key', '衣物': 'Goods', '书籍': 'Reading', '其他': 'Box' }
  return map[cat] || 'Box'
}

function getStatusType(s) {
  return { PENDING: 'warning', CLAIMED: 'success', CLOSED: 'info' }[s] || 'info'
}

function getStatusText(s) {
  return { PENDING: '待认领', CLAIMED: '已认领', CLOSED: '已关闭' }[s] || s
}

function formatTime(t) {
  if (!t) return ''
  const d = new Date(t)
  return `${d.getMonth() + 1}/${d.getDate()} ${String(d.getHours()).padStart(2, '0')}:${String(d.getMinutes()).padStart(2, '0')}`
}

function showPublishDialog() {
  publishForm.value = { type: 'LOST', itemName: '', itemCategory: '', description: '', lostLocation: '', lostTime: '', contactName: '', contactPhone: '' }
  publishVisible.value = true
}

async function submitPublish() {
  if (!publishForm.value.itemName) {
    ElMessage.warning('请输入物品名称')
    return
  }
  publishing.value = true
  try {
    const res = await publishLostFound(publishForm.value)
    ElMessage.success('发布成功！')
    publishVisible.value = false
    // 智能匹配
    const matchRes = await smartMatch(res.data)
    matchedItems.value = matchRes.data
    currentItem.value = res.data
    detailVisible.value = true
    loadData()
  } catch (e) {
  } finally {
    publishing.value = false
  }
}

function showDetail(item) {
  currentItem.value = item
  matchedItems.value = []
  detailVisible.value = true
}

async function claimItem() {
  if (!canClaim.value) {
    ElMessage.warning('仅发布者本人或管理员可标记已认领/已找回')
    return
  }
  try {
    await ElMessageBox.confirm(
      '确认该物品已认领/找回？此操作将把状态更新为"已认领"，确认后不可撤销。',
      '操作确认',
      { confirmButtonText: '确认', cancelButtonText: '取消', type: 'warning' }
    )
  } catch {
    return
  }
  try {
    await claimLostFound(currentItem.value.id)
    ElMessage.success('操作成功')
    detailVisible.value = false
    loadData()
  } catch (e) {
    // 后端 403 等错误已由拦截器提示
  }
}

async function loadData() {
  try {
    const res = await getLostFound(activeType.value || undefined)
    items.value = res.data
  } catch {}
}

onMounted(() => loadData())
</script>

<style scoped>
.lostfound-page {
  display: flex;
  flex-direction: column;
  gap: 20px;
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
  color: #909399;
  font-size: 14px;
  margin: 0;
}

.action-bar {
  display: flex;
  gap: 12px;
  align-items: center;
  flex-wrap: wrap;
}

.item-grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(260px, 1fr));
  gap: 16px;
}

.item-card {
  background: #fff;
  border-radius: 12px;
  padding: 20px;
  cursor: pointer;
  border: 1px solid #ebeef5;
  position: relative;
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.item-type-tag {
  position: absolute;
  top: 12px;
  right: 12px;
  padding: 2px 10px;
  border-radius: 12px;
  font-size: 12px;
  font-weight: 600;
  color: #fff;
}

.item-type-tag.LOST { background: linear-gradient(135deg, #f56c6c, #f78989); }
.item-type-tag.FOUND { background: linear-gradient(135deg, #67c23a, #85ce61); }

.item-icon {
  width: 64px;
  height: 64px;
  background: #f5f7fa;
  border-radius: 12px;
  display: flex;
  align-items: center;
  justify-content: center;
  color: #909399;
}

.item-card h4 {
  font-size: 16px;
  font-weight: 700;
  margin: 0;
}

.item-desc {
  font-size: 13px;
  color: #606266;
  margin: 0;
  display: -webkit-box;
  -webkit-line-clamp: 2;
  -webkit-box-orient: vertical;
  overflow: hidden;
}

.item-meta {
  display: flex;
  flex-direction: column;
  gap: 4px;
}

.item-meta span {
  display: flex;
  align-items: center;
  gap: 4px;
  font-size: 12px;
  color: #909399;
}

.match-result {
  margin-top: 16px;
}

.matched-item {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 10px;
  background: #f0f9eb;
  border-radius: 8px;
  margin-top: 8px;
  cursor: pointer;
  font-size: 13px;
}

.matched-loc {
  color: #909399;
  font-size: 12px;
  margin-left: auto;
}
</style>
