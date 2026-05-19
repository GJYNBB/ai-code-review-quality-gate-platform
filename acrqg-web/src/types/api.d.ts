/**
 * 与后端 DTO 对齐的类型定义。详见 design.md §8 / §16。
 * 仅声明前端常用字段，后续按需扩展。
 */

/** 全局角色（与后端 Role 表 code 字段一致） */
export type Role = 'DEVELOPER' | 'REVIEWER' | 'PROJECT_ADMIN' | 'SYSTEM_ADMIN' | 'CI_CD'

/** 项目级角色（project_member.project_role） */
export type ProjectRole = 'DEVELOPER' | 'REVIEWER' | 'PROJECT_ADMIN'

/** 用户状态 */
export type UserStatus = 'ENABLED' | 'DISABLED'

/** 评审任务状态字典（design §7.4） */
export type ReviewTaskStatus =
    | 'PENDING'
    | 'FETCHING_DIFF'
    | 'STATIC_SCANNING'
    | 'AI_REVIEWING'
    | 'GATE_EVALUATING'
    | 'PASSED'
    | 'FAILED_GATE'
    | 'EXECUTION_FAILED'

/** 问题状态字典 */
export type CodeIssueStatus =
    | 'NEW'
    | 'CONFIRMED'
    | 'FALSE_POSITIVE'
    | 'PENDING_VERIFY'
    | 'CLOSED'
    | 'REOPENED'

/** 严重等级 */
export type Severity = 'CRITICAL' | 'HIGH' | 'MEDIUM' | 'LOW' | 'INFO'

/** 后端统一响应包装（design §8.1） */
export interface ApiResponse<T> {
    /** 0 表示成功；失败时为字符串错误码 */
    code: number | string
    message: string
    data: T | null
    details?: FieldError[] | null
    requestId?: string
}

export interface FieldError {
    field: string
    reason: string
}

export interface PageResult<T> {
    items: T[]
    page: number
    pageSize: number
    total: number
    totalPages: number
}

/** 用户 DTO */
export interface UserDTO {
    id: number
    username: string
    email: string
    status: UserStatus
    roles: Role[]
    createdAt: string
}

/** 登录响应 */
export interface LoginResultDTO {
    accessToken: string
    refreshToken: string
    expiresIn: number
    user: UserDTO
}

/** Token 刷新响应 */
export interface RefreshResultDTO {
    accessToken: string
    expiresIn: number
}

/** 通知 DTO */
export interface NotificationDTO {
    id: number
    type: string
    title: string
    content: string
    refType?: string
    refId?: number
    readFlag: boolean
    createdAt: string
}
