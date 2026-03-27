// 车站相关 API

import { request, Result, PageResult } from '@/utils/request'
import type {
  Station,
  StationQueryParams,
  StationFormData,
  TrainRoute,
  RouteQueryParams,
} from '@/types/station'

// 获取车站列表
export function getStationList(params: StationQueryParams): Promise<Result<PageResult<Station>>> {
  return request.get('/admin/station/list', { params })
}

// 获取车站详情
export function getStationDetail(id: number): Promise<Result<Station>> {
  return request.get(`/admin/station/${id}`)
}

// 创建车站
export function createStation(data: StationFormData): Promise<Result<Station>> {
  return request.post('/admin/station', data)
}

// 更新车站
export function updateStation(id: number, data: StationFormData): Promise<Result<Station>> {
  return request.put(`/admin/station/${id}`, data)
}

// 删除车站
export function deleteStation(id: number): Promise<Result<void>> {
  return request.delete(`/admin/station/${id}`)
}

// 获取所有车站（用于下拉选择）
export function getAllStations(): Promise<Result<Station[]>> {
  return request.get('/admin/station/all')
}

// 获取线路列表
export function getRouteList(params: RouteQueryParams): Promise<Result<PageResult<TrainRoute>>> {
  return request.get('/admin/route/list', { params })
}

// 获取线路详情
export function getRouteDetail(trainId: number): Promise<Result<TrainRoute>> {
  return request.get(`/admin/route/${trainId}`)
}
