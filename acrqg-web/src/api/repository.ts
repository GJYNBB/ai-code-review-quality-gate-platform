import { request } from '@/api/http'
import type {
    ConnectivityResultDTO,
    RepositoryBindRequest,
    RepositoryBindingDTO,
    RepositoryTestRequest,
} from '@/types/api'

/** 仓库绑定 API（design §8.7）。 */

/** POST /projects/{projectId}/repository/test */
export function test(
    projectId: number,
    req: RepositoryTestRequest,
): Promise<ConnectivityResultDTO> {
    return request<ConnectivityResultDTO>({
        method: 'POST',
        url: `/projects/${projectId}/repository/test`,
        data: req,
        // 测试连通性的失败仅作业务提示，避免重复弹 ElMessage（页面会按结果决定 UI）
        skipErrorMessage: true,
    })
}

/** POST /projects/{projectId}/repository */
export function bind(
    projectId: number,
    req: RepositoryBindRequest,
): Promise<RepositoryBindingDTO> {
    return request<RepositoryBindingDTO>({
        method: 'POST',
        url: `/projects/${projectId}/repository`,
        data: req,
    })
}

/** GET /projects/{projectId}/repository */
export function get(projectId: number): Promise<RepositoryBindingDTO | null> {
    return request<RepositoryBindingDTO | null>({
        method: 'GET',
        url: `/projects/${projectId}/repository`,
    })
}
