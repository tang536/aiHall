import { ref, computed, watch, onUnmounted } from 'vue'
import { useUserStore } from '@/store/user'
import { ElMessage } from 'element-plus'
import { logout as apiLogout, verifyFocusPassword, hasFocusPassword, initFocusPassword } from '@/api'

const AUTO_LOGOUT_SECONDS = 30

// 单例状态（跨组件共享）
const focusMode = ref(false)
const setPwdVisible = ref(false)
const enterPwdVisible = ref(false)
const pwdDialogMode = ref('enter')
const setPwdForm = ref({ password: '', confirm: '' })
const enterPwd = ref('')
const pwdChecking = ref(false)

let lastActivityTime = Date.now()
let autoLogoutTimer = null
let autoLogoutTriggered = false
const activityEvents = ['mousemove', 'mousedown', 'keydown', 'scroll', 'touchstart']
let initialized = false

function getFocusKey() {
  const userStore = useUserStore()
  const uid = userStore.userInfo?.username || userStore.userName || 'guest'
  return `ai_hall_focus_active_${uid}`
}

function loadFocusState() {
  focusMode.value = localStorage.getItem(getFocusKey()) === '1'
}

const pwdDialogTitle = computed(() => pwdDialogMode.value === 'enter' ? '进入专注模式' : '退出专注模式')

async function showEnterFocusDialog() {
  if (pwdChecking.value) return
  pwdChecking.value = true
  try {
    const res = await hasFocusPassword()
    if (!res.data) {
      setPwdForm.value = { password: '', confirm: '' }
      setPwdVisible.value = true
    } else {
      pwdDialogMode.value = 'enter'
      enterPwd.value = ''
      enterPwdVisible.value = true
    }
  } catch {
    ElMessage.warning('专注模式暂不可用')
  } finally {
    pwdChecking.value = false
  }
}

function showExitFocusDialog() {
  pwdDialogMode.value = 'exit'
  enterPwd.value = ''
  enterPwdVisible.value = true
}

async function confirmSetPwd() {
  const { password, confirm } = setPwdForm.value
  if (!password) { ElMessage.warning('请输入密码'); return }
  if (password.length < 4) { ElMessage.warning('密码长度不小于4位'); return }
  if (password !== confirm) { ElMessage.warning('两次输入的密码不一致'); return }
  try {
    await initFocusPassword(password)
    setPwdVisible.value = false
    enterFocusMode()
  } catch (e) {
    ElMessage.error(e?.response?.data?.message || '设置密码失败，请稍后重试')
  }
}

async function confirmEnterPwd() {
  if (!enterPwd.value) { ElMessage.warning('请输入密码'); return }
  try {
    const res = await verifyFocusPassword(enterPwd.value)
    if (!res.data) { ElMessage.error('密码错误'); return }
    enterPwdVisible.value = false
    if (pwdDialogMode.value === 'enter') enterFocusMode()
    else exitFocusMode()
  } catch {
    ElMessage.error('验证失败')
  }
}

function enterFocusMode() {
  focusMode.value = true
  localStorage.setItem(getFocusKey(), '1')
  startAutoLogout()
  ElMessage.success('已进入专注模式，30秒无操作将自动退出登录')
}

function exitFocusMode() {
  focusMode.value = false
  localStorage.removeItem(getFocusKey())
  stopAutoLogout()
  ElMessage.success('已退出专注模式')
}

function resetActivity() {
  lastActivityTime = Date.now()
  autoLogoutTriggered = false
}

async function doAutoLogout() {
  if (autoLogoutTriggered) return
  autoLogoutTriggered = true
  const userStore = useUserStore()
  if (userStore.isLoggedIn) {
    try { await apiLogout() } catch {}
    userStore.logout()
  }
  lastActivityTime = Date.now()
  ElMessage.warning(`长时间未操作（${AUTO_LOGOUT_SECONDS}秒），已自动退出登录`)
}

function checkAutoLogout() {
  if (!focusMode.value) { resetActivity(); return }
  if (Date.now() - lastActivityTime > AUTO_LOGOUT_SECONDS * 1000) {
    doAutoLogout()
  }
}

function startAutoLogout() {
  if (autoLogoutTimer) return
  resetActivity()
  activityEvents.forEach(evt => window.addEventListener(evt, resetActivity, { passive: true }))
  autoLogoutTimer = setInterval(checkAutoLogout, 1000)
}

function stopAutoLogout() {
  if (autoLogoutTimer) {
    clearInterval(autoLogoutTimer)
    autoLogoutTimer = null
  }
  activityEvents.forEach(evt => window.removeEventListener(evt, resetActivity))
}

export function useFocusMode() {
  const userStore = useUserStore()

  if (!initialized) {
    initialized = true
    loadFocusState()
    // 用户切换账号时重新加载专注状态
    watch(() => userStore.userInfo?.username, () => {
      loadFocusState()
      if (focusMode.value) startAutoLogout()
      else stopAutoLogout()
    })
  }

  function toggleFocusMode() {
    if (focusMode.value) showExitFocusDialog()
    else showEnterFocusDialog()
  }

  function forceEnterFocusMode() {
    // 学校模式等场景：无需密码直接进入
    focusMode.value = true
    localStorage.setItem(getFocusKey(), '1')
    startAutoLogout()
  }

  function forceExitFocusMode() {
    focusMode.value = false
    localStorage.removeItem(getFocusKey())
    stopAutoLogout()
  }

  return {
    focusMode,
    setPwdVisible,
    enterPwdVisible,
    pwdDialogMode,
    setPwdForm,
    enterPwd,
    pwdDialogTitle,
    showEnterFocusDialog,
    showExitFocusDialog,
    confirmSetPwd,
    confirmEnterPwd,
    enterFocusMode,
    exitFocusMode,
    toggleFocusMode,
    forceEnterFocusMode,
    forceExitFocusMode,
    startAutoLogout,
    stopAutoLogout,
  }
}
