import { request } from '@/api/http'
import type { GateWaiverApproveRequest, GateWaiverDTO, GateWaiverSubmitRequest } from '@/types/api'

/**
 * 门禁豁免 API（design §8.7）。
 *
 * 注意：豁免申请的提交路径为 `/review-tasks/{taskId}/waivers`，审批为
 * `/waivers/{id}/approval`；与 design §8.7 文字描述（gate-waivers）小有差异，
 * 这里以后端实际控制器路径为准。
 */

/** POST /review-tasks/{taskId}/waivers */
export function apply(taskId: number, req: GateWaiverSubmitRequest): Promise<GateWaiverDTO> {
  return request<GateWaiverDTO>({
    method: 'POST',
    url: `/review-tasks/${taskId}/waivers`,
    data: req,
  })
}

/** POST /waivers/{id}/approval { approve: true } */
export function approve(
  id: number,
  req: Omit<GateWaiverApproveRequest, 'approve'> = {},
): Promise<void> {
  return request<void>({
    method: 'POST',
    url: `/waivers/${id}/approval`,
    data: { approve: true, ...req },
  })
}

/** POST /waivers/{id}/approval { approve: false } */
export function reject(
  id: number,
  req: Omit<GateWaiverApproveRequest, 'approve'> = {},
): Promise<void> {
  return request<void>({
    method: 'POST',
    url: `/waivers/${id}/approval`,
    data: { approve: false, ...req },
  })
}

/** GET /waivers/{id} */
export function get(id: number): Promise<GateWaiverDTO> {
  return request<GateWaiverDTO>({
    method: 'GET',
    url: `/waivers/${id}`,
  })
}

/**
 * 列出某任务的豁免申请。
 *
 * 后端目前仅暴露按项目维度的列表（`/projects/{projectId}/waivers`）。
 * 当只能拿到 taskId 时，调用方需要先取 task.projectId 再调 listByProject。
 * 这里保留 listByTask 的语义入口，按 query=`taskId` 透传，未来后端开放
 * 任务粒度过滤后即可生效。
 */
export function listByTask(taskId: number): Promise<GateWaiverDTO[]> {
  return request<GateWaiverDTO[]>({
    method: 'GET',
    url: `/review-tasks/${taskId}/waivers`,
  })
}

/** GET /projects/{projectId}/waivers?status=PENDING&page=&pageSize= */
export function pageByProject(
  projectId: number,
  params: { status?: string; page?: number; pageSize?: number } = {},
): Promise<GateWaiverDTO[]> {
  return request<GateWaiverDTO[]>({
    method: 'GET',
    url: `/projects/${projectId}/waivers`,
    params,
  })
}
