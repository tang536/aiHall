<template>
  <div class="repair-page">
    <div class="page-header">
      <h2><el-icon><Tools /></el-icon> 报修登记</h2>
      <p>在线提交校园设施报修申请，实时追踪处理进度</p>
    </div>

    <el-tabs v-model="activeTab">
      <!-- 提交报修 -->
      <el-tab-pane label="提交报修" name="submit">
        <div class="submit-container">
          <el-form :model="form" :rules="rules" ref="formRef" label-width="100px" class="repair-form">
            <el-form-item label="故障类型" prop="faultType">
              <el-radio-group v-model="form.faultType" class="fault-type-group">
                <el-radio-button v-for="t in faultTypes" :key="t.value" :value="t.value">
                  <el-icon><component :is="t.icon" /></el-icon> {{ t.label }}
                </el-radio-button>
              </el-radio-group>
            </el-form-item>
            <el-form-item label="报修地点" prop="location">
              <el-input v-model="form.location" placeholder="例如：学生宿舍1栋302室">
                <template #prefix><el-icon><Location /></el-icon></template>
              </el-input>
            </el-form-item>
            <el-form-item label="故障描述" prop="description">
              <el-input v-model="form.description" type="textarea" :rows="4"
                        placeholder="请详细描述故障情况，例如：卫生间水龙头漏水，水流较大..." />
            </el-form-item>
            <el-form-item label="上传照片">
              <el-upload action="#" :auto-upload="false" list-type="picture-card"
                         :on-change="handleFileChange" :limit="3" multiple>
                <el-icon><Plus /></el-icon>
              </el-upload>
              <div class="upload-tip">最多上传3张照片（演示环境仅记录文件名）</div>
            </el-form-item>
            <el-form-item label="联系人" prop="contactName">
              <el-input v-model="form.contactName" placeholder="您的姓名" />
            </el-form-item>
            <el-form-item label="联系电话" prop="contactPhone">
              <el-input v-model="form.contactPhone" placeholder="联系电话" />
            </el-form-item>
            <el-form-item>
              <el-button type="primary" size="large" :loading="submitting" @click="submitRepair">
                <el-icon><Check /></el-icon> 提交报修
              </el-button>
              <el-button size="large" @click="resetForm">重置</el-button>
            </el-form-item>
          </el-form>
        </div>
      </el-tab-pane>

      <!-- 我的报修 -->
      <el-tab-pane label="我的报修" name="my">
        <div class="query-bar">
          <el-input v-model="queryOrderNo" placeholder="输入报修单号查询" clearable>
            <template #prefix><el-icon><Search /></el-icon></template>
          </el-input>
          <el-button type="primary" @click="queryByOrderNo">查询</el-button>
        </div>

        <div class="repair-list">
          <div v-for="order in myRepairs" :key="order.id"
               class="repair-card card-hover"
               @click="showOrderDetail(order)">
            <div class="repair-header">
              <span class="order-no">{{ order.orderNo }}</span>
              <el-tag :type="getStatusType(order.status)" size="small">{{ getStatusText(order.status) }}</el-tag>
            </div>
            <h4>{{ order.faultType }} - {{ order.location }}</h4>
            <p class="repair-desc">{{ order.description }}</p>
            <div class="repair-footer">
              <span><el-icon><Clock /></el-icon> {{ formatTime(order.createTime) }}</span>
              <span v-if="order.handler"><el-icon><User /></el-icon> {{ order.handler }}</span>
            </div>
            <!-- 进度条 -->
            <el-steps :active="getStepIndex(order.status)" finish-status="success" align-center size="small" class="repair-steps">
              <el-step title="已提交" />
              <el-step title="已受理" />
              <el-step title="维修中" />
              <el-step title="已完成" />
            </el-steps>
          </div>
          <el-empty v-if="myRepairs.length === 0" description="暂无报修记录" />
        </div>
      </el-tab-pane>
    </el-tabs>

    <!-- 报修成功弹窗 -->
    <el-dialog v-model="successVisible" title="报修提交成功" width="420px">
      <div class="success-content">
        <el-result icon="success" title="报修提交成功" :sub-title="`报修单号：${successOrderNo}`">
          <template #extra>
            <el-button type="primary" @click="successVisible = false">知道了</el-button>
          </template>
        </el-result>
      </div>
    </el-dialog>

    <!-- 报修详情弹窗 -->
    <el-dialog v-model="detailVisible" title="报修详情" width="560px">
      <div v-if="currentOrder">
        <el-descriptions :column="2" border>
          <el-descriptions-item label="报修单号">{{ currentOrder.orderNo }}</el-descriptions-item>
          <el-descriptions-item label="状态">
            <el-tag :type="getStatusType(currentOrder.status)">{{ getStatusText(currentOrder.status) }}</el-tag>
          </el-descriptions-item>
          <el-descriptions-item label="故障类型">{{ currentOrder.faultType }}</el-descriptions-item>
          <el-descriptions-item label="报修地点">{{ currentOrder.location }}</el-descriptions-item>
          <el-descriptions-item label="故障描述" :span="2">{{ currentOrder.description }}</el-descriptions-item>
          <el-descriptions-item label="联系人">{{ currentOrder.contactName }}</el-descriptions-item>
          <el-descriptions-item label="联系电话">{{ currentOrder.contactPhone }}</el-descriptions-item>
          <el-descriptions-item label="处理人员">{{ currentOrder.handler || '待分配' }}</el-descriptions-item>
          <el-descriptions-item label="预计完成">{{ currentOrder.expectCompleteTime ? formatTime(currentOrder.expectCompleteTime) : '-' }}</el-descriptions-item>
          <el-descriptions-item label="处理备注" :span="2">{{ currentOrder.handlerRemark || '暂无' }}</el-descriptions-item>
        </el-descriptions>
        <div v-if="currentOrder.status === 'COMPLETED' && !currentOrder.rating" class="eval-section">
          <el-divider>服务评价</el-divider>
            <el-rate v-model="evalRating" />
            <el-input v-model="evalText" type="textarea" :rows="2" placeholder="请输入评价（选填）" style="margin-top: 8px" />
            <el-button type="primary" style="margin-top: 8px" @click="submitEval">提交评价</el-button>
        </div>
        <div v-if="currentOrder.rating" class="eval-result">
          <el-divider>已评价</el-divider>
          <el-rate :model-value="currentOrder.rating" disabled />
          <p v-if="currentOrder.evaluation" style="color: #606266; margin-top: 8px">{{ currentOrder.evaluation }}</p>
        </div>
      </div>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, reactive, onMounted } from 'vue'
import { submitRepair as apiSubmit, getMyRepairs, getRepairByOrderNo, evaluateRepair } from '@/api'
import { ElMessage } from 'element-plus'
import { useUserStore } from '@/store/user'
import { formatTime } from '@/utils/format'

const userStore = useUserStore()
const activeTab = ref('submit')
const formRef = ref()
const submitting = ref(false)
const successVisible = ref(false)
const successOrderNo = ref('')
const detailVisible = ref(false)
const currentOrder = ref(null)
const myRepairs = ref([])
const queryOrderNo = ref('')
const evalRating = ref(5)
const evalText = ref('')

const faultTypes = [
  { label: '水电', value: '水电', icon: 'Lightning' },
  { label: '门窗', value: '门窗', icon: 'Door' },
  { label: '家具', value: '家具', icon: 'House' },
  { label: '网络', value: '网络', icon: 'Connection' },
  { label: '空调', value: '空调', icon: 'Wind' },
  { label: '其他', value: '其他', icon: 'Tools' },
]

const form = reactive({
  faultType: '水电',
  location: '',
  description: '',
  contactName: userStore.userName,
  contactPhone: '',
  imageUrls: []
})

const rules = {
  faultType: [{ required: true, message: '请选择故障类型', trigger: 'change' }],
  location: [{ required: true, message: '请输入报修地点', trigger: 'blur' }],
  description: [{ required: true, message: '请描述故障情况', trigger: 'blur' }],
  contactName: [{ required: true, message: '请输入联系人', trigger: 'blur' }],
  contactPhone: [{ required: true, message: '请输入联系电话', trigger: 'blur' }],
}

function handleFileChange(file) {
  if (!form.imageUrls) form.imageUrls = []
  if (form.imageUrls.length < 3) {
    form.imageUrls.push(file.name)
  }
}

async function submitRepair() {
  try {
    await formRef.value.validate()
    submitting.value = true
    const res = await apiSubmit(form)
    successOrderNo.value = res.data.orderNo
    successVisible.value = true
    resetForm()
    loadMyRepairs()
  } catch (e) {
  } finally {
    submitting.value = false
  }
}

function resetForm() {
  form.faultType = '水电'
  form.location = ''
  form.description = ''
  form.contactPhone = ''
  form.imageUrls = []
  formRef.value?.resetFields()
}

function getStatusType(s) {
  return { SUBMITTED: 'warning', ACCEPTED: 'primary', REPAIRING: '', COMPLETED: 'success', EVALUATED: 'info' }[s] || 'info'
}

function getStatusText(s) {
  return { SUBMITTED: '已提交', ACCEPTED: '已受理', REPAIRING: '维修中', COMPLETED: '已完成', EVALUATED: '已评价' }[s] || s
}

function getStepIndex(s) {
  return { SUBMITTED: 0, ACCEPTED: 1, REPAIRING: 2, COMPLETED: 3, EVALUATED: 3 }[s] || 0
}

function showOrderDetail(order) {
  currentOrder.value = order
  evalRating.value = 5
  evalText.value = ''
  detailVisible.value = true
}

async function submitEval() {
  try {
    await evaluateRepair(currentOrder.value.id, { rating: evalRating.value, evaluation: evalText.value })
    ElMessage.success('评价成功')
    detailVisible.value = false
    loadMyRepairs()
  } catch {}
}

async function queryByOrderNo() {
  if (!queryOrderNo.value) {
    ElMessage.warning('请输入报修单号')
    return
  }
  try {
    const res = await getRepairByOrderNo(queryOrderNo.value)
    showOrderDetail(res.data)
  } catch {}
}

async function loadMyRepairs() {
  if (!userStore.isLoggedIn) {
    myRepairs.value = []
    return
  }
  try {
    const res = await getMyRepairs()
    myRepairs.value = res.data
  } catch {}
}

onMounted(() => loadMyRepairs())
</script>

<style scoped>
.repair-page {
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

.submit-container {
  background: #fff;
  border-radius: 12px;
  padding: 32px;
  width: 100%;
  box-sizing: border-box;
}

.repair-form {
  width: 100%;
}

.repair-form :deep(.el-form-item__content) {
  width: 100%;
}

.fault-type-group {
  display: flex;
  flex-wrap: wrap;
  gap: 0;
  width: 100%;
}

.fault-type-group :deep(.el-radio-button) {
  flex: 1;
  min-width: 110px;
  text-align: center;
}

.fault-type-group :deep(.el-radio-button__inner) {
  width: 100%;
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 4px;
  padding: 8px 12px;
  box-sizing: border-box;
}

.upload-tip {
  font-size: 12px;
  color: #909399;
  margin-top: 4px;
}

.query-bar {
  display: flex;
  gap: 12px;
  margin-bottom: 16px;
  width: 100%;
  align-items: center;
}

.query-bar :deep(.el-input) {
  flex: 1;
  max-width: 360px;
}

.repair-list {
  display: flex;
  flex-direction: column;
  gap: 16px;
  width: 100%;
}

.repair-card {
  background: #fff;
  border-radius: 12px;
  padding: 20px;
  cursor: pointer;
  border: 1px solid #ebeef5;
  width: 100%;
  box-sizing: border-box;
}

.repair-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 8px;
}

.order-no {
  font-family: monospace;
  font-size: 13px;
  color: #909399;
}

.repair-card h4 {
  font-size: 16px;
  font-weight: 700;
  margin: 0 0 6px 0;
}

.repair-desc {
  font-size: 13px;
  color: #606266;
  margin: 0 0 10px 0;
}

.repair-footer {
  display: flex;
  gap: 20px;
  margin-bottom: 12px;
}

.repair-footer span {
  display: flex;
  align-items: center;
  gap: 4px;
  font-size: 12px;
  color: #909399;
}

.repair-steps {
  margin-top: 8px;
}

.success-content {
  text-align: center;
}

.eval-section {
  margin-top: 16px;
}
</style>
