import { request } from '@/api/http'
import type {
  AuditLogDTO,
  AuditQuery,
  ModelConfigCreateRequest,
  ModelConfigDTO,
  ModelConfigUpdateRequest,
  PageResult,
  ScannerConfigDTO,
  ScannerConfigRequest,
  SystemParamDTO,
} from '@/types/api'

/** 系统管理 API（design §8.7，仅 SYSTEM_ADMIN）。 */

// ---- 模型配置（R21.1, R21.2）----

/** GET /admin/model-configs */
export function listModels(): Promise<ModelConfigDTO[]> {
  return request<ModelConfigDTO[]>({
    method: 'GET',
    url: '/admin/model-configs',
  })
}

/** POST /admin/model-configs */
export function createModel(req: ModelConfigCreateRequest): Promise<ModelConfigDTO> {
  return request<ModelConfigDTO>({
    method: 'POST',
    url: '/admin/model-configs',
    data: req,
  })
}

/** PATCH /admin/model-configs/{id} */
export function updateModel(id: number, req: ModelConfigUpdateRequest): Promise<ModelConfigDTO> {
  return request<ModelConfigDTO>({
    method: 'PATCH',
    url: `/admin/model-configs/${id}`,
    data: req,
  })
}

// ---- 扫描器配置（R21.3）----

/** GET /admin/scanners */
export function listScanners(): Promise<ScannerConfigDTO[]> {
  return request<ScannerConfigDTO[]>({
    method: 'GET',
    url: '/admin/scanners',
  })
}

/** POST /admin/scanners （以 name 为业务键 upsert） */
export function upsertScanner(req: ScannerConfigRequest): Promise<ScannerConfigDTO> {
  return request<ScannerConfigDTO>({
    method: 'POST',
    url: '/admin/scanners',
    data: req,
  })
}

// ---- 系统参数（R21.4）----

/** GET /admin/system-params?prefix= */
export function listSystemParams(prefix?: string): Promise<SystemParamDTO[]> {
  return request<SystemParamDTO[]>({
    method: 'GET',
    url: '/admin/system-params',
    params: prefix ? { prefix } : undefined,
  })
}

/** PATCH /admin/system-params/{key} */
export function updateSystemParam(key: string, value: string): Promise<SystemParamDTO> {
  return request<SystemParamDTO>({
    method: 'PATCH',
    url: `/admin/system-params/${encodeURIComponent(key)}`,
    data: { value },
  })
}

// ---- 审计日志（R22）----

/** GET /admin/audit-logs */
export function listAuditLogs(query: AuditQuery): Promise<PageResult<AuditLogDTO>> {
  return request<PageResult<AuditLogDTO>>({
    method: 'GET',
    url: '/admin/audit-logs',
    params: query,
  })
}
