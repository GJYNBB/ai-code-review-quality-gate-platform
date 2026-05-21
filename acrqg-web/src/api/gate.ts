import { request } from '@/api/http'
import type {
    GateResultDTO,
    QualityGateDTO,
    QualityGateSaveRequest,
} from '@/types/api'

/** 质量门禁 API（design §8.7）。 */

/** GET /projects/{projectId}/quality-gate */
export function getEnabled(projectId: number): Promise<QualityGateDTO | null> {
    return request<QualityGateDTO | null>({
        method: 'GET',
        url: `/projects/${projectId}/quality-gate`,
    })
}

/** POST /projects/{projectId}/quality-gate */
export function save(
    projectId: number,
    req: QualityGateSaveRequest,
): Promise<QualityGateDTO> {
    return request<QualityGateDTO>({
        method: 'POST',
        url: `/projects/${projectId}/quality-gate`,
        data: req,
        // 保存失败由页面侧解析 details 高亮非法行
        skipErrorMessage: true,
    })
}

/** GET /quality-gates/templates */
export function templates(): Promise<QualityGateDTO> {
    return request<QualityGateDTO>({
        method: 'GET',
        url: '/quality-gates/templates',
    })
}

/** GET /review-tasks/{taskId}/gate-result */
export function getResult(taskId: number): Promise<GateResultDTO> {
    return request<GateResultDTO>({
        method: 'GET',
        url: `/review-tasks/${taskId}/gate-result`,
    })
}
