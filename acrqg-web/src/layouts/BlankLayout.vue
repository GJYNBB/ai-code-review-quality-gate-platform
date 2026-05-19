<script setup lang="ts">
// 公开页面布局：仅居中容器，背景使用 Element Plus 的 page 颜色
</script>

<template>
  <div class="blank-layout">
    <main class="blank-layout__content">
      <router-view />
    </main>
  </div>
</template>

<style lang="scss" scoped>
.blank-layout {
  min-height: 100vh;
  display: flex;
  align-items: center;
  justify-content: center;
  background: linear-gradient(135deg, #1d2c4a 0%, #3551a3 100%);
  padding: 24px;

  &__content {
    width: 100%;
    max-width: 420px;
  }
}
</style>
