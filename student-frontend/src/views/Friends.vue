<template>
  <div class="friends-page">
    <div class="page-header">
      <h2><el-icon><UserFilled /></el-icon> 我的好友</h2>
      <p>通过平台账号（学号）搜索并添加好友，添加后即可一对一开始私聊</p>
    </div>

    <el-row :gutter="16">
      <!-- 左侧：搜索 + 好友列表 -->
      <el-col :xs="24" :md="14">
        <el-card shadow="never" class="block">
          <template #header>
            <div class="block-head">
              <span>查找同学</span>
            </div>
          </template>
          <div class="search-row">
            <el-input
              v-model="keyword"
              placeholder="输入对方的平台账号（学号）"
              clearable
              @keyup.enter="doSearch"
            >
              <template #prefix><el-icon><Search /></el-icon></template>
            </el-input>
            <el-button type="primary" :loading="searching" @click="doSearch">搜索</el-button>
          </div>

          <div v-if="searchResult" class="search-result">
            <UserCard :user="searchResult" show-chat show-add @chat="startChat" @add="addFriend" @remove="removeFriend" />
          </div>
          <el-empty
            v-else-if="searched"
            description="未找到该账号对应的用户"
            :image-size="70"
          />
          <p v-else class="hint">提示：仅能通过完整平台账号精确查找，保护同学隐私。</p>
        </el-card>

        <el-card shadow="never" class="block">
          <template #header>
            <div class="block-head">
              <span>好友列表（{{ friends.length }}）</span>
              <el-button size="small" text @click="loadFriends"><el-icon><Refresh /></el-icon></el-button>
            </div>
          </template>
          <div v-loading="loading" class="friend-list">
            <UserCard
              v-for="f in friends"
              :key="f.userId"
              :user="f"
              show-chat
              class="friend-item"
              @chat="startChat"
            >
              <template #actions>
                <el-button size="small" type="primary" plain @click="startChat(f)">
                  <el-icon><ChatDotRound /></el-icon> 聊天
                </el-button>
                <el-button size="small" text type="danger" @click="removeFriend(f)">删除</el-button>
              </template>
            </UserCard>
            <el-empty v-if="!loading && friends.length === 0" description="还没有好友，去搜索同学吧" :image-size="70" />
          </div>
        </el-card>
      </el-col>

      <!-- 右侧：申请 -->
      <el-col :xs="24" :md="10">
        <el-card shadow="never" class="block">
          <template #header>
            <div class="block-head">
              <span>
                好友申请
                <el-badge v-if="requests.length" :value="requests.length" class="req-badge" />
              </span>
              <el-button size="small" text @click="loadAll"><el-icon><Refresh /></el-icon></el-button>
            </div>
          </template>
          <div v-loading="loading" class="request-list">
            <div v-for="r in requests" :key="r.id" class="request-item">
              <el-avatar :size="38" class="avatar" :src="r.user?.avatar ? resolveAvatarUrl(r.user.avatar) : ''">{{ (r.user?.displayName || '?').charAt(0) }}</el-avatar>
              <div class="req-info">
                <div class="req-name">{{ r.user?.displayName || '未知用户' }}</div>
                <div class="req-meta">
                  <span v-if="r.user?.college">{{ r.user.college }}</span>
                  <span v-if="r.user?.maskedAccount">学号 {{ r.user.maskedAccount }}</span>
                </div>
                <div v-if="r.message" class="req-msg">“{{ r.message }}”</div>
              </div>
              <div class="req-actions">
                <el-button size="small" type="primary" @click="accept(r)">接受</el-button>
                <el-button size="small" text @click="reject(r)">拒绝</el-button>
              </div>
            </div>
            <el-empty v-if="!loading && requests.length === 0" description="暂无新的好友申请" :image-size="70" />
          </div>
        </el-card>

        <el-card shadow="never" class="block">
          <template #header>
            <div class="block-head"><span>我发出的申请（{{ sent.length }}）</span></div>
          </template>
          <div v-loading="loading" class="sent-list">
            <div v-for="r in sent" :key="r.id" class="sent-item">
              <span class="sent-name">{{ r.user?.displayName || '未知用户' }}</span>
              <el-tag size="small" :type="sentStatusType(r.status)" effect="plain">{{ sentStatusLabel(r.status) }}</el-tag>
            </div>
            <el-empty v-if="!loading && sent.length === 0" description="暂无记录" :image-size="60" />
          </div>
        </el-card>
      </el-col>
    </el-row>
  </div>
</template>

<script setup>
import { onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import UserCard from '@/components/UserCard.vue'
import {
  searchUser, listFriends, friendRequests, sentFriendRequests,
  sendFriendRequest, acceptFriend, rejectFriend, deleteFriend
} from '@/api'
import { resolveAvatarUrl } from '@/utils/community'

const router = useRouter()

const keyword = ref('')
const searching = ref(false)
const searched = ref(false)
const searchResult = ref(null)

const loading = ref(false)
const friends = ref([])
const requests = ref([])
const sent = ref([])

async function doSearch() {
  const kw = keyword.value.trim()
  if (!kw) {
    ElMessage.warning('请输入平台账号（学号）')
    return
  }
  searching.value = true
  searched.value = true
  try {
    const res = await searchUser(kw)
    // 后端返回数组（支持模糊匹配），前端按精确学号查找取第一条
    searchResult.value = Array.isArray(res?.data) ? (res.data[0] || null) : (res?.data || null)
  } catch {
    searchResult.value = null
  } finally {
    searching.value = false
  }
}

async function loadFriends() {
  const res = await listFriends()
  friends.value = res?.data || []
}

async function loadAll() {
  loading.value = true
  try {
    const [f, r, s] = await Promise.all([listFriends(), friendRequests(), sentFriendRequests()])
    friends.value = f?.data || []
    requests.value = r?.data || []
    sent.value = s?.data || []
  } finally {
    loading.value = false
  }
}

async function addFriend(user) {
  if (!user?.userId) return
  try {
    const res = await sendFriendRequest({ userId: user.userId })
    ElMessage.success(res?.message || '好友申请已发送')
    if (searchResult.value) searchResult.value.friend = true
    loadAll()
  } catch {
    /* 拦截器已提示 */
  }
}

async function accept(r) {
  try {
    await acceptFriend(r.id)
    ElMessage.success('已添加为好友')
    loadAll()
  } catch {
    /* 拦截器已提示 */
  }
}

async function reject(r) {
  try {
    await rejectFriend(r.id)
    ElMessage.success('已拒绝该申请')
    loadAll()
  } catch {
    /* 拦截器已提示 */
  }
}

async function removeFriend(f) {
  try {
    await ElMessageBox.confirm(`确定删除好友「${f.displayName}」吗？`, '提示', { type: 'warning' })
  } catch {
    return
  }
  try {
    await deleteFriend(f.userId)
    ElMessage.success('已删除好友')
    if (searchResult.value && Number(searchResult.value.userId) === Number(f.userId)) {
      searchResult.value.friend = false
    }
    loadAll()
  } catch {
    /* 拦截器已提示 */
  }
}

function startChat(user) {
  if (!user?.userId) return
  router.push({ path: '/messages', query: { peerId: user.userId } })
}

function sentStatusLabel(status) {
  return { PENDING: '待对方处理', ACCEPTED: '已通过', REJECTED: '已拒绝' }[status] || status
}

function sentStatusType(status) {
  return { PENDING: 'warning', ACCEPTED: 'success', REJECTED: 'info' }[status] || 'info'
}

onMounted(loadAll)
</script>

<style scoped>
.block {
  margin-bottom: 16px;
}

.block-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  font-weight: 600;
  color: #303133;
}

.req-badge {
  margin-left: 6px;
}

.search-row {
  display: flex;
  gap: 10px;
}

.search-result {
  margin-top: 12px;
}

.hint {
  margin: 10px 0 0;
  font-size: 12px;
  color: #a8abb2;
}

.friend-list,
.request-list,
.sent-list {
  min-height: 60px;
}

.friend-item {
  margin-bottom: 10px;
}

.avatar {
  flex-shrink: 0;
  background: linear-gradient(135deg, #409eff, #67c23a);
  color: #fff;
  font-weight: 700;
}

.request-item {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 10px 0;
  border-bottom: 1px solid #f5f7fa;
}

.request-item:last-child {
  border-bottom: none;
}

.req-info {
  flex: 1;
  min-width: 0;
}

.req-name {
  font-size: 14px;
  font-weight: 600;
  color: #303133;
}

.req-meta {
  display: flex;
  gap: 8px;
  margin-top: 2px;
  font-size: 12px;
  color: #909399;
}

.req-msg {
  margin-top: 4px;
  font-size: 12px;
  color: #909399;
}

.req-actions {
  display: flex;
  gap: 6px;
  flex-shrink: 0;
}

.sent-item {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 8px 0;
  border-bottom: 1px solid #f5f7fa;
}

.sent-item:last-child {
  border-bottom: none;
}

.sent-name {
  font-size: 13px;
  color: #606266;
}
</style>
