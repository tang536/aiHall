<template>
  <div class="home-page">
    <!-- 欢迎横幅 -->
    <div class="banner">
      <div class="banner-content">
        <h2 class="banner-title">
          <span class="gradient-text">AI驱动</span> 的一站式校园服务平台
        </h2>
        <p class="banner-desc">智能问答 · 在线办理 · 进度追踪 · 让校园事务更简单</p>
        <div class="banner-actions">
          <el-button type="primary" size="large" @click="$router.push('/chat')">
            <el-icon><ChatDotRound /></el-icon> 开始AI问答
          </el-button>
          <el-button size="large" @click="$router.push('/applications')">
            <el-icon><Document /></el-icon> 事项办理
          </el-button>
        </div>
      </div>
      <div class="banner-stats">
        <div class="stat-item">
          <div class="stat-num">{{ stats.totalUsers ?? 0 }}</div>
          <div class="stat-label">服务学生</div>
        </div>
        <div class="stat-item">
          <div class="stat-num">{{ stats.aiQuestions ?? 0 }}</div>
          <div class="stat-label">AI问答次数</div>
        </div>
        <div class="stat-item">
          <div class="stat-num">{{ stats.repairDone ?? 0 }}</div>
          <div class="stat-label">报修完成</div>
        </div>
        <div class="stat-item">
          <div class="stat-num">{{ stats.satisfaction >= 0 ? stats.satisfaction + '%' : '--' }}</div>
          <div class="stat-label">满意度</div>
        </div>
      </div>
    </div>

    <!-- 六大功能卡片 -->
    <div class="section">
      <h3 class="section-title">
        <el-icon><Grid /></el-icon> 核心服务
      </h3>
      <div class="function-grid">
        <div v-for="item in functions" :key="item.path"
             class="function-card card-hover"
             @click="$router.push(item.path)">
          <div class="card-icon" :style="{ background: item.gradient }">
            <el-icon :size="36"><component :is="item.icon" /></el-icon>
          </div>
          <div class="card-info">
            <h4>{{ item.title }}</h4>
            <p>{{ item.desc }}</p>
          </div>
          <el-icon class="card-arrow"><ArrowRight /></el-icon>
        </div>
      </div>
    </div>

    <!-- 最新通知 + AI快捷问答 -->
    <div class="section-row">
      <div class="section notification-section">
        <div class="section-header">
          <h3 class="section-title">
            <el-icon><Bell /></el-icon> 最新通知
          </h3>
          <el-button text type="primary" @click="$router.push('/notifications')">查看全部</el-button>
        </div>
        <div class="notification-list">
          <div v-for="n in latestNotifications" :key="n.id"
               class="notification-item"
               @click="viewNotification(n.id)">
            <el-tag :type="getTagType(n.category)" size="small">{{ n.category }}</el-tag>
            <span class="notif-title">{{ n.title }}</span>
            <span class="notif-time">{{ formatTime(n.publishTime) }}</span>
          </div>
          <el-empty v-if="latestNotifications.length === 0" description="暂无通知" :image-size="60" />
        </div>
      </div>

      <div class="section quick-chat">
        <div class="section-header">
          <h3 class="section-title">
            <el-icon><ChatDotRound /></el-icon> AI快捷问答
          </h3>
          <el-button text type="primary" @click="$router.push('/chat')">去对话</el-button>
        </div>
        <div class="quick-questions">
          <el-button v-for="q in quickQuestions" :key="q"
                     class="quick-btn"
                     @click="askQuestion(q)">
            {{ q }}
          </el-button>
        </div>
        <div class="ai-status">
          <el-icon :color="aiStatus.ollamaAvailable ? '#67c23a' : '#e6a23c'">
            <CircleCheck v-if="aiStatus.ollamaAvailable" />
            <Warning v-else />
          </el-icon>
          <span :class="{ 'status-ok': aiStatus.ollamaAvailable, 'status-warn': !aiStatus.ollamaAvailable }">
            {{ aiStatus.message || '检测AI服务状态...' }}
          </span>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup>
import { ref, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { getNotifications, getChatStatus, getPublicStats } from '@/api'

const router = useRouter()

const functions = [
  { title: '课表考试', desc: '查询个人课表与考试安排', icon: 'Calendar', path: '/schedule', gradient: 'linear-gradient(135deg, #941e23, #b83a3f)' },
  { title: '校园地图', desc: '校园导航与地点查询', icon: 'Location', path: '/map', gradient: 'linear-gradient(135deg, #b8935a, #d4b87a)' },
  { title: '失物招领', desc: '发布寻物启事与智能匹配', icon: 'Search', path: '/lost-found', gradient: 'linear-gradient(135deg, #c96b2c, #d98a4a)' },
  { title: '报修登记', desc: '在线报修与进度追踪', icon: 'Tools', path: '/repair', gradient: 'linear-gradient(135deg, #a03428, #c05040)' },
  { title: '通知公告', desc: '校园通知与紧急提醒', icon: 'Bell', path: '/notifications', gradient: 'linear-gradient(135deg, #6f6d68, #8a8780)' },
  { title: 'AI智能问答', desc: '基于校园知识库的智能助手', icon: 'Cpu', path: '/chat', gradient: 'linear-gradient(135deg, #761317, #941e23)' },
]

const quickQuestions = [
  '贫困认定需要什么材料？',
  '请假流程是什么？',
  '宿舍报修多久能处理？',
  '奖学金什么时候发放？',
  '成绩单在哪里开具？',
  '校园卡丢了怎么补办？',
]

const latestNotifications = ref([])
const aiStatus = ref({ ollamaAvailable: false, message: '' })
const stats = ref({})

onMounted(async () => {
  try {
    const res = await getNotifications({}, { silent: true })
    latestNotifications.value = res.data.slice(0, 5)
  } catch {}
  try {
    const res = await getChatStatus({ silent: true })
    aiStatus.value = res.data
  } catch {}
  try {
    const res = await getPublicStats({ silent: true })
    stats.value = res.data
  } catch {}
})

const viewNotification = (id) => {
  router.push(`/notifications?id=${id}`)
}

const askQuestion = (q) => {
  router.push({ path: '/chat', query: { q } })
}

const getTagType = (cat) => {
  const map = { '教务': 'primary', '后勤': 'warning', '学工': 'success', '社团': 'info', '紧急通知': 'danger' }
  return map[cat] || 'info'
}

const formatTime = (t) => {
  if (!t) return ''
  return t.substring(5, 16)
}
</script>

<style scoped>
.home-page {
  display: flex;
  flex-direction: column;
  gap: 28px;
}

/* ========== Banner 横幅 ========== */
.banner {
  background: linear-gradient(135deg, #941e23 0%, #761317 50%, #5c0e12 100%);
  border-radius: 16px;
  padding: 44px 48px;
  display: flex;
  justify-content: space-between;
  align-items: center;
  color: #fffdf9;
  position: relative;
  overflow: hidden;
  border: 1px solid rgba(184, 147, 90, 0.3);
  box-shadow: 0 8px 32px rgba(148, 30, 35, 0.18);
}

.banner::before {
  content: '';
  position: absolute;
  top: -40%;
  right: -8%;
  width: 420px;
  height: 420px;
  background: radial-gradient(circle, rgba(184, 147, 90, 0.22) 0%, transparent 70%);
  border-radius: 50%;
}

.banner::after {
  content: '';
  position: absolute;
  bottom: -30%;
  left: -5%;
  width: 280px;
  height: 280px;
  border: 1px solid rgba(184, 147, 90, 0.15);
  border-radius: 50%;
}

.banner-content {
  position: relative;
  z-index: 1;
}

.banner-title {
  font-size: 34px;
  font-weight: 700;
  margin-bottom: 14px;
  font-family: 'Noto Serif SC', 'SimSun', serif;
  letter-spacing: 1px;
  line-height: 1.3;
}

.gradient-text {
  background: linear-gradient(135deg, #f0d9a8 0%, #b8935a 50%, #d4b87a 100%);
  -webkit-background-clip: text;
  -webkit-text-fill-color: transparent;
  background-clip: text;
}

.banner-desc {
  font-size: 16px;
  color: rgba(255, 253, 249, 0.7);
  margin-bottom: 28px;
  letter-spacing: 0.5px;
}

.banner-actions {
  display: flex;
  gap: 14px;
}

.banner-actions :deep(.el-button--primary) {
  background: linear-gradient(135deg, #b8935a, #9a7a45);
  border: none;
  color: #fffdf9;
  font-weight: 600;
  letter-spacing: 2px;
  padding: 0 28px;
}

.banner-actions :deep(.el-button--primary:hover) {
  background: linear-gradient(135deg, #c9a46b, #a88550);
}

.banner-actions :deep(.el-button:not(.el-button--primary)) {
  background: rgba(255, 253, 249, 0.12);
  border: 1px solid rgba(184, 147, 90, 0.4);
  color: #fffdf9;
  font-weight: 500;
  padding: 0 28px;
}

.banner-actions :deep(.el-button:not(.el-button--primary):hover) {
  background: rgba(255, 253, 249, 0.2);
  border-color: #b8935a;
}

.banner-stats {
  display: flex;
  gap: 36px;
  z-index: 1;
  position: relative;
}

.stat-item {
  text-align: center;
}

.stat-num {
  font-size: 32px;
  font-weight: 700;
  color: #f0d9a8;
  font-family: 'Noto Serif SC', 'SimSun', serif;
  line-height: 1.2;
}

.stat-label {
  font-size: 13px;
  color: rgba(255, 253, 249, 0.6);
  margin-top: 6px;
  letter-spacing: 1px;
}

/* ========== 通用 Section ========== */
.section {
  background: #fffdf9;
  border-radius: 12px;
  padding: 28px;
  box-shadow: 0 2px 12px rgba(148, 30, 35, 0.05);
  border: 1px solid #e8dfd0;
}

.section-title {
  font-size: 18px;
  font-weight: 700;
  color: #20201f;
  display: flex;
  align-items: center;
  gap: 10px;
  margin: 0 0 20px 0;
  font-family: 'Noto Serif SC', 'SimSun', serif;
  letter-spacing: 0.5px;
}

.section-title :deep(.el-icon) {
  color: #941e23;
}

.section-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 16px;
}

.section-header .section-title {
  margin-bottom: 0;
}

/* ========== 功能卡片 ========== */
.function-grid {
  display: grid;
  grid-template-columns: repeat(3, 1fr);
  gap: 20px;
}

.function-card {
  display: flex;
  align-items: center;
  gap: 16px;
  padding: 22px 20px;
  border-radius: 12px;
  cursor: pointer;
  border: 1px solid #e8dfd0;
  background: #fffdf9;
  position: relative;
  transition: all 0.25s ease;
}

.function-card:hover {
  border-color: #b8935a;
  box-shadow: 0 6px 20px rgba(148, 30, 35, 0.1);
  transform: translateY(-2px);
}

.card-icon {
  width: 60px;
  height: 60px;
  border-radius: 14px;
  display: flex;
  align-items: center;
  justify-content: center;
  color: #fffdf9;
  flex-shrink: 0;
  box-shadow: 0 4px 12px rgba(0, 0, 0, 0.12);
}

.card-info h4 {
  font-size: 16px;
  font-weight: 700;
  color: #20201f;
  margin: 0 0 4px 0;
  font-family: 'Noto Serif SC', 'SimSun', serif;
}

.card-info p {
  font-size: 13px;
  color: #6f6d68;
  margin: 0;
}

.card-arrow {
  position: absolute;
  right: 16px;
  color: #c8c2b6;
  font-size: 18px;
  transition: color 0.2s;
}

.function-card:hover .card-arrow {
  color: #941e23;
}

/* ========== 下方双栏 ========== */
.section-row {
  display: grid;
  grid-template-columns: 1.5fr 1fr;
  gap: 20px;
}

/* ========== 通知列表 ========== */
.notification-list {
  display: flex;
  flex-direction: column;
  gap: 2px;
}

.notification-item {
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 12px 10px;
  border-radius: 8px;
  cursor: pointer;
  transition: background 0.2s;
}

.notification-item:hover {
  background: #f7f3ec;
}

.notif-title {
  flex: 1;
  font-size: 14px;
  color: #20201f;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.notif-time {
  font-size: 12px;
  color: #a8a49c;
  flex-shrink: 0;
}

/* ========== AI 快捷问答 ========== */
.quick-questions {
  display: flex;
  flex-wrap: wrap;
  gap: 10px;
  margin-bottom: 20px;
}

.quick-btn {
  border-radius: 20px !important;
  font-size: 13px !important;
  background: #f7e9e7 !important;
  border: 1px solid #e8c9c5 !important;
  color: #941e23 !important;
  padding: 8px 16px !important;
  transition: all 0.2s !important;
}

.quick-btn:hover {
  background: #941e23 !important;
  border-color: #941e23 !important;
  color: #fffdf9 !important;
}

.ai-status {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 12px 14px;
  background: #f7f3ec;
  border-radius: 8px;
  font-size: 13px;
  border: 1px solid #e8dfd0;
}

.status-ok { color: #2e7d62; font-weight: 500; }
.status-warn { color: #c96b2c; font-weight: 500; }

/* ========== 移动端响应式 ========== */
@media (max-width: 768px) {
  .home-page {
    gap: 16px;
  }
  .banner {
    flex-direction: column;
    padding: 28px 24px;
    gap: 24px;
    text-align: center;
  }
  .banner-title {
    font-size: 24px;
  }
  .banner-desc {
    font-size: 13px;
    margin-bottom: 18px;
  }
  .banner-actions {
    justify-content: center;
    flex-wrap: wrap;
  }
  .banner-actions :deep(.el-button) {
    flex: 1;
    min-width: 120px;
  }
  .banner-stats {
    gap: 20px;
    width: 100%;
    justify-content: space-around;
  }
  .stat-num {
    font-size: 22px;
  }
  .stat-label {
    font-size: 11px;
  }
  .section {
    padding: 18px;
  }
  .section-title {
    font-size: 16px;
    margin-bottom: 14px;
  }
  .function-grid {
    grid-template-columns: repeat(2, 1fr);
    gap: 12px;
  }
  .function-card {
    flex-direction: column;
    text-align: center;
    padding: 16px 12px;
    gap: 10px;
  }
  .card-icon {
    width: 48px;
    height: 48px;
  }
  .card-icon :deep(.el-icon) {
    font-size: 24px !important;
  }
  .card-info h4 {
    font-size: 14px;
  }
  .card-info p {
    font-size: 11px;
  }
  .card-arrow {
    display: none;
  }
  .section-row {
    grid-template-columns: 1fr;
    gap: 16px;
  }
  .notification-item {
    padding: 10px 4px;
    gap: 8px;
  }
  .notif-title {
    font-size: 13px;
  }
  .notif-time {
    font-size: 11px;
  }
  .quick-questions {
    gap: 8px;
  }
  .quick-btn {
    font-size: 12px !important;
    padding: 6px 14px !important;
  }
}

@media (max-width: 480px) {
  .banner-title {
    font-size: 20px;
  }
  .function-grid {
    grid-template-columns: 1fr;
  }
  .function-card {
    flex-direction: row;
    text-align: left;
  }
}
</style>
