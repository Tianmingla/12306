import React, { useCallback, useEffect, useState } from 'react';
import {
  ArrowLeft,
  Calendar,
  CreditCard,
  Loader2,
  MapPin,
  Train,
  Users,
  RefreshCw,
  XCircle,
  AlertCircle,
} from 'lucide-react';
import type { OrderListVO } from '../types';
import { cancelOrder, getOrderList, payOrder, refundOrder, submitAlipayForm } from '../services/orderService';

interface OrderHistoryPageProps {
  onBack: () => void;
  onViewDetail: (orderSn: string) => void;
}

function formatMoney(v: string | number | null | undefined): string {
  if (v === null || v === undefined) return '—';
  const n = typeof v === 'string' ? parseFloat(v) : v;
  if (Number.isNaN(n)) return String(v);
  return n.toFixed(2);
}

function formatDate(d: string | Date | null | undefined): string {
  if (!d) return '—';
  const date = typeof d === 'string' ? new Date(d) : d;
  return date.toLocaleDateString('zh-CN', {
    year: 'numeric',
    month: '2-digit',
    day: '2-digit',
  });
}

function isBeforeDeparture(runDate: string | Date | null | undefined): boolean {
  if (!runDate) return true;
  const date = typeof runDate === 'string' ? new Date(runDate) : runDate;
  return date > new Date();
}

const statusColorMap: Record<number, string> = {
  0: 'bg-amber-100 text-amber-800',
  1: 'bg-emerald-100 text-emerald-800',
  2: 'bg-gray-100 text-gray-600',
  3: 'bg-blue-100 text-blue-800',
};

const OrderHistoryPage: React.FC<OrderHistoryPageProps> = ({ onBack, onViewDetail }) => {
  const [orders, setOrders] = useState<OrderListVO[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState('');
  const [actionLoading, setActionLoading] = useState<string | null>(null);
  const [actionError, setActionError] = useState<string | null>(null);

  const loadOrders = useCallback(async () => {
    setLoading(true);
    setError('');
    try {
      const data = await getOrderList();
      setOrders(data);
    } catch (e: unknown) {
      setError(e instanceof Error ? e.message : '加载失败');
    } finally {
      setLoading(false);
    }
  }, []);

  useEffect(() => {
    void loadOrders();
  }, [loadOrders]);

  const handlePay = async (orderSn: string) => {
    setActionLoading(orderSn);
    setActionError(null);
    try {
      const vo = await payOrder(orderSn);
      if (vo.payFormHtml) {
        submitAlipayForm(vo.payFormHtml);
      } else {
        setActionError(vo.hint || '未获得支付表单');
      }
    } catch (e: unknown) {
      setActionError(e instanceof Error ? e.message : '支付失败');
    } finally {
      setActionLoading(null);
    }
  };

  const handleRefund = async (orderSn: string) => {
    if (!confirm('确定要退款吗？')) return;
    setActionLoading(orderSn);
    setActionError(null);
    try {
      await refundOrder(orderSn);
      await loadOrders();
    } catch (e: unknown) {
      setActionError(e instanceof Error ? e.message : '退款失败');
    } finally {
      setActionLoading(null);
    }
  };

  const handleCancel = async (orderSn: string) => {
    if (!confirm('确定要取消订单吗？')) return;
    setActionLoading(orderSn);
    setActionError(null);
    try {
      await cancelOrder(orderSn);
      await loadOrders();
    } catch (e: unknown) {
      setActionError(e instanceof Error ? e.message : '取消失败');
    } finally {
      setActionLoading(null);
    }
  };

  return (
    <div className="min-h-[calc(100vh-5rem)] animate-fade-in">
      <div className="max-w-4xl mx-auto px-4 py-8">
        <button
          type="button"
          onClick={onBack}
          className="inline-flex items-center gap-2 text-sm text-gray-600 hover:text-blue-600 mb-6 transition-colors"
        >
          <ArrowLeft className="h-4 w-4" />
          返回首页
        </button>

        <div className="flex items-center justify-between mb-6">
          <h1 className="text-2xl font-bold text-gray-900">我的订单</h1>
          <button
            type="button"
            onClick={() => void loadOrders()}
            className="text-sm text-blue-600 hover:text-blue-700 flex items-center gap-1"
          >
            <RefreshCw className="h-4 w-4" />
            刷新
          </button>
        </div>

        {loading && (
          <div className="flex flex-col items-center justify-center py-24 text-gray-500">
            <Loader2 className="h-10 w-10 animate-spin text-blue-500 mb-3" />
            <p>正在加载订单列表…</p>
          </div>
        )}

        {!loading && error && (
          <div className="rounded-2xl border border-red-100 bg-red-50 text-red-700 px-6 py-8 text-center">
            <AlertCircle className="h-8 w-8 mx-auto mb-2" />
            {error}
          </div>
        )}

        {actionError && (
          <div className="rounded-xl border border-red-100 bg-red-50 text-red-700 px-4 py-3 mb-4 text-sm">
            {actionError}
          </div>
        )}

        {!loading && !error && orders.length === 0 && (
          <div className="rounded-2xl border border-gray-100 bg-gray-50 text-gray-500 px-6 py-16 text-center">
            <Train className="h-12 w-12 mx-auto mb-3 text-gray-300" />
            <p>暂无订单记录</p>
          </div>
        )}

        {!loading && !error && orders.length > 0 && (
          <div className="space-y-4">
            {orders.map((order) => {
              const canPay = order.status === 0 && isBeforeDeparture(order.runDate);
              const canRefund = order.status === 1 && isBeforeDeparture(order.runDate);
              const canCancel = order.status === 0;
              const isLoading = actionLoading === order.orderSn;

              return (
                <div
                  key={order.orderSn}
                  className="rounded-2xl border border-gray-100 bg-white shadow-sm overflow-hidden hover:shadow-md transition-shadow"
                >
                  <div className="px-6 py-4 flex flex-wrap items-center justify-between gap-4 border-b border-gray-50">
                    <div className="flex items-center gap-3">
                      <Train className="h-5 w-5 text-blue-500" />
                      <span className="font-semibold text-gray-900">{order.trainNumber}</span>
                      <span
                        className={`px-2.5 py-0.5 rounded-full text-xs font-medium ${
                          statusColorMap[order.status] || 'bg-gray-100 text-gray-600'
                        }`}
                      >
                        {order.statusText}
                      </span>
                    </div>
                    <div className="text-sm text-gray-500">
                      订单号: {order.orderSn.slice(0, 8)}...
                    </div>
                  </div>

                  <div className="px-6 py-4">
                    <div className="grid sm:grid-cols-2 lg:grid-cols-4 gap-4">
                      <div className="flex items-start gap-2">
                        <MapPin className="h-4 w-4 text-gray-400 mt-0.5" />
                        <div>
                          <p className="text-xs text-gray-500">行程</p>
                          <p className="text-sm font-medium text-gray-900">
                            {order.startStation} → {order.endStation}
                          </p>
                        </div>
                      </div>
                      <div className="flex items-start gap-2">
                        <Calendar className="h-4 w-4 text-gray-400 mt-0.5" />
                        <div>
                          <p className="text-xs text-gray-500">乘车日期</p>
                          <p className="text-sm font-medium text-gray-900">{formatDate(order.runDate)}</p>
                        </div>
                      </div>
                      <div className="flex items-start gap-2">
                        <Users className="h-4 w-4 text-gray-400 mt-0.5" />
                        <div>
                          <p className="text-xs text-gray-500">乘客</p>
                          <p className="text-sm font-medium text-gray-900">{order.passengerCount} 人</p>
                        </div>
                      </div>
                      <div className="flex items-start gap-2">
                        <CreditCard className="h-4 w-4 text-gray-400 mt-0.5" />
                        <div>
                          <p className="text-xs text-gray-500">金额</p>
                          <p className="text-sm font-bold text-orange-600">¥{formatMoney(order.totalAmount)}</p>
                        </div>
                      </div>
                    </div>
                  </div>

                  <div className="px-6 py-3 bg-gray-50/50 flex flex-wrap items-center justify-between gap-3">
                    <button
                      type="button"
                      onClick={() => onViewDetail(order.orderSn)}
                      className="text-sm text-blue-600 hover:text-blue-700 font-medium"
                    >
                      查看详情
                    </button>
                    <div className="flex flex-wrap items-center gap-2">
                      {canPay && (
                        <button
                          type="button"
                          onClick={() => void handlePay(order.orderSn)}
                          disabled={isLoading}
                          className="inline-flex items-center gap-1.5 px-4 py-1.5 rounded-lg bg-gradient-to-r from-orange-500 to-amber-500 text-white text-sm font-medium hover:from-orange-600 hover:to-amber-600 disabled:opacity-60 transition-all"
                        >
                          {isLoading ? <Loader2 className="h-4 w-4 animate-spin" /> : <CreditCard className="h-4 w-4" />}
                          去支付
                        </button>
                      )}
                      {canRefund && (
                        <button
                          type="button"
                          onClick={() => void handleRefund(order.orderSn)}
                          disabled={isLoading}
                          className="inline-flex items-center gap-1.5 px-4 py-1.5 rounded-lg bg-blue-500 text-white text-sm font-medium hover:bg-blue-600 disabled:opacity-60 transition-colors"
                        >
                          {isLoading ? <Loader2 className="h-4 w-4 animate-spin" /> : <RefreshCw className="h-4 w-4" />}
                          退款
                        </button>
                      )}
                      {canCancel && (
                        <button
                          type="button"
                          onClick={() => void handleCancel(order.orderSn)}
                          disabled={isLoading}
                          className="inline-flex items-center gap-1.5 px-4 py-1.5 rounded-lg border border-gray-200 text-gray-600 text-sm font-medium hover:bg-gray-50 disabled:opacity-60 transition-colors"
                        >
                          {isLoading ? <Loader2 className="h-4 w-4 animate-spin" /> : <XCircle className="h-4 w-4" />}
                          取消订单
                        </button>
                      )}
                    </div>
                  </div>
                </div>
              );
            })}
          </div>
        )}
      </div>
    </div>
  );
};

export default OrderHistoryPage;
