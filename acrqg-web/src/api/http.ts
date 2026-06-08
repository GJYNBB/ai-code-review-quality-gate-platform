import axios, {
  type AxiosError,
  type AxiosInstance,
  type AxiosRequestConfig,
  type AxiosResponse,
  type InternalAxiosRequestConfig,
} from 'axios'
import { ElMessage } from 'element-plus'

import { useAuthStore } from '@/stores/auth'
import { uuidv4 } from '@/utils/uuid'
import { describeErrorCode, ERROR_MESSAGES } from '@/api/errorCodes'
import type { ApiResponse, RefreshResultDTO } from '@/types/api'

/**
 * 后端统一响应包装识别：
 * - 成功：code === 0（数字 0），解包返回 data
 * - 失败：code 为字符串错误码（如 'AUTH_INVALID_TOKEN'），抛出 ApiBusinessError
 */
export class ApiBusinessError extends Error {
  readonly code: string | number
  readonly httpStatus?: number
  readonly requestId?: string
  readonly details?: unknown

  constructor(opts: {
    code: string | number
    message: string
    httpStatus?: number
    requestId?: string
    details?: unknown
  }) {
    super(opts.message)
    this.code = opts.code
    this.httpStatus = opts.httpStatus
    this.requestId = opts.requestId
    this.details = opts.details
  }
}

/**
 * 扩展 axios 请求配置：
 * - skipAuth: 该请求不注入 Authorization 头（如登录、刷新接口）
 * - skipErrorMessage: 该请求失败时不弹 ElMessage（业务自行处理）
 * - _retried: 内部使用，标识该请求已重放过一次
 */
export interface ExtendedRequestConfig<D = unknown> extends AxiosRequestConfig<D> {
  skipAuth?: boolean
  skipErrorMessage?: boolean
  _retried?: boolean
}

function resolveApiBaseUrl(): string {
  const configured = String(import.meta.env.VITE_API_BASE_URL || '').trim()
  if (!configured) return '/api/v1'
  const trimmed = configured.replace(/\/+$/, '')
  return trimmed.endsWith('/api/v1') ? trimmed : `${trimmed}/api/v1`
}

/** axios 实例：基础地址默认 '/api/v1'，由 vite 代理或 nginx 反代到后端 */
export const http: AxiosInstance = axios.create({
  baseURL: resolveApiBaseUrl(),
  timeout: 30_000,
  withCredentials: true,
  xsrfCookieName: 'XSRF-TOKEN',
  xsrfHeaderName: 'X-XSRF-TOKEN',
  // 我们自行处理 ApiResponse 包装，不让 axios 在 2xx 之外抛错
  validateStatus: (status) => status >= 200 && status < 600,
})

// ----------------- request 拦截器 -----------------
http.interceptors.request.use((config: InternalAxiosRequestConfig & ExtendedRequestConfig) => {
  // 注入 X-Request-Id（与后端 traceId 串联，design §8.5）
  config.headers = config.headers ?? {}
  if (!config.headers['X-Request-Id']) {
    config.headers['X-Request-Id'] = uuidv4()
  }

  if (!config.skipAuth) {
    const auth = useAuthStore()
    auth.hydrate()
    if (auth.accessToken) {
      config.headers.Authorization = `Bearer ${auth.accessToken}`
    }
  }

  return config
})

// ----------------- refresh 单飞机制 -----------------
let refreshingPromise: Promise<string> | null = null

async function ensureCsrfToken(): Promise<void> {
  await http.get('/auth/csrf', {
    skipAuth: true,
    skipErrorMessage: true,
  } as ExtendedRequestConfig)
}

async function refreshAccessToken(): Promise<string> {
  if (refreshingPromise) return refreshingPromise
  const auth = useAuthStore()
  auth.hydrate()

  refreshingPromise = ensureCsrfToken()
    .then(() =>
      http.post<ApiResponse<RefreshResultDTO>>('/auth/refresh', undefined, {
        skipAuth: true,
        skipErrorMessage: true,
      } as ExtendedRequestConfig),
    )
    .then((data) => {
      // 响应拦截器已解包：返回的 data 即 RefreshResultDTO
      const result = data as unknown as RefreshResultDTO
      auth.setTokens({
        accessToken: result.accessToken,
        expiresIn: result.expiresIn,
      })
      return result.accessToken
    })
    .finally(() => {
      refreshingPromise = null
    })

  return refreshingPromise
}

function redirectToLogin(): void {
  // 避免循环依赖：通过 window.location 跳转
  if (typeof window === 'undefined') return
  const current = window.location.pathname + window.location.search
  // 已经在登录页则不重复跳转
  if (window.location.pathname.startsWith('/login')) return
  const redirect = encodeURIComponent(current)
  window.location.assign(`/login?redirect=${redirect}`)
}

// ----------------- response 拦截器 -----------------
http.interceptors.response.use(
  async (response: AxiosResponse<ApiResponse<unknown>>) => {
    const config = response.config as InternalAxiosRequestConfig & ExtendedRequestConfig
    const body = response.data

    // 兜底：极少数后端非 ApiResponse 的端点（如 /health 文本），直接返回原 data
    if (!body || typeof body !== 'object' || !('code' in body)) {
      return response.data as unknown as never
    }

    // 成功：code===0，解包返回 data
    if (body.code === 0 || body.code === '0') {
      return body.data as unknown as never
    }

    // AUTH_INVALID_TOKEN：尝试自动 refresh 并重放一次
    if (
      body.code === 'AUTH_INVALID_TOKEN' &&
      !config.skipAuth &&
      !config._retried &&
      // 防止 /auth/refresh 自身命中后再去 refresh 形成死循环
      !String(config.url || '').includes('/auth/refresh')
    ) {
      try {
        const newToken = await refreshAccessToken()
        const retryConfig: ExtendedRequestConfig = {
          ...config,
          _retried: true,
          headers: {
            ...(config.headers as Record<string, string>),
            Authorization: `Bearer ${newToken}`,
          },
        }
        return http.request(retryConfig) as unknown as never
      } catch (err) {
        // refresh 失败 → 清空登录态并跳转登录页
        const auth = useAuthStore()
        auth.logout()
        if (!config.skipErrorMessage) {
          ElMessage.error(ERROR_MESSAGES.AUTH_INVALID_TOKEN)
        }
        redirectToLogin()
        throw err instanceof ApiBusinessError
          ? err
          : new ApiBusinessError({
              code: 'AUTH_INVALID_TOKEN',
              message: ERROR_MESSAGES.AUTH_INVALID_TOKEN,
            })
      }
    }

    // 其余错误：弹提示并抛 ApiBusinessError
    const message = describeErrorCode(body.code, body.message)
    if (!config.skipErrorMessage) {
      ElMessage.error(message)
    }
    throw new ApiBusinessError({
      code: body.code,
      message,
      httpStatus: response.status,
      requestId: body.requestId,
      details: body.details ?? undefined,
    })
  },
  (error: AxiosError<ApiResponse<unknown>>) => {
    // 网络层错误（断网、CORS、超时等）
    const config = (error.config ?? {}) as ExtendedRequestConfig
    const httpStatus = error.response?.status
    const body = error.response?.data
    const code =
      (body && typeof body === 'object' && (body as ApiResponse<unknown>).code) || 'INTERNAL_ERROR'
    const fallback = error.message || '网络异常，请稍后再试'
    const message = describeErrorCode(code, fallback)
    if (!config.skipErrorMessage) {
      ElMessage.error(message)
    }
    return Promise.reject(
      new ApiBusinessError({
        code,
        message,
        httpStatus,
        requestId: (body as ApiResponse<unknown> | undefined)?.requestId,
        details: (body as ApiResponse<unknown> | undefined)?.details ?? undefined,
      }),
    )
  },
)

/** 业务侧统一调用入口；显式指定泛型即可获得解包后的 data 类型 */
export function request<T>(config: ExtendedRequestConfig): Promise<T> {
  return http.request<unknown, T>(config)
}

export default http
