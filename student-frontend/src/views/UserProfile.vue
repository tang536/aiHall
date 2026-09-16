<template>
  <div class="user-profile-page">
    <div v-loading="loading" class="profile-card">
      <template v-if="user">
        <!-- 头部信息 -->
        <div class="profile-header">
          <el-avatar :size="80" class="profile-avatar">
            {{ (user.displayName || '?').charAt(0) }}
          </el-avatar>
          <div class="profile-info">
            <div class="name-row">
              <h2 class="name">{{ user.displayName }}</h2>
              <el-tag v-if="isSelf" size="small" type="info" effect="plain">我自己</el-tag>
              <el-tag v-else-if="user.friend" size="small" type="success" effect="plain">好友</el-tag>
            </div>
            <div class="meta-row">
              <span v-if="user.college" class="meta-item">
                <el-icon><School /></el-icon> {{ user.college }}
              </span>
              <span v-if="user.major" class="meta-item">{{ user.major }}</span>
              <span v-if="user.grade" class="meta-item">{{ user.grade }}级</span>
            </div>
            <div v-if="user.maskedAccount" class="account-row">
              <el-icon><User /></el-icon> 学号 {{ user.maskedAccount }}
            </div>
          </div>
        </div>

        <!-- 操作按钮 -->
        <div class="profile-actions">
          <template v-if="isSelf">
            <el-button type="primary" @click="goEditProfile">
              <el-icon><Edit /></el-icon> 编辑个人资料
            </el-button>
          </template>
          <template v-else>
            <el-button type="primary" @click="startChat">
              <el-icon><ChatDotRound /></el-icon> 私聊
            </el-button>
            <el-button
              v-if="!user.friend"
              :loading="adding"
              @click="doAddFriend"
            >
              <el-icon><Plus /></el-icon> 加好友
            </el-button>
            <el-button v-else type="success" plain disabled>
              <el-icon><Check /></el-icon> 已是好友
            </el-button>
          </template>
        </div>

        <el-alert
          type="info"
          :closable="false"
          class="privacy-tip"
          title="为保护同学隐私，仅展示脱敏后的学号；手机号与邮箱不会公开。"
          show-icon
        />
      </template>

      <el-empty v-else-if="!loading" description="用户不存在或已注销" :image-size="80" />
    </div>
  </div>
</template>

<script setup>
import { computed, onMounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { School, User, Edit, ChatDotRound, Plus, Check } from '@element-plus/icons-vue'
import { useUserStore } from '@/store/user'
import { getUserProfile, sendFriendRequest } from '@/api'

const route = useRoute()
const router = useRouter()
const userStore = useUserStore()

const loading = ref(true)
const adding = ref(false)
const user = ref(null)

const isSelf = computed(() =>
  user.value && Number(user.value.userId) === Number(userStore.userId)
)

async function load() {
  loading.value = true
  try {
    const res = await getUserProfile(route.params.id)
    user.value = res?.data || null
  } catch {
    user.value = null
  } finally {
    loading.value = false
  }
}

function startChat() {
  if (!userStore.isLoggedIn) {
    router.push('/login')
    return
  }
  router.push({ path: '/messages', query: { peerId: user.value.userId } })
}

async function doAddFriend() {
  if (!userStore.isLoggedIn) {
    router.push('/login')
    return
  }
  adding.value = true
  try {
    const res = await sendFriendRequest({ userId: user.value.userId })
    ElMessage.success(res?.message || '好友申请已发送')
    user.value.friend = true
  } catch {
    /* 拦截器已提示 */
  } finally {
    adding.value = false
  }
}

function goEditProfile() {
  router.push('/profile')
}

onMounted(load)
</script>

<style scoped>
.user-profile-page {
  max-width: 720px;
  margin: 0 auto;
}

.profile-card {
  background: #fff;
  border-radius: 14px;
  padding: 28px;
  border: 1px solid #ebeef5;
}

.profile-header {
  display: flex;
  align-items: center;
  gap: 20px;
  margin-bottom: 24px;
}

.profile-avatar {
  background: linear-gradient(135deg, #409eff, #67c23a);
  color: #fff;
  font-weight: 700;
  font-size: 32px;
  flex-shrink: 0;
}

.profile-info {
  flex: 1;
  min-width: 0;
}

.name-row {
  display: flex;
  align-items: center;
  gap: 10px;
  margin-bottom: 8px;
}

.name {
  margin: 0;
  font-size: 22px;
  font-weight: 700;
  color: #303133;
}

.meta-row {
  display: flex;
  flex-wrap: wrap;
  gap: 12px;
  margin-bottom: 6px;
  font-size: 13px;
  color: #606266;
}

.meta-item {
  display: inline-flex;
  align-items: center;
  gap: 4px;
}

.account-row {
  display: flex;
  align-items: center;
  gap: 4px;
  font-size: 12px;
  color: #909399;
}

.profile-actions {
  display: flex;
  gap: 12px;
  margin-bottom: 20px;
  padding-bottom: 20px;
  border-bottom: 1px solid #f0f2f5;
}

.privacy-tip {
  margin-top: 8px;
}

@media (max-width: 600px) {
  .profile-card {
    padding: 20px 16px;
  }

  .profile-header {
    flex-direction: column;
    text-align: center;
  }

  .meta-row {
    justify-content: center;
  }

  .profile-actions {
    justify-content: center;
  }
}
</style>
