<template>
  <router-view />
</template>

<script setup>
// 管理端路由透传布局：仅负责渲染嵌套子路由。
// 各管理页（Dashboard 等）自身已用 AdminLayout 包裹侧边栏与顶栏，
// 这里若再用 AdminLayout 作为路由组件，会导致嵌套路由无法渲染、内容区空白。
</script>
