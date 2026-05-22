import { request } from '@/api/http'
import type {
    PageResult,
    UserCreateRequest,
    UserDTO,
    UserQuery,
    UserStatus,
} from '@/types/api'

/** 用户管理 API（design §8.7，仅 SYSTEM_ADMIN）。 */

/** GET /users */
export function page(query: UserQuery): Promise<PageResult<UserDTO>> {
    return request<PageResult<UserDTO>>({
        method: 'GET',
        url: '/users',
        params: query,
    })
}

/** POST /users */
export function create(req: UserCreateRequest): Promise<UserDTO> {
    return request<UserDTO>({
        method: 'POST',
        url: '/users',
        data: req,
    })
}

/** PATCH /users/{id}/status */
export function changeStatus(id: number, status: UserStatus): Promise<UserDTO> {
    return request<UserDTO>({
        method: 'PATCH',
        url: `/users/${id}/status`,
        data: { status },
    })
}
