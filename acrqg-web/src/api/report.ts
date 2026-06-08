import { request } from '@/api/http'
import type {
  DiffViewDTO,
  PageResult,
  ReviewReportDTO,
  TaskLogDTO,
  TaskLogQuery,
} from '@/types/api'

/** 评审报告 API（design §8.7）。 */

/** GET /review-tasks/{taskId}/report */
export function report(taskId: number): Promise<ReviewReportDTO> {
  return request<ReviewReportDTO>({
    method: 'GET',
    url: `/review-tasks/${taskId}/report`,
  })
}

/** GET /review-tasks/{taskId}/diff */
export function diff(taskId: number): Promise<DiffViewDTO> {
  return request<DiffViewDTO>({
    method: 'GET',
    url: `/review-tasks/${taskId}/diff`,
  })
}

/** GET /review-tasks/{taskId}/logs（与 reviewTask.logs 一致，便于按报告语义命名调用） */
export function logs(taskId: number, query: TaskLogQuery): Promise<PageResult<TaskLogDTO>> {
  return request<PageResult<TaskLogDTO>>({
    method: 'GET',
    url: `/review-tasks/${taskId}/logs`,
    params: query,
  })
}
