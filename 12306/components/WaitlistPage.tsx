
import React, { useState, useEffect } from 'react';
import {
  Clock, Users, Ticket, ArrowLeft, Plus, X, CheckCircle,
  AlertTriangle, Calendar, MapPin, Train, Trash2, RefreshCw
} from 'lucide-react';
import { WaitlistOrderVO } from '../types';
import { getWaitlistOrders, cancelWaitlist } from '../services/stationService';

interface WaitlistPageProps {
  onBack: () => void;
}

const WaitlistPage: React.FC<WaitlistPageProps> = ({ onBack }) => {
  const [orders, setOrders] = useState<WaitlistOrderVO[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    loadOrders();
    // 每60秒刷新一次
    const interval = setInterval(loadOrders, 60000);
    return () => clearInterval(interval);
  }, []);

  const loadOrders = async () => {
    try {
      setLoading(true);
      const data = await getWaitlistOrders();
      setOrders(data);
      setError(null);
    } catch (e: any) {
      setError(e.message || '加载失败');
    } finally {
      setLoading(false);
    }
  };

  const handleCancel = async (waitlistSn: string) => {
    if (!confirm('确定要取消这个候补订单吗？')) return;

    try {
      await cancelWaitlist(waitlistSn);
      setOrders(orders.map(o =>
        o.waitlistSn === waitlistSn ? { ...o, status: 3, statusText: '已取消' } : o
      ));
    } catch (e: any) {
      alert(e.message || '取消失败');
    }
  };

  const getStatusBadge = (status: number) => {
    switch (status) {
      case 0:
        return <span className="px-3 py-1 bg-blue-100 text-blue-700 rounded-full text-sm font-medium">待兑现</span>;
      case 1:
        return <span className="px-3 py-1 bg-yellow-100 text-yellow-700 rounded-full text-sm font-medium animate-pulse">兑现中</span>;
      case 2:
        return <span className="px-3 py-1 bg-green-100 text-green-700 rounded-full text-sm font-medium">已兑现</span>;
      case 3:
        return <span className="px-3 py-1 bg-gray-100 text-gray-600 rounded-full text-sm font-medium">已取消</span>;
      case 4:
        return <span className="px-3 py-1 bg-red-100 text-red-700 rounded-full text-sm font-medium">已过期</span>;
      default:
        return null;
    }
  };

  const formatDate = (dateStr: string) => {
    try {
      const date = new Date(dateStr);
      return date.toLocaleDateString('zh-CN', { month: 'long', day: 'numeric', weekday: 'short' });
    } catch {
      return dateStr;
    }
  };

  return (
    <div className="min-h-screen bg-gray-50">
      {/* 头部 */}
      <div className="bg-white border-b border-gray-200 sticky top-0 z-10">
        <div className="max-w-4xl mx-auto px-4 py-4">
          <div className="flex items-center justify-between">
            <div className="flex items-center space-x-4">
              <button onClick={onBack} className="p-2 hover:bg-gray-100 rounded-lg">
                <ArrowLeft className="h-5 w-5 text-gray-600" />
              </button>
              <div>
                <h1 className="text-xl font-bold text-gray-900">候补购票</h1>
                <p className="text-sm text-gray-500">无票时自动为您排队购票</p>
              </div>
            </div>
            <button
              onClick={loadOrders}
              className="p-2 hover:bg-gray-100 rounded-lg text-gray-600"
            >
              <RefreshCw className={`h-5 w-5 ${loading ? 'animate-spin' : ''}`} />
            </button>
          </div>
        </div>
      </div>

      <div className="max-w-4xl mx-auto px-4 py-6">
        {/* 说明卡片 */}
        <div className="bg-gradient-to-r from-blue-50 to-indigo-50 rounded-xl p-6 mb-6 border border-blue-100">
          <div className="flex items-start space-x-4">
            <div className="p-3 bg-blue-100 rounded-lg">
              <Ticket className="h-6 w-6 text-blue-600" />
            </div>
            <div>
              <h3 className="font-semibold text-gray-900">什么是候补购票？</h3>
              <p className="text-sm text-gray-600 mt-1">
                当您所需的车次无票时，可提交候补订单。一旦有票放出，系统将自动为您购票，无需手动抢票。
              </p>
              <div className="flex items-center space-x-4 mt-3 text-xs text-gray-500">
                <span className="flex items-center"><Clock className="h-3 w-3 mr-1" />全天候监控</span>
                <span className="flex items-center"><CheckCircle className="h-3 w-3 mr-1" />自动购票</span>
                <span className="flex items-center"><AlertTriangle className="h-3 w-3 mr-1" />未成功可退款</span>
              </div>
            </div>
          </div>
        </div>

        {/* 候补订单列表 */}
        {loading ? (
          <div className="text-center py-12">
            <div className="animate-spin rounded-full h-10 w-10 border-b-2 border-blue-600 mx-auto"></div>
            <p className="text-gray-500 mt-4">加载中...</p>
          </div>
        ) : error ? (
          <div className="text-center py-12 bg-white rounded-xl">
            <AlertTriangle className="h-10 w-10 text-red-400 mx-auto" />
            <p className="text-red-500 mt-4">{error}</p>
            <button onClick={loadOrders} className="mt-4 px-4 py-2 bg-blue-600 text-white rounded-lg">
              重试
            </button>
          </div>
        ) : orders.length === 0 ? (
          <div className="text-center py-16 bg-white rounded-xl">
            <Ticket className="h-16 w-16 text-gray-300 mx-auto" />
            <h3 className="text-lg font-medium text-gray-900 mt-4">暂无候补订单</h3>
            <p className="text-gray-500 mt-2">购票时若无票可选择候补</p>
            <button
              onClick={onBack}
              className="mt-6 px-6 py-2 bg-blue-600 text-white rounded-lg hover:bg-blue-700"
            >
              去购票
            </button>
          </div>
        ) : (
          <div className="space-y-4">
            {orders.map((order) => (
              <div key={order.waitlistSn} className="bg-white rounded-xl shadow-sm border border-gray-100 overflow-hidden">
                {/* 订单头部 */}
                <div className="px-6 py-4 border-b border-gray-100 flex items-center justify-between">
                  <div className="flex items-center space-x-3">
                    <span className="font-mono text-sm text-gray-500">{order.waitlistSn}</span>
                    {getStatusBadge(order.status)}
                  </div>
                  {order.status === 0 && (
                    <button
                      onClick={() => handleCancel(order.waitlistSn)}
                      className="text-red-500 hover:text-red-600 text-sm flex items-center"
                    >
                      <Trash2 className="h-4 w-4 mr-1" /> 取消
                    </button>
                  )}
                </div>

                {/* 订单内容 */}
                <div className="px-6 py-4">
                  <div className="flex items-center justify-between">
                    <div className="flex items-center space-x-6">
                      <div className="flex items-center space-x-2">
                        <Train className="h-5 w-5 text-blue-600" />
                        <span className="text-xl font-bold">{order.trainNumber}</span>
                      </div>
                      <div className="text-gray-600">
                        {order.startStation} → {order.endStation}
                      </div>
                    </div>
                    <div className="text-right">
                      <div className="text-lg font-semibold text-blue-600">¥{order.prepayAmount}</div>
                      <div className="text-xs text-gray-500">预支付金额</div>
                    </div>
                  </div>

                  <div className="grid grid-cols-4 gap-4 mt-4 text-sm">
                    <div>
                      <div className="text-gray-500">乘车日期</div>
                      <div className="font-medium mt-1">{formatDate(order.travelDate)}</div>
                    </div>
                    <div>
                      <div className="text-gray-500">座位类型</div>
                      <div className="font-medium mt-1">{order.seatTypesText}</div>
                    </div>
                    <div>
                      <div className="text-gray-500">排队位置</div>
                      <div className="font-medium mt-1">第 {order.queuePosition} 位</div>
                    </div>
                    <div>
                      <div className="text-gray-500">成功率</div>
                      <div className="font-medium mt-1 text-green-600">{order.successRate}%</div>
                    </div>
                  </div>

                  {/* 截止时间 */}
                  {order.status === 0 && (
                    <div className="mt-4 flex items-center text-sm text-gray-500">
                      <Clock className="h-4 w-4 mr-1" />
                      截止时间：{new Date(order.deadline).toLocaleString('zh-CN')}
                    </div>
                  )}

                  {/* 兑现成功 */}
                  {order.status === 2 && order.fulfilledOrderSn && (
                    <div className="mt-4 p-3 bg-green-50 rounded-lg flex items-center justify-between">
                      <div className="flex items-center text-green-700">
                        <CheckCircle className="h-5 w-5 mr-2" />
                        候补成功！已为您购票
                      </div>
                      <button className="text-blue-600 text-sm hover:underline">
                        查看订单 {order.fulfilledOrderSn}
                      </button>
                    </div>
                  )}
                </div>
              </div>
            ))}
          </div>
        )}
      </div>
    </div>
  );
};

export default WaitlistPage;
