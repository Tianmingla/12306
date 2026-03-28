// 列车相关 API

import { request, Result, PageResult } from '@/utils/request'
import type {
  Train,
  TrainStation,
  Carriage,
  Seat,
  TrainQueryParams,
  TrainFormData,
  CarriageSaveRequest,
} from '@/types/train'

// 获取列车列表
export function getTrainList(params: TrainQueryParams): Promise<Result<PageResult<Train>>> {
  return request.get('/admin/train/list', { params })
}

// 获取列车详情
export function getTrainDetail(id: number): Promise<Result<Train>> {
  return request.get(`/admin/train/${id}`)
}

// 创建列车
export function createTrain(data: TrainFormData): Promise<Result<Train>> {
  return request.post('/admin/train', data)
}

// 更新列车
export function updateTrain(id: number, data: TrainFormData): Promise<Result<Train>> {
  return request.put(`/admin/train/${id}`, data)
}

// 删除列车
export function deleteTrain(id: number): Promise<Result<void>> {
  return request.delete(`/admin/train/${id}`)
}

// 更新列车售卖状态
export function updateTrainSaleStatus(id: number, status: number): Promise<Result<void>> {
  return request.put(`/admin/train/${id}/sale-status`, { status })
}

// 获取列车经停站列表
export function getTrainStations(trainId: number): Promise<Result<TrainStation[]>> {
  return request.get(`/trainDetail/stations`, { params: { trainId } })
}

// 添加列车经停站
export function addTrainStation(data: Partial<TrainStation>): Promise<Result<TrainStation>> {
  return request.post('/admin/train/station', data)
}

// 更新列车经停站
export function updateTrainStation(id: number, data: Partial<TrainStation>): Promise<Result<TrainStation>> {
  return request.put(`/admin/train/station/${id}`, data)
}

// 删除列车经停站
export function deleteTrainStation(id: number): Promise<Result<void>> {
  return request.delete(`/admin/train/station/${id}`)
}

// 获取列车车厢列表
export function getTrainCarriages(trainId: number): Promise<Result<Carriage[]>> {
  return request.get(`/admin/train/${trainId}/carriages`)
}

// 添加车厢
export function addCarriage(data: CarriageSaveRequest): Promise<Result<string>> {
  return request.post('/admin/train/carriage', data)
}

// 更新车厢
export function updateCarriage(id: number, data: CarriageSaveRequest): Promise<Result<string>> {
  return request.put(`/admin/train/carriage/${id}`, data)
}

// 删除车厢
export function deleteCarriage(id: number): Promise<Result<string>> {
  return request.delete(`/admin/train/carriage/${id}`)
}

// 获取车厢座位列表
export function getCarriageSeats(trainId: number, carriageNumber: string): Promise<Result<Seat[]>> {
  return request.get(`/admin/train/${trainId}/carriage/${carriageNumber}/seats`)
}

// 更新座位类型
export function updateSeatType(seatId: number, seatType: number): Promise<Result<string>> {
  return request.put(`/admin/train/seat/${seatId}/type`, { seatType })
}
