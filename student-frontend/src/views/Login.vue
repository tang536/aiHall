<template>
  <div class="login-page">
    <div class="login-bg"></div>
    <div class="login-container">
      <div class="login-left">
        <div class="brand">
          <div class="brand-logo"><img src="/gxu-icon.png" alt="广西大学" /></div>
          <h1>AI学生事务智能办事大厅</h1>
          <p>面向高校学生的一站式智能办事平台</p>
        </div>
        <div class="features">
          <div class="feature-item">
            <el-icon><ChatDotRound /></el-icon>
            <span>AI智能问答，秒级响应</span>
          </div>
          <div class="feature-item">
            <el-icon><Document /></el-icon>
            <span>在线事项办理，进度透明</span>
          </div>
          <div class="feature-item">
            <el-icon><Location /></el-icon>
            <span>校园地图导航，精准定位</span>
          </div>
        </div>
      </div>
      <div class="login-right">
        <div class="login-card">
          <!-- 登录表单 -->
          <template v-if="activeTab === 'login'">
            <h2>欢迎登录</h2>
            <p class="login-subtitle">请使用学号/工号登录系统</p>
            <el-form ref="formRef" :model="form" :rules="rules" @keyup.enter="handleLogin">
              <el-form-item prop="username">
                <el-input v-model="form.username" placeholder="学号 / 工号" size="large" :prefix-icon="User" />
              </el-form-item>
              <el-form-item prop="password">
                <el-input v-model="form.password" type="password" placeholder="密码" size="large" :prefix-icon="Lock" show-password />
              </el-form-item>
              <el-form-item prop="captchaCode">
                <div class="captcha-row">
                  <el-input v-model="form.captchaCode" placeholder="验证码" size="large" :prefix-icon="Key" maxlength="4" class="captcha-input" />
                  <img :src="captchaImage" class="captcha-img" @click="refreshCaptcha" title="点击刷新验证码" />
                </div>
              </el-form-item>
              <el-button type="primary" size="large" class="login-btn" :loading="loading" @click="handleLogin">
                登 录
              </el-button>
            </el-form>
            <p class="switch-hint">
              没有账号？<span class="link" @click="switchTab('register')">立即注册</span>
              <span class="switch-divider">|</span>
              <span class="link" @click="switchTab('school')">学校账号登录</span>
            </p>
          </template>

          <!-- 学校账号登录（绑定教务系统后可用教务学号+密码登录） -->
          <template v-else-if="activeTab === 'school'">
            <h2>学校账号登录</h2>
            <p class="login-subtitle">使用已绑定教务系统的学号与密码登录</p>
            <el-alert
              type="info"
              :closable="false"
              show-icon
              class="device-alert"
              title="需先在平台绑定教务系统"
              description="请先用平台账号登录，进入「个人资料 → 校园平台绑定」完成教务系统绑定后，即可在此直接使用学校账号登录。"
            />
            <el-form ref="schoolFormRef" :model="schoolForm" :rules="rules" @keyup.enter="handleSchoolLogin">
              <el-form-item prop="username">
                <el-input v-model="schoolForm.username" placeholder="教务系统学号" size="large" :prefix-icon="User" />
              </el-form-item>
              <el-form-item prop="password">
                <el-input v-model="schoolForm.password" type="password" placeholder="教务系统密码" size="large" :prefix-icon="Lock" show-password />
              </el-form-item>
              <el-form-item prop="captchaCode">
                <div class="captcha-row">
                  <el-input v-model="schoolForm.captchaCode" placeholder="验证码" size="large" :prefix-icon="Key" maxlength="4" class="captcha-input" />
                  <img :src="captchaImage" class="captcha-img" @click="refreshCaptcha" title="点击刷新验证码" />
                </div>
              </el-form-item>
              <el-button type="primary" size="large" class="login-btn" :loading="schoolLoading" @click="handleSchoolLogin">
                学校账号登录
              </el-button>
            </el-form>
            <p class="switch-hint">
              返回 <span class="link" @click="switchTab('login')">平台账号登录</span>
            </p>
          </template>

          <!-- 注册表单 -->
          <template v-else>
            <h2>学生注册</h2>
            <p class="login-subtitle">请使用学号完成注册</p>
            <el-alert
              v-if="deviceRegistered"
              type="warning"
              :closable="false"
              show-icon
              class="device-alert"
              title="该设备已完成注册"
              description="同一台设备不可重复注册，请直接登录"
            />
            <el-form ref="registerFormRef" :model="registerForm" :rules="registerRules" @keyup.enter="handleRegister">
              <el-form-item prop="username">
                <el-input v-model="registerForm.username" placeholder="学号" size="large" :prefix-icon="User" />
              </el-form-item>
              <el-form-item prop="password">
                <el-input v-model="registerForm.password" type="password" placeholder="设置密码（至少6位）" size="large" :prefix-icon="Lock" show-password />
              </el-form-item>
              <el-form-item prop="confirmPassword">
                <el-input v-model="registerForm.confirmPassword" type="password" placeholder="确认密码" size="large" :prefix-icon="Lock" show-password />
              </el-form-item>
              <el-form-item prop="captchaCode">
                <div class="captcha-row">
                  <el-input v-model="registerForm.captchaCode" placeholder="验证码" size="large" :prefix-icon="Key" maxlength="4" class="captcha-input" />
                  <img :src="captchaImage" class="captcha-img" @click="refreshCaptcha" title="点击刷新验证码" />
                </div>
              </el-form-item>
              <el-button type="primary" size="large" class="login-btn" :loading="registerLoading" :disabled="deviceRegistered" @click="handleRegister">
                注 册
              </el-button>
            </el-form>
            <p class="switch-hint">
              已有账号？<span class="link" @click="switchTab('login')">去登录</span>
            </p>
          </template>

          <div class="login-footer">
            <el-button text @click="$router.push('/home')">
              <el-icon><ArrowLeft /></el-icon> 返回首页
            </el-button>
          </div>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup>
import { ref, reactive, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { User, Lock, Key } from '@element-plus/icons-vue'
import { login, register, getCaptcha, loginWithSchool } from '@/api'
import { useUserStore } from '@/store/user'

const router = useRouter()
const userStore = useUserStore()
const formRef = ref()
const registerFormRef = ref()
const schoolFormRef = ref()
const loading = ref(false)
const registerLoading = ref(false)
const schoolLoading = ref(false)
const captchaImage = ref('')
const activeTab = ref('login')

const form = reactive({
  username: '',
  password: '',
  captchaId: '',
  captchaCode: ''
})

const schoolForm = reactive({
  username: '',
  password: '',
  captchaId: '',
  captchaCode: ''
})

const registerForm = reactive({
  username: '',
  password: '',
  confirmPassword: '',
  captchaId: '',
  captchaCode: ''
})

// 同一台设备不可重复注册（本地标记）
const DEVICE_REGISTERED_KEY = 'ai_hall_device_registered'
const deviceRegistered = ref(localStorage.getItem(DEVICE_REGISTERED_KEY) === '1')

const rules = {
  username: [{ required: true, message: '请输入学号/工号', trigger: 'blur' }],
  password: [{ required: true, message: '请输入密码', trigger: 'blur' }],
  captchaCode: [{ required: true, message: '请输入验证码', trigger: 'blur' }]
}

const registerRules = {
  username: [
    { required: true, message: '请输入学号', trigger: 'blur' },
    { pattern: /^[0-9a-zA-Z]+$/, message: '学号只能包含数字和字母', trigger: 'blur' }
  ],
  password: [
    { required: true, message: '请设置密码', trigger: 'blur' },
    { min: 6, message: '密码至少6位', trigger: 'blur' }
  ],
  confirmPassword: [
    { required: true, message: '请再次输入密码', trigger: 'blur' },
    {
      validator: (rule, value, callback) => {
        if (value !== registerForm.password) {
          callback(new Error('两次输入的密码不一致'))
        } else {
          callback()
        }
      },
      trigger: 'blur'
    }
  ],
  captchaCode: [{ required: true, message: '请输入验证码', trigger: 'blur' }]
}

function switchTab(tab) {
  activeTab.value = tab
  if (tab === 'register' && deviceRegistered.value) {
    ElMessage.warning('该设备已完成注册，同一台设备不可重复注册')
  }
  refreshCaptcha()
}

async function refreshCaptcha() {
  try {
    const res = await getCaptcha()
    const captchaId = res.data.captchaId
    form.captchaId = captchaId
    registerForm.captchaId = captchaId
    schoolForm.captchaId = captchaId
    captchaImage.value = res.data.image
  } catch {
    // 验证码加载失败时不阻塞
  }
}

/** 登录成功后的统一跳转：学生端应用内不区分管理员路由 */
function afterLogin(data) {
  if (data.role === 'ADMIN') {
    ElMessage.success('管理员账号登录成功，请使用管理后台查看管理功能')
    router.push('/home')
  } else {
    router.push('/home')
  }
}

const handleLogin = async () => {
  try {
    await formRef.value.validate()
    loading.value = true
    const res = await login(form)
    userStore.setLogin(res.data)
    ElMessage.success(`登录成功，欢迎回来，${res.data.realName}！`)
    afterLogin(res.data)
  } catch (e) {
    // 登录失败显示错误提示并刷新验证码
    const msg = e.response?.data?.message || e.message || '登录失败，请检查账号密码'
    ElMessage.error(msg && msg !== 'Failed to fetch' ? msg : '登录失败，请稍后重试')
    refreshCaptcha()
    form.captchaCode = ''
  } finally {
    loading.value = false
  }
}

const handleSchoolLogin = async () => {
  try {
    await schoolFormRef.value.validate()
    schoolLoading.value = true
    const res = await loginWithSchool(schoolForm)
    userStore.setLogin(res.data)
    ElMessage.success(`登录成功，欢迎回来，${res.data.realName}！`)
    afterLogin(res.data)
  } catch (e) {
    const msg = e.response?.data?.message || e.message || '登录失败，请检查学校账号密码'
    ElMessage.error(msg && msg !== 'Failed to fetch' ? msg : '登录失败，请稍后重试')
    refreshCaptcha()
    schoolForm.captchaCode = ''
  } finally {
    schoolLoading.value = false
  }
}

const handleRegister = async () => {
  if (deviceRegistered.value) {
    ElMessage.warning('该设备已完成注册，同一台设备不可重复注册')
    return
  }
  try {
    await registerFormRef.value.validate()
    registerLoading.value = true
    // 提交时去掉 confirmPassword，只传后端需要的字段
    const { confirmPassword, ...submitData } = registerForm
    const res = await register(submitData)
    // 注册成功后标记本设备，同一设备不可再次注册
    localStorage.setItem(DEVICE_REGISTERED_KEY, '1')
    deviceRegistered.value = true
    userStore.setLogin(res.data)
    ElMessage.success('注册成功，已自动登录！')
    router.push('/home')
  } catch (e) {
    // 注册失败显示错误提示并刷新验证码
    const msg = e.response?.data?.message || e.message || '注册失败，请稍后重试'
    ElMessage.error(msg && msg !== 'Failed to fetch' ? msg : '注册失败，请稍后重试')
    refreshCaptcha()
    registerForm.captchaCode = ''
  } finally {
    registerLoading.value = false
  }
}

onMounted(() => {
  refreshCaptcha()
})
</script>

<style scoped>
.login-page {
  min-height: 100vh;
  display: flex;
  align-items: center;
  justify-content: center;
  position: relative;
  overflow: hidden;
  background: #f7f3ec;
}

.login-bg {
  position: absolute;
  inset: 0;
  background:
    radial-gradient(ellipse at 20% 30%, rgba(148, 30, 35, 0.08) 0%, transparent 50%),
    radial-gradient(ellipse at 80% 70%, rgba(184, 147, 90, 0.1) 0%, transparent 50%),
    linear-gradient(135deg, #f7f3ec 0%, #f0e9dc 100%);
}

.login-bg::before {
  content: '';
  position: absolute;
  top: 0;
  left: 0;
  right: 0;
  height: 5px;
  background: linear-gradient(90deg, #941e23, #b8935a, #941e23);
}

.login-bg::after {
  content: '';
  position: absolute;
  bottom: -15%;
  right: -10%;
  width: 500px;
  height: 500px;
  background: radial-gradient(circle, rgba(148, 30, 35, 0.06) 0%, transparent 70%);
  border-radius: 50%;
}

.login-container {
  display: flex;
  width: 920px;
  max-width: 95vw;
  background: #fffdf9;
  border-radius: 16px;
  overflow: hidden;
  box-shadow: 0 20px 60px rgba(148, 30, 35, 0.12), 0 4px 16px rgba(0, 0, 0, 0.06);
  position: relative;
  z-index: 1;
  border: 1px solid #e8dfd0;
}

.login-left {
  flex: 1;
  background: linear-gradient(160deg, #941e23 0%, #761317 60%, #5c0e12 100%);
  color: #fff;
  padding: 48px 40px;
  display: flex;
  flex-direction: column;
  justify-content: center;
  position: relative;
  overflow: hidden;
}

.login-left::before {
  content: '';
  position: absolute;
  top: -30%;
  right: -20%;
  width: 300px;
  height: 300px;
  border: 1px solid rgba(184, 147, 90, 0.2);
  border-radius: 50%;
}

.login-left::after {
  content: '';
  position: absolute;
  bottom: -20%;
  left: -10%;
  width: 200px;
  height: 200px;
  border: 1px solid rgba(184, 147, 90, 0.15);
  border-radius: 50%;
}

.brand-logo {
  width: 76px;
  height: 76px;
  border-radius: 50%;
  display: flex;
  align-items: center;
  justify-content: center;
  margin-bottom: 24px;
  overflow: hidden;
  box-shadow: 0 4px 16px rgba(0, 0, 0, 0.2);
  position: relative;
  z-index: 1;
}
.brand-logo img {
  width: 100%;
  height: 100%;
  object-fit: cover;
  display: block;
}

.brand h1 {
  font-size: 24px;
  font-weight: 700;
  margin-bottom: 10px;
  font-family: 'Noto Serif SC', 'SimSun', serif;
  letter-spacing: 1px;
  position: relative;
  z-index: 1;
}

.brand p {
  font-size: 14px;
  color: rgba(255, 253, 249, 0.65);
  margin-bottom: 40px;
  position: relative;
  z-index: 1;
}

.features {
  display: flex;
  flex-direction: column;
  gap: 16px;
  position: relative;
  z-index: 1;
}

.feature-item {
  display: flex;
  align-items: center;
  gap: 12px;
  font-size: 14px;
  color: rgba(255, 253, 249, 0.85);
}

.feature-item .el-icon {
  color: #b8935a;
  font-size: 18px;
}

.login-right {
  flex: 1;
  padding: 48px 40px;
  display: flex;
  align-items: center;
}

.login-card {
  width: 100%;
}

.login-card h2 {
  font-size: 26px;
  font-weight: 700;
  color: #20201f;
  margin-bottom: 8px;
  font-family: 'Noto Serif SC', 'SimSun', serif;
}

.login-subtitle {
  font-size: 14px;
  color: #6f6d68;
  margin-bottom: 28px;
}

.login-btn {
  width: 100%;
  margin-top: 8px;
  font-size: 16px;
  font-weight: 600;
  height: 44px;
  background: linear-gradient(135deg, #941e23, #761317);
  border: none;
  letter-spacing: 4px;
}

.login-btn:hover {
  background: linear-gradient(135deg, #a8282d, #8a181d);
}

.captcha-row {
  display: flex;
  gap: 10px;
  align-items: center;
  width: 100%;
}

.captcha-input {
  flex: 1;
}

.captcha-img {
  width: 110px;
  height: 40px;
  border-radius: 6px;
  cursor: pointer;
  border: 1px solid #ddd7cd;
  object-fit: cover;
  flex-shrink: 0;
  transition: opacity 0.2s;
}

.captcha-img:hover {
  opacity: 0.8;
}

.device-alert {
  margin-bottom: 16px;
}

.login-footer {
  margin-top: 20px;
  text-align: center;
}

.switch-hint {
  text-align: center;
  margin-top: 16px;
  font-size: 14px;
  color: #6f6d68;
}

.switch-hint .link {
  color: #941e23;
  cursor: pointer;
  font-weight: 500;
}

.switch-hint .link:hover {
  text-decoration: underline;
}

.switch-divider {
  margin: 0 8px;
  color: #ddd7cd;
}

/* ========== 移动端响应式 ========== */
@media (max-width: 768px) {
  .login-container {
    width: 95vw;
    max-width: 420px;
    flex-direction: column;
  }
  .login-left {
    display: none;
  }
  .login-right {
    padding: 32px 24px;
  }
  .login-card h2 {
    font-size: 22px;
  }
  .login-subtitle {
    font-size: 13px;
    margin-bottom: 24px;
  }
  .login-btn {
    font-size: 15px;
    height: 42px;
  }
  .captcha-img {
    width: 100px;
    height: 38px;
  }
}

@media (max-width: 480px) {
  .login-right {
    padding: 24px 16px;
  }
  .login-card h2 {
    font-size: 20px;
  }
  .captcha-row {
    gap: 8px;
  }
  .captcha-img {
    width: 90px;
  }
}
</style>
