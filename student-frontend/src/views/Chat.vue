<template>
  <div class="chat-page">
    <div class="chat-container">
      <!-- 移动端侧边栏切换按钮 -->
      <el-button class="mobile-sidebar-toggle" circle @click="mobileSidebarOpen = !mobileSidebarOpen">
        <el-icon><Menu /></el-icon>
      </el-button>
      <!-- 移动端遮罩层 -->
      <div class="sidebar-mask" :class="{ show: mobileSidebarOpen }" @click="mobileSidebarOpen = false"></div>
      <!-- 左侧：会话列表/快捷问题 -->
      <div class="chat-sidebar" :class="{ 'mobile-open': mobileSidebarOpen }">
        <div class="sidebar-header">
          <h3><el-icon><Cpu /></el-icon> AI校园助手</h3>
          <el-button text @click="newChat" title="新对话">
            <el-icon><Plus /></el-icon>
          </el-button>
        </div>

        <div class="ai-status-bar" :class="{ online: aiStatus.ollamaAvailable, offline: !aiStatus.ollamaAvailable }"
             @click="checkAiStatus" title="点击重新检测AI服务状态">
          <span class="status-dot"></span>
          <span>{{ aiStatus.ollamaAvailable ? 'AI服务在线' : 'AI服务离线（点击重试）' }}</span>
        </div>

        <div class="quick-section">
          <h4>快捷提问</h4>
          <div class="quick-list">
            <div v-for="q in quickQuestions" :key="q"
                 class="quick-item"
                 @click="sendQuickQuestion(q)">
              <el-icon><ChatDotRound /></el-icon>
              <span>{{ q }}</span>
            </div>
          </div>
        </div>

        <div class="capability-section">
          <h4>能力范围</h4>
          <div class="capability-list">
            <div class="cap-item"><el-icon type="success"><CircleCheck /></el-icon> 奖助学金政策</div>
            <div class="cap-item"><el-icon type="success"><CircleCheck /></el-icon> 请假办理流程</div>
            <div class="cap-item"><el-icon type="success"><CircleCheck /></el-icon> 证明开具指南</div>
            <div class="cap-item"><el-icon type="success"><CircleCheck /></el-icon> 宿舍报修服务</div>
            <div class="cap-item"><el-icon type="success"><CircleCheck /></el-icon> 部门联系方式</div>
            <div class="cap-item"><el-icon type="success"><CircleCheck /></el-icon> 校园规章制度</div>
          </div>
        </div>
      </div>

      <!-- 右侧：对话区域 -->
      <div class="chat-main">
        <div class="chat-messages" ref="messagesContainer">
          <!-- 欢迎消息 -->
          <div v-if="messages.length === 0" class="welcome-message">
            <div class="welcome-avatar"><img src="/gxu-icon.png" alt="AI助手" /></div>
            <h2>你好，我是校园AI助手 👋</h2>
            <p>我可以帮你解答校园事务相关问题，包括奖助学金、请假流程、证明开具、宿舍报修等。</p>
            <p>基于校园知识库回答准确可追溯。</p>
            <div class="welcome-suggestions">
              <div v-for="s in suggestions" :key="s" class="suggestion-btn" @click="sendQuickQuestion(s)">
                {{ s }}
              </div>
            </div>
          </div>

          <!-- 消息列表 -->
          <div v-for="(msg, idx) in messages" :key="idx"
               class="message-item" :class="msg.role">
            <div class="msg-avatar" v-if="msg.role === 'assistant'"><img src="/gxu-icon.png" alt="AI" /></div>
            <div class="msg-bubble">
              <div class="msg-content" v-html="formatContent(msg.content)"></div>
              <!-- 引用来源 -->
              <div v-if="msg.sources && msg.sources.length > 0" class="msg-sources">
                <el-icon><Document /></el-icon>
                <span>参考来源：</span>
                <span v-for="(s, i) in msg.sources" :key="i" class="source-tag">{{ s }}</span>
              </div>
              <!-- 反馈按钮 -->
              <div v-if="msg.role === 'assistant' && !msg.loading" class="msg-actions">
                <el-button text size="small" :class="{ active: msg.feedback === 1 }" @click="sendFeedback(msg, 1)">
                  <el-icon><Top /></el-icon> 有用
                </el-button>
                <el-button text size="small" :class="{ active: msg.feedback === -1 }" @click="sendFeedback(msg, -1)">
                  <el-icon><Bottom /></el-icon> 没用
                </el-button>
                <span v-if="msg.responseTime" class="response-time">回答耗时 {{ formatResponseTime(msg.responseTime) }}</span>
              </div>
              <!-- 加载动画 -->
              <div v-if="msg.loading" class="typing-indicator">
                <span></span><span></span><span></span>
              </div>
            </div>
            <div class="msg-avatar user-avatar" v-if="msg.role === 'user'">
              {{ userStore.userName ? userStore.userName.charAt(0) : '我' }}
            </div>
          </div>
        </div>

        <!-- 输入区域 -->
        <div class="chat-input-area">
          <div class="input-wrapper">
            <el-input v-model="inputMessage" type="textarea" :rows="2"
                      placeholder="输入你的问题，按 Enter 发送，Shift+Enter 换行..."
                      @keydown.enter.exact.prevent="sendMessage"
                      resize="none" />
            <div class="input-actions">
              <span class="input-hint">Enter 发送 · Shift+Enter 换行</span>
              <el-button type="primary" :loading="sending" :disabled="!inputMessage.trim()" @click="sendMessage">
                <el-icon><Promotion /></el-icon> 发送
              </el-button>
            </div>
          </div>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup>
import { ref, onMounted, nextTick, watch } from 'vue'
import { useRoute } from 'vue-router'
import { streamChat, getChatStatus, chatFeedback } from '@/api'
import { useUserStore } from '@/store/user'
import { ElMessage } from 'element-plus'

const route = useRoute()
const userStore = useUserStore()

const messages = ref([])
const mobileSidebarOpen = ref(false)
const inputMessage = ref('')
const sending = ref(false)
const sessionId = ref('')
const aiStatus = ref({ ollamaAvailable: false, message: '' })
const messagesContainer = ref(null)

const quickQuestions = [
  '贫困认定需要什么材料？',
  '请假流程是什么？',
  '宿舍报修多久能处理？',
  '奖学金什么时候发放？',
  '成绩单在哪里开具？',
  '校园卡丢了怎么补办？',
]

const suggestions = [
  '贫困认定需要什么材料？',
  '请假审批流程是怎样的？',
  '如何开具在读证明？',
  '宿舍报修电话是多少？',
]

function formatContent(content) {
  if (!content) return ''
  // 简单的 markdown 格式化
  return content
    .replace(/\n/g, '<br>')
    .replace(/\*\*(.*?)\*\*/g, '<strong>$1</strong>')
    .replace(/^### (.*?)$/gm, '<h4 style="margin:8px 0">$1</h4>')
    .replace(/^## (.*?)$/gm, '<h3 style="margin:10px 0">$1</h3>')
}

// 后端返回的响应时间为毫秒，展示统一换算为秒
function formatResponseTime(ms) {
  if (!ms && ms !== 0) return ''
  const seconds = ms / 1000
  return seconds < 10 ? seconds.toFixed(1) + ' 秒' : Math.round(seconds) + ' 秒'
}

function scrollToBottom() {
  nextTick(() => {
    if (messagesContainer.value) {
      messagesContainer.value.scrollTop = messagesContainer.value.scrollHeight
    }
  })
}

function sendQuickQuestion(q) {
  inputMessage.value = q
  sendMessage()
}

async function sendMessage() {
  const text = inputMessage.value.trim()
  if (!text || sending.value) return

  // 添加用户消息
  messages.value.push({ role: 'user', content: text })
  inputMessage.value = ''

  // 添加AI消息（初始为加载态，收到第一个字后转为流式文本）
  // 用数组索引引用，确保通过响应式数组修改时触发 Vue 重渲染
  const aiMsgIndex = messages.value.length
  messages.value.push({ role: 'assistant', content: '', loading: true, sources: [], responseTime: null })
  sending.value = true
  scrollToBottom()

  const setAi = (patch) => {
    const msg = messages.value[aiMsgIndex]
    if (msg) Object.assign(msg, patch)
  }

  try {
    const done = await streamChat(
      {
        message: text,
        sessionId: sessionId.value || undefined,
        userId: userStore.userInfo?.userId
      },
      (delta) => {
        // 收到首个片段即停止三点加载，开始逐字显示
        const msg = messages.value[aiMsgIndex]
        if (msg) {
          if (msg.loading) msg.loading = false
          msg.content += delta
        }
        scrollToBottom()
      }
    )
    if (done) {
      setAi({
        content: done.answer || messages.value[aiMsgIndex]?.content,
        sources: done.sources || [],
        responseTime: done.responseTime,
        messageId: done.messageId
      })
      if (done.sessionId) sessionId.value = done.sessionId
    }
  } catch (e) {
    setAi({
      content: '抱歉，AI 老师暂时不在线，请稍后再试。如果问题仍然存在，可以联系管理员处理。'
    })
  } finally {
    setAi({ loading: false })
    sending.value = false
    scrollToBottom()
  }
}

async function sendFeedback(msg, feedback) {
  if (msg.feedback === feedback) {
    msg.feedback = null
    return
  }
  if (!msg.messageId) {
    ElMessage.warning('该条消息暂无法提交反馈')
    return
  }
  try {
    await chatFeedback({ messageId: msg.messageId, feedback })
    msg.feedback = feedback
    ElMessage.success(feedback === 1 ? '感谢反馈' : '已记录反馈，我们会持续改进')
  } catch (e) {
    ElMessage.error('反馈提交失败，请稍后重试')
  }
}

function newChat() {
  messages.value = []
  sessionId.value = ''
}

async function checkAiStatus() {
  try {
    const res = await getChatStatus({ silent: true })
    aiStatus.value = res.data
  } catch {
    // 后端不可用时静默处理，状态显示离线即可，不弹错误
  }
}

onMounted(async () => {
  await checkAiStatus()
  // 从URL参数自动提问
  if (route.query.q) {
    inputMessage.value = route.query.q
    setTimeout(() => sendMessage(), 300)
  }
})
</script>

<style scoped>
.chat-page {
  height: calc(100vh - 140px);
  min-height: 500px;
}

.chat-container {
  display: flex;
  height: 100%;
  background: #fffdf9;
  border-radius: 12px;
  overflow: hidden;
  box-shadow: 0 2px 12px rgba(148, 30, 35, 0.06);
  border: 1px solid #e8dfd0;
}

.chat-sidebar {
  width: 280px;
  background: linear-gradient(180deg, #941e23 0%, #761317 100%);
  color: #fff;
  display: flex;
  flex-direction: column;
  flex-shrink: 0;
}

.sidebar-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 20px 16px;
  border-bottom: 1px solid rgba(184, 147, 90, 0.25);
}

.sidebar-header h3 {
  font-size: 16px;
  font-weight: 700;
  display: flex;
  align-items: center;
  gap: 8px;
  margin: 0;
  font-family: 'Noto Serif SC', 'SimSun', serif;
  color: #fffdf9;
}

.sidebar-header :deep(.el-button) {
  color: rgba(255, 253, 249, 0.7);
}

.sidebar-header :deep(.el-button:hover) {
  color: #b8935a;
}

.ai-status-bar {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 10px 16px;
  font-size: 12px;
  cursor: pointer;
  transition: background 0.2s;
}

.ai-status-bar:hover {
  background: rgba(255, 253, 249, 0.06);
}

.ai-status-bar.online { color: #8fce9e; }
.ai-status-bar.offline { color: #e6c47a; }

.status-dot {
  width: 8px;
  height: 8px;
  border-radius: 50%;
  background: currentColor;
  animation: pulse 2s infinite;
}

@keyframes pulse {
  0%, 100% { opacity: 1; }
  50% { opacity: 0.4; }
}

.quick-section, .capability-section {
  padding: 16px;
  border-bottom: 1px solid rgba(184, 147, 90, 0.15);
}

.quick-section h4, .capability-section h4 {
  font-size: 12px;
  color: rgba(255, 253, 249, 0.5);
  margin: 0 0 10px 0;
  text-transform: uppercase;
  letter-spacing: 2px;
  font-weight: 600;
}

.quick-list {
  display: flex;
  flex-direction: column;
  gap: 6px;
}

.quick-item {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 8px 10px;
  border-radius: 8px;
  font-size: 13px;
  cursor: pointer;
  transition: all 0.2s;
  color: rgba(255, 253, 249, 0.8);
}

.quick-item:hover {
  background: rgba(184, 147, 90, 0.15);
  color: #fffdf9;
}

.quick-item .el-icon {
  color: #b8935a;
}

.capability-list {
  display: flex;
  flex-direction: column;
  gap: 6px;
}

.cap-item {
  display: flex;
  align-items: center;
  gap: 8px;
  font-size: 13px;
  color: rgba(255, 253, 249, 0.7);
}

.cap-item .el-icon {
  color: #8fce9e;
}

.chat-main {
  flex: 1;
  display: flex;
  flex-direction: column;
  min-width: 0;
  background: #faf7f0;
}

.chat-messages {
  flex: 1;
  overflow-y: auto;
  padding: 24px;
  display: flex;
  flex-direction: column;
}

.welcome-message {
  text-align: center;
  padding: 60px 20px;
}

.welcome-avatar {
  width: 72px;
  height: 72px;
  border-radius: 50%;
  display: flex;
  align-items: center;
  justify-content: center;
  margin: 0 auto 20px;
  overflow: hidden;
  box-shadow: 0 4px 16px rgba(148, 30, 35, 0.2);
}
.welcome-avatar img {
  width: 100%;
  height: 100%;
  object-fit: cover;
  display: block;
}

.welcome-message h2 {
  font-size: 24px;
  font-weight: 700;
  margin-bottom: 12px;
  font-family: 'Noto Serif SC', 'SimSun', serif;
  color: #20201f;
}

.welcome-message p {
  color: #6f6d68;
  font-size: 14px;
  margin: 4px 0;
}

.welcome-suggestions {
  display: flex;
  flex-wrap: wrap;
  gap: 10px;
  justify-content: center;
  margin-top: 24px;
}

.suggestion-btn {
  padding: 8px 16px;
  background: #f7e9e7;
  border: 1px solid #e8c9c5;
  border-radius: 20px;
  font-size: 13px;
  color: #941e23;
  cursor: pointer;
  transition: all 0.2s;
}

.suggestion-btn:hover {
  background: #941e23;
  color: #fffdf9;
  border-color: #941e23;
}

.message-item {
  display: flex;
  gap: 12px;
  margin-bottom: 20px;
  max-width: 60%;
  align-items: flex-start;
}

.message-item.user {
  align-self: flex-end;
  flex-direction: row;
}

.message-item.assistant {
  align-self: flex-start;
}

.msg-avatar {
  width: 36px;
  height: 36px;
  border-radius: 50%;
  display: flex;
  align-items: center;
  justify-content: center;
  flex-shrink: 0;
  overflow: hidden;
}
.msg-avatar img {
  width: 100%;
  height: 100%;
  object-fit: cover;
  display: block;
}

.user-avatar {
  background: linear-gradient(135deg, #b8935a, #9a7a45);
  border-color: #d4b87a;
  font-size: 14px;
}

.msg-bubble {
  background: #fffdf9;
  border: 1px solid #e8dfd0;
  border-radius: 12px;
  padding: 12px 16px;
  font-size: 14px;
  line-height: 1.7;
  color: #20201f;
  position: relative;
  max-width: 100%;
  min-width: 0;
  box-shadow: 0 1px 4px rgba(0, 0, 0, 0.03);
}

.message-item.user .msg-bubble {
  background: linear-gradient(135deg, #941e23, #761317);
  border-color: #941e23;
  color: #fffdf9;
}

.msg-content {
  word-break: break-word;
}

.msg-sources {
  margin-top: 10px;
  padding-top: 8px;
  border-top: 1px solid #e8dfd0;
  font-size: 12px;
  color: #6f6d68;
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 6px;
}

.source-tag {
  background: #f7e9e7;
  color: #941e23;
  padding: 2px 8px;
  border-radius: 4px;
  font-size: 11px;
}

.msg-actions {
  display: flex;
  align-items: center;
  gap: 4px;
  margin-top: 8px;
}

.msg-actions :deep(.el-button) {
  font-size: 12px;
  padding: 4px 8px;
  color: #6f6d68;
}

.msg-actions :deep(.el-button.active) {
  color: #941e23;
}

.response-time {
  font-size: 11px;
  color: #a8a49c;
  margin-left: auto;
}

.typing-indicator {
  display: flex;
  gap: 4px;
  padding: 4px 0;
}

.typing-indicator span {
  width: 8px;
  height: 8px;
  background: #b8935a;
  border-radius: 50%;
  animation: typing 1.4s infinite;
}

.typing-indicator span:nth-child(2) { animation-delay: 0.2s; }
.typing-indicator span:nth-child(3) { animation-delay: 0.4s; }

@keyframes typing {
  0%, 60%, 100% { transform: translateY(0); opacity: 0.4; }
  30% { transform: translateY(-6px); opacity: 1; }
}

.chat-input-area {
  padding: 16px 24px;
  border-top: 1px solid #e8dfd0;
  background: #fffdf9;
}

.input-wrapper {
  background: #faf7f0;
  border: 1px solid #e8dfd0;
  border-radius: 12px;
  padding: 12px;
  transition: border-color 0.2s;
}

.input-wrapper:focus-within {
  border-color: #b8935a;
}

.input-actions {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-top: 8px;
}

.input-hint {
  font-size: 12px;
  color: #a8a49c;
}

/* 发送按钮 */
.input-actions :deep(.el-button--primary) {
  background: linear-gradient(135deg, #941e23, #761317);
  border: none;
}

.input-actions :deep(.el-button--primary:hover) {
  background: linear-gradient(135deg, #a8282d, #8a181d);
}

/* 移动端侧边栏切换按钮 */
.mobile-sidebar-toggle {
  display: none;
  position: absolute;
  top: 12px;
  left: 12px;
  z-index: 10;
  background: #941e23;
  border-color: #941e23;
}

/* ========== 移动端响应式 ========== */
@media (max-width: 768px) {
  .chat-page {
    height: calc(100vh - 100px);
    min-height: 400px;
  }
  .chat-container {
    position: relative;
  }
  .mobile-sidebar-toggle {
    display: flex;
  }
  .chat-sidebar {
    position: absolute;
    left: 0;
    top: 0;
    bottom: 0;
    z-index: 20;
    transform: translateX(-100%);
    transition: transform 0.3s ease;
    width: 260px;
  }
  .chat-sidebar.mobile-open {
    transform: translateX(0);
    box-shadow: 2px 0 12px rgba(0,0,0,0.15);
  }
  .sidebar-mask {
    display: none;
    position: absolute;
    top: 0;
    left: 0;
    right: 0;
    bottom: 0;
    background: rgba(0,0,0,0.4);
    z-index: 15;
  }
  .sidebar-mask.show {
    display: block;
  }
  .chat-messages {
    padding: 16px 12px;
  }
  .message-item {
    max-width: 85%;
    gap: 8px;
    margin-bottom: 14px;
  }
  .msg-avatar {
    width: 32px;
    height: 32px;
  }
  .msg-bubble {
    padding: 10px 12px;
    font-size: 13px;
    line-height: 1.6;
  }
  .welcome-message {
    padding: 30px 16px;
  }
  .welcome-avatar {
    width: 56px;
    height: 56px;
  }
  .welcome-message h2 {
    font-size: 18px;
  }
  .welcome-message p {
    font-size: 13px;
  }
  .welcome-suggestions {
    gap: 8px;
    margin-top: 16px;
  }
  .suggestion-btn {
    font-size: 12px;
    padding: 6px 12px;
  }
  .chat-input-area {
    padding: 12px;
  }
  .input-wrapper {
    padding: 10px;
  }
  .input-hint {
    font-size: 11px;
  }
}

@media (max-width: 480px) {
  .message-item {
    max-width: 90%;
  }
  .chat-sidebar {
    width: 240px;
  }
}
</style>
