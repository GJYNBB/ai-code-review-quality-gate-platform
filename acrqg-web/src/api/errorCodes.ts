/**
 * 后端错误码 → 中文提示映射。
 * 与 design.md §8.2 ErrorCode 枚举完全对齐：覆盖全部 16 项。
 *
 * - SUCCESS（code === 0）由响应拦截器解包后不再走错误提示，因此映射表不含。
 * - 所有错误码均以字符串形式返回。
 */
export const ERROR_MESSAGES: Record<string, string> = {
  AUTH_INVALID_CREDENTIALS: '用户名或密码错误',
  AUTH_INVALID_TOKEN: '访问令牌无效或已过期，请重新登录',
  AUTH_ACCOUNT_DISABLED: '账号已被禁用，请联系管理员',
  PERMISSION_DENIED: '权限不足，无法访问该资源',
  VALIDATION_ERROR: '参数校验失败，请检查输入',
  PROJECT_NAME_EXISTS: '项目名称已存在',
  REPOSITORY_UNREACHABLE: '仓库不可访问，请检查地址或访问令牌',
  WEBHOOK_SIGNATURE_INVALID: 'Webhook 签名校验失败',
  TASK_DUPLICATED: '评审任务已存在，无需重复创建',
  TASK_NOT_RETRYABLE: '当前任务状态不支持重试',
  TASK_NOT_FOUND: '评审任务不存在',
  AI_SERVICE_UNAVAILABLE: 'AI 服务暂不可用，已自动降级',
  GATE_RULE_INVALID: '门禁规则配置非法',
  WAIVER_DUPLICATED: '已存在有效的豁免申请',
  INTERNAL_ERROR: '系统繁忙，请稍后再试',
}

export function describeErrorCode(code: string | number | undefined, fallback?: string): string {
  if (typeof code === 'string' && ERROR_MESSAGES[code]) return ERROR_MESSAGES[code]
  return fallback || '请求失败，请稍后再试'
}
