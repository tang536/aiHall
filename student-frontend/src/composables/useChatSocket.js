import { ref } from 'vue'
import { ElNotification } from 'element-plus'
import { useChatStore } from '@/store/chat'
import { useUserStore } from '@/store/user'

/**
 * 私聊 WebSocket 单连接管理器。
 * - 全应用只保持一条连接（登录后由 App.vue 调用 connect()）
 * - 使用浏览器原生 WebSocket，无额外依赖；开发环境经 vite 的 /ws 代理转发
 * - 断线按指数退避重连；断连期间启用未读数轮询兜底，恢复后自动停止轮询
 * - 组件通过 onMessage(cb) 订阅消息，返回取消订阅函数
 */

const listeners = new Set()
let socket = null
let reconnectTimer = null
let pollTimer = null
let attempts = 0
let manuallyClosed = false

const MAX_ATTEMPTS = 6
const RECONNECT_DELAYS = [1000, 2000, 5000, 10000, 15000, 20000]
const POLL_INTERVAL = 15000

export const chatSocketStatus = ref('idle') // idle | connecting | open | closed

/** 计算 WebSocket 地址：开发环境走 vite 代理，生产按平台直连后端 */
function buildUrl() {
  const token = localStorage.getItem('token') || ''
  const isCapacitor = typeof window !== 'undefined' && !!(window.Capacitor && window.Capacitor.isNativePlatform)
  const isElectron = typeof navigator !== 'undefined' && navigator.userAgent.includes('Electron')

  let origin = window.location.origin
  if (import.meta.env.PROD && isCapacitor) {
    origin = 'http://10.2.50.4:8080'
  } else if (import.meta.env.PROD && isElectron) {
    origin = 'http://localhost:8080'
  }
  const wsOrigin = origin.replace(/^http/, 'ws')
  return `${wsOrigin}/ws/chat?token=${encodeURIComponent(token)}`
}

/** 订阅服务端推送，返回取消订阅函数 */
export function onMessage(cb) {
  listeners.add(cb)
  return () => listeners.delete(cb)
}

function emit(payload) {
  listeners.forEach((cb) => {
    try {
      cb(payload)
    } catch (e) {
      console.error('聊天消息处理失败', e)
    }
  })
}

function startPolling() {
  const chat = useChatStore()
  if (pollTimer) return
  // 断了实时通道时用轮询保证未读数不丢
  pollTimer = setInterval(() => chat.refreshUnread(), POLL_INTERVAL)
}

function stopPolling() {
  if (pollTimer) {
    clearInterval(pollTimer)
    pollTimer = null
  }
}

function scheduleReconnect() {
  const chat = useChatStore()
  if (manuallyClosed || attempts >= MAX_ATTEMPTS) {
    startPolling()
    return
  }
  const delay = RECONNECT_DELAYS[Math.min(attempts, RECONNECT_DELAYS.length - 1)]
  attempts += 1
  clearTimeout(reconnectTimer)
  reconnectTimer = setTimeout(() => connect(), delay)
}

export function connect() {
  const userStore = useUserStore()
  const chat = useChatStore()
  if (!userStore.isLoggedIn || userStore.isAdmin) return
  if (socket && (socket.readyState === WebSocket.OPEN || socket.readyState === WebSocket.CONNECTING)) return

  manuallyClosed = false
  chatSocketStatus.value = 'connecting'

  try {
    socket = new WebSocket(buildUrl())
  } catch (e) {
    console.warn('WebSocket 创建失败', e)
    scheduleReconnect()
    return
  }

  socket.onopen = () => {
    attempts = 0
    chatSocketStatus.value = 'open'
    chat.setConnected(true)
    stopPolling()
    chat.refreshUnread()
  }

  socket.onmessage = (event) => {
    let payload
    try {
      payload = JSON.parse(event.data)
    } catch {
      return
    }

    if (payload.type === 'MESSAGE') {
      emit(payload)
      const peerId = payload.message?.senderId
      // 若当前正与该用户聊天，则不累加未读（Messages 页会立即标记已读）
      if (chat.activePeerId && Number(chat.activePeerId) === Number(peerId)) {
        chat.refreshUnread()
      } else {
        chat.incrementUnread(1)
        const name = payload.from?.displayName || '有用户'
        ElNotification({
          title: '收到新私信',
          message: `${name}：${payload.message?.content || '[非文本消息]'}`,
          type: 'info',
          duration: 3500,
          position: 'bottom-right'
        })
      }
      return
    }

    if (payload.type === 'CONNECTED') {
      chatSocketStatus.value = 'open'
      return
    }

    // SENT / PONG / ERROR 交由订阅方处理（如发送回执）
    emit(payload)
  }

  socket.onclose = () => {
    chatSocketStatus.value = 'closed'
    chat.setConnected(false)
    if (!manuallyClosed) scheduleReconnect()
  }

  socket.onerror = () => {
    // 关闭事件会随后触发，这里只记录状态
    chatSocketStatus.value = 'closed'
  }
}

/** 断开连接（退出登录时调用） */
export function disconnect() {
  manuallyClosed = true
  attempts = 0
  clearTimeout(reconnectTimer)
  reconnectTimer = null
  stopPolling()
  if (socket) {
    try {
      socket.close()
    } catch {
      /* 忽略 */
    }
    socket = null
  }
  chatSocketStatus.value = 'idle'
  useChatStore().reset()
}

/** 是否已连接（用于决定聊天页是否降级为轮询刷新） */
export function isConnected() {
  return !!socket && socket.readyState === WebSocket.OPEN
}

/** 直接经 WebSocket 发送（失败时调用方可回退到 REST 接口） */
export function send(payload) {
  if (!isConnected()) return false
  try {
    socket.send(JSON.stringify(payload))
    return true
  } catch {
    return false
  }
}
