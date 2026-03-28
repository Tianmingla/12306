// 操作日志相关 API

import { request, Result, PageResult } from '@/utils/request'
import type { OperationLog, LogQueryParams } from '@/types/log'

// 获取操作日志列表
export function getLogList(params: LogQueryParams): Promise<Result<PageResult<OperationLog>>> {
  return request.get('/admin/log/list', { params })
}

// 获取日志详情
export function getLogDetail(id: number): Promise<Result<OperationLog>> {
  return request.get(`/admin/log/${id}`)
}

// 清理过期日志
export function cleanExpiredLogs(days: number): Promise<Result<string>> {
  return request.delete('/admin/log/clean', { params: { days } })
}
