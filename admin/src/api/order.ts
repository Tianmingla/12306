// 订单相关 API

import { request, Result, PageResult } from '@/utils/request'
import type {
  Order,
  OrderDetail,
  OrderQueryParams,
  RefundRequest,
  RefundRecord,
} from '@/types/order'

// 获取订单列表
export function getOrderList(params: OrderQueryParams): Promise<Result<PageResult<Order>>> {
  return request.get('/admin/order/list', { params })
}

// 获取订单详情
export function getOrderDetail(orderSn: string): Promise<Result<OrderDetail>> {
  return request.get(`/order/detail/${orderSn}`)
}

// 更新订单状态
export function updateOrderStatus(orderSn: string, status: number): Promise<Result<void>> {
  return request.put(`/admin/order/${orderSn}/status`, { status })
}

// 取消订单
export function cancelOrder(orderSn: string): Promise<Result<void>> {
  return request.post(`/order/cancel/${orderSn}`)
}

// 退款
export function refundOrder(orderSn: string): Promise<Result<void>> {
  return request.post(`/order/refund/${orderSn}`)
}

// 获取退款记录列表
export function getRefundList(params: OrderQueryParams): Promise<Result<PageResult<RefundRecord>>> {
  return request.get('/admin/refund/list', { params })
}

// 审核退款申请
export function auditRefund(id: number, approved: boolean, remark?: string): Promise<Result<void>> {
  return request.put(`/admin/refund/${id}/audit`, { approved, remark })
}

// 导出订单
export function exportOrders(params: OrderQueryParams): Promise<Result<string>> {
  return request.get('/admin/order/export', { params })
}
