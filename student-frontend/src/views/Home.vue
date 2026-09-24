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
          <el-icon :color="aiStatus.ollamaAvailable ? '#2e7d62' : '#b8935a'">
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


      param($m) $m.Groups[1].Value -replace 'border-radius:\s*\d+px;', 'border-radius: 0;'
    
