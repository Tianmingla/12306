// 操作日志类型定义

/**
 * 操作日志信息
 */
export interface OperationLog {
  id: number
  adminUserId: number
  adminUsername: string
  operationType: string
  module: string
  description?: string
  requestMethod: string
  requestUrl: string
  requestParams?: string
  responseResult?: string
  ip: string
  status: number  // 0-成功, 1-失败
  errorMsg?: string
  duration: number  // 执行时长(毫秒)
  createTime: string
}

/**
 * 操作日志查询参数
 */
export interface LogQueryParams {
  pageNum?: number
  pageSize?: number
  adminUsername?: string
  operationType?: string
  module?: string
  status?: number
  startTime?: string
  endTime?: string
}

// 操作类型名称
export function getOperationTypeName(type: string): string {
  const types: Record<string, string> = {
    'CREATE': '新增',
    'UPDATE': '修改',
    'DELETE': '删除',
    'LOGIN': '登录',
    'EXPORT': '导出',
    'IMPORT': '导入',
  }
  return types[type] || type
}

// 操作状态名称
export function getStatusName(status: number): string {
  return status === 0 ? '成功' : '失败'
}
