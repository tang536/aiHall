<template>
  <div class="app-container">
    <!-- 全局加载进度条 -->
    <div v-if="routeLoading" class="global-loading-bar" :style="{ width: loadingProgress + '%' }"></div>

    <!-- 顶部导航栏 -->
    <header class="app-header" v-if="!isLoginPage">
      <div class="header-left" @click="$router.push('/home')">
        <div class="logo"><img src="/gxu-icon.png" alt="广西大学" /></div>
        <div class="title-group">
          <h1 class="main-title">AI学生事务智能办事大厅</h1>
          <p class="sub-title">广西大学 · 计算机与电子信息学院</p>
        </div>
        <el-tag v-if="schoolMode" type="warning" effect="dark" class="school-mode-badge">
          <el-icon><School /></el-icon> 学校模式
        </el-tag>
        <el-tag v-if="focusMode" type="danger" effect="dark" class="focus-badge">
          <el-icon><Timer /></el-icon> 专注中
        </el-tag>
      </div>
      <!-- 桌面端导航 -->
      <div class="header-right desktop-nav">
        <el-button text @click="$router.push('/chat')">
          <el-icon><ChatDotRound /></el-icon> AI助手
        </el-button>
        <el-button text @click="$router.push('/market')">
          <el-icon><ShoppingBag /></el-icon> 二手交易
        </el-button>
        <el-button text @click="$router.push('/posts')">
          <el-icon><ChatLineSquare /></el-icon> 校园论坛
        </el-button>
        <el-badge :value="chat.unread" :hidden="!chat.unread" :max="99" class="nav-badge">
          <el-button text @click="$router.push('/messages')">
            <el-icon><Message /></el-icon> 消息
          </el-button>
        </el-badge>
        <el-button text @click="$router.push('/notifications')">
          <el-icon><Bell /></el-icon> 通知
        </el-button>
        <template v-if="userStore.isLoggedIn">
          <el-dropdown @command="handleCommand">
            <span class="user-info">
              <el-avatar :size="32" class="avatar">
                {{ userStore.userName.charAt(0) }}
              </el-avatar>
              <span class="username">{{ userStore.userName }}</span>
              <el-icon><ArrowDown /></el-icon>
            </span>
            <template #dropdown>
              <el-dropdown-menu>
                <el-dropdown-item command="profile" v-if="!userStore.isAdmin">
                  <el-icon><User /></el-icon> 个人中心
                </el-dropdown-item>
                <el-dropdown-item command="friends" v-if="!userStore.isAdmin">
                  <el-icon><UserFilled /></el-icon> 我的好友
                </el-dropdown-item>
                <el-dropdown-item command="messages" v-if="!userStore.isAdmin">
                  <el-icon><Message /></el-icon> 我的消息
                </el-dropdown-item>
                <el-dropdown-item command="myRepair" v-if="!userStore.isAdmin">
                  <el-icon><Tools /></el-icon> 我的报修
                </el-dropdown-item>
                <el-dropdown-item command="myApp" v-if="!userStore.isAdmin">
                  <el-icon><Document /></el-icon> 我的申请
                </el-dropdown-item>
                <el-dropdown-item command="feedback" v-if="!userStore.isAdmin">
                  <el-icon><EditPen /></el-icon> 意见反馈
                </el-dropdown-item>
                <el-dropdown-item divided command="logout">
                  <el-icon><SwitchButton /></el-icon> 退出登录
                </el-dropdown-item>
              </el-dropdown-menu>
            </template>
          </el-dropdown>
        </template>
        <template v-else>
          <el-button type="primary" @click="$router.push('/login')">
            <el-icon><User /></el-icon> 登录
          </el-button>
        </template>
      </div>
      <!-- 移动端汉堡按钮 -->
      <div class="mobile-menu-btn" @click="mobileMenuVisible = true">
        <el-icon :size="24"><Menu /></el-icon>
      </div>
    </header>

    <!-- 移动端抽屉菜单 -->
    <el-drawer v-model="mobileMenuVisible" title="导航菜单" direction="rtl" size="280px">
      <div class="mobile-nav">
        <div class="mobile-nav-item" @click="navigateTo('/chat')">
          <el-icon><ChatDotRound /></el-icon> AI助手
        </div>
        <div class="mobile-nav-item" @click="navigateTo('/market')">
          <el-icon><ShoppingBag /></el-icon> 二手交易
        </div>
        <div class="mobile-nav-item" @click="navigateTo('/posts')">
          <el-icon><ChatLineSquare /></el-icon> 校园论坛
        </div>
        <div class="mobile-nav-item" @click="navigateTo('/notifications')">
          <el-icon><Bell /></el-icon> 通知
        </div>
        <el-divider />
        <template v-if="userStore.isLoggedIn">
          <div class="mobile-user-info">
            <el-avatar :size="40" class="avatar">{{ userStore.userName.charAt(0) }}</el-avatar>
            <span class="mobile-username">{{ userStore.userName }}</span>
          </div>
          <el-divider />
          <div class="mobile-nav-item" v-if="!userStore.isAdmin" @click="navigateTo('/messages')">
            <el-icon><Message /></el-icon> 我的消息
            <el-badge v-if="chat.unread" :value="chat.unread" :max="99" class="mobile-badge" />
          </div>
          <div class="mobile-nav-item" v-if="!userStore.isAdmin" @click="navigateTo('/friends')">
            <el-icon><UserFilled /></el-icon> 我的好友
          </div>
          <div class="mobile-nav-item" v-if="!userStore.isAdmin" @click="navigateTo('/profile')">
            <el-icon><User /></el-icon> 个人中心
          </div>
          <div class="mobile-nav-item" v-if="!userStore.isAdmin" @click="navigateTo('/repair')">
            <el-icon><Tools /></el-icon> 我的报修
          </div>
          <div class="mobile-nav-item" v-if="!userStore.isAdmin" @click="navigateTo('/applications')">
            <el-icon><Document /></el-icon> 我的申请
          </div>
          <div class="mobile-nav-item" v-if="!userStore.isAdmin" @click="navigateTo('/feedback')">
            <el-icon><EditPen /></el-icon> 意见反馈
          </div>
          <el-divider />
          <div class="mobile-nav-item logout" @click="handleCommand('logout')">
            <el-icon><SwitchButton /></el-icon> 退出登录
          </div>
        </template>
        <template v-else>
          <el-button type="primary" style="width:100%" @click="navigateTo('/login')">
            <el-icon><User /></el-icon> 登录
          </el-button>
        </template>
      </div>
    </el-drawer>

    <!-- 主内容区 -->
    <main class="app-main" :class="{ 'full-page': isLoginPage }">
      <router-view v-slot="{ Component, route: routeInfo }">
        <transition name="fade" mode="out-in">
          <ErrorBoundary :key="routeInfo.fullPath">
            <component :is="Component" />
          </ErrorBoundary>
        </transition>
      </router-view>
    </main>

    <!-- 底部 -->
    <footer class="app-footer" v-if="!isLoginPage">
      <p>© 2026 广西大学计算机与信息技术学院 · AI学生事务智能办事大厅 · 第18组</p>
    </footer>

    <!-- 设置专注模式密码 -->
    <el-dialog v-model="setPwdVisible" title="设置专注模式密码" width="400px" :close-on-click-modal="false">
      <el-form label-width="80px">
        <el-form-item label="密码">
          <el-input v-model="setPwdForm.password" type="password" show-password placeholder="请输入密码" />
        </el-form-item>
        <el-form-item label="确认密码">
          <el-input v-model="setPwdForm.confirm" type="password" show-password placeholder="请再次输入密码" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="setPwdVisible = false">取消</el-button>
        <el-button type="primary" @click="confirmSetPwd">确认设置</el-button>
      </template>
    </el-dialog>

    <!-- 输入专注模式密码（进入/退出共用） -->
    <el-dialog v-model="enterPwdVisible" :title="pwdDialogTitle" width="380px" :close-on-click-modal="false">
      <el-form label-width="80px">
        <el-form-item label="密码">
          <el-input v-model="enterPwd" type="password" show-password placeholder="请输入专注模式密码" @keyup.enter="confirmEnterPwd" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="enterPwdVisible = false">取消</el-button>
        <el-button type="primary" @click="confirmEnterPwd">确认</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, computed, onMounted, onUnmounted, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { useUserStore } from '@/store/user'
import { useChatStore } from '@/store/chat'
import { ElMessageBox, ElMessage } from 'element-plus'
import { logout as apiLogout } from '@/api'
import { useFocusMode } from '@/composables/useFocusMode'
import { connect as connectChat, disconnect as disconnectChat } from '@/composables/useChatSocket'
import { School } from '@element-plus/icons-vue'
import ErrorBoundary from '@/components/ErrorBoundary.vue'

const route = useRoute()
const router = useRouter()
const userStore = useUserStore()
const chat = useChatStore()

const {
  focusMode,
  setPwdVisible,
  enterPwdVisible,
  setPwdForm,
  enterPwd,
  pwdDialogTitle,
  confirmSetPwd,
  confirmEnterPwd,
  forceEnterFocusMode,
  forceExitFocusMode,
  startAutoLogout,
  stopAutoLogout,
} = useFocusMode()

const isLoginPage = computed(() => route.path === '/login')

// 学校模式：URL参数 ?mode=school
const schoolMode = computed(() => {
  const params = new URLSearchParams(window.location.search)
  return params.get('mode') === 'school'
})

// 移动端菜单
const mobileMenuVisible = ref(false)

function navigateTo(path) {
  mobileMenuVisible.value = false
  router.push(path)
}

// 路由切换加载进度条
const routeLoading = ref(false)
const loadingProgress = ref(0)
let loadingTimer = null

function startLoading() {
  routeLoading.value = true
  loadingProgress.value = 10
  loadingTimer = setInterval(() => {
    if (loadingProgress.value < 85) {
      loadingProgress.value += Math.random() * 15
    }
  }, 200)
}

function finishLoading() {
  clearInterval(loadingTimer)
  loadingProgress.value = 100
  setTimeout(() => {
    routeLoading.value = false
    loadingProgress.value = 0
  }, 200)
}

const removeBeforeEach = router.beforeEach((to, from, next) => {
  if (to.path !== from.path) startLoading()
  next()
})

const removeAfterEach = router.afterEach(() => finishLoading())

onUnmounted(() => {
  removeBeforeEach()
  removeAfterEach()
  clearInterval(loadingTimer)
  stopAutoLogout()
})

onMounted(() => {
  // 刷新时仍在专注模式则恢复自动退出计时
  if (focusMode.value) startAutoLogout()
  // 学校模式：自动进入专注模式，无需密码
  if (schoolMode.value && !focusMode.value) {
    forceEnterFocusMode()
  }
  // 已登录则建立私聊长连接
  if (userStore.isLoggedIn) connectChat()
})

// 登录 / 退出时同步维护私聊长连接
watch(() => userStore.token, (token) => {
  if (token && !userStore.isAdmin) {
    connectChat()
  } else {
    disconnectChat()
  }
})

const handleCommand = async (command) => {
  switch (command) {
    case 'profile':
      router.push('/profile')
      break
    case 'friends':
      router.push('/friends')
      break
    case 'messages':
      router.push('/messages')
      break
    case 'feedback':
      router.push('/feedback')
      break
    case 'myRepair':
      router.push('/repair')
      break
    case 'myApp':
      router.push('/applications')
      break
    case 'logout':
      try {
        await ElMessageBox.confirm('确定要退出登录吗？', '提示', {
          confirmButtonText: '确定',
          cancelButtonText: '取消',
          type: 'warning'
        })
        await apiLogout()
        disconnectChat()
        userStore.logout()
        // 退出登录时同时退出专注模式
        if (focusMode.value) forceExitFocusMode()
        ElMessage.success('已退出登录')
        router.push('/home')
      } catch {}
      break
  }
}
</script>

<style scoped>
.app-container {
  min-height: 100vh;
  display: flex;
  flex-direction: column;
  background: var(--ct-paper);
}

.app-header {
  background: rgba(255, 253, 249, 0.96);
  color: var(--ct-ink);
  padding: 0 32px;
  height: 76px;
  display: flex;
  align-items: center;
  justify-content: space-between;
  box-shadow: 0 2px 8px rgba(74, 49, 33, 0.08);
  position: sticky;
  top: 0;
  z-index: 2000;
  border-top: 5px solid var(--ct-red);
  border-bottom: 1px solid var(--ct-line);
  backdrop-filter: blur(16px);
}

.header-left {
  display: flex;
  align-items: center;
  gap: 14px;
  cursor: pointer;
}

.logo {
  width: 44px;
  height: 44px;
  border-radius: 50%;
  display: flex;
  align-items: center;
  justify-content: center;
  overflow: hidden;
  flex-shrink: 0;
}
.logo img {
  width: 100%;
  height: 100%;
  object-fit: cover;
  display: block;
}

.main-title {
  font-size: 19px;
  font-weight: 700;
  letter-spacing: 2px;
  margin: 0;
  color: var(--ct-red);
  font-family: var(--ct-serif);
}

.sub-title {
  font-size: 11px;
  color: var(--ct-muted);
  margin: 3px 0 0 0;
  letter-spacing: 0.5px;
}

.school-mode-badge {
  margin-left: 12px;
  display: flex;
  align-items: center;
  gap: 4px;
}

.header-right {
  display: flex;
  align-items: center;
  gap: 6px;
}

.header-right :deep(.el-button) {
  color: #4f4d49;
  font-size: 14px;
  font-weight: 600;
  padding: 8px 14px;
  border-radius: 0;
}

.header-right :deep(.el-button:hover) {
  color: var(--ct-red);
  background: var(--ct-red-soft);
}

.user-info {
  display: flex;
  align-items: center;
  gap: 8px;
  cursor: pointer;
  padding: 4px 12px;
  border-radius: 6px;
  transition: background 0.2s;
}

.user-info:hover {
  background: var(--ct-red-soft);
}

.avatar {
  background: var(--ct-red);
  color: #fff;
  font-weight: 700;
}

.username {
  font-size: 14px;
  color: var(--ct-ink);
  font-weight: 600;
}

.app-main {
  flex: 1;
  padding: 28px 32px;
  max-width: 1280px;
  margin: 0 auto;
  width: 100%;
}

.app-main.full-page {
  padding: 0;
  max-width: 100%;
}

.focus-badge {
  margin-left: 12px;
  display: flex;
  align-items: center;
  gap: 4px;
  animation: pulse 2s infinite;
}

@keyframes pulse {
  0%, 100% { opacity: 1; }
  50% { opacity: 0.7; }
}

.app-footer {
  background: #2d2925;
  color: #a9a097;
  text-align: center;
  padding: 24px 16px;
  font-size: 12px;
  border-top: 3px solid var(--ct-red);
}

.app-footer p {
  margin: 0;
  letter-spacing: 1px;
}

/* 移动端菜单按钮 */
.mobile-menu-btn {
  display: none;
  cursor: pointer;
  padding: 8px;
  border-radius: 8px;
  transition: background 0.2s;
  color: var(--ct-ink);
}
.mobile-menu-btn:hover {
  background: var(--ct-red-soft);
}

/* 移动端导航 */
.mobile-nav {
  padding: 16px 0;
}
.mobile-nav-item {
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 14px 20px;
  font-size: 15px;
  color: var(--ct-ink);
  cursor: pointer;
  transition: all 0.2s;
  border-left: 3px solid transparent;
}
.mobile-nav-item:hover {
  background: var(--ct-red-soft);
  color: var(--ct-red);
  border-left-color: var(--ct-red);
}
.mobile-nav-item.logout {
  color: var(--ct-red);
}
.mobile-user-info {
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 0 20px;
}
.mobile-username {
  font-size: 16px;
  font-weight: 700;
  color: var(--ct-ink);
  font-family: var(--ct-serif);
}
.mobile-badge {
  margin-left: auto;
}
.nav-badge {
  margin: 0 4px;
}
.nav-badge :deep(.el-badge__content) {
  border: none;
}

/* ========== 移动端响应式 ========== */
@media (max-width: 768px) {
  .app-header {
    padding: 0 16px;
    height: 64px;
  }
  .header-left {
    gap: 10px;
  }
  .logo {
    width: 36px;
    height: 36px;
  }
  .main-title {
    font-size: 15px;
    letter-spacing: 1px;
  }
  .sub-title {
    font-size: 10px;
  }
  .desktop-nav {
    display: none !important;
  }
  .mobile-menu-btn {
    display: flex;
    align-items: center;
    justify-content: center;
  }
  .app-main {
    padding: 16px 12px;
  }
  .app-footer {
    padding: 16px 12px;
    font-size: 11px;
  }
}

@media (max-width: 480px) {
  .main-title {
    font-size: 13px;
  }
  .sub-title {
    display: none;
  }
  .app-main {
    padding: 12px 8px;
  }
}
</style>
