<template>
  <div class="error-boundary-wrapper">
    <div v-if="hasError" class="error-boundary">
      <el-result icon="error" title="页面出错了" :sub-title="errorMessage || '渲染时发生未知错误，请重试'">
        <template #extra>
          <el-button type="primary" @click="reset">重试</el-button>
          <el-button @click="reload">刷新页面</el-button>
        </template>
      </el-result>
    </div>
    <slot v-else />
  </div>
</template>

<script setup>
import { ref, onErrorCaptured } from 'vue'

// 全局错误边界：捕获子孙组件渲染期 / 生命周期 / 事件处理中的异常，
// 避免单个组件崩溃把整页拖成白屏。配合列表页的 ErrorState 使用。
const hasError = ref(false)
const errorMessage = ref('')

onErrorCaptured((err) => {
  console.error('[ErrorBoundary] 捕获到渲染异常:', err)
  errorMessage.value = err && err.message ? err.message : String(err)
  hasError.value = true
  // 返回 false 阻止异常继续向上冒泡
  return false
})

function reset() {
  hasError.value = false
  errorMessage.value = ''
}
function reload() {
  window.location.reload()
}
</script>

<style scoped>
.error-boundary {
  padding: 48px 16px;
  min-height: 240px;
  display: flex;
  align-items: center;
  justify-content: center;
}
</style>
