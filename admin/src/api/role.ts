// 角色管理相关 API

import { request, Result, PageResult } from '@/utils/request'
import type {
  Role,
  Permission,
  RoleDetailResponse,
  RoleQueryParams,
  RoleSaveRequest,
} from '@/types/role'

// 获取角色列表
export function getRoleList(params: RoleQueryParams): Promise<Result<PageResult<Role>>> {
  return request.get('/admin/role/list', { params })
}

// 获取角色详情
export function getRoleDetail(id: number): Promise<Result<RoleDetailResponse>> {
  return request.get(`/admin/role/${id}`)
}

// 创建角色
export function createRole(data: RoleSaveRequest): Promise<Result<string>> {
  return request.post('/admin/role', data)
}

// 更新角色
export function updateRole(id: number, data: RoleSaveRequest): Promise<Result<string>> {
  return request.put(`/admin/role/${id}`, data)
}

// 删除角色
export function deleteRole(id: number): Promise<Result<string>> {
  return request.delete(`/admin/role/${id}`)
}

// 更新角色状态
export function updateRoleStatus(id: number, status: number): Promise<Result<string>> {
  return request.put(`/admin/role/${id}/status`, { status })
}

// 获取所有权限列表
export function getAllPermissions(): Promise<Result<Permission[]>> {
  return request.get('/admin/role/permissions')
}

// 获取角色的权限ID列表
export function getRolePermissionIds(id: number): Promise<Result<number[]>> {
  return request.get(`/admin/role/${id}/permissions`)
}

// 分配角色权限
export function assignPermissions(id: number, permissionIds: number[]): Promise<Result<string>> {
  return request.post(`/admin/role/${id}/permissions`, { permissionIds })
}
