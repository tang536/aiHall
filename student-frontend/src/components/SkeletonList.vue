<template>
  <div class="skeleton-list" :class="type">
    <template v-if="type === 'card'">
      <div v-for="i in count" :key="i" class="sk-card">
        <div class="sk-cover shimmer"></div>
        <div class="sk-body">
          <div class="sk-line shimmer" style="width: 72%"></div>
          <div class="sk-line shimmer" style="width: 42%"></div>
          <div class="sk-line sm shimmer" style="width: 56%"></div>
        </div>
      </div>
    </template>
    <template v-else>
      <div v-for="i in rows" :key="i" class="sk-row">
        <div v-for="(c, idx) in columns" :key="idx" class="sk-cell shimmer" :style="{ width: c + '%' }"></div>
      </div>
    </template>
  </div>
</template>

<script setup>
// 列表加载骨架屏：列表拉取时为「首屏」提供占位，避免空白闪烁。
// type=card 用于商品/帖子网格，type=table 用于管理端表格。
defineProps({
  type: { type: String, default: 'card' }, // 'card' | 'table'
  count: { type: Number, default: 8 },
  rows: { type: Number, default: 8 },
  columns: { type: Array, default: () => [22, 26, 16, 14, 12, 10] },
})
</script>

<style scoped>
.skeleton-list { width: 100%; }
.shimmer {
  background: linear-gradient(90deg, #f0ebe2 25%, #e5ddd0 37%, #f0ebe2 63%);
  background-size: 400% 100%;
  animation: sk-shimmer 1.4s ease infinite;
  border-radius: 6px;
}
@keyframes sk-shimmer {
  0% { background-position: 100% 0; }
  100% { background-position: -100% 0; }
}
.sk-card {
  border: 1px solid var(--ct-line);
  border-radius: 10px;
  overflow: hidden;
  background: var(--ct-white);
}
.sk-cover { width: 100%; height: 150px; border-radius: 0; }
.sk-body { padding: 12px; }
.sk-line { height: 14px; margin-bottom: 10px; }
.sk-line.sm { height: 12px; }
.skeleton-list.card {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(200px, 1fr));
  gap: 16px;
}
.sk-row {
  display: flex;
  gap: 14px;
  padding: 14px 8px;
  border-bottom: 1px solid var(--ct-line);
}
.sk-cell { height: 16px; }
</style>
