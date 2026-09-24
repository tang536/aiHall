<template>
  <div class="user-profile-page">
    <div v-loading="loading" class="profile-card">
      <template v-if="user">
        <!-- 头部信息 -->
        <div class="profile-header">
          <el-avatar :size="80" class="profile-avatar" :src="user.avatar ? resolveAvatarUrl(user.avatar) : ''">
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
            <el-button v-else type="danger" plain :loading="removing" @click="doRemoveFriend">
              <el-icon><Delete /></el-icon> 删好友
            </el-button>
          </template>
        </div>
      </template>

      <el-empty v-else-if="!loading" description="用户不存在或已注销" :image-size="80" />
    </div>
  </div>
</template>

<script setup>
import { computed, onMounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import { School, User, Edit, ChatDotRound, Plus, Delete } from '@element-plus/icons-vue'
import { useUserStore } from '@/store/user'
import { getUserProfile, sendFriendRequest, deleteFriend } from '@/api'
import { resolveAvatarUrl } from '@/utils/community'

const route = useRoute()
const router = useRouter()
const userStore = useUserStore()

const loading = ref(true)
const adding = ref(false)
const removing = ref(false)
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

async function doRemoveFriend() {
  try {
    await ElMessageBox.confirm('确定删除该好友吗？删除后需要重新申请才能恢复。', '删除好友', {
      confirmButtonText: '确定删除', cancelButtonText: '取消', type: 'warning'
    })
  } catch {
    return
  }
  removing.value = true
  try {
    await deleteFriend(user.value.userId)
    ElMessage.success('已删除好友')
    user.value.friend = false
  } catch {
    /* 拦截器已提示 */
  } finally {
    removing.value = false
  }
}

function goEditProfile() {
  router.push('/profile')
}

onMounted(load)
</script>


      param($m) $m.Groups[1].Value -replace 'border-radius:\s*\d+px;', 'border-radius: 0;'
    
