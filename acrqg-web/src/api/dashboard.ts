import { request } from '@/api/http'
import type { DashboardQuery, QualityTrendDTO, RiskFileDTO } from '@/types/api'

/** 项目质量看板 API（design §8.7）。 */

/** GET /projects/{projectId}/dashboard/trend */
export function trend(projectId: number, query: DashboardQuery): Promise<QualityTrendDTO> {
  return request<QualityTrendDTO>({
    method: 'GET',
    url: `/projects/${projectId}/dashboard/trend`,
    params: query,
  })
}

/** GET /projects/{projectId}/dashboard/risk-files?limit= */
export function riskFiles(
  projectId: number,
  query: { limit?: number } = {},
): Promise<RiskFileDTO[]> {
  return request<RiskFileDTO[]>({
    method: 'GET',
    url: `/projects/${projectId}/dashboard/risk-files`,
    params: query,
  })
}
