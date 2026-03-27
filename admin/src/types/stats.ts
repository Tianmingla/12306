// 统计数据类型定义

// Dashboard 统计数据
export interface DashboardStats {
  totalUsers: number
  totalOrders: number
  totalTrains: number
  totalStations: number
  todayTickets: number
  todayAmount: number
  userGrowth: number
  orderGrowth: number
}

// 趋势数据点
export interface TrendDataPoint {
  date: string
  value: number
}

// 订单趋势数据
export interface OrderTrend {
  dates: string[]
  orders: number[]
  amounts: number[]
}

// 列车类型分布
export interface TrainTypeDistribution {
  name: string
  value: number
}

// 热门线路
export interface HotRoute {
  route: string
  count: number
  growth: number
}

// 销售统计查询参数
export interface StatsQueryParams {
  startDate?: string
  endDate?: string
  type?: 'day' | 'week' | 'month'
}
