import { defineStore } from 'pinia'
import type { Role, UserDTO } from '@/types/api'

/**
 * Auth Store
 *
 * 关联需求：R1（登录）/ R2（角色）/ R3.2（禁用立即失效）
 *
 * 持久化策略：accessToken / refreshToken / expiresAt / user 同步写 localStorage，
 * 刷新页面时由 hydrate() 还原；遇到 AUTH_INVALID_TOKEN 时清空。
 */

const STORAGE_KEY = 'acrqg.auth'

interface PersistedState {
  accessToken: string | null
  refreshToken: string | null
  expiresAt: number | null
  user: UserDTO | null
}

interface AuthState extends PersistedState {
  /** 标识是否已经从 localStorage 还原过状态 */
  hydrated: boolean
}

function loadFromStorage(): PersistedState {
  if (typeof window === 'undefined') {
    return { accessToken: null, refreshToken: null, expiresAt: null, user: null }
  }
  try {
    const raw = window.localStorage.getItem(STORAGE_KEY)
    if (!raw) {
      return { accessToken: null, refreshToken: null, expiresAt: null, user: null }
    }
    const parsed = JSON.parse(raw) as Partial<PersistedState>
    return {
      accessToken: parsed.accessToken ?? null,
      refreshToken: parsed.refreshToken ?? null,
      expiresAt: parsed.expiresAt ?? null,
      user: parsed.user ?? null,
    }
  } catch {
    return { accessToken: null, refreshToken: null, expiresAt: null, user: null }
  }
}

function saveToStorage(state: PersistedState): void {
  if (typeof window === 'undefined') return
  try {
    window.localStorage.setItem(
      STORAGE_KEY,
      JSON.stringify({
        accessToken: state.accessToken,
        refreshToken: state.refreshToken,
        expiresAt: state.expiresAt,
        user: state.user,
      } satisfies PersistedState),
    )
  } catch {
    /* localStorage 不可用时静默忽略 */
  }
}

function clearStorage(): void {
  if (typeof window === 'undefined') return
  try {
    window.localStorage.removeItem(STORAGE_KEY)
  } catch {
    /* ignore */
  }
}

export const useAuthStore = defineStore('auth', {
  state: (): AuthState => ({
    accessToken: null,
    refreshToken: null,
    expiresAt: null,
    user: null,
    hydrated: false,
  }),

  getters: {
    isAuthenticated(state): boolean {
      return Boolean(state.accessToken && state.user)
    },
    /** 当前用户的全局角色列表（design §5.4 路由守卫使用） */
    roles(state): Role[] {
      return state.user?.roles ?? []
    },
    isExpired(state): boolean {
      if (!state.expiresAt) return false
      return Date.now() >= state.expiresAt
    },
  },

  actions: {
    /** 应用启动时调用，从 localStorage 还原 token / user */
    hydrate() {
      if (this.hydrated) return
      const persisted = loadFromStorage()
      this.accessToken = persisted.accessToken
      this.refreshToken = persisted.refreshToken
      this.expiresAt = persisted.expiresAt
      this.user = persisted.user
      this.hydrated = true
    },

    /**
     * 登录成功后写入认证信息。
     * 注意：API 调用本身在 src/api/auth.ts 实现，store 仅维护状态（B0-B.4 阶段 API 模块尚未完全落地）。
     */
    setSession(payload: {
      accessToken: string
      refreshToken: string
      expiresIn: number
      user: UserDTO
    }) {
      this.accessToken = payload.accessToken
      this.refreshToken = payload.refreshToken
      this.expiresAt = Date.now() + payload.expiresIn * 1000
      this.user = payload.user
      saveToStorage({
        accessToken: this.accessToken,
        refreshToken: this.refreshToken,
        expiresAt: this.expiresAt,
        user: this.user,
      })
    },

    /** 刷新 accessToken 与 refreshToken，匹配后端 refresh token rotation 语义 */
    setTokens(payload: { accessToken: string; refreshToken: string; expiresIn: number }) {
      this.accessToken = payload.accessToken
      this.refreshToken = payload.refreshToken
      this.expiresAt = Date.now() + payload.expiresIn * 1000
      saveToStorage({
        accessToken: this.accessToken,
        refreshToken: this.refreshToken,
        expiresAt: this.expiresAt,
        user: this.user,
      })
    },

    /** 兼容旧调用：仅刷新 accessToken，refreshToken 与 user 保持不变 */
    setAccessToken(payload: { accessToken: string; expiresIn: number }) {
      this.accessToken = payload.accessToken
      this.expiresAt = Date.now() + payload.expiresIn * 1000
      saveToStorage({
        accessToken: this.accessToken,
        refreshToken: this.refreshToken,
        expiresAt: this.expiresAt,
        user: this.user,
      })
    },

    setUser(user: UserDTO | null) {
      this.user = user
      saveToStorage({
        accessToken: this.accessToken,
        refreshToken: this.refreshToken,
        expiresAt: this.expiresAt,
        user: this.user,
      })
    },

    /** 登出：清空内存与 localStorage（实际撤销后端 token 的请求由调用方先行发起） */
    logout() {
      this.accessToken = null
      this.refreshToken = null
      this.expiresAt = null
      this.user = null
      clearStorage()
    },
  },
})
