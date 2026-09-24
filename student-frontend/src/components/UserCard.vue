<template>
  <div class="user-card" :class="{ compact }">
    <el-badge :value="unread" :hidden="!unread" :max="99" class="unread-badge">
      <el-avatar :size="compact ? 36 : 48" class="avatar clickable" :src="user?.avatar ? resolveAvatarUrl(user.avatar) : ''" @click="goProfile">
        {{ (user?.displayName || '?').charAt(0) }}
      </el-avatar>
    </el-badge>
    <div class="info">
      <div class="name-row">
        <span class="name clickable" @click="goProfile">{{ user?.displayName || '未知用户' }}</span>
        <el-tag v-if="user?.friend" size="small" type="success" effect="plain">好友</el-tag>
      </div>
      <div class="meta">
        <span v-if="user?.college">{{ user.college }}</span>
        <span v-if="user?.major">{{ user.major }}</span>
        <span v-if="user?.grade">{{ user.grade }}</span>
      </div>
      <!-- 学号按平台隐私策略脱敏展示 -->
      <div v-if="user?.maskedAccount" class="account">学号 {{ user.maskedAccount }}</div>
    </div>
    <div class="actions">
      <slot name="actions">
        <el-button v-if="showChat" size="small" type="primary" plain @click="$emit('chat', user)">
          <el-icon><ChatDotRound /></el-icon> 聊一聊
        </el-button>
        <el-button v-if="showAdd && !user?.friend && !isSelf" size="small" plain @click="$emit('add', user)">
          <el-icon><Plus /></el-icon> 加好友
        </el-button>
        <el-button v-if="showAdd && user?.friend && !isSelf" size="small" type="danger" plain @click="$emit('remove', user)">
          <el-icon><Delete /></el-icon> 删好友
        </el-button>
      </slot>
    </div>
  </div>
</template>

<script setup>
import { computed } from 'vue'
import { useRouter } from 'vue-router'
import { useUserStore } from '@/store/user'
import { resolveAvatarUrl } from '@/utils/community'

/**
 * 统一的用户展示卡片：全部字段来自后端 PublicUserVO，
 * 不含手机号、邮箱等隐私信息；学号按业务要求公开展示。
 */
const props = defineProps({
  user: { type: Object, default: () => ({}) },
  compact: { type: Boolean, default: false },
  showChat: { type: Boolean, default: false },
  showAdd: { type: Boolean, default: false },
  unread: { type: Number, default: 0 }
})
defineEmits(['chat', 'add', 'remove'])

const router = useRouter()
const userStore = useUserStore()
const isSelf = computed(() => props.user?.userId && props.user.userId === userStore.userId)

function goProfile() {
  if (!props.user?.userId) return
  router.push(`/user/${props.user.userId}`)
}
</script>

<style scoped>
.user-card {
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 12px;
  border-radius: 10px;
  background: #fff;
  border: 1px solid #ebeef5;
}

.avatar {
  flex-shrink: 0;
  background: linear-gradient(135deg, #941e23, #761317);
  color: #fff;
  font-weight: 700;
}

.unread-badge :deep(.el-badge__content) {
  border: none;
  background: #e74c3c;
}

.clickable {
  cursor: pointer;
}

.clickable:hover {
  opacity: 0.8;
}

.info {
  flex: 1;
  min-width: 0;
}

.name-row {
  display: flex;
  align-items: center;
  gap: 6px;
}

.name {
  font-weight: 600;
  color: #303133;
  font-size: 14px;
}

.meta {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
  margin-top: 2px;
  font-size: 12px;
  color: #909399;
}

.account {
  margin-top: 2px;
  font-size: 12px;
  color: #c0c4cc;
}

.actions {
  display: flex;
  gap: 8px;
  flex-shrink: 0;
}

.compact {
  padding: 8px;
}
</style>
