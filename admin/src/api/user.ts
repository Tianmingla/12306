// 用户相关 API

import { request, Result, PageResult, PageParams } from '@/utils/request'
import type {
  User,
  Passenger,
  LoginRequest,
  LoginResponse,
  UserQueryParams,
} from '@/types/user'

// 管理员登录
export function login(data: LoginRequest): Promise<Result<LoginResponse>> {
  return request.post('/admin/auth/login', data)
}

// 获取当前管理员信息
export function getUserInfo(): Promise<Result<User>> {
  return request.get('/admin/auth/info')
}

// 获取用户列表
export function getUserList(params: UserQueryParams): Promise<Result<PageResult<User>>> {
  return request.get('/admin/user/list', { params })
}

// 获取用户详情
export function getUserDetail(id: number): Promise<Result<User>> {
  return request.get(`/admin/user/${id}`)
}

// 禁用/启用用户
export function toggleUserStatus(id: number): Promise<Result<void>> {
  return request.put(`/admin/user/${id}/status`)
}

// 获取用户乘车人列表
export function getUserPassengers(userId: number): Promise<Result<Passenger[]>> {
  return request.get(`/admin/user/${userId}/passengers`)
}

// 管理员重置用户密码
export function resetUserPassword(id: number): Promise<Result<void>> {
  return request.put(`/admin/user/${id}/reset-password`)
}
