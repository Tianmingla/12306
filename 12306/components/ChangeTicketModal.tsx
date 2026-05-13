import React, { useCallback, useEffect, useState } from 'react';
import {
  X,
  Loader2,
  Train,
  MapPin,
  Clock,
  Check,
  AlertCircle,
} from 'lucide-react';
import type { TrainTicket, SearchParams, OrderDetailVO, ChangeOrderRequest } from '../types';
import { searchTickets } from '../services/ticketService';
import { changeOrder } from '../services/orderService';

interface ChangeTicketModalProps {
  order: OrderDetailVO;
  onClose: () => void;
  onChanged: (newOrderSn: string) => void;
}

type Step = 'search' | 'select' | 'submitting' | 'success' | 'error';

const ChangeTicketModal: React.FC<ChangeTicketModalProps> = ({ order, onClose, onChanged }) => {
  const [step, setStep] = useState<Step>('search');
  const [tickets, setTickets] = useState<TrainTicket[]>([]);
  const [loading, setLoading] = useState(false);
  const [selectedTicket, setSelectedTicket] = useState<TrainTicket | null>(null);
  const [selectedSeatType, setSelectedSeatType] = useState<string>('');
  const [errorMsg, setErrorMsg] = useState('');

  const runDate = order.runDate
    ? new Date(order.runDate).toISOString().slice(0, 10)
    : '';

  const doSearch = useCallback(async () => {
    setLoading(true);
    setErrorMsg('');
    try {
      const params: SearchParams = {
        from: order.startStation,
        to: order.endStation,
        date: runDate,
        onlyHighSpeed: false,
        searchType: 'oneWay',
      };
      const results = await searchTickets(params);
      // Exclude the original train
      const filtered = results.filter(
        (t) => !t.trainNumber.includes(order.trainNumber)
      );
      setTickets(filtered);
    } catch (e: unknown) {
      setErrorMsg(e instanceof Error ? e.message : '查询车次失败');
    } finally {
      setLoading(false);
    }
  }, [order.startStation, order.endStation, order.trainNumber, runDate]);

  useEffect(() => {
    void doSearch();
  }, [doSearch]);

  const handleSelectTicket = (ticket: TrainTicket) => {
    setSelectedTicket(ticket);
    // Auto-select cheapest available seat type
    if (ticket.prices) {
      const entries = Object.entries(ticket.prices).filter(([, v]) => v > 0);
      if (entries.length > 0) {
        entries.sort((a, b) => a[1] - b[1]);
        setSelectedSeatType(entries[0][0]);
      }
    }
    setStep('select');
  };

  const handleSubmit = async () => {
    if (!selectedTicket) return;
    setStep('submitting');
    setErrorMsg('');
    try {
      const passengerIds = order.items
        .map((it) => it.passengerId)
        .filter((id): id is number => id != null);

      const request: ChangeOrderRequest = {
        trainNumber: selectedTicket.trainNumber,
        startStation: order.startStation,
        endStation: order.endStation,
        runDate,
        passengerIds,
        seatTypelist: [selectedSeatType],
      };
      const newOrderSn = await changeOrder(order.orderSn, request);
      setStep('success');
      onChanged(newOrderSn);
    } catch (e: unknown) {
      setErrorMsg(e instanceof Error ? e.message : '改签失败');
      setStep('error');
    }
  };

  return (
    <div className="fixed inset-0 z-50 flex items-center justify-center p-4 bg-black/60 backdrop-blur-sm animate-fade-in">
      <div className="bg-white rounded-2xl w-full max-w-2xl shadow-2xl overflow-hidden flex flex-col max-h-[85vh]">
        {/* Header */}
        <div className="bg-gradient-to-r from-blue-600 to-indigo-600 p-6 text-white relative flex-shrink-0">
          <button
            onClick={onClose}
            className="absolute top-4 right-4 p-1 rounded-full hover:bg-white/20 transition-colors"
          >
            <X className="h-5 w-5" />
          </button>
          <h2 className="text-xl font-bold mb-1">改签车票</h2>
          <div className="flex items-center gap-2 text-blue-100 text-sm">
            <MapPin className="h-4 w-4" />
            <span>{order.startStation} → {order.endStation}</span>
            <span className="mx-1">|</span>
            <Clock className="h-4 w-4" />
            <span>{runDate}</span>
            <span className="mx-1">|</span>
            <Train className="h-4 w-4" />
            <span>原车次 {order.trainNumber}</span>
          </div>
        </div>

        {/* Body */}
        <div className="flex-1 overflow-y-auto p-6 bg-gray-50">
          {/* Search / Loading */}
          {step === 'search' && (
            <div>
              {loading && (
                <div className="flex flex-col items-center py-12 text-gray-500">
                  <Loader2 className="h-8 w-8 animate-spin text-blue-500 mb-3" />
                  <p>正在查询可改签车次…</p>
                </div>
              )}
              {!loading && errorMsg && (
                <div className="text-sm text-red-600 bg-red-50 border border-red-100 rounded-xl px-4 py-3">
                  {errorMsg}
                </div>
              )}
              {!loading && !errorMsg && tickets.length === 0 && (
                <div className="text-center py-12 text-gray-500">
                  <p>暂无可改签的同路线车次</p>
                </div>
              )}
              {!loading && tickets.length > 0 && (
                <div className="space-y-3">
                  <p className="text-sm text-gray-600 mb-2">选择新车次：</p>
                  {tickets.map((t) => (
                    <button
                      key={t.id}
                      type="button"
                      onClick={() => handleSelectTicket(t)}
                      className="w-full text-left p-4 rounded-xl border border-gray-200 bg-white hover:border-blue-400 hover:shadow-md transition-all"
                    >
                      <div className="flex items-center justify-between">
                        <div>
                          <p className="font-bold text-gray-900 flex items-center gap-2">
                            <Train className="h-4 w-4 text-blue-500" />
                            {t.trainNumber}
                          </p>
                          <p className="text-sm text-gray-600 mt-1">
                            {t.fromStation} → {t.toStation}
                          </p>
                        </div>
                        <div className="text-right">
                          <p className="text-sm text-gray-600">
                            {t.departureTime} - {t.arrivalTime}
                          </p>
                          <p className="text-lg font-bold text-orange-600 mt-1">
                            ¥{t.price}
                          </p>
                        </div>
                      </div>
                      {t.seatsAvailable && (
                        <div className="flex flex-wrap gap-2 mt-2">
                          {Object.entries(t.seatsAvailable).map(([type, count]) => (
                            <span
                              key={type}
                              className={`text-xs px-2 py-0.5 rounded-full ${
                                count > 0
                                  ? 'bg-green-50 text-green-700'
                                  : 'bg-red-50 text-red-600'
                              }`}
                            >
                              {type} {count > 0 ? `${count}张` : '无票'}
                            </span>
                          ))}
                        </div>
                      )}
                    </button>
                  ))}
                </div>
              )}
            </div>
          )}

          {/* Seat type selection */}
          {step === 'select' && selectedTicket && (
            <div className="space-y-5">
              <button
                type="button"
                onClick={() => {
                  setStep('search');
                  setSelectedTicket(null);
                }}
                className="text-sm text-blue-600 hover:text-blue-800"
              >
                ← 返回车次列表
              </button>

              <div className="bg-white p-4 rounded-xl border border-gray-200">
                <p className="font-bold text-gray-900 flex items-center gap-2 mb-2">
                  <Train className="h-4 w-4 text-blue-500" />
                  {selectedTicket.trainNumber}
                </p>
                <p className="text-sm text-gray-600">
                  {selectedTicket.fromStation} → {selectedTicket.toStation} |{' '}
                  {selectedTicket.departureTime} - {selectedTicket.arrivalTime}
                </p>
              </div>

              <div>
                <p className="text-sm font-bold text-gray-800 mb-3">选择座位类型</p>
                <div className="flex flex-wrap gap-3">
                  {selectedTicket.prices &&
                    Object.entries(selectedTicket.prices)
                      .filter(([, v]) => v > 0)
                      .map(([type, price]) => {
                        const available = selectedTicket.seatsAvailable?.[type] ?? 0;
                        const isSelected = selectedSeatType === type;
                        return (
                          <button
                            key={type}
                            type="button"
                            disabled={available <= 0}
                            onClick={() => setSelectedSeatType(type)}
                            className={`px-4 py-3 rounded-xl border text-left transition-all ${
                              isSelected
                                ? 'border-blue-500 bg-blue-50 ring-2 ring-blue-200'
                                : available > 0
                                  ? 'border-gray-200 bg-white hover:border-blue-300'
                                  : 'border-gray-100 bg-gray-50 text-gray-400 cursor-not-allowed'
                            }`}
                          >
                            <p className="font-medium">{type}</p>
                            <p className="text-sm text-orange-600">¥{price}</p>
                            <p className={`text-xs ${available > 0 ? 'text-green-600' : 'text-red-500'}`}>
                              {available > 0 ? `余${available}张` : '无票'}
                            </p>
                          </button>
                        );
                      })}
                </div>
              </div>

              <div className="bg-amber-50 border border-amber-200 text-amber-800 text-sm px-4 py-3 rounded-lg">
                改签后将释放原订单座位，新订单需重新支付。原订单状态将变更为"已改签"。
              </div>
            </div>
          )}

          {/* Submitting */}
          {step === 'submitting' && (
            <div className="flex flex-col items-center py-16 text-gray-500">
              <Loader2 className="h-10 w-10 animate-spin text-blue-500 mb-3" />
              <p className="font-medium text-gray-800">正在改签…</p>
              <p className="text-sm mt-1">请稍候</p>
            </div>
          )}

          {/* Success */}
          {step === 'success' && (
            <div className="flex flex-col items-center py-16 text-center">
              <div className="w-16 h-16 bg-green-100 text-green-600 rounded-full flex items-center justify-center mb-4">
                <Check className="h-8 w-8" />
              </div>
              <p className="text-xl font-bold text-gray-800">改签成功</p>
              <p className="text-sm text-gray-500 mt-2">原订单已标记为"已改签"，新订单已创建。</p>
            </div>
          )}

          {/* Error */}
          {step === 'error' && (
            <div className="flex flex-col items-center py-16 text-center">
              <div className="w-16 h-16 bg-red-100 text-red-600 rounded-full flex items-center justify-center mb-4">
                <AlertCircle className="h-8 w-8" />
              </div>
              <p className="text-xl font-bold text-gray-800">改签失败</p>
              <p className="text-sm text-gray-500 mt-2">{errorMsg}</p>
              <button
                type="button"
                onClick={() => {
                  setStep('search');
                  setErrorMsg('');
                }}
                className="mt-4 px-6 py-2 bg-blue-600 text-white rounded-lg hover:bg-blue-700"
              >
                重新选择
              </button>
            </div>
          )}
        </div>

        {/* Footer */}
        {(step === 'search' || step === 'select') && (
          <div className="p-4 bg-white border-t border-gray-200 flex items-center justify-end gap-3 flex-shrink-0">
            <button
              type="button"
              onClick={onClose}
              className="px-6 py-2 rounded-xl border border-gray-300 text-gray-700 font-medium hover:bg-gray-50 transition-colors"
            >
              取消
            </button>
            {step === 'select' && (
              <button
                type="button"
                onClick={() => void handleSubmit()}
                disabled={!selectedSeatType}
                className="px-8 py-2 rounded-xl bg-blue-600 text-white font-bold hover:bg-blue-700 disabled:opacity-50 transition-colors"
              >
                确认改签
              </button>
            )}
          </div>
        )}

        {step === 'success' && (
          <div className="p-4 bg-white border-t border-gray-200 flex items-center justify-end flex-shrink-0">
            <button
              type="button"
              onClick={onClose}
              className="px-8 py-2 rounded-xl bg-blue-600 text-white font-bold hover:bg-blue-700 transition-colors"
            >
              完成
            </button>
          </div>
        )}
      </div>
    </div>
  );
};

export default ChangeTicketModal;
