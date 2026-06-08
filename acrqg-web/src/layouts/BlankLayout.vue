<script setup lang="ts">
// 公开页面布局：使用与控制台一致的 Aegis 视觉语言承载登录/错误页。
</script>

<template>
  <div class="blank-layout">
    <div class="blank-layout__orb blank-layout__orb--one" />
    <div class="blank-layout__orb blank-layout__orb--two" />
    <main class="blank-layout__content">
      <router-view />
    </main>
  </div>
</template>

<style lang="scss" scoped>
.blank-layout {
  position: relative;
  min-height: 100vh;
  display: grid;
  place-items: center;
  padding: 28px;
  overflow: auto;
  background:
    radial-gradient(circle at 18% 14%, rgba(37, 99, 235, 0.42), transparent 30%),
    radial-gradient(circle at 84% 10%, rgba(6, 182, 212, 0.34), transparent 24%),
    linear-gradient(135deg, #07111f 0%, #0f1f38 48%, #102a47 100%);

  &__content {
    position: relative;
    z-index: 1;
    width: min(1080px, 100%);
  }

  &__orb {
    position: absolute;
    border-radius: 999px;
    filter: blur(8px);
    opacity: 0.55;

    &--one {
      width: 360px;
      height: 360px;
      left: -120px;
      bottom: -120px;
      background: rgba(37, 99, 235, 0.25);
    }

    &--two {
      width: 260px;
      height: 260px;
      right: -80px;
      top: 12%;
      background: rgba(6, 182, 212, 0.22);
    }
  }
}
</style>
