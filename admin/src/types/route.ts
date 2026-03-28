// 线路管理类型定义

/**
 * 线路查询参数
 */
export interface RouteQueryParams {
  pageNum: number
  pageSize: number
  trainNumber?: string
  stationName?: string
}

/**
 * 站点信息
 */
export interface StationVO {
  id: number
  sequence: number
  stationName: string
  arrivalTime: string
  departureTime: string
  stopoverTime: number
}

/**
 * 线路详情响应
 */
export interface RouteDetailResponse {
  trainId: number
  trainNumber: string
  startStation: string
  endStation: string
  stationCount: number
  departureTime: string
  arrivalTime: string
  duration: number
  stations: StationVO[]
}

/**
 * 经停站保存请求
 */
export interface TrainStationSaveRequest {
  trainId?: number
  trainNumber?: string
  stationId?: number
  stationName?: string
  sequence?: number
  arrivalTime?: string
  departureTime?: string
  stopoverTime?: number
  runDate?: string
}
