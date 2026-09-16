<template>
  <div class="post-detail" v-loading="loading">
    <div class="page-header">
      <h2><el-icon><ChatLineSquare /></el-icon> 帖子详情</h2>
      <el-button text @click="$router.back()">
        <el-icon><Back /></el-icon> 返回列表
      </el-button>
    </div>

    <template v-if="post">
      <div class="post-main">
        <!-- 帖子正文 -->
        <div class="post-body">
          <div class="author-row">
            <el-avatar :size="44" class="avatar clickable" @click="goUserProfile(post.author)">{{ (post.author?.displayName || '?').charAt(0) }}</el-avatar>
            <div class="author-info">
              <div class="name-line">
                <span class="name clickable" @click="goUserProfile(post.author)">{{ post.author?.displayName }}</span>
                <el-tag size="small" effect="plain">{{ POST_CATEGORY_LABEL[post.category] || '其他' }}</el-tag>
              </div>
              <div class="meta-line">
                <span v-if="post.author?.college">{{ post.author.college }}</span>
                <span v-if="post.author?.major">{{ post.author.major }}</span>
                <span v-if="post.author?.grade">{{ post.author.grade }}</span>
                <span>学号 {{ post.author?.maskedAccount }}</span>
                <span>{{ relativeTime(post.createTime) }}</span>
              </div>
            </div>
            <div class="author-actions">
              <el-button v-if="!isAuthor" size="small" type="primary" plain @click="goChat(post.author)">
                <el-icon><ChatDotRound /></el-icon> 聊一聊
              </el-button>
              <el-button v-if="!isAuthor && !post.author?.friend" size="small" plain @click="addFriend(post.author)">
                <el-icon><Plus /></el-icon> 加好友
              </el-button>
            </div>
          </div>

          <h3 class="post-title">{{ post.title || '（无标题）' }}</h3>
          <p class="post-content">{{ post.content }}</p>

          <div v-if="splitImages(post.images).length" class="post-images">
            <el-image
              v-for="(url, idx) in splitImages(post.images)"
              :key="idx"
              :src="url"
              fit="cover"
              class="post-image"
              :preview-src-list="splitImages(post.images)"
              :initial-index="idx"
            />
          </div>

          <div class="post-actions">
            <el-button text type="primary" :disabled="!userStore.isLoggedIn" @click="doLike">
              <el-icon><Pointer /></el-icon> 点赞 {{ post.likeCount || 0 }}
            </el-button>
            <el-button
              text
              :type="favorited ? 'danger' : 'primary'"
              :disabled="!userStore.isLoggedIn"
              :loading="favoriting"
              @click="doFavorite"
            >
              <el-icon><Star /></el-icon> {{ favorited ? '已收藏' : '收藏' }}
            </el-button>
            <el-button v-if="isAuthor" text type="danger" @click="doDeletePost">
              <el-icon><Delete /></el-icon> 删除帖子
            </el-button>
          </div>
        </div>

        <!-- 回复区 -->
        <div class="reply-panel">
          <h4 class="reply-title">{{ post.replies?.length || 0 }} 条回复</h4>

          <div v-if="userStore.isLoggedIn" class="reply-editor">
            <el-input
              v-model="replyContent"
              type="textarea"
              :rows="3"
              maxlength="2000"
              :placeholder="replyTarget ? `回复 @${replyTarget.authorName}` : '写下你的回复…'"
            />
            <div class="editor-foot">
              <span v-if="replyTarget" class="target-tip">
                正在回复 @{{ replyTarget.authorName }}
                <el-button text size="small" @click="cancelReply">取消</el-button>
              </span>
              <span v-else></span>
              <el-button type="primary" :loading="replying" :disabled="!replyContent.trim()" @click="submitReply">
                发表回复
              </el-button>
            </div>
          </div>
          <el-alert v-else type="info" :closable="false" title="登录后即可回复帖子" style="margin-bottom: 12px" />

          <div class="reply-list">
            <div
              v-for="reply in post.replies"
              :key="reply.id"
              class="reply-item"
              :class="{ nested: !!reply.parentId }"
            >
              <el-avatar :size="32" class="reply-avatar clickable" @click="goUserProfile(reply.author)">{{ (reply.author?.displayName || '?').charAt(0) }}</el-avatar>
              <div class="reply-body">
                <div class="reply-head">
                  <span class="reply-author clickable" @click="goUserProfile(reply.author)">{{ reply.author?.displayName }}</span>
                  <span v-if="reply.replyToName" class="reply-to">回复 @{{ reply.replyToName }}</span>
                  <span v-if="Number(reply.userId) === Number(post.userId)" class="op-tag">楼主</span>
                  <span class="reply-time">{{ relativeTime(reply.createTime) }}</span>
                </div>
                <p class="reply-content">{{ reply.content }}</p>
                <div class="reply-actions">
                  <el-button text size="small" @click="startReply(reply)">回复</el-button>
                  <el-button
                    v-if="canDeleteReply(reply)"
                    text
                    size="small"
                    type="danger"
                    @click="doDeleteReply(reply)"
                  >删除</el-button>
                  <el-button
                    v-if="Number(reply.userId) !== Number(userStore.userId)"
                    text
                    size="small"
                    @click="goChat(reply.author)"
                  >私聊</el-button>
                </div>
              </div>
            </div>
            <el-empty v-if="!post.replies?.length" description="还没有回复，来做第一个回复的人" :image-size="70" />
          </div>
        </div>
      </div>
    </template>

    <el-empty v-else-if="!loading" description="帖子不存在或已被删除" />
  </div>
</template>

<script setup>
import { computed, onMounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import { useUserStore } from '@/store/user'
import { getPost, replyPost, deleteReply, likePost, deletePost, sendFriendRequest, togglePostFavorite, checkPostFavorite } from '@/api'
import { POST_CATEGORY_LABEL, splitImages, relativeTime } from '@/utils/community'

const route = useRoute()
const router = useRouter()
const userStore = useUserStore()

const loading = ref(true)
const post = ref(null)
const replyContent = ref('')
const replyTarget = ref(null)
const replying = ref(false)
const favorited = ref(false)
const favoriting = ref(false)

const isAuthor = computed(() => post.value && Number(post.value.userId) === Number(userStore.userId))

async function load() {
  loading.value = true
  try {
    const res = await getPost(route.params.id)
    post.value = res?.data || null
    // 检查收藏状态
    if (post.value && userStore.isLoggedIn) {
      try {
        const favRes = await checkPostFavorite(route.params.id)
        favorited.value = !!favRes?.data?.favorited
      } catch { /* 忽略 */ }
    } else {
      favorited.value = false
    }
  } catch {
    post.value = null
  } finally {
    loading.value = false
  }
}

function startReply(reply) {
  if (!userStore.isLoggedIn) {
    ElMessage.warning('请先登录后再回复')
    return
  }
  replyTarget.value = { id: reply.id, authorName: reply.author?.displayName || '该用户' }
}

function cancelReply() {
  replyTarget.value = null
}

async function submitReply() {
  const content = replyContent.value.trim()
  if (!content) return
  replying.value = true
  try {
    await replyPost(post.value.id, {
      content,
      parentId: replyTarget.value?.id ?? null
    })
    ElMessage.success('回复成功')
    replyContent.value = ''
    replyTarget.value = null
    await load()
  } finally {
    replying.value = false
  }
}

function canDeleteReply(reply) {
  return Number(reply.userId) === Number(userStore.userId) || isAuthor.value
}

async function doDeleteReply(reply) {
  try {
    await ElMessageBox.confirm('确定删除这条回复吗？', '删除确认', {
      confirmButtonText: '确定删除', cancelButtonText: '取消', type: 'warning'
    })
  } catch {
    return
  }
  await deleteReply(reply.id)
  ElMessage.success('已删除')
  await load()
}

async function doLike() {
  const res = await likePost(post.value.id)
  post.value.likeCount = res?.data?.likeCount ?? (post.value.likeCount || 0) + 1
}

async function doFavorite() {
  if (!userStore.isLoggedIn) {
    router.push('/login')
    return
  }
  favoriting.value = true
  try {
    const res = await togglePostFavorite(post.value.id)
    favorited.value = !!res?.data?.favorited
    ElMessage.success(favorited.value ? '已加入收藏' : '已取消收藏')
  } catch {
    /* 拦截器已提示 */
  } finally {
    favoriting.value = false
  }
}

async function doDeletePost() {
  try {
    await ElMessageBox.confirm('确定删除这篇帖子吗？删除后无法恢复。', '删除确认', {
      confirmButtonText: '确定删除', cancelButtonText: '取消', type: 'warning'
    })
  } catch {
    return
  }
  await deletePost(post.value.id)
  ElMessage.success('已删除')
  router.push('/posts')
}

function goUserProfile(user) {
  if (!user?.userId) return
  router.push(`/user/${user.userId}`)
}

function goChat(user) {
  if (!userStore.isLoggedIn) {
    router.push('/login')
    return
  }
  router.push({ path: '/messages', query: { peerId: user.userId } })
}

async function addFriend(user) {
  if (!userStore.isLoggedIn) {
    router.push('/login')
    return
  }
  try {
    const res = await sendFriendRequest({ userId: user.userId })
    ElMessage.success(res?.message || '好友申请已发送')
    user.friend = true
  } catch {
    /* 提示由拦截器处理 */
  }
}

onMounted(() => {
  if (!userStore.isLoggedIn) {
    // 未登录也允许浏览，仅限制互动
  }
  load()
})
</script>

<style scoped>
.post-main {
  display: grid;
  grid-template-columns: minmax(0, 1.25fr) minmax(0, 1fr);
  gap: 20px;
  align-items: start;
}

.post-body,
.reply-panel {
  background: #fff;
  border: 1px solid #ebeef5;
  border-radius: 12px;
  padding: 20px;
}

.author-row {
  display: flex;
  align-items: center;
  gap: 12px;
}

.avatar,
.reply-avatar {
  background: linear-gradient(135deg, #409eff, #67c23a);
  color: #fff;
  font-weight: 700;
  flex-shrink: 0;
}

.clickable {
  cursor: pointer;
}

.clickable:hover {
  opacity: 0.8;
}

.author-info {
  flex: 1;
  min-width: 0;
}

.name-line {
  display: flex;
  align-items: center;
  gap: 8px;
}

.name {
  font-weight: 600;
  color: #303133;
}

.meta-line {
  display: flex;
  flex-wrap: wrap;
  gap: 10px;
  margin-top: 3px;
  font-size: 12px;
  color: #909399;
}

.author-actions {
  display: flex;
  gap: 8px;
  flex-shrink: 0;
}

.post-title {
  margin: 18px 0 10px;
  font-size: 20px;
  color: #303133;
  line-height: 1.5;
}

.post-content {
  margin: 0;
  color: #606266;
  line-height: 1.9;
  white-space: pre-wrap;
}

.post-images {
  display: flex;
  flex-wrap: wrap;
  gap: 10px;
  margin-top: 14px;
}

.post-image {
  width: 150px;
  height: 112px;
  border-radius: 8px;
  overflow: hidden;
}

.post-actions {
  display: flex;
  gap: 12px;
  margin-top: 16px;
  padding-top: 14px;
  border-top: 1px solid #f2f3f5;
}

.reply-title {
  margin: 0 0 14px;
  font-size: 15px;
  color: #303133;
}

.reply-editor {
  margin-bottom: 18px;
}

.editor-foot {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-top: 8px;
}

.target-tip {
  font-size: 12px;
  color: #909399;
}

.reply-list {
  display: grid;
  gap: 14px;
}

.reply-item {
  display: flex;
  gap: 10px;
}

.reply-item.nested {
  margin-left: 24px;
  padding-left: 12px;
  border-left: 2px solid #f2f3f5;
}

.reply-body {
  flex: 1;
  min-width: 0;
}

.reply-head {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 8px;
  font-size: 12px;
}

.reply-author {
  font-weight: 600;
  color: #303133;
  font-size: 13px;
}

.reply-to {
  color: #409eff;
}

.op-tag {
  padding: 0 6px;
  border-radius: 3px;
  background: #ecf5ff;
  color: #409eff;
  font-size: 11px;
}

.reply-time {
  color: #c0c4cc;
}

.reply-content {
  margin: 5px 0 2px;
  color: #606266;
  font-size: 13px;
  line-height: 1.7;
  white-space: pre-wrap;
}

.reply-actions {
  display: flex;
  gap: 4px;
}

@media (max-width: 900px) {
  .post-main {
    grid-template-columns: 1fr;
  }
  .author-row {
    flex-wrap: wrap;
  }
}
</style>
