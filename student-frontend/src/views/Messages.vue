<template>
  <div class="messages-page">
    <div class="page-header">
      <h2><el-icon><ChatDotRound /></el-icon> 我的消息</h2>
      <p>
        与同学一对一私聊，支持发送图片与分享商品 / 帖子
        <el-tag v-if="chat.connected" size="small" type="success" effect="plain" class="conn-tag">实时连接中</el-tag>
        <el-tag v-else size="small" type="info" effect="plain" class="conn-tag">离线模式</el-tag>
      </p>
    </div>

    <div class="chat-layout">
      <!-- 会话列表 -->
      <aside class="conversation-panel">
        <div class="panel-head">
          <span>会话（{{ conversations.length }}）</span>
          <el-button size="small" text @click="loadConversations">
            <el-icon><Refresh /></el-icon>
          </el-button>
        </div>

        <div v-loading="loading" class="conversation-list">
          <div
            v-for="conv in conversations"
            :key="conv.peer?.userId"
            class="conversation-item"
            :class="{ active: Number(activePeerId) === Number(conv.peer?.userId) }"
            @click="openConversation(conv.peer)"
          >
            <el-badge :value="conv.unread" :hidden="!conv.unread" class="conv-badge">
              <el-avatar :size="40" class="avatar">{{ (conv.peer?.displayName || '?').charAt(0) }}</el-avatar>
            </el-badge>
            <div class="conv-info">
              <div class="conv-top">
                <span class="conv-name">{{ conv.peer?.displayName }}</span>
                <span class="conv-time">{{ relativeTime(conv.lastTime) }}</span>
              </div>
              <div class="conv-preview">
                <span v-if="conv.fromMe" class="me-prefix">我：</span>{{ conv.lastMessage }}
              </div>
            </div>
          </div>
          <el-empty v-if="!loading && conversations.length === 0" description="暂无会话" :image-size="70" />
        </div>

        <div class="panel-foot">
          <el-button size="small" style="width: 100%" @click="$router.push('/friends')">
            <el-icon><UserFilled /></el-icon> 我的好友
          </el-button>
        </div>
      </aside>

      <!-- 聊天窗口 -->
      <section class="chat-panel">
        <template v-if="peer">
          <div class="chat-head">
            <el-avatar :size="34" class="avatar">{{ (peer.displayName || '?').charAt(0) }}</el-avatar>
            <div class="chat-head-info">
              <span class="chat-name">{{ peer.displayName }}</span>
              <span class="chat-meta">
                <span v-if="peer.college">{{ peer.college }}</span>
                <span v-if="peer.maskedAccount">学号 {{ peer.maskedAccount }}</span>
              </span>
            </div>
            <el-button v-if="!peer.friend" size="small" plain @click="addFriend(peer)">
              <el-icon><Plus /></el-icon> 加好友
            </el-button>
            <el-button size="small" text @click="closeChat">关闭</el-button>
          </div>

          <ChatWindow
            :messages="messages"
            :peer="peer"
            :self-id="userStore.userId"
            :self-name="userStore.userName || '我'"
            :sending="sending"
            @send="doSend"
          />
        </template>
        <el-empty v-else description="从左侧选择一个会话开始聊天" />
      </section>
    </div>
  </div>
</template>

<script setup>
import { computed, onBeforeUnmount, onMounted, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import ChatWindow from '@/components/ChatWindow.vue'
import { useUserStore } from '@/store/user'
import { useChatStore } from '@/store/chat'
import { onMessage as onSocketMessage } from '@/composables/useChatSocket'
import {
  listConversations, chatHistory, sendMessage, markConversationRead, getUserProfile, sendFriendRequest
} from '@/api'
import { relativeTime } from '@/utils/community'

const route = useRoute()
const router = useRouter()
const userStore = useUserStore()
const chat = useChatStore()

const loading = ref(false)
const sending = ref(false)
const conversations = ref([])
const messages = ref([])
const peer = ref(null)
let unsubscribe = null
let pollTimer = null

const activePeerId = computed(() => peer.value?.userId || null)

async function loadConversations() {
  loading.value = true
  try {
    const res = await listConversations()
    conversations.value = res?.data || []
  } finally {
    loading.value = false
  }
  chat.refreshUnread()
}

async function loadHistory(peerId) {
  const res = await chatHistory(peerId)
  messages.value = res?.data || []
}

async function openConversation(user) {
  if (!user?.userId) return
  peer.value = user
  chat.setActivePeer(user.userId)
  await Promise.all([loadHistory(user.userId), markRead(user.userId)])
  syncRouteQuery(user.userId)
}

async function markRead(peerId) {
  try {
    await markConversationRead(peerId)
    const conv = conversations.value.find((c) => Number(c.peer?.userId) === Number(peerId))
    if (conv) conv.unread = 0
    chat.refreshUnread()
  } catch {
    /* 静默失败 */
  }
}

function syncRouteQuery(peerId) {
  if (Number(route.query.peerId) === Number(peerId)) return
  router.replace({ path: '/messages', query: { peerId } })
}

async function doSend({ content, msgType = 'TEXT', refType = null, refId = null }) {
  if (!peer.value) return
  sending.value = true
  try {
    const res = await sendMessage({
      peerId: peer.value.userId, content, msgType, refType, refId
    })
    if (res?.data) messages.value.push(res.data)
    // 刷新会话列表以更新最后一条消息
    await loadConversations()
  } finally {
    sending.value = false
  }
}

async function addFriend(user) {
  try {
    const res = await sendFriendRequest({ userId: user.userId })
    ElMessage.success(res?.message || '好友申请已发送')
    peer.value.friend = true
  } catch {
    /* 拦截器已提示 */
  }
}

function closeChat() {
  peer.value = null
  messages.value = []
  chat.clearActivePeer()
  router.replace({ path: '/messages' })
}

/** 收到实时消息：若是当前会话则直接追加，否则只刷新列表与未读 */
function handleSocketMessage(payload) {
  if (payload.type !== 'MESSAGE') return
  const msg = payload.message
  if (!msg) return
  if (chat.activePeerId && Number(chat.activePeerId) === Number(msg.senderId)) {
    if (!messages.value.some((m) => m.id === msg.id)) {
      messages.value.push(msg)
    }
    markRead(msg.senderId)
  }
  loadConversations()
}

/** 从 URL 打开指定会话（商品 / 帖子页的「聊一聊」入口） */
async function openFromQuery() {
  const peerId = route.query.peerId
  if (!peerId) return
  const existing = conversations.value.find((c) => Number(c.peer?.userId) === Number(peerId))
  if (existing) {
    await openConversation(existing.peer)
    return
  }
  try {
    const res = await getUserProfile(peerId)
    if (res?.data) await openConversation(res.data)
  } catch {
    /* 用户不存在则忽略 */
  }
}

watch(() => route.query.peerId, (val) => {
  if (val && Number(val) !== Number(activePeerId.value)) openFromQuery()
})

onMounted(async () => {
  await loadConversations()
  await openFromQuery()
  // WebSocket 断开时用轮询兜底刷新会话与未读
  pollTimer = setInterval(() => {
    if (!chat.connected) {
      loadConversations()
    }
  }, 20000)
  unsubscribe = onSocketMessage(handleSocketMessage)
})

onBeforeUnmount(() => {
  if (unsubscribe) unsubscribe()
  if (pollTimer) clearInterval(pollTimer)
  chat.clearActivePeer()
})
</script>

<style scoped>
.conn-tag {
  margin-left: 8px;
}

.chat-layout {
  display: grid;
  grid-template-columns: 290px minmax(0, 1fr);
  gap: 16px;
  height: calc(100vh - 260px);
  min-height: 460px;
}

.conversation-panel,
.chat-panel {
  background: #fff;
  border: 1px solid #ebeef5;
  border-radius: 12px;
  display: flex;
  flex-direction: column;
  min-height: 0;
  overflow: hidden;
}

.panel-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 12px 14px;
  border-bottom: 1px solid #f2f3f5;
  font-size: 14px;
  font-weight: 600;
  color: #303133;
}

.conversation-list {
  flex: 1;
  overflow-y: auto;
  padding: 6px;
  min-height: 0;
}

.conversation-item {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 10px;
  border-radius: 8px;
  cursor: pointer;
  transition: background 0.2s;
}

.conversation-item:hover {
  background: #f5f7fa;
}

.conversation-item.active {
  background: #ecf5ff;
}

.avatar {
  background: linear-gradient(135deg, #409eff, #67c23a);
  color: #fff;
  font-weight: 700;
  flex-shrink: 0;
}

.conv-badge {
  flex-shrink: 0;
}

.conv-info {
  flex: 1;
  min-width: 0;
}

.conv-top {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 6px;
}

.conv-name {
  font-size: 13px;
  font-weight: 600;
  color: #303133;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.conv-time {
  font-size: 11px;
  color: #c0c4cc;
  flex-shrink: 0;
}

.conv-preview {
  margin-top: 2px;
  font-size: 12px;
  color: #909399;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.me-prefix {
  color: #c0c4cc;
}

.panel-foot {
  padding: 10px;
  border-top: 1px solid #f2f3f5;
}

.chat-panel {
  padding: 0;
}

.chat-head {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 10px 14px;
  border-bottom: 1px solid #f2f3f5;
}

.chat-head-info {
  flex: 1;
  min-width: 0;
}

.chat-name {
  font-size: 14px;
  font-weight: 600;
  color: #303133;
}

.chat-meta {
  display: flex;
  gap: 10px;
  font-size: 12px;
  color: #909399;
  margin-top: 2px;
}

.chat-panel > :deep(.chat-window) {
  flex: 1;
  min-height: 0;
}

@media (max-width: 900px) {
  .chat-layout {
    grid-template-columns: 1fr;
    height: auto;
  }
  .conversation-panel {
    max-height: 260px;
  }
  .chat-panel {
    height: 520px;
  }
}
</style>
