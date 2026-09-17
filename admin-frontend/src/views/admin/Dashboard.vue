<template>
  <AdminLayout>
    <!-- 加载中 -->
    <div v-if="loading" v-loading="true" class="loading-wrap"></div>

    <!-- 服务暂不可用提示 -->
    <el-alert
      v-else-if="loadError"
      type="error"
      :closable="false"
      show-icon
      class="error-alert"
      title="暂时连不上系统，数据加载失败"
      description="请稍后刷新页面重试；如果还是不行，请联系管理员处理。"
    />

    <template v-else>
      <!-- 统计卡片 -->
      <div class="stat-cards">
      <div class="stat-card card-hover" v-for="card in statCards" :key="card.label">
        <div class="stat-icon" :style="{ background: card.gradient }">
          <el-icon :size="28"><component :is="card.icon" /></el-icon>
        </div>
        <div class="stat-info">
          <div class="stat-value">{{ card.value }}</div>
          <div class="stat-label">{{ card.label }}</div>
        </div>
      </div>
    </div>

    <div class="charts-row">
      <!-- 报修状态分布 -->
      <div class="chart-card">
        <h3>报修工单状态分布</h3>
        <div ref="repairChartRef" class="chart-container"></div>
      </div>
      <!-- 事项类型分布 -->
      <div class="chart-card">
        <h3>事项申请类型分布</h3>
        <div ref="appChartRef" class="chart-container"></div>
      </div>
    </div>

    <div class="charts-row">
      <!-- 学生反馈状态分布 -->
      <div class="chart-card">
        <h3>学生反馈处理情况</h3>
        <div ref="feedbackChartRef" class="chart-container"></div>
      </div>
      <!-- 二手交易概览 -->
      <div class="chart-card">
        <h3>二手商品状态分布</h3>
        <div ref="marketChartRef" class="chart-container"></div>
      </div>
    </div>

    <div class="charts-row">
      <!-- 收入趋势（模拟） -->
      <div class="chart-card chart-wide">
        <h3>业务办理趋势（近7天）</h3>
        <div ref="trendChartRef" class="chart-container"></div>
      </div>
    </div>

    <!-- 高频问题 -->
    <div class="hot-section">
      <h3>高频问题 TOP5</h3>
      <div class="hot-list" v-if="hotQuestions.length > 0">
        <div v-for="(q, idx) in hotQuestions" :key="idx" class="hot-item">
          <span class="hot-rank" :class="'rank-' + (idx + 1)">{{ idx + 1 }}</span>
          <span class="hot-question">{{ q.question }}</span>
          <span class="hot-count">{{ q.count }}次</span>
        </div>
      </div>
      <el-empty v-else description="暂无问答数据，用户提问后自动统计" :image-size="60" />
      </div>
    </template>
  </AdminLayout>
</template>

<script setup>
import { ref, onMounted, onBeforeUnmount, nextTick } from 'vue'
import AdminLayout from '@/components/AdminLayout.vue'
import { getDashboard } from '@/api'
import * as echarts from 'echarts'

const stats = ref({})
const hotQuestions = ref([])
const repairChartRef = ref(null)
const appChartRef = ref(null)
const trendChartRef = ref(null)
const feedbackChartRef = ref(null)
const marketChartRef = ref(null)
const loading = ref(true)
const loadError = ref(false)

const statCards = ref([])
const chartInstances = []

function resizeCharts() {
  chartInstances.forEach((c) => c && c.resize())
}

function initStatCards() {
  const r = stats.value.repair || {}
  const a = stats.value.application || {}
  const c = stats.value.chat || {}
  const f = stats.value.feedback || {}
  const m = stats.value.market || {}
  const p = stats.value.post || {}
  statCards.value = [
    { label: '报修工单总数', value: r.total || 0, icon: 'Tools', gradient: 'linear-gradient(135deg, #f56c6c, #f78989)' },
    { label: '待处理报修', value: r.submitted || 0, icon: 'Warning', gradient: 'linear-gradient(135deg, #e6a23c, #ebb563)' },
    { label: '事项申请总数', value: a.total || 0, icon: 'Document', gradient: 'linear-gradient(135deg, #409eff, #66b1ff)' },
    { label: '审核中申请', value: a.reviewing || 0, icon: 'Clock', gradient: 'linear-gradient(135deg, #67c23a, #85ce61)' },
    { label: 'AI问答总次数', value: c.totalMessages || 0, icon: 'ChatDotRound', gradient: 'linear-gradient(135deg, #8e44ad, #9b59b6)' },
    { label: '用户提问数', value: c.userMessages || 0, icon: 'QuestionFilled', gradient: 'linear-gradient(135deg, #00b894, #00cec9)' },
    { label: 'AI回复数', value: c.assistantMessages || 0, icon: 'Cpu', gradient: 'linear-gradient(135deg, #6c5ce7, #a29bfe)' },
    { label: '满意度', value: (c.satisfactionRate || 0) + '%', icon: 'Star', gradient: 'linear-gradient(135deg, #fdcb6e, #e17055)' },
    { label: '平均响应', value: c.avgResponseTime ? formatSeconds(c.avgResponseTime) : '-', icon: 'Timer', gradient: 'linear-gradient(135deg, #0984e3, #74b9ff)' },
    { label: '通知公告数', value: stats.value.notificationCount || 0, icon: 'Bell', gradient: 'linear-gradient(135deg, #909399, #a6a9ad)' },
    // ========== 新增模块统计 ==========
    { label: '学生反馈总数', value: f.total || 0, icon: 'EditPen', gradient: 'linear-gradient(135deg, #e17055, #fab1a0)' },
    { label: '待处理反馈', value: f.pending || 0, icon: 'BellFilled', gradient: 'linear-gradient(135deg, #d63031, #ff7675)' },
    { label: '在售商品', value: m.onSale || 0, icon: 'ShoppingBag', gradient: 'linear-gradient(135deg, #00b894, #55efc4)' },
    { label: '成交订单', value: m.orderTotal || 0, icon: 'Tickets', gradient: 'linear-gradient(135deg, #0984e3, #74b9ff)' },
    { label: '帖子总数', value: p.total || 0, icon: 'ChatLineSquare', gradient: 'linear-gradient(135deg, #6c5ce7, #a29bfe)' },
  ]
}

// 后端返回的响应时间为毫秒，展示统一换算为秒
function formatSeconds(ms) {
  if (!ms && ms !== 0) return '-'
  const seconds = ms / 1000
  return seconds < 10 ? seconds.toFixed(1) + ' 秒' : Math.round(seconds) + ' 秒'
}

function initCharts() {
  // 报修状态饼图
  if (repairChartRef.value) {
    const chart = echarts.init(repairChartRef.value)
    chartInstances.push(chart)
    const r = stats.value.repair || {}
    chart.setOption({
      tooltip: { trigger: 'item' },
      legend: { bottom: 0, textStyle: { fontSize: 12 } },
      series: [{
        type: 'pie',
        radius: ['40%', '70%'],
        avoidLabelOverlap: false,
        itemStyle: { borderRadius: 8, borderColor: '#fff', borderWidth: 2 },
        label: { show: false },
        data: [
          { value: r.submitted || 0, name: '已提交', itemStyle: { color: '#e6a23c' } },
          { value: r.accepted || 0, name: '已受理', itemStyle: { color: '#409eff' } },
          { value: r.repairing || 0, name: '维修中', itemStyle: { color: '#8e44ad' } },
          { value: r.completed || 0, name: '已完成', itemStyle: { color: '#67c23a' } },
        ]
      }]
    })
  }

  // 事项类型饼图
  if (appChartRef.value) {
    const chart = echarts.init(appChartRef.value)
    chartInstances.push(chart)
    const a = stats.value.appTypeDistribution || {}
    chart.setOption({
      tooltip: { trigger: 'item' },
      legend: { bottom: 0, textStyle: { fontSize: 12 } },
      series: [{
        type: 'pie',
        radius: ['40%', '70%'],
        itemStyle: { borderRadius: 8, borderColor: '#fff', borderWidth: 2 },
        label: { show: false },
        data: [
          { value: a['奖助学金'] || 0, name: '奖助学金', itemStyle: { color: '#f56c6c' } },
          { value: a['请假申请'] || 0, name: '请假申请', itemStyle: { color: '#409eff' } },
          { value: a['证明开具'] || 0, name: '证明开具', itemStyle: { color: '#67c23a' } },
        ]
      }]
    })
  }

  // 学生反馈处理情况（饼图）
  if (feedbackChartRef.value) {
    const chart = echarts.init(feedbackChartRef.value)
    chartInstances.push(chart)
    const f = stats.value.feedback || {}
    chart.setOption({
      tooltip: { trigger: 'item' },
      legend: { bottom: 0, textStyle: { fontSize: 12 } },
      series: [{
        type: 'pie',
        radius: ['40%', '70%'],
        itemStyle: { borderRadius: 8, borderColor: '#fff', borderWidth: 2 },
        label: { show: false },
        data: [
          { value: f.pending || 0, name: '待处理', itemStyle: { color: '#e6a23c' } },
          { value: f.processing || 0, name: '处理中', itemStyle: { color: '#409eff' } },
          { value: f.resolved || 0, name: '已解决', itemStyle: { color: '#67c23a' } },
        ]
      }]
    })
  }

  // 二手商品状态分布（饼图）
  if (marketChartRef.value) {
    const chart = echarts.init(marketChartRef.value)
    chartInstances.push(chart)
    const m = stats.value.market || {}
    chart.setOption({
      tooltip: { trigger: 'item' },
      legend: { bottom: 0, textStyle: { fontSize: 12 } },
      series: [{
        type: 'pie',
        radius: ['40%', '70%'],
        itemStyle: { borderRadius: 8, borderColor: '#fff', borderWidth: 2 },
        label: { show: false },
        data: [
          { value: m.onSale || 0, name: '在售', itemStyle: { color: '#67c23a' } },
          { value: m.offShelf || 0, name: '已下架', itemStyle: { color: '#909399' } },
          { value: m.sold || 0, name: '已售出', itemStyle: { color: '#e6a23c' } },
        ]
      }]
    })
  }

  // 趋势图（使用后端真实统计数据）
  if (trendChartRef.value) {
    const chart = echarts.init(trendChartRef.value)
    chartInstances.push(chart)
    const trend = stats.value.trend || {}
    const days = trend.days || []
    chart.setOption({
      tooltip: { trigger: 'axis' },
      legend: { data: ['报修提交', '事项申请', 'AI问答'], top: 0 },
      grid: { left: '3%', right: '4%', bottom: '3%', containLabel: true },
      xAxis: { type: 'category', data: days, boundaryGap: false },
      yAxis: { type: 'value', minInterval: 1 },
      series: [
        { name: '报修提交', type: 'line', smooth: true, data: trend.repair || [], itemStyle: { color: '#f56c6c' }, areaStyle: { opacity: 0.1 } },
        { name: '事项申请', type: 'line', smooth: true, data: trend.application || [], itemStyle: { color: '#409eff' }, areaStyle: { opacity: 0.1 } },
        { name: 'AI问答', type: 'line', smooth: true, data: trend.chat || [], itemStyle: { color: '#67c23a' }, areaStyle: { opacity: 0.1 } },
      ]
    })
  }
}

onMounted(async () => {
  loading.value = true
  loadError.value = false
  try {
    const res = await getDashboard()
    stats.value = res.data
    hotQuestions.value = res.data.hotQuestions || []
    initStatCards()
    await nextTick()
    // 延迟初始化，等 AdminLayout 布局/侧边栏动画稳定后再画，避免容器宽度为 0
    setTimeout(() => {
      initCharts()
      resizeCharts()
    }, 200)
    window.addEventListener('resize', resizeCharts)
  } catch (e) {
    console.error(e)
    loadError.value = true
  } finally {
    loading.value = false
  }
})

onBeforeUnmount(() => {
  window.removeEventListener('resize', resizeCharts)
  chartInstances.forEach((c) => c && c.dispose())
  chartInstances.length = 0
})
</script>

<style scoped>
.loading-wrap {
  min-height: 360px;
  display: flex;
  align-items: center;
  justify-content: center;
}

.error-alert {
  margin-bottom: 20px;
}

.stat-cards {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(200px, 1fr));
  gap: 16px;
  margin-bottom: 20px;
}

.stat-card {
  background: #fff;
  border-radius: 12px;
  padding: 20px;
  display: flex;
  align-items: center;
  gap: 14px;
  border: 1px solid #ebeef5;
}

.stat-icon {
  width: 52px;
  height: 52px;
  border-radius: 12px;
  display: flex;
  align-items: center;
  justify-content: center;
  color: #fff;
  flex-shrink: 0;
}

.stat-value {
  font-size: 24px;
  font-weight: 800;
  color: #303133;
}

.stat-label {
  font-size: 12px;
  color: #909399;
  margin-top: 2px;
}

.charts-row {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 16px;
  margin-bottom: 20px;
}

.chart-card {
  background: #fff;
  border-radius: 12px;
  padding: 20px;
  border: 1px solid #ebeef5;
}

.chart-card.chart-wide {
  grid-column: span 2;
}

.chart-card h3 {
  font-size: 15px;
  font-weight: 700;
  margin: 0 0 16px 0;
  color: #303133;
}

.chart-container {
  height: 280px;
}

.hot-section {
  background: #fff;
  border-radius: 12px;
  padding: 20px;
  border: 1px solid #ebeef5;
}

.hot-section h3 {
  font-size: 15px;
  font-weight: 700;
  margin: 0 0 16px 0;
}

.hot-list {
  display: flex;
  flex-direction: column;
  gap: 10px;
}

.hot-item {
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 10px 14px;
  background: #f5f7fa;
  border-radius: 8px;
}

.hot-rank {
  width: 24px;
  height: 24px;
  border-radius: 6px;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 13px;
  font-weight: 700;
  color: #fff;
  background: #c0c4cc;
}

.hot-rank.rank-1 { background: #f56c6c; }
.hot-rank.rank-2 { background: #e6a23c; }
.hot-rank.rank-3 { background: #409eff; }

.hot-question {
  flex: 1;
  font-size: 14px;
  color: #303133;
}

.hot-count {
  font-size: 13px;
  color: #909399;
}

/* ========== 移动端响应式 ========== */
@media (max-width: 768px) {
  .stat-cards {
    grid-template-columns: repeat(2, 1fr);
    gap: 10px;
  }
  .stat-card {
    padding: 14px 12px;
    gap: 10px;
  }
  .stat-icon {
    width: 40px;
    height: 40px;
  }
  .stat-icon :deep(.el-icon) {
    font-size: 20px !important;
  }
  .stat-value {
    font-size: 18px;
  }
  .stat-label {
    font-size: 11px;
  }
  .charts-row {
    grid-template-columns: 1fr;
    gap: 12px;
  }
  .chart-card {
    padding: 14px;
  }
  .chart-card.chart-wide {
    grid-column: span 1;
  }
  .chart-container {
    height: 240px;
  }
  .hot-section {
    padding: 14px;
  }
}
</style>
