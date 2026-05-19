<script setup lang="ts">
// 应用根组件：仅承载路由出口，具体布局由路由 component 中的 layout 决定
</script>

<template>
  <router-view />
</template>

<style lang="scss" scoped>
:deep(html, body, #app) {
  height: 100%;
  margin: 0;
}
</style>
