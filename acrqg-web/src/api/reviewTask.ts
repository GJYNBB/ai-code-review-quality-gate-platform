import { request } from '@/api/http'
import type {
    CancelRequest,
    PageResult,
    RetryRequest,
    ReviewTaskCreateRequest,
    ReviewTaskDTO,
    ReviewTaskQuery,
    TaskLogDTO,
    TaskLogQuery,
} from '@/types/api'

/** 评审任务 API（design §8.7）。 */

/** GET /review-tasks */
export function page(query: ReviewTaskQuery): Promise<PageResult<ReviewTaskDTO>> {
    return request<PageResult<ReviewTaskDTO>>({
        method: 'GET',
        url: '/review-tasks',
        params: query,
    })
}

/** GET /review-tasks/{id} */
export function get(id: number): Promise<ReviewTaskDTO> {
    return request<ReviewTaskDTO>({
        method: 'GET',
        url: `/review-tasks/${id}`,
    })
}

/**
 * POST /review-tasks
 *
 * @param req             创建请求体
 * @param idempotencyKey  可选幂等键；存在时注入到 `Idempotency-Key` 请求头
 *                        （后端 24h 内同 key 返回同一任务，design §8.5/R8.4）
 */
export function create(
    req: ReviewTaskCreateRequest,
    idempotencyKey?: string,
): Promise<ReviewTaskDTO> {
    return request<ReviewTaskDTO>({
        method: 'POST',
        url: '/review-tasks',
        data: req,
        headers: idempotencyKey ? { 'Idempotency-Key': idempotencyKey } : undefined,
    })
}

/** POST /review-tasks/{id}/retry */
export function retry(id: number, req: RetryRequest): Promise<ReviewTaskDTO> {
    return request<ReviewTaskDTO>({
        method: 'POST',
        url: `/review-tasks/${id}/retry`,
        data: req,
    })
}

/** POST /review-tasks/{id}/cancel */
export function cancel(id: number, req: CancelRequest): Promise<ReviewTaskDTO> {
    return request<ReviewTaskDTO>({
        method: 'POST',
        url: `/review-tasks/${id}/cancel`,
        data: req,
    })
}

/** GET /review-tasks/{id}/logs */
export function logs(id: number, query: TaskLogQuery): Promise<PageResult<TaskLogDTO>> {
    return request<PageResult<TaskLogDTO>>({
        method: 'GET',
        url: `/review-tasks/${id}/logs`,
        params: query,
    })
}
