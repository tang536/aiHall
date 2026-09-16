<template>
  <div class="error-state">
    <el-empty :description="message || '加载失败，请稍后重试'">
      <el-button type="primary" @click="$emit('retry')">
        <el-icon><Refresh /></el-icon> 重新加载
      </el-button>
    </el-empty>
  </div>
</template>

<script setup>
import { Refresh } from '@element-plus/icons-vue'

// 列表数据加载失败时的占位（替代原来的「空白列表」）：给出原因 + 一键重试。
defineProps({
  message: { type: String, default: '' },
})
defineEmits(['retry'])
</script>

<style scoped>
.error-state { padding: 32px 0; }
</style>
