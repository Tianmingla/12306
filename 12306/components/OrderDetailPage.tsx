import React, { useCallback, useEffect, useState } from 'react';
import {
  ArrowLeft,
  Calendar,
  CreditCard,
  Loader2,
  MapPin,
  Train,
  User,
  Armchair,
} from 'lucide-react';
import type { OrderDetailVO } from '../types';
import { getOrderDetail, payOrder, submitAlipayForm } from '../services/orderService';

interface OrderDetailPageProps {
  orderSn: string;
  onBack: () => void;
}

function formatMoney(v: string | number | null | undefined): string {
  if (v === null || v === undefined) return '—';
  const n = typeof v === 'string' ? parseFloat(v) : v;
  if (Number.isNaN(n)) return String(v);
  return n.toFixed(2);
}

function seatTypeLabel(t: number | null | undefined): string {
  if (t == null) return '—';
  const m: Record<number, string> = {
    0: '硬座',
    1: '二等座',
    2: '一等座',
    3: '商务座',
  };
  return m[t] ?? `类型${t}`;
}

const OrderDetailPage: React.FC<OrderDetailPageProps> = ({ orderSn, onBack }) => {
  const [detail, setDetail] = useState<OrderDetailVO | null>(null);
  const [loading, setLoading] = useState(true);
  const [loadError, setLoadError] = useState('');
  const [payError, setPayError] = useState('');
  const [paying, setPaying] = useState(false);

  const load = useCallback(async () => {
    setLoading(true);
    setLoadError('');
    try {
      const d = await getOrderDetail(orderSn);
      setDetail(d);
    } catch (e: unknown) {
      setLoadError(e instanceof Error ? e.message : '加载失败');
      setDetail(null);
    } finally {
      setLoading(false);
    }
  }, [orderSn]);

  useEffect(() => {
    void load();
    const onFocus = () => void load();
    window.addEventListener('focus', onFocus);
    return () => window.removeEventListener('focus', onFocus);
  }, [load]);

  const handlePay = async () => {
    setPaying(true);
    setPayError('');
    try {
      const vo = await payOrder(orderSn);
      if (vo.payFormHtml) {
        submitAlipayForm(vo.payFormHtml);
      } else {
        setPayError(vo.hint || '未获得支付表单，请检查后端支付宝配置');
      }
    } catch (e: unknown) {
      setPayError(e instanceof Error ? e.message : '支付失败');
    } finally {
      setPaying(false);
    }
  };

  return (
    <div className="min-h-[calc(100vh-5rem)] animate-fade-in">
      <div className="max-w-3xl mx-auto px-4 py-8">
        <button
          type="button"
          onClick={onBack}
          className="inline-flex items-center gap-2 text-sm text-gray-600 hover:text-blue-600 mb-6 transition-colors"
        >
          <ArrowLeft className="h-4 w-4" />
          返回
        </button>

        {loading && (
          <div className="flex flex-col items-center justify-center py-24 text-gray-500">
            <Loader2 className="h-10 w-10 animate-spin text-blue-500 mb-3" />
            <p>正在加载订单…</p>
          </div>
        )}

        {!loading && loadError && !detail && (
          <div className="rounded-2xl border border-red-100 bg-red-50 text-red-700 px-6 py-8 text-center">
            {loadError}
          </div>
        )}

        {!loading && detail && (
          <>
            <div className="rounded-3xl overflow-hidden shadow-xl shadow-blue-900/10 border border-blue-100/80 mb-8">
              <div className="bg-gradient-to-br from-blue-700 via-blue-600 to-indigo-700 px-8 py-10 text-white relative">
                <div className="absolute top-0 right-0 w-64 h-64 bg-white/10 rounded-full blur-3xl -translate-y-1/2 translate-x-1/2 pointer-events-none" />
                <div className="relative">
                  <div className="flex items-center gap-2 text-blue-100 text-sm mb-2">
                    <Train className="h-4 w-4" />
                    <span>订单号</span>
                  </div>
                  <p className="text-lg font-mono tracking-wide break-all opacity-95">{detail.orderSn}</p>
                  <div className="mt-6 flex flex-wrap gap-3 items-center">
                    <span
                      className={`inline-flex items-center px-3 py-1 rounded-full text-xs font-semibold ${
                        detail.status === 0
                          ? 'bg-amber-400/90 text-amber-950'
                          : detail.status === 1
                            ? 'bg-emerald-400/90 text-emerald-950'
                            : 'bg-white/20 text-white'
                      }`}
                    >
                      {detail.statusText}
                    </span>
                    <span className="text-3xl font-bold">
                      ¥{formatMoney(detail.totalAmount)}
                    </span>
                  </div>
                </div>
              </div>

              <div className="bg-white px-8 py-6 space-y-6">
                <div className="grid sm:grid-cols-2 gap-4">
                  <div className="flex gap-3 p-4 rounded-2xl bg-gray-50 border border-gray-100">
                    <div className="p-2 rounded-xl bg-blue-100 text-blue-600">
                      <MapPin className="h-5 w-5" />
                    </div>
                    <div>
                      <p className="text-xs text-gray-500 mb-1">行程</p>
                      <p className="font-semibold text-gray-900">
                        {detail.startStation} → {detail.endStation}
                      </p>
                      <p className="text-sm text-gray-600 mt-1 flex items-center gap-1">
                        <Train className="h-3.5 w-3.5" />
                        {detail.trainNumber}
                      </p>
                    </div>
                  </div>
                  <div className="flex gap-3 p-4 rounded-2xl bg-gray-50 border border-gray-100">
                    <div className="p-2 rounded-xl bg-indigo-100 text-indigo-600">
                      <Calendar className="h-5 w-5" />
                    </div>
                    <div>
                      <p className="text-xs text-gray-500 mb-1">乘车日期</p>
                      <p className="font-semibold text-gray-900">
                        {detail.runDate
                          ? new Date(detail.runDate).toLocaleString('zh-CN', {
                              year: 'numeric',
                              month: '2-digit',
                              day: '2-digit',
                            })
                          : '—'}
                      </p>
                      <p className="text-xs text-gray-500 mt-1">账号 {detail.username}</p>
                    </div>
                  </div>
                </div>

                <div>
                  <h3 className="text-sm font-bold text-gray-800 mb-3 flex items-center gap-2">
                    <User className="h-4 w-4 text-blue-500" />
                    乘车人 / 席位
                  </h3>
                  <div className="space-y-3">
                    {detail.items.map((it) => (
                      <div
                        key={it.id}
                        className="flex flex-wrap items-center justify-between gap-3 p-4 rounded-2xl border border-gray-100 bg-gradient-to-r from-white to-gray-50/80"
                      >
                        <div>
                          <p className="font-medium text-gray-900">{it.passengerName || '—'}</p>
                          <p className="text-xs text-gray-500 mt-0.5">{it.idCardMasked}</p>
                        </div>
                        <div className="flex flex-wrap items-center gap-4 text-sm">
                          <span className="inline-flex items-center gap-1 text-gray-600">
                            <Armchair className="h-4 w-4 text-blue-500" />
                            {it.carriageNumber} 车厢 {it.seatNumber}
                          </span>
                          <span className="text-gray-500">{seatTypeLabel(it.seatType)}</span>
                          <span className="font-semibold text-orange-600">¥{formatMoney(it.amount)}</span>
                        </div>
                      </div>
                    ))}
                  </div>
                </div>

                {payError && (
                  <div className="text-sm text-red-600 bg-red-50 border border-red-100 rounded-xl px-4 py-3">
                    {payError}
                  </div>
                )}

                {detail.status === 0 && (
                  <div className="flex flex-col sm:flex-row sm:items-center sm:justify-between gap-4 pt-2 border-t border-gray-100">
                    <p className="text-sm text-gray-500 flex items-center gap-2">
                      <CreditCard className="h-4 w-4" />
                      使用支付宝沙箱完成支付（需在 order-service 配置密钥）
                    </p>
                    <button
                      type="button"
                      onClick={() => void handlePay()}
                      disabled={paying}
                      className="inline-flex justify-center items-center gap-2 px-8 py-3 rounded-xl bg-gradient-to-r from-orange-500 to-amber-500 text-white font-bold shadow-lg shadow-orange-500/30 hover:from-orange-600 hover:to-amber-600 disabled:opacity-60 transition-all"
                    >
                      {paying ? <Loader2 className="h-5 w-5 animate-spin" /> : null}
                      {paying ? '跳转中…' : '去支付'}
                    </button>
                  </div>
                )}

                {detail.status === 1 && (
                  <p className="text-center text-emerald-600 font-medium py-4">
                    支付成功，祝您旅途愉快。
                  </p>
                )}
              </div>
            </div>
          </>
        )}
      </div>
    </div>
  );
};

export default OrderDetailPage;
