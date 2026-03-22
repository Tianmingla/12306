
import React, { useEffect, useState } from 'react';
import { TrainTicket } from '../types';
import { X, UserPlus, Check, CreditCard, AlertCircle } from 'lucide-react';
import { purchaseTicket } from '../services/ticketService';
import { listPassengers } from '../services/passengerService';
import type { PassengerApi } from '../types';

interface BookingModalProps {
  ticket: TrainTicket | null;
  onClose: () => void;
  /** 查询车次使用的出发日期 yyyy-MM-dd */
  travelDate: string;
  /** 下单成功，跳转订单详情 */
  onPurchaseSuccess?: (orderSn: string) => void;
}

function passengerTypeLabel(t: number): string {
  switch (t) {
    case 1: return '成人';
    case 2: return '儿童';
    case 3: return '学生';
    default: return '其他';
  }
}

const BookingModal: React.FC<BookingModalProps> = ({ ticket, onClose, travelDate, onPurchaseSuccess }) => {
  const [passengers, setPassengers] = useState<PassengerApi[]>([]);
  const [passengersLoading, setPassengersLoading] = useState(false);
  const [selectedPassengers, setSelectedPassengers] = useState<string[]>([]);
  const [selectedSeats, setSelectedSeats] = useState<string[]>([]);
  const [step, setStep] = useState<'fill' | 'paying' | 'error'>('fill');
  const [errorMessage, setErrorMessage] = useState<string>('');

  useEffect(() => {
    if (!ticket) return;
    const token = localStorage.getItem('token');
    if (!token) {
      setPassengers([]);
      setSelectedPassengers([]);
      return;
    }
    setPassengersLoading(true);
    listPassengers()
      .then((list) => {
        setPassengers(list);
        if (list.length > 0) {
          setSelectedPassengers([String(list[0].id)]);
        } else {
          setSelectedPassengers([]);
        }
      })
      .catch(() => setPassengers([]))
      .finally(() => setPassengersLoading(false));
  }, [ticket]);

  if (!ticket) return null;

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

    setStep('paying');
    try {
      const IDCardCodelist = selectedPassengers.map((id) => Number(id));
      const seatTypelist = selectedPassengers.map(() => '二等座');
      const chooseSeats = selectedSeats;

      const request = {
        account,
        IDCardCodelist,
        seatTypelist,
        chooseSeats,
        trainNum: ticket.trainNumber,
        startStation: ticket.fromStation,
        endStation: ticket.toStation,
        date: travelDate,
      };

      const json = await purchaseTicket(request);
      const orderSn = json.data?.orderSn;
      if (!orderSn) {
        throw new Error('未返回订单号');
      }
      onPurchaseSuccess?.(orderSn);
      onClose();
    } catch (error: unknown) {
      console.error(error);
      setErrorMessage(error instanceof Error ? error.message : '购票失败，请重试');
      setStep('error');
    }
  };

  const togglePassenger = (id: string) => {
    setSelectedPassengers((prev) => {
      const next = prev.includes(id) ? prev.filter((p) => p !== id) : [...prev, id];
      if (selectedSeats.length > next.length) {
        setSelectedSeats(selectedSeats.slice(0, next.length));
      }
      return next;
    });
  };

  const toggleSeat = (seat: string) => {
    setSelectedSeats((prev) => {
      if (prev.includes(seat)) {
        return prev.filter((s) => s !== seat);
      }
      if (prev.length < selectedPassengers.length) {
        return [...prev, seat];
      }
      return [...prev.slice(1), seat];
    });
  };

  const totalPrice = ticket.price * selectedPassengers.length;
  const userPhone = localStorage.getItem('userPhone') || '';
  const maskedPhone = userPhone.length >= 7
    ? `${userPhone.slice(0, 3)}****${userPhone.slice(-4)}`
    : userPhone || '—';

  return (
      <div className="fixed inset-0 z-50 flex items-center justify-center p-4 bg-black/60 backdrop-blur-sm animate-fade-in">
        <div className="bg-white rounded-2xl w-full max-w-2xl shadow-2xl overflow-hidden flex flex-col max-h-[90vh]">
          <div className="bg-gradient-to-r from-blue-600 to-blue-700 p-6 text-white relative flex-shrink-0">
            <button
                onClick={onClose}
                className="absolute top-6 right-6 p-1 rounded-full hover:bg-white/20 transition-colors"
            >
              <X className="h-5 w-5" />
            </button>
            <div className="flex justify-between items-end">
              <div>
                <div className="flex items-center space-x-3 mb-1">
                  <span className="text-2xl font-bold">{ticket.fromStation}</span>
                  <div className="flex flex-col items-center px-2">
                    <span className="text-xs opacity-80">{ticket.trainNumber}</span>
                    <div className="w-16 h-0.5 bg-white/50 my-1 relative">
                      <div className="absolute right-0 -top-1 w-2 h-2 border-t border-r border-white/50 rotate-45"></div>
                    </div>
                    <span className="text-xs opacity-80">{ticket.duration}</span>
                  </div>
                  <span className="text-2xl font-bold">{ticket.toStation}</span>
                </div>
                <p className="text-blue-100 text-sm mt-2">
                  {ticket.departureTime} 出发 • {travelDate}
                </p>
              </div>
              <div className="text-right">
                <p className="text-xs opacity-80 mb-1">单张票价</p>
                <p className="text-3xl font-bold">¥{ticket.price}</p>
              </div>
            </div>
          </div>

          <div className="flex-1 overflow-y-auto p-6 bg-gray-50">
            {step === 'fill' && (
                <div className="space-y-6">
                  {!localStorage.getItem('token') && (
                    <div className="bg-amber-50 border border-amber-100 text-amber-800 text-sm px-4 py-3 rounded-lg">
                      请先登录后再选择乘车人并提交订单。
                    </div>
                  )}
                  <div className="bg-white p-5 rounded-xl border border-gray-100 shadow-sm">
                    <div className="flex justify-between items-center mb-4">
                      <h3 className="font-bold text-gray-800">选择乘车人</h3>
                      <span className="text-xs text-gray-400">在导航栏用户菜单中可管理乘车人</span>
                    </div>
                    {passengersLoading ? (
                      <p className="text-sm text-gray-500">加载乘车人…</p>
                    ) : passengers.length === 0 ? (
                      <div className="text-sm text-gray-500 flex items-center gap-2">
                        <UserPlus className="h-4 w-4" />
                        暂无乘车人，请登录后在右上角用户菜单 → 乘车人管理 中添加。
                      </div>
                    ) : (
                    <div className="flex flex-wrap gap-3">
                      {passengers.map((p) => {
                        const idStr = String(p.id);
                        const isSelected = selectedPassengers.includes(idStr);
                        return (
                            <button
                                key={p.id}
                                type="button"
                                onClick={() => togglePassenger(idStr)}
                                className={`flex items-center px-4 py-2 rounded-lg border transition-all ${
                                    isSelected
                                        ? 'bg-blue-600 border-blue-600 text-white shadow-md'
                                        : 'bg-white border-gray-200 text-gray-600 hover:border-blue-300'
                                }`}
                            >
                              <span className="mr-2">{p.realName}</span>
                              <span className={`text-xs px-1.5 py-0.5 rounded ${isSelected ? 'bg-white/20' : 'bg-gray-100 text-gray-500'}`}>
                          {passengerTypeLabel(p.passengerType)}
                        </span>
                              {isSelected && <Check className="h-3 w-3 ml-2" />}
                            </button>
                        );
                      })}
                    </div>
                    )}
                  </div>

                  <div className="bg-white p-5 rounded-xl border border-gray-100 shadow-sm">
                    <h3 className="font-bold text-gray-800 mb-4">选座服务 <span className="text-xs font-normal text-gray-500 ml-2">(仅供参考，余票不足时随机分配)</span></h3>
                    <div className="flex justify-center space-x-6">
                      {['A', 'B', 'C', '过道', 'D', 'F'].map((seat, i) => {
                        if (seat === '过道') return <div key={i} className="w-8 flex items-center justify-center text-gray-300 text-xs">|</div>;
                        const isSelected = selectedSeats.includes(seat);
                        return (
                            <button
                                key={seat}
                                type="button"
                                onClick={() => toggleSeat(seat)}
                                className={`w-10 h-10 rounded-lg flex items-center justify-center font-bold text-sm transition-all ${
                                    isSelected
                                        ? 'bg-blue-600 text-white shadow-lg ring-2 ring-blue-200'
                                        : 'bg-gray-100 text-gray-500 hover:bg-gray-200'
                                }`}
                            >
                              {seat}
                            </button>
                        );
                      })}
                    </div>
                    <div className="text-center mt-2 text-xs text-gray-500">
                      已选 {selectedSeats.length}/{selectedPassengers.length} 个偏好座位
                    </div>
                    <div className="flex justify-center mt-3 space-x-8 text-xs text-gray-400">
                      <span className="flex items-center"><div className="w-3 h-3 bg-gray-200 rounded mr-1"></div> 靠窗</span>
                      <span className="flex items-center"><div className="w-3 h-3 bg-gray-200 rounded mr-1"></div> 过道</span>
                      <span className="flex items-center"><div className="w-3 h-3 bg-gray-200 rounded mr-1"></div> 靠窗</span>
                    </div>
                  </div>

                  <div className="bg-white p-5 rounded-xl border border-gray-100 shadow-sm flex items-center justify-between">
                    <div>
                      <h3 className="font-bold text-gray-800">联系方式</h3>
                      <p className="text-sm text-gray-500 mt-1">{maskedPhone}</p>
                    </div>
                    <CreditCard className="h-8 w-8 text-gray-200" />
                  </div>
                </div>
            )}

            {step === 'paying' && (
                <div className="flex flex-col items-center justify-center h-64">
                  <div className="w-16 h-16 border-4 border-blue-600 border-t-transparent rounded-full animate-spin mb-6"></div>
                  <h3 className="text-xl font-bold text-gray-800">正在创建订单…</h3>
                  <p className="text-gray-500 mt-2">请稍候</p>
                </div>
            )}

            {step === 'error' && (
                <div className="flex flex-col items-center justify-center h-64 text-center">
                  <div className="w-16 h-16 bg-red-100 text-red-600 rounded-full flex items-center justify-center mb-6">
                    <AlertCircle className="h-8 w-8" />
                  </div>
                  <h3 className="text-2xl font-bold text-gray-800">预订失败</h3>
                  <p className="text-gray-500 mt-2 mb-6">{errorMessage}</p>
                  <div className="flex space-x-4">
                    <button type="button" onClick={() => setStep('fill')} className="px-6 py-2 bg-blue-600 text-white font-medium rounded-lg hover:bg-blue-700 transition-colors">
                      重新尝试
                    </button>
                    <button type="button" onClick={onClose} className="px-6 py-2 bg-gray-100 text-gray-700 font-medium rounded-lg hover:bg-gray-200 transition-colors">
                      取消
                    </button>
                  </div>
                </div>
            )}

          </div>

          {step === 'fill' && (
              <div className="p-4 bg-white border-t border-gray-200 flex items-center justify-between flex-shrink-0">
                <div className="text-gray-600">
                  <span className="text-sm">共 {selectedPassengers.length} 人</span>
                  <div className="text-orange-600 font-bold text-2xl leading-none">
                    <span className="text-sm">¥</span>{totalPrice}
                  </div>
                </div>
                <button
                    type="button"
                    onClick={() => void handleSubmit()}
                    disabled={selectedPassengers.length === 0 || !localStorage.getItem('token')}
                    className="bg-orange-500 hover:bg-orange-600 text-white px-8 py-3 rounded-xl font-bold shadow-lg shadow-orange-500/30 transition-all transform active:scale-95 disabled:opacity-50 disabled:cursor-not-allowed"
                >
                  提交订单
                </button>
              </div>
          )}
        </div>
      </div>
  );
};

export default BookingModal;
