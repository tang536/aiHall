<template>
  <div class="profile-page">
    <div class="page-header">
      <h2><el-icon><User /></el-icon> 个人资料</h2>
      <p>完善个人资料，方便办理各类校园事务；如需更换登录密码，可在下方修改。</p>
    </div>

    <div v-if="loading" v-loading="true" class="loading-wrap"></div>

    <template v-else>
      <!-- 基本信息 -->
      <div class="card">
        <div class="card-title">
          <el-icon><InfoFilled /></el-icon> 基本信息
        </div>
        <el-form ref="profileFormRef" :model="profileForm" :rules="profileRules" label-width="90px" class="profile-form">
          <el-row :gutter="24">
            <el-col :xs="24" :sm="12">
              <el-form-item label="学号">
                <el-input v-model="profileForm.username" disabled placeholder="学号不可修改" />
              </el-form-item>
            </el-col>
            <el-col :xs="24" :sm="12">
              <el-form-item label="姓名" prop="realName">
                <el-input v-model="profileForm.realName" placeholder="请输入姓名" maxlength="20" show-word-limit />
              </el-form-item>
            </el-col>
            <el-col :xs="24" :sm="12">
              <el-form-item label="学院" prop="college">
                <el-input v-model="profileForm.college" placeholder="请输入学院，如：计算机与电子信息学院" maxlength="50" />
              </el-form-item>
            </el-col>
            <el-col :xs="24" :sm="12">
              <el-form-item label="专业" prop="major">
                <el-input v-model="profileForm.major" placeholder="请输入专业" maxlength="30" />
              </el-form-item>
            </el-col>
            <el-col :xs="24" :sm="12">
              <el-form-item label="年级" prop="grade">
                <el-input v-model="profileForm.grade" placeholder="如：2024级" maxlength="20" />
              </el-form-item>
            </el-col>
            <el-col :xs="24" :sm="12">
              <el-form-item label="手机号" prop="phone">
                <el-input v-model="profileForm.phone" placeholder="请输入手机号" maxlength="20" />
              </el-form-item>
            </el-col>
            <el-col :xs="24" :sm="12">
              <el-form-item label="邮箱" prop="email">
                <el-input v-model="profileForm.email" placeholder="请输入邮箱" maxlength="50" />
              </el-form-item>
            </el-col>
          </el-row>
          <div class="form-actions">
            <el-button type="primary" :loading="saving" @click="saveProfile">
              <el-icon><Check /></el-icon> 保存资料
            </el-button>
          </div>
        </el-form>
      </div>

      <!-- 专注模式 -->
      <div class="card">
        <div class="card-title">
          <el-icon><Timer /></el-icon> 专注模式
          <span class="card-sub">开启后30秒无操作将自动退出登录，帮助你保持专注；进入和退出均需密码</span>
        </div>
        <div class="focus-block">
          <div class="focus-status">
            <el-tag :type="focusMode ? 'danger' : 'info'" effect="dark" size="large">
              {{ focusMode ? '专注中' : '未开启' }}
            </el-tag>
            <span class="focus-desc">
              {{ focusMode ? '当前已开启专注模式，30秒无操作将自动退出登录' : '开启后进入专注状态，帮助你集中注意力办理事务' }}
            </span>
          </div>
          <div class="focus-actions">
            <el-button v-if="!focusMode" type="primary" @click="showEnterFocusDialog">
              <el-icon><Timer /></el-icon> 开启专注模式
            </el-button>
            <el-button v-else type="danger" @click="showExitFocusDialog">
              <el-icon><SwitchButton /></el-icon> 退出专注模式
            </el-button>
          </div>
        </div>
      </div>

      <!-- 我的钱包 -->
      <div class="card">
        <div class="card-title">
          <el-icon><Wallet /></el-icon> 我的钱包
          <span class="card-sub">用于二手交易的余额；演示环境支持自助充值，金额变更均有流水记录</span>
        </div>
        <div class="wallet-block">
          <div class="wallet-balance">
            <span class="balance-label">当前余额</span>
            <span class="balance-value">{{ formatMoney(balance) }}</span>
          </div>
          <div class="wallet-actions">
            <el-button type="primary" @click="rechargeVisible = true">
              <el-icon><Plus /></el-icon> 充值
            </el-button>
            <el-button plain @click="openTransactions">
              <el-icon><Tickets /></el-icon> 余额流水
            </el-button>
          </div>
        </div>
      </div>

      <!-- 校园平台绑定 -->
      <div class="card">
        <div class="card-title">
          <el-icon><Link /></el-icon> 校园平台绑定
          <span class="card-sub">绑定后可在登录页使用「学校账号登录」直接进入平台；凭证加密保存</span>
        </div>

        <!-- 教务系统绑定 -->
        <div class="bind-block">
          <div class="bind-head">
            <div class="bind-name">
              <el-icon><Notebook /></el-icon> 广西大学教学信息服务平台
            </div>
            <el-tag v-if="bindStatus.jwxt.bound" type="success" size="small">已绑定：{{ bindStatus.jwxt.account }}</el-tag>
            <el-tag v-else type="info" size="small">未绑定</el-tag>
          </div>
          <el-form label-width="70px" class="bind-form" @submit.prevent>
            <el-row :gutter="16">
              <el-col :xs="24" :sm="8">
                <el-form-item label="学号">
                  <el-input v-model="jwxtForm.username" placeholder="请输入教务系统学号" maxlength="30" />
                </el-form-item>
              </el-col>
              <el-col :xs="24" :sm="8">
                <el-form-item label="密码">
                  <el-input v-model="jwxtForm.password" type="password" show-password placeholder="请输入教务系统密码" @keyup.enter="doBindJwxt" />
                </el-form-item>
              </el-col>
              <el-col :xs="24" :sm="8" class="bind-btn-col">
                <el-button type="primary" :loading="jwxtBinding" @click="doBindJwxt">
                  <el-icon><Link /></el-icon> 绑定
                </el-button>
              </el-col>
            </el-row>
          </el-form>
        </div>
      </div>

      <!-- 修改密码 -->
      <div class="card">
        <div class="card-title">
          <el-icon><Lock /></el-icon> 修改密码
        </div>
        <el-form ref="pwdFormRef" :model="pwdForm" :rules="pwdRules" label-width="100px" class="profile-form pwd-form">
          <el-form-item label="原密码" prop="oldPassword">
            <el-input v-model="pwdForm.oldPassword" type="password" show-password placeholder="请输入当前使用的密码" />
          </el-form-item>
          <el-form-item label="新密码" prop="newPassword">
            <el-input v-model="pwdForm.newPassword" type="password" show-password placeholder="至少6位，建议包含字母和数字" maxlength="30" />
          </el-form-item>
          <el-form-item label="确认新密码" prop="confirmPassword">
            <el-input v-model="pwdForm.confirmPassword" type="password" show-password placeholder="请再次输入新密码" maxlength="30" @keyup.enter="changePassword" />
          </el-form-item>
          <div class="form-actions">
            <el-button type="danger" plain :loading="changingPwd" @click="changePassword">
              <el-icon><Key /></el-icon> 修改密码
            </el-button>
            <p class="tip">修改成功后需要重新登录</p>
          </div>
        </el-form>
      </div>
    </template>

    <!-- 充值 -->
    <el-dialog v-model="rechargeVisible" title="余额充值" width="380px">
      <p class="dialog-tip">演示环境：充值立即到账，不涉及真实支付。</p>
      <el-form label-width="72px">
        <el-form-item label="充值金额">
          <el-input-number v-model="rechargeAmount" :min="1" :max="10000" :step="10" style="width: 100%" />
        </el-form-item>
      </el-form>
      <div class="quick-amounts">
        <el-button v-for="amt in [20, 50, 100, 200]" :key="amt" size="small" plain @click="rechargeAmount = amt">
          ¥{{ amt }}
        </el-button>
      </div>
      <template #footer>
        <el-button @click="rechargeVisible = false">取消</el-button>
        <el-button type="primary" :loading="recharging" @click="doRecharge">确认充值</el-button>
      </template>
    </el-dialog>

    <!-- 余额流水 -->
    <el-dialog v-model="txVisible" title="余额流水" width="560px">
      <el-table :data="transactions" v-loading="txLoading" height="360" size="small">
        <el-table-column prop="createTime" label="时间" width="160">
          <template #default="{ row }">{{ formatDateTime(row.createTime) }}</template>
        </el-table-column>
        <el-table-column prop="type" label="类型" width="90">
          <template #default="{ row }">
            <el-tag size="small" :type="txTypeTag(row.type)" effect="plain">{{ txTypeLabel(row.type) }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="amount" label="变动" width="100" align="right">
          <template #default="{ row }">
            <span :class="Number(row.amount) >= 0 ? 'income' : 'expense'">
              {{ Number(row.amount) >= 0 ? '+' : '' }}{{ formatMoney(row.amount) }}
            </span>
          </template>
        </el-table-column>
        <el-table-column prop="balanceAfter" label="余额" width="100" align="right">
          <template #default="{ row }">{{ formatMoney(row.balanceAfter) }}</template>
        </el-table-column>
        <el-table-column prop="remark" label="说明" min-width="140" show-overflow-tooltip />
      </el-table>
      <el-empty v-if="!txLoading && transactions.length === 0" description="暂无流水" :image-size="70" />
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import {
  getUserInfo, updateProfile, updatePassword, logout as apiLogout,
  bindJwxt, getBindStatus, getWallet, recharge, getTransactions
} from '@/api'
import { useUserStore } from '@/store/user'
import { useFocusMode } from '@/composables/useFocusMode'
import { formatMoney } from '@/utils/community'
import { User, InfoFilled, Lock, Check, Key, Link, Notebook, Timer, SwitchButton, Wallet, Plus, Tickets } from '@element-plus/icons-vue'

const router = useRouter()
const userStore = useUserStore()

const { focusMode, showEnterFocusDialog, showExitFocusDialog } = useFocusMode()

const loading = ref(true)
const saving = ref(false)
const changingPwd = ref(false)

// ========== 钱包 ==========
const balance = ref(0)
const rechargeVisible = ref(false)
const recharging = ref(false)
const rechargeAmount = ref(50)
const txVisible = ref(false)
const txLoading = ref(false)
const transactions = ref([])

async function loadWallet() {
  try {
    const res = await getWallet()
    balance.value = Number(res?.data?.balance ?? 0)
    userStore.setBalance(balance.value)
  } catch {
    /* 拦截器已提示 */
  }
}

async function doRecharge() {
  const amount = Number(rechargeAmount.value)
  if (!amount || amount <= 0) {
    ElMessage.warning('请输入正确的充值金额')
    return
  }
  recharging.value = true
  try {
    const res = await recharge(amount)
    balance.value = Number(res?.data?.balance ?? balance.value)
    userStore.setBalance(balance.value)
    ElMessage.success(`充值成功，当前余额 ${formatMoney(balance.value)}`)
    rechargeVisible.value = false
  } catch (e) {
    ElMessage.error(e?.message || '充值失败，请稍后重试')
  } finally {
    recharging.value = false
  }
}

async function openTransactions() {
  txVisible.value = true
  txLoading.value = true
  try {
    const res = await getTransactions()
    transactions.value = res?.data || []
  } finally {
    txLoading.value = false
  }
}

function txTypeLabel(type) {
  return { RECHARGE: '充值', PAY: '支出', INCOME: '收入', ADMIN_ADJUST: '管理员调整' }[type] || type
}

function txTypeTag(type) {
  return { RECHARGE: 'success', PAY: 'danger', INCOME: 'primary', ADMIN_ADJUST: 'warning' }[type] || 'info'
}

function formatDateTime(v) {
  if (!v) return ''
  return String(v).replace('T', ' ').slice(0, 16)
}

const profileFormRef = ref()
const pwdFormRef = ref()

const profileForm = ref({
  username: '',
  realName: '',
  college: '',
  major: '',
  grade: '',
  phone: '',
  email: ''
})

const profileRules = {
  realName: [{ required: true, message: '请输入姓名', trigger: 'blur' }],
  phone: [{ pattern: /^1[3-9]\d{9}$/, message: '手机号格式不正确', trigger: 'blur' }],
  email: [{ type: 'email', message: '邮箱格式不正确', trigger: 'blur' }]
}

const pwdForm = ref({ oldPassword: '', newPassword: '', confirmPassword: '' })

// ========== 校园平台绑定 ==========
const bindStatus = ref({ jwxt: { bound: false, account: '' } })
const jwxtForm = ref({ username: '', password: '' })
const jwxtBinding = ref(false)

async function loadBindStatus() {
  try {
    const res = await getBindStatus()
    for (const item of res.data || []) {
      if (item.platform === 'JWXT') bindStatus.value.jwxt = item
    }
  } catch {
    // 请求层已统一提示
  }
}

// 展示绑定错误：业务错误用后端文案；HTTP 错误取后端返回；网络错误已由拦截器统一提示，不重复弹
function showBindError(e, fallback) {
  if (e?.isBiz) {
    ElMessage.error(e.message)
  } else if (e?.response) {
    ElMessage.error(e.response.data?.message || fallback)
  }
  // 网络错误：拦截器已提示，这里静默
}

async function doBindJwxt() {
  if (!jwxtForm.value.username || !jwxtForm.value.password) {
    ElMessage.warning('请输入教务系统的学号和密码')
    return
  }
  jwxtBinding.value = true
  try {
    await bindJwxt({ username: jwxtForm.value.username, password: jwxtForm.value.password })
    ElMessage.success('绑定成功')
    jwxtForm.value.password = ''
    await loadBindStatus()
  } catch (e) {
    showBindError(e, '绑定失败，请稍后再试')
  } finally {
    jwxtBinding.value = false
  }
}

const pwdRules = {
  oldPassword: [{ required: true, message: '请输入原密码', trigger: 'blur' }],
  newPassword: [
    { required: true, message: '请输入新密码', trigger: 'blur' },
    { min: 6, message: '新密码至少6位', trigger: 'blur' }
  ],
  confirmPassword: [
    { required: true, message: '请再次输入新密码', trigger: 'blur' },
    {
      validator: (rule, value, callback) => {
        if (value !== pwdForm.value.newPassword) callback(new Error('两次输入的密码不一致'))
        else callback()
      },
      trigger: 'blur'
    }
  ]
}

async function loadProfile() {
  try {
    const res = await getUserInfo()
    const u = res.data || {}
    profileForm.value = {
      username: u.username || '',
      realName: u.realName || '',
      college: u.college || '',
      major: u.major || '',
      grade: u.grade || '',
      phone: u.phone || '',
      email: u.email || ''
    }
  } catch {
    // 登录态失效等错误已由请求层统一提示
  } finally {
    loading.value = false
  }
}

async function saveProfile() {
  await profileFormRef.value.validate(async (valid) => {
    if (!valid) return
    saving.value = true
    try {
      const res = await updateProfile({
        realName: profileForm.value.realName,
        college: profileForm.value.college,
        major: profileForm.value.major,
        grade: profileForm.value.grade,
        phone: profileForm.value.phone,
        email: profileForm.value.email
      })
      // 同步更新本地登录信息中的显示名和学院
      if (userStore.userInfo) {
        userStore.userInfo.realName = res.data?.realName || profileForm.value.realName
        userStore.userInfo.college = res.data?.college || profileForm.value.college
        localStorage.setItem('userInfo', JSON.stringify(userStore.userInfo))
      }
      ElMessage.success(res.message || '资料已更新')
    } catch (e) {
      ElMessage.error(e?.response?.data?.message || e.message || '保存失败，请稍后重试')
    } finally {
      saving.value = false
    }
  })
}

async function changePassword() {
  await pwdFormRef.value.validate(async (valid) => {
    if (!valid) return
    try {
      await ElMessageBox.confirm('修改密码后需要重新登录，确定继续吗？', '提示', {
        confirmButtonText: '确定修改',
        cancelButtonText: '再想想',
        type: 'warning'
      })
    } catch {
      return
    }
    changingPwd.value = true
    try {
      const res = await updatePassword({
        oldPassword: pwdForm.value.oldPassword,
        newPassword: pwdForm.value.newPassword
      })
      ElMessage.success(res.message || '密码已修改')
      // 密码修改成功后退出登录，回到登录页用新密码登录
      try { await apiLogout() } catch {}
      userStore.logout()
      router.push('/login')
    } catch (e) {
      ElMessage.error(e?.response?.data?.message || e.message || '修改失败，请稍后重试')
      changingPwd.value = false
    }
  })
}

onMounted(() => {
  loadProfile()
  loadBindStatus()
  loadWallet()
})
</script>

<style scoped>
.profile-page {
  max-width: 860px;
  margin: 0 auto;
}

.page-header {
  margin-bottom: 20px;
}

.page-header h2 {
  display: flex;
  align-items: center;
  gap: 8px;
  margin: 0 0 6px 0;
  font-size: 22px;
  color: #303133;
}

.page-header p {
  margin: 0;
  color: #909399;
  font-size: 14px;
}

.loading-wrap {
  min-height: 300px;
}

.card {
  background: #fff;
  border-radius: 12px;
  box-shadow: 0 2px 12px rgba(0, 0, 0, 0.04);
  padding: 24px;
  margin-bottom: 20px;
}

.card-title {
  display: flex;
  align-items: center;
  gap: 8px;
  font-size: 16px;
  font-weight: 600;
  color: #303133;
  margin-bottom: 20px;
  padding-bottom: 12px;
  border-bottom: 1px solid #f0f2f5;
}

.profile-form :deep(.el-form-item) {
  margin-bottom: 20px;
}

.form-actions {
  margin-top: 8px;
}

.form-actions .el-button .el-icon {
  margin-right: 6px;
}

/* ========== 校园平台绑定 ========== */
.card-sub {
  font-size: 13px;
  font-weight: 400;
  color: #909399;
  margin-left: 8px;
}

.bind-block {
  padding: 16px;
  border: 1px solid #ebeef5;
  border-radius: 10px;
  margin-bottom: 16px;
  background: #fafbfc;
}

.bind-block:last-child {
  margin-bottom: 0;
}

.bind-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 14px;
}

.bind-name {
  display: flex;
  align-items: center;
  gap: 6px;
  font-size: 15px;
  font-weight: 600;
  color: #303133;
}

.bind-form {
  max-width: none;
}

.bind-btn-col {
  display: flex;
  align-items: flex-start;
}

.tip {
  margin: 10px 0 0 0;
  font-size: 13px;
  color: #909399;
}

/* ========== 专注模式 ========== */
.focus-block {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 16px;
  border: 1px solid #ebeef5;
  border-radius: 10px;
  background: #fafbfc;
}

.focus-status {
  display: flex;
  align-items: center;
  gap: 12px;
}

.focus-desc {
  font-size: 13px;
  color: #606266;
}

/* ========== 钱包 ========== */
.wallet-block {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 18px 20px;
  border-radius: 10px;
  background: linear-gradient(135deg, #1a1a2e 0%, #16213e 55%, #0f3460 100%);
  color: #fff;
  flex-wrap: wrap;
  gap: 14px;
}

.balance-label {
  display: block;
  font-size: 13px;
  opacity: 0.75;
}

.balance-value {
  display: block;
  margin-top: 4px;
  font-size: 30px;
  font-weight: 700;
  letter-spacing: 1px;
}

.wallet-actions {
  display: flex;
  gap: 10px;
}

.dialog-tip {
  margin: 0 0 14px;
  font-size: 13px;
  color: #909399;
}

.quick-amounts {
  display: flex;
  gap: 8px;
  padding-left: 72px;
  margin-top: -6px;
}

.income {
  color: #f56c6c;
  font-weight: 600;
}

.expense {
  color: #67c23a;
  font-weight: 600;
}

@media (max-width: 768px) {
  .card {
    padding: 16px;
  }
  .profile-form :deep(.el-form-item__label) {
    width: 80px !important;
  }
}
</style>
