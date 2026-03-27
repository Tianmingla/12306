// 订单相关类型定义

// 订单信息
export interface Order {
  id: number
  orderSn: string
  userId: number
  username: string
  trainNumber: string
  carriageNumber: number
  seatNumber: string
  startStation: string
  endStation: string
  runDate: string
  departureTime: string
  arrivalTime: string
  passengerName: string
  passengerIdCard: string
  totalAmount: number
  status: OrderStatus
  payTime?: string
  payType?: PayType
  createTime: string
  updateTime: string
}

// 订单状态
export enum OrderStatus {
  PENDING = 0,        // 待支付
  PAID = 1,           // 已支付
  CANCELLED = 2,      // 已取消
  REFUNDED = 3,       // 已退款
  COMPLETED = 4,      // 已完成
}

// 支付方式
export enum PayType {
  ALIPAY = 0,         // 支付宝
  WECHAT = 1,         // 微信
  BANK_CARD = 2,      // 银行卡
}

// 订单详情
export interface OrderDetail extends Order {
  carriageType: string
  seatType: string
  passengerType: string
  idCardType: string
}

// 订单查询参数
export interface OrderQueryParams {
  pageNum?: number
  pageSize?: number
  keyword?: string
  orderSn?: string
  status?: OrderStatus
  trainNumber?: string
  startTime?: string
  endTime?: string
}

// 退款申请
export interface RefundRequest {
  orderSn: string
  reason: string
}

// 退款记录
export interface RefundRecord {
  id: number
  orderSn: string
  refundAmount: number
  reason: string
  status: RefundStatus
  createTime: string
  updateTime: string
}

// 退款状态
export enum RefundStatus {
  PENDING = 0,        // 待处理
  APPROVED = 1,       // 已通过
  REJECTED = 2,       // 已拒绝
  COMPLETED = 3,      // 已完成
}
