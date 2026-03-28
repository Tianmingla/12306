// 角色管理类型定义

/**
 * 角色信息
 */
export interface Role {
  id: number
  roleName: string
  roleCode: string
  description?: string
  status: number
  createTime: string
  updateTime: string
}

/**
 * 权限信息
 */
export interface Permission {
  id: number
  permissionName: string
  permissionCode: string
  resourceType: number  // 1-菜单, 2-按钮, 3-API
  parentId: number
  resourceUrl?: string
  sortOrder: number
  description?: string
  status: number
}

/**
 * 角色详情响应
 */
export interface RoleDetailResponse {
  id: number
  roleName: string
  roleCode: string
  description?: string
  status: number
  permissionIds: number[]
  permissions: PermissionVO[]
}

export interface PermissionVO {
  id: number
  permissionName: string
  permissionCode: string
  resourceType: number
  parentId: number
}

/**
 * 角色查询参数
 */
export interface RoleQueryParams {
  pageNum?: number
  pageSize?: number
  roleName?: string
  roleCode?: string
  status?: number
}

/**
 * 角色保存请求
 */
export interface RoleSaveRequest {
  id?: number
  roleName: string
  roleCode: string
  description?: string
  permissionIds?: number[]
}

// 资源类型名称
export function getResourceTypeName(type: number): string {
  const types = ['', '菜单', '按钮', 'API']
  return types[type] || '未知'
}
