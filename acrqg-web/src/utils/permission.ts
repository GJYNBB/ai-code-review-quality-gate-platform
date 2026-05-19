import type { Role } from '@/types/api'

/** 用户是否拥有任意一个指定全局角色 */
export function hasAnyRole(userRoles: Role[] | undefined, required: Role[]): boolean {
    if (!required || required.length === 0) return true
    if (!userRoles || userRoles.length === 0) return false
    return required.some((r) => userRoles.includes(r))
}
