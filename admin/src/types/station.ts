// 车站相关类型定义

// 车站信息
export interface Station {
  id: number
  code: string
  name: string
  spell: string
  region?: string
  regionName?: string
  createTime: string
  updateTime: string
}

// 车站查询参数
export interface StationQueryParams {
  pageNum?: number
  pageSize?: number
  keyword?: string
  region?: string
}

// 车站创建/更新请求
export interface StationFormData {
  id?: number
  code: string
  name: string
  spell: string
  region?: string
  regionName?: string
}

// 线路信息（列车-车站关联）
export interface TrainRoute {
  id: number
  trainId: number
  trainNumber: string
  startStation: string
  endStation: string
  departureTime: string
  arrivalTime: string
  duration: number
  stations: TrainStation[]
}

// 线路查询参数
export interface RouteQueryParams {
  pageNum?: number
  pageSize?: number
  trainNumber?: string
  startStation?: string
  endStation?: string
}
