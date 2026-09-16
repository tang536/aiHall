<template>
  <div class="admin-layout">
    <!-- 侧边栏 -->
    <aside class="admin-sidebar" :class="{ 'is-mobile': isMobile }">
      <div class="sidebar-logo">
        <span class="logo-mark"><img src="/gxu-icon.png" alt="广西大学" /></span>
        <span>管理后台</span>
      </div>
      <el-menu :default-active="activeMenu" router class="sidebar-menu" :collapse="isMobile" :collapse-transition="false" background-color="#761317" text-color="#e8d5c8" active-text-color="#f0c896">
        <el-menu-item index="/admin/dashboard">
          <el-icon><DataAnalysis /></el-icon>
          <span>数据概览</span>
        </el-menu-item>
        <el-menu-item index="/admin/repairs">
          <el-icon><Tools /></el-icon>
          <span>报修管理</span>
        </el-menu-item>
        <el-menu-item index="/admin/applications">
          <el-icon><Document /></el-icon>
          <span>申请管理</span>
        </el-menu-item>
        <el-menu-item index="/admin/notifications">
          <el-icon><Bell /></el-icon>
          <span>通知管理</span>
        </el-menu-item>
        <el-menu-item index="/admin/knowledge">
          <el-icon><Reading /></el-icon>
          <span>知识库管理</span>
        </el-menu-item>
        <el-menu-item index="/admin/feedbacks">
          <el-icon><EditPen /></el-icon>
          <span>学生反馈</span>
        </el-menu-item>
        <el-menu-item index="/admin/market">
          <el-icon><ShoppingBag /></el-icon>
          <span>二手商品管理</span>
        </el-menu-item>
        <el-menu-item index="/admin/posts">
          <el-icon><ChatLineSquare /></el-icon>
          <span>帖子管理</span>
        </el-menu-item>
        <el-menu-item index="/admin/users">
          <el-icon><UserFilled /></el-icon>
          <span>用户管理</span>
        </el-menu-item>
        <el-menu-item index="/admin/locations">
          <el-icon><Location /></el-icon>
          <span>地点管理</span>
        </el-menu-item>
        <el-menu-item index="/admin/settings">
          <el-icon><Setting /></el-icon>
          <span>系统设置</span>
        </el-menu-item>
      </el-menu>
      <div class="sidebar-footer">
        <el-button text @click="goStudentFrontend">
          <el-icon><Back /></el-icon> <span class="footer-text">返回前台</span>
        </el-button>
      </div>
    </aside>

    <!-- 主内容 -->
    <div class="admin-main">
      <header class="admin-header">
        <h2>{{ pageTitle }}</h2>
        <div class="header-right">
          <el-dropdown @command="handleCommand">
            <span class="user-info">
              <el-avatar :size="32" class="avatar">{{ userStore.userName?.charAt(0) || 'A' }}</el-avatar>
              <span class="admin-name">{{ userStore.userName || '管理员' }}</span>
              <el-icon class="caret"><ArrowDown /></el-icon>
            </span>
            <template #dropdown>
              <el-dropdown-menu>
                <el-dropdown-item command="logout">
                  <el-icon><SwitchButton /></el-icon> 退出登录
                </el-dropdown-item>
              </el-dropdown-menu>
            </template>
          </el-dropdown>
        </div>
      </header>
      <div class="admin-content">
        <slot></slot>
      </div>
    </div>
  </div>
</template>

<script setup>
import { computed, ref, onMounted, onUnmounted } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { useUserStore } from '@/store/user'
import { logout as apiLogout } from '@/api'
import { ElMessage, ElMessageBox } from 'element-plus'

const route = useRoute()
const router = useRouter()
const userStore = useUserStore()

// 移动端判断：侧边栏自动折叠为图标栏
const isMobile = ref(false)
function updateIsMobile() {
  isMobile.value = window.innerWidth < 768
}
onMounted(() => {
  updateIsMobile()
  window.addEventListener('resize', updateIsMobile)
})
onUnmounted(() => window.removeEventListener('resize', updateIsMobile))

const activeMenu = computed(() => route.path)

const pageTitle = computed(() => {
  const map = {
    '/admin/dashboard': '数据概览',
    '/admin/repairs': '报修管理',
    '/admin/applications': '申请管理',
    '/admin/notifications': '通知管理',
    '/admin/knowledge': '知识库管理',
    '/admin/feedbacks': '学生反馈',
    '/admin/market': '二手商品管理',
    '/admin/posts': '帖子管理',
    '/admin/users': '用户管理',
    '/admin/locations': '地点管理',
    '/admin/settings': '系统设置',
  }
  return map[route.path] || '管理后台'
})

// 返回学生端前台（学生端与服务端为独立应用，使用浏览器新标签打开）
function goStudentFrontend() {
  window.open('http://localhost:5173', '_blank')
}

// 右上角用户菜单
async function handleCommand(command) {
  if (command !== 'logout') return
  try {
    await ElMessageBox.confirm('确定要退出登录吗？', '提示', {
      confirmButtonText: '确定',
      cancelButtonText: '取消',
      type: 'warning'
    })
    try { await apiLogout() } catch { /* 后端不可用也允许本地退出 */ }
    userStore.logout()
    ElMessage.success('已退出登录')
    router.push('/login')
  } catch { /* 用户取消 */ }
}
</script>

<style scoped>
.admin-layout {
  display: flex;
  height: 100vh;
  min-height: 500px;
  overflow: hidden;
  background: var(--ct-paper);
}

.admin-sidebar {
  width: 220px;
  background: linear-gradient(180deg, #761317 0%, #5c0e12 100%);
  display: flex;
  flex-direction: column;
  flex-shrink: 0;
  transition: width 0.2s ease;
  border-right: 3px solid var(--ct-gold);
}

.admin-sidebar.is-mobile {
  width: 64px;
}

.admin-sidebar.is-mobile .sidebar-logo {
  justify-content: center;
  padding: 16px 0;
}

.admin-sidebar.is-mobile .sidebar-logo span:not(.logo-mark),
.admin-sidebar.is-mobile .footer-text {
  display: none;
}

.admin-sidebar.is-mobile .sidebar-footer {
  padding: 12px 0;
  text-align: center;
}

.admin-sidebar.is-mobile .sidebar-footer :deep(.el-button) {
  padding: 8px 0;
  width: 100%;
}

.sidebar-logo {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 20px 16px;
  color: #fff;
  font-size: 17px;
  font-weight: 700;
  border-bottom: 1px solid rgba(240, 200, 150, 0.2);
  font-family: var(--ct-serif);
  letter-spacing: 2px;
}

.logo-mark {
  width: 36px;
  height: 36px;
  border-radius: 50%;
  display: grid;
  place-items: center;
  overflow: hidden;
  flex-shrink: 0;
}
.logo-mark img {
  width: 100%;
  height: 100%;
  object-fit: cover;
  display: block;
}

.sidebar-menu {
  flex: 1;
  border-right: none !important;
}

.sidebar-menu :deep(.el-menu-item) {
  height: 48px;
  line-height: 48px;
  transition: all 0.2s;
}

.sidebar-menu :deep(.el-menu-item:hover) {
  background: rgba(240, 200, 150, 0.12) !important;
  color: #f0c896 !important;
}

.sidebar-menu :deep(.el-menu-item.is-active) {
  background: rgba(240, 200, 150, 0.18) !important;
  border-left: 3px solid var(--ct-gold);
}

.sidebar-footer {
  padding: 16px;
  border-top: 1px solid rgba(240, 200, 150, 0.2);
}

.sidebar-footer :deep(.el-button) {
  color: rgba(232, 213, 200, 0.7);
}

.sidebar-footer :deep(.el-button:hover) {
  color: #f0c896;
  background: rgba(240, 200, 150, 0.1);
}

.admin-main {
  flex: 1;
  display: flex;
  flex-direction: column;
  background: var(--ct-paper);
  min-width: 0;
}

.admin-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 16px 28px;
  background: var(--ct-white);
  border-bottom: 1px solid var(--ct-line);
  border-top: 5px solid var(--ct-red);
}

.admin-header h2 {
  font-size: 20px;
  font-weight: 700;
  margin: 0;
  font-family: var(--ct-serif);
  color: var(--ct-ink);
  letter-spacing: 2px;
  padding-left: 12px;
  border-left: 4px solid var(--ct-red);
}

.header-right {
  display: flex;
  align-items: center;
  gap: 10px;
}

.user-info {
  display: flex;
  align-items: center;
  gap: 8px;
  cursor: pointer;
  padding: 4px 12px;
  border-radius: 6px;
  transition: background 0.2s;
  outline: none;
}

.user-info:hover {
  background: var(--ct-red-soft);
}

.caret {
  color: var(--ct-muted);
  font-size: 12px;
}

.avatar {
  background: var(--ct-red);
  color: #fff;
  font-weight: 700;
}

.admin-name {
  font-size: 14px;
  color: var(--ct-ink);
  font-weight: 600;
}

.admin-content {
  flex: 1;
  padding: 24px;
  overflow-y: auto;
}

/* ========== 移动端响应式 ========== */
@media (max-width: 768px) {
  .admin-layout {
    border-radius: 0;
  }
  .admin-header {
    padding: 12px 14px;
  }
  .admin-header h2 {
    font-size: 16px;
  }
  .admin-content {
    padding: 12px;
  }
  .user-info {
    padding: 4px 6px;
  }
  .admin-name {
    display: none;
  }
}
</style>
