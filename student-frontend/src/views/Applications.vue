<template>
  <div class="app-page">
    <div class="page-header">
      <h2><el-icon><Document /></el-icon> 事项办理</h2>
      <p>在线提交奖助学金、请假、证明开具等事项申请，实时追踪审核进度</p>
    </div>

    <el-tabs v-model="activeTab">
      <!-- 事项办理入口 -->
      <el-tab-pane label="事项办理" name="apply">
        <div class="service-grid">
          <div v-for="s in services" :key="s.type"
               class="service-card card-hover"
               @click="startApply(s)">
            <div class="service-icon" :style="{ background: s.gradient }">
              <el-icon :size="36"><component :is="s.icon" /></el-icon>
            </div>
            <h4>{{ s.title }}</h4>
            <p>{{ s.desc }}</p>
            <el-button type="primary" size="small">立即办理</el-button>
          </div>
        </div>
      </el-tab-pane>

      <!-- 我的申请 -->
      <el-tab-pane label="我的申请" name="my">
        <div class="app-list">
          <div v-for="app in myApplications" :key="app.id"
               class="app-card card-hover"
               @click="showAppDetail(app)">
            <div class="app-header">
              <el-tag :type="getTypeType(app.type)">{{ getTypeText(app.type) }}</el-tag>
              <el-tag :type="getStatusType(app.status)" size="small">{{ getStatusText(app.status) }}</el-tag>
            </div>
            <h4>{{ app.title }}</h4>
            <div class="app-meta">
              <span>申请编号：{{ app.applicationNo }}</span>
              <span>当前节点：{{ app.currentNode }}</span>
              <span>提交时间：{{ formatTime(app.createTime) }}</span>
            </div>
            <div v-if="app.rejectReason" class="reject-reason">
              <el-alert :title="'驳回原因：' + app.rejectReason" type="error" :closable="false" show-icon />
            </div>
            <!-- 进度 -->
            <el-steps :active="getStepIndex(app.status)" finish-status="success" align-center size="small" class="app-steps">
              <el-step title="已提交" />
              <el-step title="审核中" />
              <el-step title="已完成" />
            </el-steps>
          </div>
          <el-empty v-if="myApplications.length === 0" description="暂无申请记录" />
        </div>
      </el-tab-pane>
    </el-tabs>

    <!-- 申请表单弹窗 -->
    <el-dialog v-model="applyVisible" :title="'提交' + currentService?.title" width="600px" top="5vh">
      <el-form :model="applyForm" :rules="applyRules" ref="applyFormRef" label-width="100px">
        <template v-if="currentService?.type === 'SCHOLARSHIP'">
          <el-form-item label="申请类型" prop="title">
            <el-select v-model="applyForm.title" placeholder="请选择" style="width: 100%">
              <el-option label="国家奖学金申请" value="国家奖学金申请" />
              <el-option label="国家励志奖学金申请" value="国家励志奖学金申请" />
              <el-option label="国家助学金申请" value="国家助学金申请" />
              <el-option label="校级奖学金申请" value="校级奖学金申请" />
            </el-select>
          </el-form-item>
          <el-form-item label="家庭年收入">
            <el-input v-model="applyForm.income" placeholder="单位：元" />
          </el-form-item>
          <el-form-item label="家庭人口">
            <el-input-number v-model="applyForm.familySize" :min="1" :max="20" />
          </el-form-item>
          <el-form-item label="申请等级">
            <el-radio-group v-model="applyForm.level">
              <el-radio value="特别困难">特别困难</el-radio>
              <el-radio value="比较困难">比较困难</el-radio>
              <el-radio value="一般困难">一般困难</el-radio>
            </el-radio-group>
          </el-form-item>
          <el-form-item label="申请理由">
            <el-input v-model="applyForm.reason" type="textarea" :rows="3" placeholder="请说明申请理由" />
          </el-form-item>
        </template>

        <template v-else-if="currentService?.type === 'LEAVE'">
          <el-form-item label="请假类型" prop="leaveType">
            <el-radio-group v-model="applyForm.leaveType">
              <el-radio value="病假">病假</el-radio>
              <el-radio value="事假">事假</el-radio>
              <el-radio value="公假">公假</el-radio>
            </el-radio-group>
          </el-form-item>
          <el-form-item label="开始时间" prop="startDate">
            <el-date-picker v-model="applyForm.startDate" type="datetime" placeholder="选择开始时间" style="width: 100%" />
          </el-form-item>
          <el-form-item label="结束时间" prop="endDate">
            <el-date-picker v-model="applyForm.endDate" type="datetime" placeholder="选择结束时间" style="width: 100%" />
          </el-form-item>
          <el-form-item label="请假原因" prop="reason">
            <el-input v-model="applyForm.reason" type="textarea" :rows="3" placeholder="请详细说明请假原因" />
          </el-form-item>
          <el-form-item label="证明材料">
            <el-upload action="#" :auto-upload="false" :limit="3">
              <el-button><el-icon><Upload /></el-icon> 上传证明材料</el-button>
            </el-upload>
          </el-form-item>
        </template>

        <template v-else-if="currentService?.type === 'CERTIFICATE'">
          <el-form-item label="证明类型" prop="title">
            <el-select v-model="applyForm.title" placeholder="请选择" style="width: 100%">
              <el-option label="在读证明" value="在读证明" />
              <el-option label="成绩单（中文）" value="成绩单（中文）" />
              <el-option label="成绩单（中英文）" value="成绩单（中英文）" />
              <el-option label="学籍证明" value="学籍证明" />
              <el-option label="毕业证明" value="毕业证明" />
            </el-select>
          </el-form-item>
          <el-form-item label="用途">
            <el-input v-model="applyForm.purpose" placeholder="例如：实习、出国、就业等" />
          </el-form-item>
          <el-form-item label="份数">
            <el-input-number v-model="applyForm.copies" :min="1" :max="10" />
          </el-form-item>
          <el-form-item label="是否需要英文">
            <el-switch v-model="applyForm.needEnglish" />
          </el-form-item>
        </template>

        <template v-else>
          <el-form-item label="事项名称" prop="title">
            <el-input v-model="applyForm.title" placeholder="请输入事项名称" />
          </el-form-item>
          <el-form-item label="详细说明">
            <el-input v-model="applyForm.reason" type="textarea" :rows="4" placeholder="请详细说明" />
          </el-form-item>
        </template>
      </el-form>
      <template #footer>
        <el-button @click="applyVisible = false">取消</el-button>
        <el-button type="primary" :loading="applying" @click="submitApplication">提交申请</el-button>
      </template>
    </el-dialog>

    <!-- 申请详情弹窗 -->
    <el-dialog v-model="detailVisible" title="申请详情" width="560px">
      <div v-if="currentApp">
        <el-descriptions :column="2" border>
          <el-descriptions-item label="申请编号">{{ currentApp.applicationNo }}</el-descriptions-item>
          <el-descriptions-item label="事项类型">{{ getTypeText(currentApp.type) }}</el-descriptions-item>
          <el-descriptions-item label="事项名称" :span="2">{{ currentApp.title }}</el-descriptions-item>
          <el-descriptions-item label="当前状态">
            <el-tag :type="getStatusType(currentApp.status)">{{ getStatusText(currentApp.status) }}</el-tag>
          </el-descriptions-item>
          <el-descriptions-item label="当前节点">{{ currentApp.currentNode }}</el-descriptions-item>
          <el-descriptions-item label="提交时间">{{ formatTime(currentApp.createTime) }}</el-descriptions-item>
          <el-descriptions-item label="完成时间">{{ currentApp.completeTime ? formatTime(currentApp.completeTime) : '-' }}</el-descriptions-item>
          <el-descriptions-item label="表单数据" :span="2">
            <pre style="white-space: pre-wrap; font-size: 12px; margin: 0">{{ currentApp.formData }}</pre>
          </el-descriptions-item>
          <el-descriptions-item v-if="currentApp.rejectReason" label="驳回原因" :span="2">
            <span style="color: #f56c6c">{{ currentApp.rejectReason }}</span>
          </el-descriptions-item>
        </el-descriptions>
      </div>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, reactive, onMounted } from 'vue'
import { submitApplication as apiSubmit, getMyApplications } from '@/api'
import { ElMessage } from 'element-plus'
import { useUserStore } from '@/store/user'
import { formatTime } from '@/utils/format'

const userStore = useUserStore()
const activeTab = ref('apply')
const applyVisible = ref(false)
const applying = ref(false)
const detailVisible = ref(false)
const currentService = ref(null)
const currentApp = ref(null)
const myApplications = ref([])
const applyFormRef = ref()

const services = [
  { type: 'SCHOLARSHIP', title: '奖助学金', desc: '国家奖学金、励志奖学金、助学金等申请', icon: 'Trophy', gradient: 'linear-gradient(135deg, #f56c6c, #f78989)' },
  { type: 'LEAVE', title: '请假申请', desc: '病假、事假、公假在线申请与审批', icon: 'Calendar', gradient: 'linear-gradient(135deg, #409eff, #66b1ff)' },
  { type: 'CERTIFICATE', title: '证明开具', desc: '在读证明、成绩单、学籍证明等', icon: 'Document', gradient: 'linear-gradient(135deg, #67c23a, #85ce61)' },
  { type: 'OTHER', title: '其他事项', desc: '其他校园事务申请', icon: 'MoreFilled', gradient: 'linear-gradient(135deg, #909399, #a6a9ad)' },
]

const applyForm = reactive({
  type: '',
  title: '',
  income: '',
  familySize: 5,
  level: '',
  reason: '',
  leaveType: '病假',
  startDate: '',
  endDate: '',
  purpose: '',
  copies: 1,
  needEnglish: false
})

const applyRules = {
  title: [{ required: true, message: '请选择/输入事项名称', trigger: 'change' }],
  leaveType: [{ required: true, message: '请选择请假类型', trigger: 'change' }],
}

function startApply(service) {
  if (!userStore.isLoggedIn) {
    ElMessage.warning('请先登录后再办理事项')
    return
  }
  currentService.value = service
  applyForm.type = service.type
  applyForm.title = ''
  applyForm.reason = ''
  applyVisible.value = true
}

async function submitApplication() {
  try {
    await applyFormRef.value.validate()
    applying.value = true
    const formData = JSON.stringify({
      ...applyForm,
      type: undefined,
      title: undefined
    })
    const res = await apiSubmit({
      type: applyForm.type,
      title: applyForm.title || (applyForm.type === 'LEAVE' ? applyForm.leaveType : currentService.value.title),
      formData
    })
    ElMessage.success(`申请提交成功！申请编号：${res.data.applicationNo}`)
    applyVisible.value = false
    activeTab.value = 'my'
    loadMyApplications()
  } catch (e) {
  } finally {
    applying.value = false
  }
}

function getTypeText(t) {
  return { SCHOLARSHIP: '奖助学金', LEAVE: '请假申请', CERTIFICATE: '证明开具', OTHER: '其他事项' }[t] || t
}

function getTypeType(t) {
  return { SCHOLARSHIP: 'danger', LEAVE: 'primary', CERTIFICATE: 'success', OTHER: 'info' }[t] || 'info'
}

function getStatusType(s) {
  return { SUBMITTED: 'warning', REVIEWING: '', APPROVED: 'success', REJECTED: 'danger', COMPLETED: 'success', DRAFT: 'info' }[s] || 'info'
}

function getStatusText(s) {
  return { SUBMITTED: '已提交', REVIEWING: '审核中', APPROVED: '已通过', REJECTED: '已驳回', COMPLETED: '已完成', DRAFT: '草稿' }[s] || s
}

function getStepIndex(s) {
  return { SUBMITTED: 0, REVIEWING: 1, APPROVED: 2, COMPLETED: 2, REJECTED: 1 }[s] || 0
}

function showAppDetail(app) {
  currentApp.value = app
  detailVisible.value = true
}

async function loadMyApplications() {
  if (!userStore.isLoggedIn) return
  try {
    const res = await getMyApplications()
    myApplications.value = res.data
  } catch {}
}

onMounted(() => loadMyApplications())
</script>

<style scoped>
.app-page {
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

.service-grid {
  display: grid;
  grid-template-columns: repeat(4, 1fr);
  gap: 20px;
}

.service-card {
  background: #fff;
  border-radius: 12px;
  padding: 28px 20px;
  text-align: center;
  cursor: pointer;
  border: 1px solid #ebeef5;
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 12px;
}

.service-icon {
  width: 72px;
  height: 72px;
  border-radius: 18px;
  display: flex;
  align-items: center;
  justify-content: center;
  color: #fff;
}

.service-card h4 {
  font-size: 18px;
  font-weight: 700;
  margin: 0;
}

.service-card p {
  font-size: 13px;
  color: #909399;
  margin: 0;
  flex: 1;
}

.app-list {
  display: flex;
  flex-direction: column;
  gap: 16px;
}

.app-card {
  background: #fff;
  border-radius: 12px;
  padding: 20px;
  cursor: pointer;
  border: 1px solid #ebeef5;
}

.app-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 10px;
}

.app-card h4 {
  font-size: 16px;
  font-weight: 700;
  margin: 0 0 10px 0;
}

.app-meta {
  display: flex;
  flex-wrap: wrap;
  gap: 16px;
  margin-bottom: 12px;
}

.app-meta span {
  font-size: 13px;
  color: #606266;
}

.reject-reason {
  margin-bottom: 12px;
}

.app-steps {
  margin-top: 8px;
}
</style>
