<script setup lang="ts">
/**
 * 登录页占位（B5 批次替换为完整实现）。
 * 仅展示静态卡片，避免在 B0-B 阶段调用尚未上线的后端 /auth/login。
 */
</script>

<template>
  <el-card class="login-card" shadow="never">
    <template #header>
      <h2 class="login-card__title">AI 代码评审与质量门禁平台</h2>
    </template>
    <el-empty description="登录功能将在 B5-A 批次接入后端 /auth/login">
      <p class="login-card__hint">B0-B 仅完成前端基础设施 Bootstrap，登录页面 UI 将在 B5 落地。</p>
    </el-empty>
  </el-card>
</template>

<style lang="scss" scoped>
.login-card {
  border-radius: 12px;

  &__title {
    text-align: center;
    margin: 0;
    font-size: 18px;
    font-weight: 600;
  }

  &__hint {
    color: var(--el-text-color-secondary);
    font-size: 13px;
    text-align: center;
  }
}
</style>
