import { defineStore } from 'pinia'

import * as projectApi from '@/api/project'
import type { ProjectDTO } from '@/types/api'

/**
 * Project Store（design §5.3）。
 *
 * 关联需求：R4（项目管理）/ R6（项目成员上下文）。
 * 提供：
 * - 项目列表的轻量缓存（用于全局头部 / Dashboard 项目切换器）
 * - 当前项目上下文 currentProjectId（页面与头部切换器共享）
 */
interface ProjectState {
  /** 用户可见的项目列表（仅用于头部 / 选择器，不承载完整分页） */
  projects: ProjectDTO[]
  /** 当前选中的项目 id；null 表示未选择 */
  currentProjectId: number | null
  /** 项目列表是否已加载过 */
  loaded: boolean
  /** 是否正在加载列表 */
  loading: boolean
}

const STORAGE_KEY = 'acrqg.project.current'

function readPersistedId(): number | null {
  if (typeof window === 'undefined') return null
  const raw = window.localStorage.getItem(STORAGE_KEY)
  if (!raw) return null
  const id = Number(raw)
  return Number.isFinite(id) ? id : null
}

function writePersistedId(id: number | null): void {
  if (typeof window === 'undefined') return
  if (id == null) {
    window.localStorage.removeItem(STORAGE_KEY)
  } else {
    window.localStorage.setItem(STORAGE_KEY, String(id))
  }
}

export const useProjectStore = defineStore('project', {
  state: (): ProjectState => ({
    projects: [],
    currentProjectId: readPersistedId(),
    loaded: false,
    loading: false,
  }),

  getters: {
    currentProject(state): ProjectDTO | null {
      if (state.currentProjectId == null) return null
      return state.projects.find((p) => p.id === state.currentProjectId) ?? null
    },
  },

  actions: {
    /** 拉取项目列表（一次性，size=200 足以覆盖头部下拉） */
    async loadAll(force = false) {
      if (this.loaded && !force) return
      if (this.loading) return
      this.loading = true
      try {
        const res = await projectApi.page({ page: 1, pageSize: 200 })
        this.projects = res.items
        this.loaded = true
        // 自动定位 currentProjectId：失效则取首个
        if (
          this.currentProjectId != null &&
          !this.projects.some((p) => p.id === this.currentProjectId)
        ) {
          this.currentProjectId = this.projects[0]?.id ?? null
          writePersistedId(this.currentProjectId)
        } else if (this.currentProjectId == null && this.projects.length > 0) {
          this.currentProjectId = this.projects[0].id
          writePersistedId(this.currentProjectId)
        }
      } finally {
        this.loading = false
      }
    },

    setCurrentProject(id: number | null) {
      this.currentProjectId = id
      writePersistedId(id)
    },

    reset() {
      this.projects = []
      this.currentProjectId = null
      this.loaded = false
      this.loading = false
      writePersistedId(null)
    },
  },
})
