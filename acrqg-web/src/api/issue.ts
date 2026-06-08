import { request } from '@/api/http'
import type { CodeIssueDTO, IssueQuery, IssueStatusChangeRequest, PageResult } from '@/types/api'

/** 问题 API（design §8.7）。 */

/** GET /review-tasks/{taskId}/issues */
export function pageByTask(taskId: number, query: IssueQuery): Promise<PageResult<CodeIssueDTO>> {
  return request<PageResult<CodeIssueDTO>>({
    method: 'GET',
    url: `/review-tasks/${taskId}/issues`,
    params: query,
    // axios 默认对数组使用 brackets；后端期望 key 重复，因此在 paramsSerializer 上处理
    paramsSerializer: { indexes: null },
  })
}

/** GET /issues/{id} */
export function get(id: number): Promise<CodeIssueDTO> {
  return request<CodeIssueDTO>({
    method: 'GET',
    url: `/issues/${id}`,
  })
}

/** PATCH /issues/{id}/status */
export function changeStatus(id: number, req: IssueStatusChangeRequest): Promise<void> {
  return request<void>({
    method: 'PATCH',
    url: `/issues/${id}/status`,
    data: req,
  })
}

/** POST /issues/{id}/comments */
export function addComment(id: number, content: string): Promise<void> {
  return request<void>({
    method: 'POST',
    url: `/issues/${id}/comments`,
    data: { content },
  })
}
