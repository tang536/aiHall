<template>
  <div class="chat-window">
    <div ref="listRef" class="msg-list">
      <el-empty v-if="messages.length === 0" description="还没有消息，打个招呼吧" :image-size="70" />
      <div
        v-for="msg in messages"
        :key="msg.id"
        class="msg-row"
        :class="{ mine: isMine(msg) }"
      >
        <el-avatar
          :size="34"
          class="avatar"
          :class="{ clickable: !isMine(msg) }"
          :src="!isMine(msg) && props.peer?.avatar ? resolveAvatarUrl(props.peer.avatar) : ''"
          @click="!isMine(msg) && goPeerProfile()"
        >{{ avatarText(msg) }}</el-avatar>
        <div class="bubble-wrap">
          <div class="bubble" :class="{ mine: isMine(msg), share: isShare(msg) }">
            <!-- 分享卡片：商品 / 帖子 -->
            <div v-if="isShare(msg)" class="share-card" @click="openShare(msg)">
              <el-icon><component :is="msg.msgType === 'ITEM' ? 'Goods' : 'Document'" /></el-icon>
              <div class="share-body">
                <div class="share-title">{{ msg.content || (msg.msgType === 'ITEM' ? '商品分享' : '帖子分享') }}</div>
                <div class="share-tip">点击查看详情</div>
              </div>
            </div>
            <template v-else>
              <el-image
                v-if="msg.msgType === 'IMAGE'"
                :src="msg.content"
                :preview-src-list="[msg.content]"
                fit="cover"
                class="msg-image"
              />
              <span v-else class="msg-text">{{ msg.content }}</span>
            </template>
          </div>
          <div class="msg-time">{{ formatTime(msg.createTime) }}</div>
        </div>
      </div>
    </div>

    <div class="input-area">
      <el-input
        v-model="draft"
        type="textarea"
        :rows="2"
        resize="none"
        maxlength="2000"
        placeholder="输入消息，Enter 发送，Shift + Enter 换行"
        @keydown.enter.exact.prevent="submit"
      />
      <div class="input-actions">
        <el-upload
          :show-file-list="false"
          accept="image/*"
          :http-request="uploadAndSend"
          class="image-btn"
        >
          <el-button text :loading="uploading">
            <el-icon><Picture /></el-icon> 图片
          </el-button>
        </el-upload>
        <el-button type="primary" :loading="sending" :disabled="!draft.trim()" @click="submit">
          <el-icon><Promotion /></el-icon> 发送
        </el-button>
      </div>
    </div>
  </div>
</template>

<script setup>
import { computed, nextTick, ref, watch } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { uploadImage } from '@/api'
import { formatTime } from '@/utils/format'
import { resolveAvatarUrl } from '@/utils/community'

const props = defineProps({
  /** 已按时间正序的消息列表 */
  messages: { type: Array, default: () => [] },
  /** 聊天对象（脱敏后的公开信息） */
  peer: { type: Object, default: () => ({}) },
  selfId: { type: [Number, String], default: null },
  selfName: { type: String, default: '我' },
  sending: { type: Boolean, default: false }
})
const emit = defineEmits(['send'])
const router = useRouter()

const draft = ref('')
const uploading = ref(false)
const listRef = ref(null)

function isMine(msg) {
  return Number(msg.senderId) === Number(props.selfId)
}

function isShare(msg) {
  return msg.msgType === 'ITEM' || msg.msgType === 'POST'
}

function avatarText(msg) {
  const name = isMine(msg) ? props.selfName : props.peer?.displayName
  return (name || '?').charAt(0)
}

function openShare(msg) {
  if (msg.msgType === 'ITEM' && msg.refId) router.push(`/market/${msg.refId}`)
  if (msg.msgType === 'POST' && msg.refId) router.push(`/posts/${msg.refId}`)
}

function goPeerProfile() {
  if (props.peer?.userId) {
    router.push(`/user/${props.peer.userId}`)
  }
}

function scrollToBottom() {
  nextTick(() => {
    const el = listRef.value
    if (el) el.scrollTop = el.scrollHeight
  })
}

watch(() => props.messages.length, scrollToBottom, { immediate: true })
watch(() => props.peer?.userId, scrollToBottom)

function submit() {
  const text = draft.value.trim()
  if (!text) return
  emit('send', { content: text, msgType: 'TEXT' })
  draft.value = ''
}

async function uploadAndSend(option) {
  const file = option.file
  if (!file) return
  if (file.size > 5 * 1024 * 1024) {
    ElMessage.error('图片大小不能超过 5MB')
    return
  }
  uploading.value = true
  try {
    const res = await uploadImage(file)
    const url = res?.data?.url
    if (url) emit('send', { content: url, msgType: 'IMAGE' })
  } finally {
    uploading.value = false
  }
}
</script>

<style scoped>
.chat-window {
  display: flex;
  flex-direction: column;
  height: 100%;
  min-height: 0;
}

.msg-list {
  flex: 1;
  overflow-y: auto;
  padding: 16px;
  background: #faf7f0;
  min-height: 0;
}

.msg-row {
  display: flex;
  gap: 10px;
  margin-bottom: 14px;
}

.msg-row.mine {
  flex-direction: row-reverse;
}

.avatar {
  flex-shrink: 0;
  background: linear-gradient(135deg, #941e23, #761317);
  border: 1.5px solid #b8935a;
  color: #fffdf9;
  font-weight: 700;
  font-family: 'Noto Serif SC', 'SimSun', serif;
}

.avatar.clickable {
  cursor: pointer;
  transition: opacity 0.2s;
}

.avatar.clickable:hover {
  opacity: 0.8;
}

.bubble-wrap {
  max-width: 68%;
  display: flex;
  flex-direction: column;
}

.msg-row.mine .bubble-wrap {
  align-items: flex-end;
}

.bubble {
  padding: 9px 13px;
  border-radius: 10px;
  background: #fffdf9;
  border: 1px solid #e8dfd0;
  color: #20201f;
  font-size: 14px;
  line-height: 1.6;
  word-break: break-word;
  white-space: pre-wrap;
  box-shadow: 0 1px 3px rgba(0, 0, 0, 0.03);
}

.bubble.mine {
  background: linear-gradient(135deg, #941e23, #761317);
  border-color: #941e23;
  color: #fffdf9;
}

.bubble.share {
  background: #fffdf9;
  border-color: #ddd7cd;
  color: #20201f;
  padding: 0;
  overflow: hidden;
}

.msg-image {
  max-width: 180px;
  max-height: 180px;
  border-radius: 6px;
  display: block;
}

.msg-time {
  margin-top: 4px;
  font-size: 11px;
  color: #a8a49c;
}

.share-card {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 10px 12px;
  cursor: pointer;
  min-width: 180px;
  transition: background 0.2s;
}

.share-card:hover {
  background: #f7f3ec;
}

.share-card .el-icon {
  color: #941e23;
  font-size: 20px;
}

.share-title {
  font-size: 13px;
  font-weight: 600;
  color: #20201f;
}

.share-tip {
  font-size: 11px;
  color: #6f6d68;
}

.input-area {
  border-top: 1px solid #e8dfd0;
  padding: 10px 12px 12px;
  background: #fffdf9;
}

.input-actions {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-top: 8px;
}

.input-actions :deep(.el-button--primary) {
  background: linear-gradient(135deg, #941e23, #761317);
  border: none;
}

.input-actions :deep(.el-button--primary:hover) {
  background: linear-gradient(135deg, #a8282d, #8a181d);
}

.image-btn {
  display: inline-block;
}

.image-btn :deep(.el-button) {
  color: #6f6d68;
}

.image-btn :deep(.el-button:hover) {
  color: #941e23;
}
</style>
