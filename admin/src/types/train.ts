// 列车相关类型定义

// 列车信息
export interface Train {
  id: number
  trainNumber: string
  trainType: TrainType
  trainTag?: string
  trainBrand?: string
  saleStatus: SaleStatus
  createTime: string
  updateTime: string
}

// 列车类型
export enum TrainType {
  HIGH_SPEED = 0,     // 高铁
  BULLET = 1,         // 动车
  EXPRESS = 2,        // 特快
  FAST = 3,           // 快速
  NORMAL = 4,         // 普快
}

// 售卖状态
export enum SaleStatus {
  ON_SALE = 0,        // 可售
  STOP_SALE = 1,      // 停售
}

// 列车经停站
export interface TrainStation {
  id: number
  trainId: number
  trainNumber: string
  stationId: number
  stationName: string
  sequence: number
  arrivalTime?: string
  departureTime?: string
  stopTime?: number
  createTime: string
  updateTime: string
}

// 车厢信息
export interface Carriage {
  id: number
  trainId: number
  carriageNumber: string
  carriageType: number
  seatCount: number
  createTime: string
  updateTime: string
}

// 车厢保存请求
export interface CarriageSaveRequest {
  trainId: number
  carriageNumber: string
  carriageType: number
  seatCount: number
}

// 座位信息
export interface Seat {
  id: number
  trainId: number
  carriageNumber: string
  seatNumber: string
  seatType: number
  createTime: string
  updateTime: string
}

// 列车查询参数（普通分页）
export interface TrainQueryParams {
  pageNum?: number
  pageSize?: number
  keyword?: string
  trainType?: TrainType
  saleStatus?: SaleStatus
}

// 列车创建/更新请求
export interface TrainFormData {
  id?: number
  trainNumber: string
  trainType: TrainType
  trainTag?: string
  trainBrand?: string
  saleStatus: SaleStatus
}

// 获取车厢类型的名称
export function getCarriageTypeName(type: number): string {
  const types = ['商务座', '一等座', '二等座', '硬卧', '软卧', '硬座', '软座']
  return types[type] || '未知'
}
