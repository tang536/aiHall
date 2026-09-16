import { defineStore } from 'pinia'
import { unreadCount } from '@/api'

/**
 * 私聊状态：未读总数（顶部徽标）、当前打开的会话对象。
 * WebSocket 收到新消息或轮询兜底时会刷新未读数。
 */
export const useChatStore = defineStore('chat', {
  state: () => ({
    /** 未读消息总数 */
    unreadTotal: 0,
    /** 当前正在聊天的对方 userId（Messages 页打开时设置） */
    activePeerId: null,
    /** WebSocket 是否已连接（断线时启用轮询兜底） */
    connected: false
  }),
  actions: {
    setUnreadTotal(n) {
      this.unreadTotal = Number(n) || 0
    },
    incrementUnread(n = 1) {
      this.unreadTotal += n
    },
    setActivePeer(peerId) {
      this.activePeerId = peerId
    },
    clearActivePeer() {
      this.activePeerId = null
    },
    setConnected(v) {
      this.connected = !!v
    },
    /** 从后端刷新未读总数（静默，失败不打扰用户） */
    async refreshUnread() {
      try {
        const res = await unreadCount({ silent: true })
        this.setUnreadTotal(res?.data?.count ?? 0)
      } catch {
        /* 后端不可用时保持原值 */
      }
    },
    reset() {
      this.unreadTotal = 0
      this.activePeerId = null
      this.connected = false
    }
  }
})
