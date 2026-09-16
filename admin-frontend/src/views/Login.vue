<template>
  <div class="login-page">
    <div class="login-bg"></div>
    <div class="login-container">
      <div class="login-card">
        <div class="brand">
          <div class="brand-logo"><img src="/gxu-icon.png" alt="广西大学" /></div>
          <h1>AI学生事务智能办事大厅</h1>
          <p>管理员后台管理系统</p>
        </div>
        <h2>管理员登录</h2>
        <el-form ref="formRef" :model="form" :rules="rules" @keyup.enter="handleLogin">
          <el-form-item prop="username">
            <el-input v-model="form.username" placeholder="管理员账号" size="large" :prefix-icon="User" />
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
      </div>
    </div>
  </div>
</template>

<script setup>
import { ref, reactive, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { User, Lock, Key, Cpu } from '@element-plus/icons-vue'
import { login, getCaptcha } from '@/api'
import { useUserStore } from '@/store/user'

const router = useRouter()
const userStore = useUserStore()
const formRef = ref()
const loading = ref(false)
const captchaImage = ref('')

const form = reactive({
  username: '',
  password: '',
  captchaId: '',
  captchaCode: ''
})

const rules = {
  username: [{ required: true, message: '请输入管理员账号', trigger: 'blur' }],
  password: [{ required: true, message: '请输入密码', trigger: 'blur' }],
  captchaCode: [{ required: true, message: '请输入验证码', trigger: 'blur' }]
}

async function refreshCaptcha() {
  try {
    const res = await getCaptcha()
    form.captchaId = res.data.captchaId
    captchaImage.value = res.data.image
  } catch {
    // 验证码加载失败时不阻塞登录
  }
}

const handleLogin = async () => {
  try {
    await formRef.value.validate()
    loading.value = true
    const res = await login(form)
    if (res.data.role !== 'ADMIN') {
      ElMessage.error('该账号不是管理员账号')
      refreshCaptcha()
      form.captchaCode = ''
      return
    }
    userStore.setLogin(res.data)
    ElMessage.success(`欢迎回来，${res.data.realName}！`)
    router.push('/admin/dashboard')
  } catch (e) {
    refreshCaptcha()
    form.captchaCode = ''
  } finally {
    loading.value = false
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
    radial-gradient(ellipse at 30% 20%, rgba(148, 30, 35, 0.1) 0%, transparent 50%),
    radial-gradient(ellipse at 70% 80%, rgba(184, 147, 90, 0.12) 0%, transparent 50%),
    linear-gradient(160deg, #761317 0%, #941e23 40%, #5c0e12 100%);
}

.login-bg::before {
  content: '';
  position: absolute;
  top: 0;
  left: 0;
  right: 0;
  height: 5px;
  background: linear-gradient(90deg, #b8935a, #d4b87a, #b8935a);
}

.login-bg::after {
  content: '';
  position: absolute;
  top: 50%;
  left: 50%;
  transform: translate(-50%, -50%);
  width: 600px;
  height: 600px;
  border: 1px solid rgba(184, 147, 90, 0.12);
  border-radius: 50%;
}

.login-container {
  position: relative;
  z-index: 1;
  width: 440px;
  max-width: 95vw;
}

.login-card {
  background: #fffdf9;
  border-radius: 16px;
  padding: 44px 40px;
  box-shadow: 0 24px 60px rgba(0, 0, 0, 0.25);
  border: 1px solid rgba(184, 147, 90, 0.3);
  position: relative;
}

.login-card::before {
  content: '';
  position: absolute;
  top: 0;
  left: 50%;
  transform: translateX(-50%);
  width: 60px;
  height: 3px;
  background: linear-gradient(90deg, #941e23, #b8935a);
  border-radius: 0 0 3px 3px;
}

.brand {
  text-align: center;
  margin-bottom: 28px;
}

.brand-logo {
  width: 72px;
  height: 72px;
  border-radius: 50%;
  display: flex;
  align-items: center;
  justify-content: center;
  margin: 0 auto 16px;
  overflow: hidden;
  box-shadow: 0 4px 16px rgba(148, 30, 35, 0.3);
}
.brand-logo img {
  width: 100%;
  height: 100%;
  object-fit: cover;
  display: block;
}

.brand h1 {
  font-size: 20px;
  font-weight: 700;
  color: #20201f;
  margin-bottom: 6px;
  font-family: 'Noto Serif SC', 'SimSun', serif;
  letter-spacing: 1px;
}

.brand p {
  font-size: 13px;
  color: #6f6d68;
}

.login-card h2 {
  font-size: 22px;
  font-weight: 700;
  color: #941e23;
  margin-bottom: 24px;
  text-align: center;
  font-family: 'Noto Serif SC', 'SimSun', serif;
  letter-spacing: 2px;
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
}

@media (max-width: 480px) {
  .login-card {
    padding: 32px 24px;
  }
  .brand h1 {
    font-size: 18px;
  }
}
</style>
