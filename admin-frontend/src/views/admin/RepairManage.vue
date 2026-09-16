<template>
  <AdminLayout>
    <div class="manage-page">
      <div class="toolbar">
        <el-radio-group v-model="statusFilter" @change="loadData">
          <el-radio-button value="">全部</el-radio-button>
          <el-radio-button value="SUBMITTED">已提交</el-radio-button>
          <el-radio-button value="ACCEPTED">已受理</el-radio-button>
          <el-radio-button value="REPAIRING">维修中</el-radio-button>
          <el-radio-button value="COMPLETED">已完成</el-radio-button>
        </el-radio-group>
      </div>

      <el-table :data="repairs" stripe style="width: 100%">
        <el-table-column prop="orderNo" label="报修单号" width="170" />
        <el-table-column prop="faultType" label="故障类型" width="100">
          <template #default="{ row }">
            <el-tag size="small">{{ row.faultType }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="location" label="报修地点" width="160" />
        <el-table-column prop="description" label="故障描述" min-width="200" show-overflow-tooltip />
        <el-table-column prop="contactName" label="联系人" width="90" />
        <el-table-column prop="handler" label="处理人员" width="100">
          <template #default="{ row }">{{ row.handler || '待分配' }}</template>
        </el-table-column>
        <el-table-column label="状态" width="100">
          <template #default="{ row }">
            <el-tag :type="getStatusType(row.status)" size="small">{{ getStatusText(row.status) }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="提交时间" width="160">
          <template #default="{ row }">{{ formatTime(row.createTime) }}</template>
        </el-table-column>
        <el-table-column label="操作" width="200" fixed="right">
          <template #default="{ row }">
            <el-button text type="primary" size="small" @click="viewDetail(row)">详情</el-button>
            <el-button v-if="row.status === 'SUBMITTED'" text type="success" size="small" @click="updateStatus(row, 'ACCEPTED', '已受理')">受理</el-button>
            <el-button v-if="row.status === 'ACCEPTED'" text type="warning" size="small" @click="updateStatus(row, 'REPAIRING', '维修中')">开始维修</el-button>
            <el-button v-if="row.status === 'REPAIRING'" text type="success" size="small" @click="updateStatus(row, 'COMPLETED', '维修完成')">完成</el-button>
          </template>
        </el-table-column>
      </el-table>

      <!-- 详情弹窗 -->
      <el-dialog v-model="detailVisible" title="报修详情" width="560px">
        <div v-if="current">
          <el-descriptions :column="2" border>
            <el-descriptions-item label="报修单号">{{ current.orderNo }}</el-descriptions-item>
            <el-descriptions-item label="状态"><el-tag :type="getStatusType(current.status)">{{ getStatusText(current.status) }}</el-tag></el-descriptions-item>
            <el-descriptions-item label="故障类型">{{ current.faultType }}</el-descriptions-item>
            <el-descriptions-item label="报修地点">{{ current.location }}</el-descriptions-item>
            <el-descriptions-item label="故障描述" :span="2">{{ current.description }}</el-descriptions-item>
            <el-descriptions-item label="联系人">{{ current.contactName }}</el-descriptions-item>
            <el-descriptions-item label="联系电话">{{ current.contactPhone }}</el-descriptions-item>
            <el-descriptions-item label="处理人员">{{ current.handler || '待分配' }}</el-descriptions-item>
            <el-descriptions-item label="预计完成">{{ current.expectCompleteTime ? formatTime(current.expectCompleteTime) : '-' }}</el-descriptions-item>
            <el-descriptions-item label="处理备注" :span="2">{{ current.handlerRemark || '暂无' }}</el-descriptions-item>
            <el-descriptions-item v-if="current.rating" label="服务评分" :span="2">
              <el-rate :model-value="current.rating" disabled />
              <span v-if="current.evaluation" style="margin-left: 12px; color: #606266">{{ current.evaluation }}</span>
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
import { adminGetRepairs, adminUpdateRepairStatus } from '@/api'
import { ElMessage, ElMessageBox } from 'element-plus'
import { formatTime } from '@/utils/format'

const statusFilter = ref('')
const repairs = ref([])
const detailVisible = ref(false)
const current = ref(null)

function getStatusType(s) {
  return { SUBMITTED: 'warning', ACCEPTED: 'primary', REPAIRING: '', COMPLETED: 'success', EVALUATED: 'info' }[s] || 'info'
}
function getStatusText(s) {
  return { SUBMITTED: '已提交', ACCEPTED: '已受理', REPAIRING: '维修中', COMPLETED: '已完成', EVALUATED: '已评价' }[s] || s
}

function viewDetail(row) {
  current.value = row
  detailVisible.value = true
}

async function updateStatus(row, status, text) {
  try {
    await ElMessageBox.confirm(`确定将工单 ${row.orderNo} 标记为「${text}」吗？`, '确认操作', { type: 'warning' })
    await adminUpdateRepairStatus(row.id, { status, remark: text })
    ElMessage.success('操作成功')
    loadData()
  } catch {}
}

async function loadData() {
  try {
    const res = await adminGetRepairs(statusFilter.value || undefined)
    repairs.value = res.data
  } catch {}
}

onMounted(() => loadData())
</script>

<style scoped>
.manage-page { display: flex; flex-direction: column; gap: 16px; }
.toolbar { display: flex; gap: 12px; }
</style>
