<script setup lang="ts">
/**
 * UI-004 项目详情（B5-A.4）。
 *
 * 关联需求：R4 / R5 / R6。
 *
 * 通过 el-tabs 组合 3 个子模块：
 *   - 基本信息（本页内联）
 *   - 成员管理（MemberManageTab，B5-A.6）
 *   - 仓库绑定（RepositoryBindingTab，B5-A.5）
 */
import { computed, onMounted, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ArrowLeft } from '@element-plus/icons-vue'

import * as projectApi from '@/api/project'
import { formatDateTime } from '@/utils/format'
import type { ProjectDTO } from '@/types/api'

import MemberManageTab from '@/pages/project/MemberManageTab.vue'
import RepositoryBindingTab from '@/pages/project/RepositoryBindingTab.vue'

const route = useRoute()
const router = useRouter()

const projectId = computed(() => Number(route.params.projectId))
const project = ref<ProjectDTO | null>(null)
const loading = ref(false)
const activeTab = ref<'info' | 'members' | 'repository'>('info')

async function loadDetail() {
  if (!Number.isFinite(projectId.value)) return
  loading.value = true
  try {
    project.value = await projectApi.get(projectId.value)
  } finally {
    loading.value = false
  }
}

function goBack() {
  router.push('/projects')
}

onMounted(() => {
  void loadDetail()
})

watch(projectId, () => {
  void loadDetail()
})
</script>

<template>
  <div v-loading="loading" class="project-detail-page">
    <el-page-header :icon="ArrowLeft" @back="goBack">
      <template #content>
        <span class="project-detail-page__title">{{ project?.name ?? '项目详情' }}</span>
      </template>
    </el-page-header>

    <el-tabs v-model="activeTab" class="project-detail-page__tabs">
      <el-tab-pane label="基本信息" name="info">
        <el-descriptions v-if="project" :column="2" border>
          <el-descriptions-item label="项目 ID">{{ project.id }}</el-descriptions-item>
          <el-descriptions-item label="名称">{{ project.name }}</el-descriptions-item>
          <el-descriptions-item label="语言">{{ project.language }}</el-descriptions-item>
          <el-descriptions-item label="默认分支">{{ project.defaultBranch }}</el-descriptions-item>
          <el-descriptions-item label="成员数">{{ project.memberCount }}</el-descriptions-item>
          <el-descriptions-item label="创建时间">{{
            formatDateTime(project.createdAt)
          }}</el-descriptions-item>
          <el-descriptions-item label="描述" :span="2">{{
            project.description || '-'
          }}</el-descriptions-item>
        </el-descriptions>
      </el-tab-pane>

      <el-tab-pane label="成员" name="members" lazy>
        <MemberManageTab :project-id="projectId" />
      </el-tab-pane>

      <el-tab-pane label="仓库绑定" name="repository" lazy>
        <RepositoryBindingTab :project-id="projectId" />
      </el-tab-pane>
    </el-tabs>
  </div>
</template>

<style lang="scss" scoped>
.project-detail-page {
  display: flex;
  flex-direction: column;
  gap: 16px;

  &__title {
    font-size: 16px;
    font-weight: 600;
  }

  &__tabs {
    background: var(--el-bg-color);
    padding: 16px;
    border-radius: 4px;
  }
}
</style>
