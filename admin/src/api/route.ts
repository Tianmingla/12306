// 线路管理相关 API

import { request, Result, PageResult } from '@/utils/request'
import type {
  RouteQueryParams,
  RouteDetailResponse,
  TrainStationSaveRequest,
  StationVO,
} from '@/types/route'

// 获取线路列表（分页）
export function getRouteList(params: RouteQueryParams): Promise<Result<PageResult<RouteDetailResponse>>> {
  return request.get('/admin/route/list', { params })
}

// 获取线路详情
export function getRouteDetail(trainId: number): Promise<Result<RouteDetailResponse>> {
  return request.get(`/admin/route/${trainId}`)
}

// 获取列车经停站列表
export function getTrainStations(trainId: number): Promise<Result<StationVO[]>> {
  return request.get(`/admin/route/${trainId}/stations`)
}

// 新增经停站
export function addTrainStation(data: TrainStationSaveRequest): Promise<Result<string>> {
  return request.post('/admin/route/station', data)
}

// 批量保存列车经停站
export function batchSaveTrainStations(trainId: number, stations: TrainStationSaveRequest[]): Promise<Result<string>> {
  return request.post(`/admin/route/${trainId}/stations`, stations)
}

// 删除经停站
export function deleteTrainStation(id: number): Promise<Result<string>> {
  return request.delete(`/admin/route/station/${id}`)
}
