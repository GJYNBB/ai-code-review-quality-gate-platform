<script setup lang="ts">
/**
 * ReviewReportDiffTab（B5-A.9）。
 *
 * 关联需求：R16.4。
 *
 * 调 report.diff，渲染 DiffViewer 组件。
 */
import { onMounted, ref, watch } from 'vue'

import * as reportApi from '@/api/report'
import DiffViewer from '@/components/DiffViewer.vue'
import type { DiffViewDTO } from '@/types/api'

const props = defineProps<{ taskId: number }>()

const loading = ref(false)
const diff = ref<DiffViewDTO | null>(null)

async function load() {
  if (!Number.isFinite(props.taskId)) return
  loading.value = true
  try {
    diff.value = await reportApi.diff(props.taskId)
  } finally {
    loading.value = false
  }
}

onMounted(load)
watch(() => props.taskId, load)
</script>

<template>
  <div v-loading="loading" class="diff-tab">
    <el-empty v-if="!diff || (diff.files?.length ?? 0) === 0" description="暂无差异数据" />
    <template v-else>
      <p class="diff-tab__summary">
        共 <strong>{{ diff.changedFileCount }}</strong> 个文件变更
      </p>
      <DiffViewer :files="diff.files" />
    </template>
  </div>
</template>

<style lang="scss" scoped>
.diff-tab {
  &__summary {
    margin: 0 0 12px;
    color: var(--el-text-color-secondary);
  }
}
</style>
