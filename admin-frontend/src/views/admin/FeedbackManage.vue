<template>
  <AdminLayout>
    <div class="manage-page">
      <div class="toolbar">
        <el-radio-group v-model="statusFilter" @change="loadData">
          <el-radio-button value="">全部状态</el-radio-button>
          <el-radio-button value="PENDING">待处理</el-radio-button>
          <el-radio-button value="PROCESSING">处理中</el-radio-button>
          <el-radio-button value="RESOLVED">已解决</el-radio-button>
        </el-radio-group>
        <el-select v-model="typeFilter" placeholder="全部类型" clearable style="width: 150px" @change="loadData">
          <el-option label="功能建议" value="SUGGESTION" />
          <el-option label="问题投诉" value="COMPLAINT" />
          <el-option label="故障反馈" value="BUG" />
          <el-option label="其他" value="OTHER" />
        </el-select>
        <el-button @click="loadData"><el-icon><Refresh /></el-icon> 刷新</el-button>
        <div class="toolbar-tip">
          共 {{ total }} 条 · 本页待处理
          <strong class="pending-count">{{ pendingCount }}</strong>
        </div>
      </div>

      <el-table :data="feedbacks" stripe style="width: 100%">
        <el-table-column label="类型" width="100">
          <template #default="{ row }">
            <el-tag size="small" effect="plain">{{ typeText(row.type) }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="title" label="标题" min-width="160" show-overflow-tooltip />
        <el-table-column prop="content" label="内容" min-width="240" show-overflow-tooltip />
        <el-table-column label="提交用户" width="110">
          <template #default="{ row }">用户 #{{ row.userId }}</template>
        </el-table-column>
        <el-table-column label="联系方式" width="140" show-overflow-tooltip>
          <template #default="{ row }">{{ row.contact || '未留' }}</template>
        </el-table-column>
        <el-table-column label="状态" width="100">
          <template #default="{ row }">
            <el-tag size="small" :type="statusType(row.status)">{{ statusText(row.status) }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="提交时间" width="160">
          <template #default="{ row }">{{ formatTime(row.createTime) }}</template>
        </el-table-column>
        <el-table-column label="操作" width="150" fixed="right">
          <template #default="{ row }">
            <el-button text type="primary" size="small" @click="openReply(row)">查看/回复</el-button>
            <el-button text type="danger" size="small" @click="remove(row)">删除</el-button>
          </template>
        </el-table-column>
      </el-table>

      <el-empty v-if="feedbacks.length === 0" description="暂无学生反馈" :image-size="80" />

      <div style="display: flex; justify-content: flex-end; margin-top: 16px">
        <el-pagination
          v-model:current-page="page"
          v-model:page-size="size"
          :total="total"
          :page-sizes="[10, 20, 50]"
          layout="total, sizes, prev, pager, next"
          @current-change="loadData"
          @size-change="onSizeChange"
        />
      </div>

      <!-- 详情 / 回复 -->
      <el-dialog v-model="detailVisible" title="反馈详情" width="640px">
        <template v-if="current">
          <el-descriptions :column="2" border>
            <el-descriptions-item label="类型">{{ typeText(current.type) }}</el-descriptions-item>
            <el-descriptions-item label="状态">
              <el-tag size="small" :type="statusType(current.status)">{{ statusText(current.status) }}</el-tag>
            </el-descriptions-item>
            <el-descriptions-item label="标题" :span="2">{{ current.title || '（未填写）' }}</el-descriptions-item>
            <el-descriptions-item label="内容" :span="2">
              <div class="feedback-content">{{ current.content }}</div>
            </el-descriptions-item>
            <el-descriptions-item label="联系方式" :span="2">{{ current.contact || '未留' }}</el-descriptions-item>
            <el-descriptions-item label="提交时间" :span="2">{{ formatTime(current.createTime) }}</el-descriptions-item>
          </el-descriptions>

          <div v-if="imageList.length" class="feedback-images">
            <el-image
              v-for="(url, idx) in imageList"
              :key="idx"
              :src="url"
              :preview-src-list="imageList"
              :initial-index="idx"
              fit="cover"
              class="feedback-image"
            />
          </div>

          <el-divider content-position="left">管理员回复</el-divider>

          <el-form label-width="80px">
            <el-form-item label="处理状态">
              <el-radio-group v-model="replyForm.status">
                <el-radio-button value="PENDING">待处理</el-radio-button>
                <el-radio-button value="PROCESSING">处理中</el-radio-button>
                <el-radio-button value="RESOLVED">已解决</el-radio-button>
              </el-radio-group>
            </el-form-item>
            <el-form-item label="回复内容">
              <el-input
                v-model="replyForm.adminReply"
                type="textarea"
                :rows="4"
                maxlength="2000"
                show-word-limit
                placeholder="请输入给学生的回复，学生可在「意见反馈」中看到"
              />
            </el-form-item>
          </el-form>

          <div v-if="current.adminReply" class="prev-reply">
            <span class="prev-label">上次回复（{{ formatTime(current.replyTime) }}）：</span>
            <span>{{ current.adminReply }}</span>
          </div>
        </template>
        <template #footer>
          <el-button @click="detailVisible = false">取消</el-button>
          <el-button type="primary" :loading="submitting" @click="submitReply">提交回复</el-button>
        </template>
      </el-dialog>
    </div>
  </AdminLayout>
</template>

<script setup>
import { computed, onMounted, ref } from 'vue'
import AdminLayout from '@/components/AdminLayout.vue'
import { adminGetFeedbacks, adminReplyFeedback, adminDeleteFeedback } from '@/api'
import { ElMessage, ElMessageBox } from 'element-plus'
import { formatTime, feedbackTypeMap, feedbackStatusMap, feedbackStatusTypeMap } from '@/utils/format'

const statusFilter = ref('')
const typeFilter = ref('')
const feedbacks = ref([])
// 服务端分页：只加载当前页，page/size 直接透传给后端
const page = ref(1)
const size = ref(20)
const total = ref(0)

const detailVisible = ref(false)
const submitting = ref(false)
const current = ref(null)
const replyForm = ref({ status: 'PROCESSING', adminReply: '' })

const pendingCount = computed(() => feedbacks.value.filter((f) => f.status === 'PENDING').length)

const imageList = computed(() =>
  (current.value?.images || '').split(',').map((s) => s.trim()).filter(Boolean)
)

function typeText(t) {
  return feedbackTypeMap[t] || t
}
function statusText(s) {
  return feedbackStatusMap[s] || s
}
function statusType(s) {
  return feedbackStatusTypeMap[s] || 'info'
}

async function loadData() {
  try {
    const res = await adminGetFeedbacks({
      status: statusFilter.value || undefined,
      type: typeFilter.value || undefined,
      page: page.value,
      size: size.value
    })
    feedbacks.value = res.data?.list || []
    total.value = res.data?.total || 0
  } catch {
    /* 拦截器已提示 */
  }
}

/** 改每页条数后回到第一页，避免停留在越界页码上 */
function onSizeChange() {
  page.value = 1
  loadData()
}

function openReply(row) {
  current.value = row
  replyForm.value = {
    status: row.status === 'PENDING' ? 'PROCESSING' : row.status,
    adminReply: row.adminReply || ''
  }
  detailVisible.value = true
}

async function submitReply() {
  if (!replyForm.value.adminReply?.trim()) {
    ElMessage.warning('请填写回复内容')
    return
  }
  submitting.value = true
  try {
    await adminReplyFeedback(current.value.id, {
      adminReply: replyForm.value.adminReply.trim(),
      status: replyForm.value.status
    })
    ElMessage.success('回复已发送')
    detailVisible.value = false
    loadData()
  } catch {
    /* 拦截器已提示 */
  } finally {
    submitting.value = false
  }
}

async function remove(row) {
  try {
    await ElMessageBox.confirm('确定删除该条反馈吗？删除后不可恢复。', '提示', { type: 'warning' })
  } catch {
    return
  }
  try {
    await adminDeleteFeedback(row.id)
    ElMessage.success('已删除')
    loadData()
  } catch {
    /* 拦截器已提示 */
  }
}

onMounted(loadData)
</script>

<style scoped>
.manage-page {
  display: flex;
  flex-direction: column;
  gap: 16px;
}
.toolbar {
  display: flex;
  gap: 12px;
  align-items: center;
  flex-wrap: wrap;
}
.toolbar-tip {
  margin-left: auto;
  font-size: 13px;
  color: #909399;
}
.pending-count {
  color: #e6a23c;
}
.feedback-content {
  white-space: pre-wrap;
  line-height: 1.7;
}
.feedback-images {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
  margin-top: 14px;
}
.feedback-image {
  width: 96px;
  height: 96px;
  border-radius: 8px;
}
.prev-reply {
  margin-top: 8px;
  padding: 10px 12px;
  background: #f5f7fa;
  border-radius: 8px;
  font-size: 13px;
  color: #606266;
  line-height: 1.6;
}
.prev-label {
  color: #909399;
}
</style>
