<template>
  <div class="notification-page">
    <div class="page-header">
      <h2><el-icon><Bell /></el-icon> 通知公告</h2>
      <p>查看校园各类通知公告，紧急通知实时提醒</p>
    </div>

    <div class="filter-bar">
      <el-radio-group v-model="activeCategory" @change="loadData">
        <el-radio-button value="">全部</el-radio-button>
        <el-radio-button value="教务">教务</el-radio-button>
        <el-radio-button value="学工">学工</el-radio-button>
        <el-radio-button value="后勤">后勤</el-radio-button>
        <el-radio-button value="社团">社团</el-radio-button>
        <el-radio-button value="紧急通知">紧急通知</el-radio-button>
      </el-radio-group>
      <el-input v-model="searchKeyword" placeholder="搜索通知标题..." style="width: 240px" clearable @keyup.enter="loadData">
        <template #prefix><el-icon><Search /></el-icon></template>
      </el-input>
    </div>

    <!-- 个人通知（好友申请、商品被购买等） -->
    <div v-if="userStore.isLoggedIn" class="personal-section">
      <div class="section-header">
        <h3><el-icon><Message /></el-icon> 我的消息</h3>
        <el-button v-if="userNotifications.some(n => !n.isRead)" size="small" text type="primary" @click="markAllRead">
          全部标为已读
        </el-button>
      </div>
      <div v-loading="personalLoading" class="personal-list">
        <div
          v-for="n in userNotifications"
          :key="n.id"
          class="personal-item card-hover"
          :class="{ unread: !n.isRead }"
          @click="handlePersonalClick(n)"
        >
          <div class="personal-icon" :class="n.type">
            <el-icon v-if="n.type === 'FRIEND_REQUEST'"><User /></el-icon>
            <el-icon v-else-if="n.type === 'ORDER_PURCHASE'"><Goods /></el-icon>
            <el-icon v-else><Bell /></el-icon>
          </div>
          <div class="personal-content">
            <div class="personal-title">
              {{ n.title }}
              <span v-if="!n.isRead" class="unread-dot"></span>
            </div>
            <p class="personal-desc">{{ n.content }}</p>
            <span class="personal-time">{{ formatTime(n.createTime) }}</span>
          </div>
        </div>
        <el-empty v-if="!personalLoading && userNotifications.length === 0" description="暂无个人消息" :image-size="60" />
      </div>
    </div>

    <!-- 紧急通知横幅 -->
    <div v-if="emergencyNotifications.length > 0" class="emergency-banner">
      <el-alert v-for="n in emergencyNotifications" :key="n.id"
                :title="n.title" type="error" :closable="false" show-icon
                @click="viewNotification(n)" class="emergency-item" />
    </div>

    <div class="notification-list">
      <div v-for="n in notifications" :key="n.id"
           class="notification-item card-hover"
           @click="viewNotification(n)">
        <div class="notif-left">
          <el-tag :type="getTagType(n.category)" size="small" class="cat-tag">{{ n.category }}</el-tag>
          <el-tag v-if="n.isTop" type="danger" size="small" effect="dark">置顶</el-tag>
          <el-tag v-if="n.isEmergency" type="danger" size="small" effect="dark">紧急</el-tag>
        </div>
        <div class="notif-content">
          <h4>{{ n.title }}</h4>
          <p>{{ stripHtml(n.summary || n.content?.substring(0, 100)) }}...</p>
        </div>
        <div class="notif-right">
          <span class="dept">{{ n.department }}</span>
          <span class="time">{{ formatTime(n.publishTime) }}</span>
          <span class="views"><el-icon><View /></el-icon> {{ n.viewCount }}</span>
        </div>
      </div>
      <el-empty v-if="notifications.length === 0" description="暂无通知" />
    </div>

    <!-- 通知详情弹窗 -->
    <el-dialog v-model="detailVisible" :title="currentNotification?.title" width="640px" top="5vh">
      <div v-if="currentNotification" class="detail-content">
        <div class="detail-meta">
          <el-tag :type="getTagType(currentNotification.category)">{{ currentNotification.category }}</el-tag>
          <span>发布部门：{{ currentNotification.department }}</span>
          <span>发布时间：{{ formatTime(currentNotification.publishTime) }}</span>
          <span>阅读：{{ currentNotification.viewCount }}次</span>
        </div>
        <el-divider />
        <div class="detail-body" v-html="currentNotification.content?.replace(/\n/g, '<br>')"></div>
      </div>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, onMounted, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { getNotifications, getNotificationDetail, getEmergencyNotifications, getUserNotifications, markUserNotificationRead, markAllUserNotificationsRead } from '@/api'
import { useUserStore } from '@/store/user'
import { formatTime } from '@/utils/format'

const route = useRoute()
const router = useRouter()
const userStore = useUserStore()
const activeCategory = ref('')
const searchKeyword = ref('')
const notifications = ref([])
const emergencyNotifications = ref([])
const detailVisible = ref(false)
const currentNotification = ref(null)
// 个人通知
const userNotifications = ref([])
const personalLoading = ref(false)

function getTagType(cat) {
  const map = { '教务': '', '后勤': 'warning', '学工': 'success', '社团': 'info', '紧急通知': 'danger' }
  return map[cat] || ''
}

/** 去除 HTML 标签，兼容历史 PDF 导入数据（旧版存的是 <p>...</p>） */
function stripHtml(s) {
  if (!s) return ''
  return s.replace(/<[^>]+>/g, '').trim()
}

async function loadData() {
  try {
    const params = {}
    if (activeCategory.value) params.category = activeCategory.value
    if (searchKeyword.value) params.keyword = searchKeyword.value
    const res = await getNotifications(params)
    notifications.value = res.data
  } catch {}
}

async function viewNotification(n) {
  try {
    const res = await getNotificationDetail(n.id)
    currentNotification.value = res.data
    detailVisible.value = true
  } catch {}
}

// 个人通知
async function loadPersonal() {
  if (!userStore.isLoggedIn) return
  personalLoading.value = true
  try {
    const res = await getUserNotifications()
    userNotifications.value = res?.data || []
  } catch {
    userNotifications.value = []
  } finally {
    personalLoading.value = false
  }
}

async function markAllRead() {
  try {
    await markAllUserNotificationsRead()
    userNotifications.value.forEach(n => { n.isRead = true })
    ElMessage.success('已全部标为已读')
  } catch {}
}

async function handlePersonalClick(n) {
  if (!n.isRead) {
    try {
      await markUserNotificationRead(n.id)
      n.isRead = true
    } catch {}
  }
  // 根据类型跳转
  if (n.type === 'FRIEND_REQUEST') {
    router.push('/friends')
  } else if (n.type === 'ORDER_PURCHASE') {
    router.push('/market')
  }
}

onMounted(async () => {
  await loadData()
  loadPersonal()
  try {
    const res = await getEmergencyNotifications()
    emergencyNotifications.value = res.data
  } catch {}
  // 从URL参数打开详情
  if (route.query.id) {
    const n = notifications.value.find(x => x.id === Number(route.query.id))
    if (n) viewNotification(n)
  }
})
</script>

<style scoped>
.notification-page {
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

.filter-bar {
  display: flex;
  gap: 16px;
  align-items: center;
  flex-wrap: wrap;
}

.personal-section {
  background: #fff;
  border-radius: 14px;
  padding: 20px 24px;
  border: 1px solid #ebeef5;
}

.section-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 14px;
}

.section-header h3 {
  margin: 0;
  font-size: 16px;
  font-weight: 700;
  display: flex;
  align-items: center;
  gap: 8px;
  color: #303133;
}

.personal-list {
  display: flex;
  flex-direction: column;
  gap: 10px;
}

.personal-item {
  display: flex;
  gap: 14px;
  padding: 14px 16px;
  border-radius: 10px;
  cursor: pointer;
  border: 1px solid #f0f2f5;
  transition: all 0.2s;
}

.personal-item.unread {
  background: #f0f7ff;
  border-color: #d9ecff;
}

.personal-icon {
  width: 40px;
  height: 40px;
  border-radius: 50%;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 18px;
  flex-shrink: 0;
  color: #fff;
}

.personal-icon.FRIEND_REQUEST {
  background: linear-gradient(135deg, #67c23a, #85ce61);
}

.personal-icon.ORDER_PURCHASE {
  background: linear-gradient(135deg, #e6a23c, #ebb563);
}

.personal-icon:not(.FRIEND_REQUEST):not(.ORDER_PURCHASE) {
  background: linear-gradient(135deg, #409eff, #66b1ff);
}

.personal-content {
  flex: 1;
  min-width: 0;
}

.personal-title {
  font-weight: 600;
  font-size: 14px;
  color: #303133;
  display: flex;
  align-items: center;
  gap: 6px;
}

.unread-dot {
  width: 8px;
  height: 8px;
  border-radius: 50%;
  background: #f56c6c;
  flex-shrink: 0;
}

.personal-desc {
  margin: 4px 0 0 0;
  font-size: 13px;
  color: #606266;
  line-height: 1.5;
}

.personal-time {
  display: inline-block;
  margin-top: 6px;
  font-size: 12px;
  color: #909399;
}

.emergency-banner {
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.emergency-item {
  cursor: pointer;
}

.notification-list {
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.notification-item {
  display: flex;
  align-items: center;
  gap: 16px;
  background: #fff;
  border-radius: 12px;
  padding: 18px 24px;
  cursor: pointer;
  border: 1px solid #ebeef5;
}

.notif-left {
  display: flex;
  gap: 6px;
  flex-shrink: 0;
  width: 140px;
}

.cat-tag {
  flex-shrink: 0;
}

.notif-content {
  flex: 1;
  min-width: 0;
}

.notif-content h4 {
  font-size: 15px;
  font-weight: 600;
  margin: 0 0 4px 0;
  color: #303133;
}

.notif-content p {
  font-size: 13px;
  color: #909399;
  margin: 0;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.notif-right {
  display: flex;
  flex-direction: column;
  align-items: flex-end;
  gap: 4px;
  flex-shrink: 0;
}

.notif-right .dept {
  font-size: 12px;
  color: #606266;
}

.notif-right .time {
  font-size: 12px;
  color: #c0c4cc;
}

.notif-right .views {
  font-size: 12px;
  color: #c0c4cc;
  display: flex;
  align-items: center;
  gap: 2px;
}

.detail-meta {
  display: flex;
  gap: 16px;
  align-items: center;
  flex-wrap: wrap;
  font-size: 13px;
  color: #909399;
}

.detail-body {
  font-size: 14px;
  line-height: 1.8;
  color: #303133;
  padding: 0 8px;
}
</style>
