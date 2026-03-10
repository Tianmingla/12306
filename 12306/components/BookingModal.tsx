
import React, { useState } from 'react';
import { TrainTicket, Passenger } from '../types';
import { X, UserPlus, Check, CreditCard, AlertCircle } from 'lucide-react';

interface BookingModalProps {
  ticket: TrainTicket | null;
  onClose: () => void;
}

const MOCK_PASSENGERS: Passenger[] = [
  { id: '1', name: '张三', idCard: '110101199001011234', type: 'adult' },
  { id: '2', name: '李四', idCard: '110101199505204321', type: 'student' },
  { id: '3', name: '王五', idCard: '320502198811110000', type: 'adult' },
];

const BookingModal: React.FC<BookingModalProps> = ({ ticket, onClose }) => {
  const [selectedPassengers, setSelectedPassengers] = useState<string[]>(['1']);
  const [selectedSeat, setSelectedSeat] = useState<string>('');
  const [step, setStep] = useState<'fill' | 'paying' | 'success'>('fill');

  if (!ticket) return null;

  const handleSubmit = () => {
    setStep('paying');
    setTimeout(() => {
      setStep('success');
    }, 2000);
  };

  const togglePassenger = (id: string) => {
    setSelectedPassengers(prev => 
      prev.includes(id) ? prev.filter(p => p !== id) : [...prev, id]
    );
  };

  const totalPrice = ticket.price * selectedPassengers.length;

  return (
    <div className="fixed inset-0 z-50 flex items-center justify-center p-4 bg-black/60 backdrop-blur-sm animate-fade-in">
      <div className="bg-white rounded-2xl w-full max-w-2xl shadow-2xl overflow-hidden flex flex-col max-h-[90vh]">
        {/* Header */}
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
                 {ticket.departureTime} 出发 • {new Date().toLocaleDateString('zh-CN')}
              </p>
            </div>
            <div className="text-right">
              <p className="text-xs opacity-80 mb-1">单张票价</p>
              <p className="text-3xl font-bold">¥{ticket.price}</p>
            </div>
          </div>
        </div>

        {/* Content */}
        <div className="flex-1 overflow-y-auto p-6 bg-gray-50">
          {step === 'fill' && (
            <div className="space-y-6">
              {/* Passenger Selection */}
              <div className="bg-white p-5 rounded-xl border border-gray-100 shadow-sm">
                <div className="flex justify-between items-center mb-4">
                  <h3 className="font-bold text-gray-800">选择乘车人</h3>
                  <button className="text-blue-600 text-sm flex items-center hover:bg-blue-50 px-2 py-1 rounded transition-colors">
                    <UserPlus className="h-4 w-4 mr-1" /> 添加乘车人
                  </button>
                </div>
                <div className="flex flex-wrap gap-3">
                  {MOCK_PASSENGERS.map(p => {
                    const isSelected = selectedPassengers.includes(p.id);
                    return (
                      <button
                        key={p.id}
                        onClick={() => togglePassenger(p.id)}
                        className={`flex items-center px-4 py-2 rounded-lg border transition-all ${
                          isSelected 
                            ? 'bg-blue-600 border-blue-600 text-white shadow-md' 
                            : 'bg-white border-gray-200 text-gray-600 hover:border-blue-300'
                        }`}
                      >
                        <span className="mr-2">{p.name}</span>
                        <span className={`text-xs px-1.5 py-0.5 rounded ${isSelected ? 'bg-white/20' : 'bg-gray-100 text-gray-500'}`}>
                          {p.type === 'adult' ? '成人' : '学生'}
                        </span>
                        {isSelected && <Check className="h-3 w-3 ml-2" />}
                      </button>
                    );
                  })}
                </div>
              </div>

              {/* Seat Selection */}
              <div className="bg-white p-5 rounded-xl border border-gray-100 shadow-sm">
                 <h3 className="font-bold text-gray-800 mb-4">选座服务 <span className="text-xs font-normal text-gray-500 ml-2">(仅供参考，余票不足时随机分配)</span></h3>
                 <div className="flex justify-center space-x-6">
                    {['A', 'B', 'C', '过道', 'D', 'F'].map((seat, i) => {
                      if (seat === '过道') return <div key={i} className="w-8 flex items-center justify-center text-gray-300 text-xs">|</div>;
                      const isSelected = selectedSeat === seat;
                      return (
                        <button
                          key={seat}
                          onClick={() => setSelectedSeat(seat)}
                          className={`w-10 h-10 rounded-lg flex items-center justify-center font-bold text-sm transition-all ${
                            isSelected 
                            ? 'bg-blue-600 text-white shadow-lg ring-2 ring-blue-200' 
                            : 'bg-gray-100 text-gray-500 hover:bg-gray-200'
                          }`}
                        >
                          {seat}
                        </button>
                      )
                    })}
                 </div>
                 <div className="flex justify-center mt-3 space-x-8 text-xs text-gray-400">
                    <span className="flex items-center"><div className="w-3 h-3 bg-gray-200 rounded mr-1"></div> 靠窗</span>
                    <span className="flex items-center"><div className="w-3 h-3 bg-gray-200 rounded mr-1"></div> 过道</span>
                    <span className="flex items-center"><div className="w-3 h-3 bg-gray-200 rounded mr-1"></div> 靠窗</span>
                 </div>
              </div>

              {/* Contact Info (Mock) */}
              <div className="bg-white p-5 rounded-xl border border-gray-100 shadow-sm flex items-center justify-between">
                <div>
                  <h3 className="font-bold text-gray-800">联系方式</h3>
                  <p className="text-sm text-gray-500 mt-1">138****8888</p>
                </div>
                <button className="text-blue-600 text-sm">修改</button>
              </div>
            </div>
          )}

          {step === 'paying' && (
            <div className="flex flex-col items-center justify-center h-64">
              <div className="w-16 h-16 border-4 border-blue-600 border-t-transparent rounded-full animate-spin mb-6"></div>
              <h3 className="text-xl font-bold text-gray-800">正在安全支付...</h3>
              <p className="text-gray-500 mt-2">请稍候，正在为您出票</p>
            </div>
          )}

          {step === 'success' && (
            <div className="flex flex-col items-center justify-center h-64 text-center">
              <div className="w-16 h-16 bg-green-100 text-green-600 rounded-full flex items-center justify-center mb-6">
                <Check className="h-8 w-8" />
              </div>
              <h3 className="text-2xl font-bold text-gray-800">预订成功！</h3>
              <p className="text-gray-500 mt-2 mb-6">出票成功短信已发送至您的手机</p>
              <button onClick={onClose} className="px-6 py-2 bg-gray-100 text-gray-700 font-medium rounded-lg hover:bg-gray-200 transition-colors">
                返回首页
              </button>
            </div>
          )}
        </div>

        {/* Footer Actions */}
        {step === 'fill' && (
          <div className="p-4 bg-white border-t border-gray-200 flex items-center justify-between flex-shrink-0">
             <div className="text-gray-600">
               <span className="text-sm">共 {selectedPassengers.length} 人</span>
               <div className="text-orange-600 font-bold text-2xl leading-none">
                 <span className="text-sm">¥</span>{totalPrice}
               </div>
             </div>
             <button 
               onClick={handleSubmit}
               disabled={selectedPassengers.length === 0}
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
