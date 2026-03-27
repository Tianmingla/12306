// Mock 数据服务 - 开发阶段使用

import type {
  User,
  Passenger,
  Train,
  TrainStation,
  Station,
  Order,
  DashboardStats,
} from '@/types'
import { UserStatus, TrainType, SaleStatus, OrderStatus } from '@/types'

// 模拟延迟
const delay = (ms: number) => new Promise((resolve) => setTimeout(resolve, ms))

// 生成随机ID
const generateId = () => Math.floor(Math.random() * 100000)

// Mock 用户数据
export const mockUsers: User[] = [
  {
    id: 1,
    username: '张三',
    phone: '13800138001',
    email: 'zhangsan@example.com',
    status: UserStatus.NORMAL,
    createTime: '2024-01-15 10:30:00',
    updateTime: '2024-01-15 10:30:00',
  },
  {
    id: 2,
    username: '李四',
    phone: '13800138002',
    email: 'lisi@example.com',
    status: UserStatus.NORMAL,
    createTime: '2024-01-16 14:20:00',
    updateTime: '2024-01-16 14:20:00',
  },
  {
    id: 3,
    username: '王五',
    phone: '13800138003',
    email: 'wangwu@example.com',
    status: UserStatus.DISABLED,
    createTime: '2024-01-17 09:15:00',
    updateTime: '2024-01-20 16:00:00',
  },
  {
    id: 4,
    username: '赵六',
    phone: '13800138004',
    email: 'zhaoliu@example.com',
    status: UserStatus.NORMAL,
    createTime: '2024-01-18 11:45:00',
    updateTime: '2024-01-18 11:45:00',
  },
  {
    id: 5,
    username: '钱七',
    phone: '13800138005',
    email: 'qianqi@example.com',
    status: UserStatus.NORMAL,
    createTime: '2024-01-19 08:30:00',
    updateTime: '2024-01-19 08:30:00',
  },
]

// Mock 乘车人数据
export const mockPassengers: Passenger[] = [
  {
    id: 1,
    userId: 1,
    realName: '张三',
    idCardType: 0,
    idCardNumber: '110101199001011234',
    passengerType: 0,
    phone: '13800138001',
    createTime: '2024-01-15 10:30:00',
    updateTime: '2024-01-15 10:30:00',
  },
  {
    id: 2,
    userId: 1,
    realName: '张小三',
    idCardType: 0,
    idCardNumber: '110101201501011234',
    passengerType: 1,
    createTime: '2024-01-15 10:35:00',
    updateTime: '2024-01-15 10:35:00',
  },
]

// Mock 列车数据
export const mockTrains: Train[] = [
  {
    id: 1,
    trainNumber: 'G1',
    trainType: TrainType.HIGH_SPEED,
    trainTag: '复兴号',
    trainBrand: 'CR400AF',
    saleStatus: SaleStatus.ON_SALE,
    createTime: '2024-01-01 00:00:00',
    updateTime: '2024-01-01 00:00:00',
  },
  {
    id: 2,
    trainNumber: 'G2',
    trainType: TrainType.HIGH_SPEED,
    trainTag: '复兴号',
    trainBrand: 'CR400BF',
    saleStatus: SaleStatus.ON_SALE,
    createTime: '2024-01-01 00:00:00',
    updateTime: '2024-01-01 00:00:00',
  },
  {
    id: 3,
    trainNumber: 'D301',
    trainType: TrainType.BULLET,
    trainTag: '和谐号',
    saleStatus: SaleStatus.ON_SALE,
    createTime: '2024-01-01 00:00:00',
    updateTime: '2024-01-01 00:00:00',
  },
  {
    id: 4,
    trainNumber: 'T109',
    trainType: TrainType.EXPRESS,
    saleStatus: SaleStatus.ON_SALE,
    createTime: '2024-01-01 00:00:00',
    updateTime: '2024-01-01 00:00:00',
  },
  {
    id: 5,
    trainNumber: 'K101',
    trainType: TrainType.FAST,
    saleStatus: SaleStatus.STOP_SALE,
    createTime: '2024-01-01 00:00:00',
    updateTime: '2024-01-20 12:00:00',
  },
]

// Mock 车站数据
export const mockStations: Station[] = [
  { id: 1, code: 'BJP', name: '北京', spell: 'beijing', region: 'HB', regionName: '华北', createTime: '2024-01-01', updateTime: '2024-01-01' },
  { id: 2, code: 'SHH', name: '上海', spell: 'shanghai', region: 'HD', regionName: '华东', createTime: '2024-01-01', updateTime: '2024-01-01' },
  { id: 3, code: 'TJP', name: '天津', spell: 'tianjin', region: 'HB', regionName: '华北', createTime: '2024-01-01', updateTime: '2024-01-01' },
  { id: 4, code: 'CQW', name: '重庆', spell: 'chongqing', region: 'XN', regionName: '西南', createTime: '2024-01-01', updateTime: '2024-01-01' },
  { id: 5, code: 'GZQ', name: '广州', spell: 'guangzhou', region: 'HN', regionName: '华南', createTime: '2024-01-01', updateTime: '2024-01-01' },
  { id: 6, code: 'SZQ', name: '深圳', spell: 'shenzhen', region: 'HN', regionName: '华南', createTime: '2024-01-01', updateTime: '2024-01-01' },
  { id: 7, code: 'NJH', name: '南京', spell: 'nanjing', region: 'HD', regionName: '华东', createTime: '2024-01-01', updateTime: '2024-01-01' },
  { id: 8, code: 'HGH', name: '杭州', spell: 'hangzhou', region: 'HD', regionName: '华东', createTime: '2024-01-01', updateTime: '2024-01-01' },
  { id: 9, code: 'WHN', name: '武汉', spell: 'wuhan', region: 'HZ', regionName: '华中', createTime: '2024-01-01', updateTime: '2024-01-01' },
  { id: 10, code: 'CDW', name: '成都', spell: 'chengdu', region: 'XN', regionName: '西南', createTime: '2024-01-01', updateTime: '2024-01-01' },
]

// Mock 列车经停站
export const mockTrainStations: TrainStation[] = [
  { id: 1, trainId: 1, trainNumber: 'G1', stationId: 1, stationName: '北京', sequence: 1, arrivalTime: undefined, departureTime: '07:00', stopTime: 0, createTime: '2024-01-01', updateTime: '2024-01-01' },
  { id: 2, trainId: 1, trainNumber: 'G1', stationId: 7, stationName: '南京', sequence: 2, arrivalTime: '09:30', departureTime: '09:35', stopTime: 5, createTime: '2024-01-01', updateTime: '2024-01-01' },
  { id: 3, trainId: 1, trainNumber: 'G1', stationId: 2, stationName: '上海', sequence: 3, arrivalTime: '11:00', departureTime: undefined, stopTime: 0, createTime: '2024-01-01', updateTime: '2024-01-01' },
]

// Mock 订单数据
export const mockOrders: Order[] = [
  {
    id: 1,
    orderSn: 'ORD202401150001',
    userId: 1,
    username: '张三',
    trainNumber: 'G1',
    carriageNumber: 5,
    seatNumber: '12A',
    startStation: '北京',
    endStation: '上海',
    runDate: '2024-01-20',
    departureTime: '07:00',
    arrivalTime: '11:00',
    passengerName: '张三',
    passengerIdCard: '110101199001011234',
    totalAmount: 553,
    status: OrderStatus.PAID,
    payTime: '2024-01-15 10:30:00',
    payType: 0,
    createTime: '2024-01-15 10:25:00',
    updateTime: '2024-01-15 10:30:00',
  },
  {
    id: 2,
    orderSn: 'ORD202401160002',
    userId: 2,
    username: '李四',
    trainNumber: 'D301',
    carriageNumber: 3,
    seatNumber: '8F',
    startStation: '北京',
    endStation: '南京',
    runDate: '2024-01-22',
    departureTime: '08:00',
    arrivalTime: '14:30',
    passengerName: '李四',
    passengerIdCard: '110101199202021234',
    totalAmount: 280,
    status: OrderStatus.PENDING,
    createTime: '2024-01-16 14:20:00',
    updateTime: '2024-01-16 14:20:00',
  },
  {
    id: 3,
    orderSn: 'ORD202401170003',
    userId: 3,
    username: '王五',
    trainNumber: 'G2',
    carriageNumber: 2,
    seatNumber: '3C',
    startStation: '上海',
    endStation: '北京',
    runDate: '2024-01-18',
    departureTime: '08:00',
    arrivalTime: '12:00',
    passengerName: '王五',
    passengerIdCard: '110101199303031234',
    totalAmount: 553,
    status: OrderStatus.REFUNDED,
    createTime: '2024-01-17 09:15:00',
    updateTime: '2024-01-18 16:00:00',
  },
]

// Mock Dashboard 统计
export const mockDashboardStats: DashboardStats = {
  totalUsers: 12580,
  totalOrders: 45320,
  totalTrains: 856,
  totalStations: 2450,
  todayTickets: 1234,
  todayAmount: 682500,
  userGrowth: 12.5,
  orderGrowth: 8.3,
}

// Mock API 响应生成器
export const mockApi = {
  // 分页列表
  createPageResponse<T>(list: T[], pageNum: number = 1, pageSize: number = 10) {
    const start = (pageNum - 1) * pageSize
    const end = start + pageSize
    return {
      list: list.slice(start, end),
      total: list.length,
      pageNum,
      pageSize,
    }
  },

  // 成功响应
  success<T>(data: T, message: string = '操作成功') {
    return {
      code: 200,
      message,
      data,
    }
  },

  // 错误响应
  error(message: string = '操作失败', code: number = 500) {
    return {
      code,
      message,
      data: null,
    }
  },
}

// 开发环境 Mock 拦截
export function setupMock() {
  if (import.meta.env.DEV) {
    console.log('[Mock] Mock 服务已启用')
  }
}
