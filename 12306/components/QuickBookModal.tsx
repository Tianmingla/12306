import React, { useEffect, useState, useRef } from 'react';
import { TrainTicket, TicketSegment, SEAT_TYPE_CODE_MAP } from '../types';
import { purchaseTicket, checkTicketPurchaseStatus } from '../services/ticketService';
import { listPassengers } from '../services/passengerService';
import type { PassengerApi } from '../types';
import { X, UserPlus, Check, Train, ArrowRight } from 'lucide-react';

interface QuickBookModalProps {
  ticket: TrainTicket | null;
  travelDate: string;
  onClose: () => void;
  onPurchaseSuccess?: (orderSn: string) => void;
}

const SEAT_TYPES = ['二等座', '一等座', '商务座'];

const QuickBookModal: React.FC<QuickBookModalProps> = ({ ticket, travelDate, onClose, onPurchaseSuccess }) => {
  const [passengers, setPassengers] = useState<PassengerApi[]>([]);
  const [passengersLoading, setPassengersLoading] = useState(true);
  const [selectedPassengers, setSelectedPassengers] = useState<string[]>([]);

  // 每个 segment 的座位选择
  const [segmentSeatTypes, setSegmentSeatTypes] = useState<Record<number, string>>({});

  const [step, setStep] = useState<'fill' | 'submitting' | 'processing' | 'success' | 'error'>('fill');
  const [errorMessage, setErrorMessage] = useState('');
  const [orderSn, setOrderSn] = useState('');
  const [progress, setProgress] = useState({ current: 0, total: 0, message: '' });
  const pollingRef = useRef<ReturnType<typeof setTimeout> | null>(null);

  // 初始化：默认选择第一个座位类型
  useEffect(() => {
    if (!ticket?.segments) return;

    const defaults: Record<number, string> = {};
    ticket.segments.forEach((seg, idx) => {
      // 查找第一个有余票的座位类型，否则默认二等座
      const seatTypes = Object.keys(seg.seatsAvailable || {});
      const availableSeat = seatTypes.find(type => (seg.seatsAvailable[type] || 0) > 0);
      defaults[idx] = availableSeat || '二等座';
    });
    setSegmentSeatTypes(defaults);
  }, [ticket]);

  // 加载乘客
  useEffect(() => {
    if (!ticket) return;

    const token = localStorage.getItem('token');
    if (!token) {
      setPassengers([]);
      setPassengersLoading(false);
      return;
    }

    setPassengersLoading(true);
    listPassengers()
      .then((list) => {
        setPassengers(list);
        if (list.length > 0) {
          setSelectedPassengers([String(list[0].id)]);
        }
      })
      .catch(() => setPassengers([]))
      .finally(() => setPassengersLoading(false));
  }, [ticket]);

  // 清理轮询
  useEffect(() => {
    return () => {
      if (pollingRef.current) clearTimeout(pollingRef.current);
    };
  }, []);

  if (!ticket || !ticket.segments) return null;

  const isTransfer = ticket.segments.length > 1;
  const segments = ticket.segments;

  const togglePassenger = (id: string) => {
    setSelectedPassengers(prev =>
      prev.includes(id) ? prev.filter(p => p !== id) : [...prev, id]
    );
  };

  const handleSeatTypeChange = (segmentIdx: number, seatType: string) => {
    setSegmentSeatTypes(prev => ({ ...prev, [segmentIdx]: seatType }));
  };

  const getSegmentPrice = (segmentIdx: number, seatType: string): number => {
    const segment = segments[segmentIdx];
    return segment?.prices?.[seatType] || 0;
  };

  const getSegmentSeats = (segmentIdx: number): number => {
    const segment = segments[segmentIdx];
    const seatType = segmentSeatTypes[segmentIdx];
    return segment?.seatsAvailable?.[seatType] || 0;
  };

  const totalPrice = segments.reduce((sum, _, idx) => {
    const seatType = segmentSeatTypes[idx] || '二等座';
    return sum + getSegmentPrice(idx, seatType) * selectedPassengers.length;
  }, 0);

  const handleSubmit = async () => {
    const token = localStorage.getItem('token');
    if (!token) {
      setErrorMessage('请先登录后再购票');
      setStep('error');
      return;
    }
    const account = localStorage.getItem('userPhone') || '';
    if (!account) {
      setErrorMessage('无法获取登录手机号，请重新登录');
      setStep('error');
      return;
    }
    if (selectedPassengers.length === 0) {
      setErrorMessage('请选择乘车人');
      setStep('error');
      return;
    }

    setStep('submitting');
    setProgress({ current: 0, total: segments.length, message: '准备下单...' });

    let successOrderSn = '';
    let hasError = false;

    for (let i = 0; i < segments.length; i++) {
      const segment = segments[i];
      const seatType = segmentSeatTypes[i] || '二等座';
      const seatTypeCode = SEAT_TYPE_CODE_MAP[seatType];

      setProgress({ current: i + 1, total: segments.length, message: `正在购买第${i + 1}程 (${segment.trainNumber})...` });

      try {
        const request = {
          account,
          IDCardCodelist: selectedPassengers.map(Number),
          seatTypelist: [seatType],
          chooseSeats: [] as string[],
          trainNum: segment.trainNumber,
          startStation: segment.fromStation,
          endStation: segment.toStation,
          date: travelDate,
        };

        const json = await purchaseTicket(request);
        const status = json.data?.status;
        const orderSn = json.data?.orderSn;
        const reqId = json.data?.requestId;

        if (status === 'PROCESSING' && reqId) {
          // 异步模式，需要轮询
          const finalStatus = await pollForResult(reqId);
          if (finalStatus === 'SUCCESS') {
            successOrderSn = json.data?.orderSn || successOrderSn;
          } else {
            throw new Error('购票处理失败');
          }
        } else if (orderSn) {
          successOrderSn = orderSn;
        } else {
          throw new Error('未返回订单号');
        }
      } catch (error) {
        console.error(`第${i + 1}程购票失败:`, error);
        hasError = true;
        setErrorMessage(`第${i + 1}程 (${segment.trainNumber}) 购票失败`);
        setStep('error');
        return;
      }
    }

    if (!hasError && successOrderSn) {
      setOrderSn(successOrderSn);
      setStep('success');
    }
  };

  const pollForResult = (requestId: string): Promise<string> => {
    return new Promise((resolve, reject) => {
      const poll = async () => {
        try {
          const json = await checkTicketPurchaseStatus(requestId);
          const status = json.data?.status;

          if (status === 'SUCCESS') {
            resolve('SUCCESS');
          } else if (status === 'FAILED') {
            reject(new Error(json.data?.errorMessage || '购票失败'));
          } else {
            pollingRef.current = setTimeout(poll, 2000);
          }
        } catch (error) {
          pollingRef.current = setTimeout(poll, 3000);
        }
      };
      poll();
    });
  };

  return (
    <div className="fixed inset-0 z-50 flex items-center justify-center p-4 bg-black/60 backdrop-blur-sm animate-fade-in">
      <div className="bg-white rounded-2xl w-full max-w-3xl shadow-2xl overflow-hidden flex flex-col max-h-[90vh]">
        {/* Header */}
        <div className="bg-gradient-to-r from-orange-500 to-orange-600 p-5 text-white relative flex-shrink-0">
          <button onClick={onClose} className="absolute top-4 right-4 p-1 rounded-full hover:bg-white/20">
            <X className="h-5 w-5" />
          </button>
          <div className="flex items-center gap-3">
            <Train className="h-6 w-6" />
            <div>
              <h2 className="text-lg font-bold">一键购票</h2>
              <p className="text-orange-100 text-sm">{ticket.fromStation} → {ticket.toStation} · {travelDate}</p>
            </div>
          </div>
        </div>

        {/* Content */}
        <div className="flex-1 overflow-y-auto p-5 bg-gray-50">
          {step === 'fill' && (
            <div className="space-y-5">
              {/* 乘客选择 */}
              <div className="bg-white p-4 rounded-xl border border-gray-100 shadow-sm">
                <h3 className="font-bold text-gray-800 mb-3 flex items-center gap-2">
                  <UserPlus className="h-4 w-4" /> 选择乘车人
                </h3>
                {passengersLoading ? (
                  <p className="text-sm text-gray-500">加载中...</p>
                ) : passengers.length === 0 ? (
                  <p className="text-sm text-gray-500">暂无乘车人，请在用户菜单中添加</p>
                ) : (
                  <div className="flex flex-wrap gap-2">
                    {passengers.map(p => {
                      const isSelected = selectedPassengers.includes(String(p.id));
                      return (
                        <button
                          key={p.id}
                          onClick={() => togglePassenger(String(p.id))}
                          className={`px-3 py-1.5 rounded-lg border text-sm transition-all ${
                            isSelected ? 'bg-blue-600 border-blue-600 text-white' : 'bg-white border-gray-200 text-gray-600 hover:border-blue-300'
                          }`}
                        >
                          {p.realName}
                          {isSelected && <Check className="h-3 w-3 inline ml-1" />}
                        </button>
                      );
                    })}
                  </div>
                )}
              </div>

              {/* 座位类型选择 */}
              {segments.map((seg, idx) => (
                <div key={idx} className="bg-white p-4 rounded-xl border border-gray-100 shadow-sm">
                  <div className="flex items-center justify-between mb-3">
                    <div className="flex items-center gap-2">
                      <span className="bg-blue-100 text-blue-700 text-xs px-2 py-0.5 rounded">第{idx + 1}程</span>
                      <span className="font-medium text-gray-800">{seg.trainNumber}</span>
                    </div>
                    <div className="text-sm text-gray-500">
                      {seg.fromStation} <ArrowRight className="h-3 w-3 inline mx-1" /> {seg.toStation}
                    </div>
                  </div>

                  <div className="flex items-center gap-4">
                    <span className="text-sm text-gray-500">座位类型：</span>
                    <div className="flex gap-2">
                      {SEAT_TYPES.map(seatType => {
                        const count = seg.seatsAvailable?.[seatType] || 0;
                        const isAvailable = count > 0;
                        const isSelected = segmentSeatTypes[idx] === seatType;

                        return (
                          <button
                            key={seatType}
                            onClick={() => handleSeatTypeChange(idx, seatType)}
                            disabled={!isAvailable && count !== 0}
                            className={`px-3 py-1.5 rounded-lg border text-sm transition-all ${
                              isSelected
                                ? 'bg-orange-500 border-orange-500 text-white'
                                : isAvailable || count === 0
                                  ? 'bg-white border-gray-200 text-gray-700 hover:border-orange-300'
                                  : 'bg-gray-100 border-gray-100 text-gray-400 cursor-not-allowed'
                            }`}
                          >
                            {seatType}
                            <span className={`ml-1 text-xs ${isAvailable ? 'text-green-600' : 'text-gray-400'}`}>
                              {count > 20 ? '有票' : (count > 0 ? `${count}张` : '售罄')}
                            </span>
                          </button>
                        );
                      })}
                    </div>
                    <div className="ml-auto text-orange-500 font-bold">
                      ¥{getSegmentPrice(idx, segmentSeatTypes[idx] || '二等座')}
                    </div>
                  </div>
                </div>
              ))}

              {/* 底部汇总 */}
              <div className="bg-white p-4 rounded-xl border border-gray-100 shadow-sm flex items-center justify-between">
                <div>
                  <span className="text-gray-500 text-sm">已选 {selectedPassengers.length} 人 × {segments.length} 程</span>
                </div>
                <div className="text-right">
                  <span className="text-gray-500 text-sm">总价 </span>
                  <span className="text-2xl font-bold text-orange-500">¥{totalPrice}</span>
                </div>
              </div>
            </div>
          )}

          {step === 'submitting' && (
            <div className="flex flex-col items-center justify-center h-64">
              <div className="w-12 h-12 border-4 border-orange-500 border-t-transparent rounded-full animate-spin mb-4"></div>
              <h3 className="text-lg font-bold text-gray-800">{progress.message}</h3>
              <p className="text-gray-500 text-sm mt-2">{progress.current} / {progress.total}</p>
              <div className="w-48 h-2 bg-gray-200 rounded-full mt-3 overflow-hidden">
                <div
                  className="h-full bg-orange-500 transition-all duration-300"
                  style={{ width: `${(progress.current / progress.total) * 100}%` }}
                />
              </div>
            </div>
          )}

          {step === 'success' && (
            <div className="flex flex-col items-center justify-center h-64">
              <div className="w-16 h-16 bg-green-100 text-green-600 rounded-full flex items-center justify-center mb-4">
                <Check className="h-8 w-8" />
              </div>
              <h3 className="text-xl font-bold text-gray-800">购票成功！</h3>
              <p className="text-gray-500 mt-2">订单号: {orderSn}</p>
              <button
                onClick={() => {
                  onPurchaseSuccess?.(orderSn);
                  onClose();
                }}
                className="mt-4 px-6 py-2 bg-orange-500 text-white rounded-lg font-medium hover:bg-orange-600"
              >
                查看订单
              </button>
            </div>
          )}

          {step === 'error' && (
            <div className="flex flex-col items-center justify-center h-64">
              <div className="w-16 h-16 bg-red-100 text-red-600 rounded-full flex items-center justify-center mb-4">
                <X className="h-8 w-8" />
              </div>
              <h3 className="text-xl font-bold text-gray-800">购票失败</h3>
              <p className="text-gray-500 mt-2">{errorMessage}</p>
              <div className="flex gap-3 mt-4">
                <button
                  onClick={() => setStep('fill')}
                  className="px-6 py-2 bg-blue-600 text-white rounded-lg font-medium hover:bg-blue-700"
                >
                  重试
                </button>
                <button
                  onClick={onClose}
                  className="px-6 py-2 bg-gray-100 text-gray-700 rounded-lg font-medium hover:bg-gray-200"
                >
                  关闭
                </button>
              </div>
            </div>
          )}
        </div>

        {/* Footer */}
        {step === 'fill' && (
          <div className="p-4 bg-white border-t border-gray-200 flex justify-end gap-3 flex-shrink-0">
            <button
              onClick={onClose}
              className="px-5 py-2 bg-gray-100 text-gray-700 rounded-lg font-medium hover:bg-gray-200"
            >
              取消
            </button>
            <button
              onClick={() => handleSubmit()}
              disabled={selectedPassengers.length === 0 || !localStorage.getItem('token')}
              className="px-6 py-2 bg-orange-500 text-white rounded-lg font-medium hover:bg-orange-600 disabled:opacity-50 disabled:cursor-not-allowed"
            >
              一键购票
            </button>
          </div>
        )}
      </div>
    </div>
  );
};

export default QuickBookModal;