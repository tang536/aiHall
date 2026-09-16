import axios from 'axios'
import { ElMessage } from 'element-plus'
import router from '@/router'

// 生产环境下根据运行平台切换后端地址：
// - Electron（桌面 App）：直接访问本机后端
// - Capacitor（安卓 App）：通过局域网访问电脑上的后端
// - 浏览器：走相对路径 /api（由 vite 代理或部署服务器转发）
const isElectron = typeof navigator !== 'undefined' && navigator.userAgent.includes('Electron')
const isCapacitor = typeof window !== 'undefined' && !!(window.Capacitor && window.Capacitor.isNativePlatform)
let baseURL = '/api'
if (import.meta.env.PROD && isElectron) {
  baseURL = 'http://localhost:8080/api'
} else if (import.meta.env.PROD && isCapacitor) {
  baseURL = 'http://10.2.50.4:8080/api'
}

const request = axios.create({
  baseURL,
  timeout: 30000
})

// 全局错误去重：同类错误在窗口期内只弹一次
let lastErrorTime = 0
let lastErrorType = ''
const ERROR_DEDUP_WINDOW = 3000 // 3秒内同类错误不重复弹窗

function shouldShowError(type) {
  const now = Date.now()
  if (type === lastErrorType && now - lastErrorTime < ERROR_DEDUP_WINDOW) {
    return false
  }
  lastErrorTime = now
  lastErrorType = type
  return true
}

request.interceptors.request.use(
  config => {
    const token = localStorage.getItem('token')
    if (token) {
      config.headers.Authorization = `Bearer ${token}`
    }
    return config
  },
  error => Promise.reject(error)
)

// 登录态失效统一处理：清除本地登录信息并回到登录页
// 若为"在其他设备登录被顶下线"，给出明确提示
function handleAuthFailure(message) {
  const kicked = message && message.includes('其他设备')
  localStorage.removeItem('token')
  localStorage.removeItem('userInfo')
  if (router.currentRoute.value.path !== '/login') {
    if (kicked) {
      ElMessage.warning('账号已在其他设备登录，您已被迫下线，请重新登录')
    } else {
      ElMessage.warning(message || '登录已失效，请重新登录')
    }
    router.push('/login')
  }
}

/**
 * 滑动续期：令牌剩余有效期不足时，后端会用 X-New-Token 响应头下发一枚新令牌。
 * 这里静默替换本地令牌，用户不会感知，也不需要重新登录。
 * （axios 会把响应头名转成小写）
 */
function applyRenewedToken(response) {
  const fresh = response.headers?.['x-new-token']
  if (!fresh || localStorage.getItem('token') === fresh) return
  localStorage.setItem('token', fresh)
}

request.interceptors.response.use(
  response => {
    applyRenewedToken(response)
    const res = response.data
    if (res.code !== 200) {
      if (res.code === 401) {
        handleAuthFailure(res.message)
      }
      return Promise.reject(new Error(res.message || '请求出错'))
    }
    return res
  },
  error => {
    // silent 配置：调用方自行处理错误，不弹窗
    if (error.config?.silent) {
      return Promise.reject(error)
    }
    if (error.response?.status === 401) {
      handleAuthFailure(error.response.data?.message)
      return Promise.reject(error)
    }
    // 网络错误去重，避免一次页面加载多次弹错
    const msg = error.message
    let errorType = 'unknown'
    let userMsg = '网络不太顺畅，请稍后再试一次'
    if (msg === 'Failed to fetch' || msg === 'Network Error' || error.code === 'ERR_NETWORK') {
      errorType = 'network'
      userMsg = '暂时连不上系统服务，请检查网络后重试；如果一直这样，请联系管理员'
    } else if (error.code === 'ECONNABORTED' || msg?.includes('timeout')) {
      errorType = 'timeout'
      userMsg = '页面响应有点慢，请稍后再试一次'
    }
    if (shouldShowError(errorType)) {
      ElMessage.error(userMsg)
    }
    return Promise.reject(error)
  }
)

export default request
