import { request } from '@/api/http'
import type {
  AddMemberRequest,
  PageResult,
  ProjectCreateRequest,
  ProjectDTO,
  ProjectMemberDTO,
  ProjectQuery,
  ProjectUpdateRequest,
} from '@/types/api'

/** 项目管理 / 成员 API（design §8.7）。 */

/** GET /projects */
export function page(query: ProjectQuery): Promise<PageResult<ProjectDTO>> {
  return request<PageResult<ProjectDTO>>({
    method: 'GET',
    url: '/projects',
    params: query,
  })
}

/** GET /projects/{id} */
export function get(id: number): Promise<ProjectDTO> {
  return request<ProjectDTO>({
    method: 'GET',
    url: `/projects/${id}`,
  })
}

/** POST /projects */
export function create(req: ProjectCreateRequest): Promise<ProjectDTO> {
  return request<ProjectDTO>({
    method: 'POST',
    url: '/projects',
    data: req,
  })
}

/** PUT /projects/{id} */
export function update(id: number, req: ProjectUpdateRequest): Promise<ProjectDTO> {
  return request<ProjectDTO>({
    method: 'PUT',
    url: `/projects/${id}`,
    data: req,
  })
}

/** GET /projects/{id}/members */
export function listMembers(id: number): Promise<ProjectMemberDTO[]> {
  return request<ProjectMemberDTO[]>({
    method: 'GET',
    url: `/projects/${id}/members`,
  })
}

/** POST /projects/{id}/members */
export function addMember(id: number, req: AddMemberRequest): Promise<void> {
  return request<void>({
    method: 'POST',
    url: `/projects/${id}/members`,
    data: req,
  })
}

/** DELETE /projects/{id}/members/{userId} */
export function removeMember(id: number, userId: number): Promise<void> {
  return request<void>({
    method: 'DELETE',
    url: `/projects/${id}/members/${userId}`,
  })
}
