<template>
  <AdminLayout>
    <div class="manage-page">
      <div class="toolbar">
        <el-radio-group v-model="statusFilter" @change="loadData">
          <el-radio-button value="">全部</el-radio-button>
          <el-radio-button value="SUBMITTED">已提交</el-radio-button>
          <el-radio-button value="REVIEWING">审核中</el-radio-button>
          <el-radio-button value="APPROVED">已通过</el-radio-button>
          <el-radio-button value="REJECTED">已驳回</el-radio-button>
        </el-radio-group>
      </div>

      <el-table :data="applications" stripe style="width: 100%">
        <el-table-column prop="applicationNo" label="申请编号" width="170" />
        <el-table-column label="类型" width="100">
          <template #default="{ row }"><el-tag :type="getTypeType(row.type)" size="small">{{ getTypeText(row.type) }}</el-tag></template>
        </el-table-column>
        <el-table-column prop="title" label="事项名称" min-width="160" show-overflow-tooltip />
        <el-table-column prop="studentName" label="申请人" width="90" />
        <el-table-column prop="currentNode" label="当前节点" width="120" />
        <el-table-column label="状态" width="100">
          <template #default="{ row }"><el-tag :type="getStatusType(row.status)" size="small">{{ getStatusText(row.status) }}</el-tag></template>
        </el-table-column>
        <el-table-column label="提交时间" width="160">
          <template #default="{ row }">{{ formatTime(row.createTime) }}</template>
        </el-table-column>
        <el-table-column label="操作" width="220" fixed="right">
          <template #default="{ row }">
            <el-button text type="primary" size="small" @click="viewDetail(row)">详情</el-button>
            <el-button v-if="['SUBMITTED','REVIEWING'].includes(row.status)" text type="success" size="small" @click="reviewAction(row, 'approve')">通过</el-button>
            <el-button v-if="['SUBMITTED','REVIEWING'].includes(row.status)" text type="danger" size="small" @click="reviewAction(row, 'reject')">驳回</el-button>
          </template>
        </el-table-column>
      </el-table>

      <!-- 详情弹窗 -->
      <el-dialog v-model="detailVisible" title="申请详情" width="600px">
        <div v-if="current">
          <el-descriptions :column="2" border>
            <el-descriptions-item label="申请编号">{{ current.applicationNo }}</el-descriptions-item>
            <el-descriptions-item label="类型">{{ getTypeText(current.type) }}</el-descriptions-item>
            <el-descriptions-item label="事项名称" :span="2">{{ current.title }}</el-descriptions-item>
            <el-descriptions-item label="申请人">{{ current.studentName }}</el-descriptions-item>
            <el-descriptions-item label="状态"><el-tag :type="getStatusType(current.status)">{{ getStatusText(current.status) }}</el-tag></el-descriptions-item>
            <el-descriptions-item label="当前节点">{{ current.currentNode }}</el-descriptions-item>
            <el-descriptions-item label="提交时间">{{ formatTime(current.createTime) }}</el-descriptions-item>
            <el-descriptions-item label="表单数据" :span="2">
              <pre style="white-space: pre-wrap; font-size: 12px; margin: 0">{{ current.formData }}</pre>
            </el-descriptions-item>
            <el-descriptions-item v-if="current.rejectReason" label="驳回原因" :span="2">
              <span style="color: #f56c6c">{{ current.rejectReason }}</span>
            </el-descriptions-item>
          </el-descriptions>
        </div>
      </el-dialog>
    </div>
  </AdminLayout>
</template>

<script setup>
import { ref, onMounted } from 'vue'
import AdminLayout from '@/components/AdminLayout.vue'
import { adminGetApplications, adminReviewApplication } from '@/api'
import { ElMessage, ElMessageBox } from 'element-plus'
import { formatTime } from '@/utils/format'

const statusFilter = ref('')
const applications = ref([])
const detailVisible = ref(false)
const current = ref(null)

function getTypeText(t) { return { SCHOLARSHIP: '奖助学金', LEAVE: '请假申请', CERTIFICATE: '证明开具', OTHER: '其他' }[t] || t }
function getTypeType(t) { return { SCHOLARSHIP: 'danger', LEAVE: 'primary', CERTIFICATE: 'success', OTHER: 'info' }[t] || 'info' }
function getStatusType(s) { return { SUBMITTED: 'warning', REVIEWING: '', APPROVED: 'success', REJECTED: 'danger', COMPLETED: 'success' }[s] || 'info' }
function getStatusText(s) { return { SUBMITTED: '已提交', REVIEWING: '审核中', APPROVED: '已通过', REJECTED: '已驳回', COMPLETED: '已完成' }[s] || s }

function viewDetail(row) { current.value = row; detailVisible.value = true }

async function reviewAction(row, action) {
  try {
    let remark = ''
    if (action === 'reject') {
      const { value } = await ElMessageBox.prompt('请输入驳回原因', '驳回申请', { confirmButtonText: '确定', cancelButtonText: '取消', inputType: 'textarea' })
      remark = value
    } else {
      await ElMessageBox.confirm(`确定通过申请 ${row.applicationNo} 吗？`, '确认通过', { type: 'success' })
    }
    await adminReviewApplication(row.id, { action, remark, nextNode: action === 'approve' ? '' : undefined })
    ElMessage.success('操作成功')
    loadData()
  } catch {}
}

async function loadData() {
  try {
    const res = await adminGetApplications({ status: statusFilter.value || undefined })
    applications.value = res.data
  } catch {}
}

onMounted(() => loadData())
</script>

<style scoped>
.manage-page { display: flex; flex-direction: column; gap: 16px; }
.toolbar { display: flex; gap: 12px; }
</style>
