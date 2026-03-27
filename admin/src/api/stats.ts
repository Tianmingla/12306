// 统计数据相关 API

import { request, Result } from '@/utils/request'
import type {
  DashboardStats,
  OrderTrend,
  TrainTypeDistribution,
  HotRoute,
  StatsQueryParams,
} from '@/types/stats'

// 获取 Dashboard 统计数据
export function getDashboardStats(): Promise<Result<DashboardStats>> {
  return request.get('/admin/stats/dashboard')
}

// 获取订单趋势数据
export function getOrderTrend(params: StatsQueryParams): Promise<Result<OrderTrend>> {
  return request.get('/admin/stats/order-trend', { params })
}

// 获取列车类型分布
export function getTrainTypeDistribution(): Promise<Result<TrainTypeDistribution[]>> {
  return request.get('/admin/stats/train-distribution')
}

// 获取热门线路
export function getHotRoutes(limit?: number): Promise<Result<HotRoute[]>> {
  return request.get('/admin/stats/hot-routes', { params: { limit: limit || 10 } })
}

// 获取销售额统计
export function getSalesStats(params: StatsQueryParams): Promise<Result<{ total: number; trend: { date: string; amount: number }[] }>> {
  return request.get('/admin/stats/sales', { params })
}
