import { request, type ExtendedRequestConfig } from '@/api/http'
import type { LoginRequest, LoginResultDTO, RefreshResultDTO, UserDTO } from '@/types/api'

/**
 * 认证相关 API（design §8.7）。
 * baseURL 已包含 `/api/v1`，故此处只写资源路径。
 */

/** GET /auth/csrf —— 写入 XSRF-TOKEN Cookie */
export function csrf(): Promise<void> {
  return request<void>({
    method: 'GET',
    url: '/auth/csrf',
    skipAuth: true,
    skipErrorMessage: true,
  })
}

/** POST /auth/login */
export function login(req: LoginRequest, config?: ExtendedRequestConfig): Promise<LoginResultDTO> {
  return request<LoginResultDTO>({
    method: 'POST',
    url: '/auth/login',
    data: req,
    // 登录失败由页面侧解析错误码，避免被全局 ElMessage 提示掩盖
    skipAuth: true,
    skipErrorMessage: true,
    ...config,
  })
}

/** POST /auth/logout —— refresh token 由 HttpOnly Cookie 自动携带 */
export async function logout(): Promise<void> {
  await csrf()
  return request<void>({
    method: 'POST',
    url: '/auth/logout',
  })
}

/** POST /auth/refresh —— refresh token 由 HttpOnly Cookie 自动携带 */
export async function refresh(): Promise<RefreshResultDTO> {
  await csrf()
  return request<RefreshResultDTO>({
    method: 'POST',
    url: '/auth/refresh',
    skipAuth: true,
    skipErrorMessage: true,
  })
}

/** GET /auth/me */
export function me(): Promise<UserDTO> {
  return request<UserDTO>({
    method: 'GET',
    url: '/auth/me',
  })
}
