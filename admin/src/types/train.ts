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
  carriageNumber: number
  carriageType: CarriageType
  seatCount: number
  createTime: string
  updateTime: string
}

// 车厢类型
export enum CarriageType {
  BUSINESS = 0,       // 商务座
  FIRST_CLASS = 1,    // 一等座
  SECOND_CLASS = 2,   // 二等座
  HARD_SLEEPER = 3,   // 硬卧
  SOFT_SLEEPER = 4,   // 软卧
  HARD_SEAT = 5,      // 硬座
  SOFT_SEAT = 6,      // 软座
}

// 座位信息
export interface Seat {
  id: number
  trainId: number
  carriageNumber: number
  seatNumber: string
  seatType: SeatType
  seatStatus: SeatStatus
  createTime: string
  updateTime: string
}

// 座位类型
export enum SeatType {
  WINDOW = 0,         // 靠窗
  AISLE = 1,          // 靠过道
  MIDDLE = 2,         // 中间
}

// 座位状态
export enum SeatStatus {
  AVAILABLE = 0,      // 可售
  SOLD = 1,           // 已售
  LOCKED = 2,         // 锁定
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
