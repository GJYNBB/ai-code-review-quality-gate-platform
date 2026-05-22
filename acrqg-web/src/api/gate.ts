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

/** 别名：与 design §5.x 命名约定保持一致（B5-A.1 接口清单）。 */
export const listTemplates = templates

/**
 * GET /projects/{projectId}/quality-gates/versions
 *
 * 返回该项目所有历史版本（含 enabled=true 的当前版本）；列表项不携带 rules，
 * 详情请求 getVersion(gateId)。后端若尚未实现该端点，调用方应做 catch 兜底。
 */
export function listVersions(projectId: number): Promise<QualityGateDTO[]> {
    return request<QualityGateDTO[]>({
        method: 'GET',
        url: `/projects/${projectId}/quality-gates/versions`,
        skipErrorMessage: true,
    })
}

/** GET /quality-gates/{gateId}（按 id 取某个历史版本的详情，含 rules） */
export function getVersion(gateId: number): Promise<QualityGateDTO> {
    return request<QualityGateDTO>({
        method: 'GET',
        url: `/quality-gates/${gateId}`,
    })
}

/** GET /review-tasks/{taskId}/gate-result */
export function getResult(taskId: number): Promise<GateResultDTO> {
    return request<GateResultDTO>({
        method: 'GET',
        url: `/review-tasks/${taskId}/gate-result`,
    })
}
