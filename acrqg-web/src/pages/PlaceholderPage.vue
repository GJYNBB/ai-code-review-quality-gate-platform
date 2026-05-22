<script setup lang="ts">
import { useRoute } from 'vue-router'

const route = useRoute()

const title = (route.meta?.title as string) ?? '页面占位'
const description = (route.meta?.placeholderDescription as string) ?? '该页面将在 B5 批次落地'
</script>

<template>
  <div class="placeholder-page">
    <el-card shadow="never">
      <template #header>
        <span class="placeholder-page__title">{{ title }}</span>
      </template>
      <el-empty :description="description">
        <template #image>
          <el-icon size="64"><promotion /></el-icon>
        </template>
        <template #default>
          <p class="placeholder-page__path">路由：{{ route.fullPath }}</p>
        </template>
      </el-empty>
    </el-card>
  </div>
</template>

<style lang="scss" scoped>
.placeholder-page {
  &__title {
    font-weight: 600;
  }

  &__path {
    color: var(--el-text-color-secondary);
    font-family: 'Source Code Pro', Consolas, monospace;
    font-size: 13px;
  }
}
</style>
