
import React from 'react';
import { Coffee, ShieldCheck, Ticket, Wifi, Monitor, Clock, MapPin, BookOpen } from 'lucide-react';
import { AppView } from '../types';

interface FeaturesProps {
  onNavigate?: (view: AppView) => void;
}

const Features: React.FC<FeaturesProps> = ({ onNavigate }) => {
  const services = [
    { icon: <Ticket className="h-6 w-6 text-blue-600" />, title: '智能订票', desc: '智能行程规划' },
    { icon: <ShieldCheck className="h-6 w-6 text-green-600" />, title: '出行保险', desc: '全方位出行保障' },
    { icon: <Coffee className="h-6 w-6 text-orange-600" />, title: '餐饮配送', desc: '高铁美食直达座位' },
    { icon: <Wifi className="h-6 w-6 text-purple-600" />, title: '列车Wi-Fi', desc: '高速网络全覆盖' },
  ];

  const quickActions = [
    {
      icon: <Monitor className="h-8 w-8 text-blue-600" />,
      title: '车站大屏',
      desc: '实时列车正晚点信息',
      view: AppView.STATION_SCREEN,
      color: 'bg-blue-50 hover:bg-blue-100'
    },
    {
      icon: <Clock className="h-8 w-8 text-orange-600" />,
      title: '候补购票',
      desc: '无票自动排队抢票',
      view: AppView.WAITLIST,
      color: 'bg-orange-50 hover:bg-orange-100'
    },
    {
      icon: <MapPin className="h-8 w-8 text-green-600" />,
      title: '车站引导',
      desc: '快速定位站内设施',
      view: AppView.STATION_GUIDE,
      color: 'bg-green-50 hover:bg-green-100'
    },
    {
      icon: <BookOpen className="h-8 w-8 text-purple-600" />,
      title: '出行指南',
      desc: '贴心出行攻略',
      view: AppView.TRAVEL_GUIDE,
      color: 'bg-purple-50 hover:bg-purple-100'
    },
  ];

  return (
    <div className="max-w-7xl mx-auto px-4 sm:px-6 lg:px-8 py-16">
      {/* 快捷功能入口 */}
      <h2 className="text-2xl font-bold text-gray-800 mb-8">快捷服务</h2>
      <div className="grid grid-cols-2 sm:grid-cols-4 gap-4 mb-12">
        {quickActions.map((action, i) => (
          <button
            key={i}
            onClick={() => onNavigate?.(action.view)}
            className={`p-6 rounded-xl border border-gray-100 transition-all hover:shadow-md flex flex-col items-center text-center ${action.color}`}
          >
            <div className="mb-3">{action.icon}</div>
            <h3 className="font-semibold text-gray-900">{action.title}</h3>
            <p className="text-xs text-gray-500 mt-1">{action.desc}</p>
          </button>
        ))}
      </div>

      <h2 className="text-2xl font-bold text-gray-800 mb-8">会员专享服务</h2>
      <div className="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-4 gap-6">
        {services.map((s, i) => (
          <div key={i} className="bg-white p-6 rounded-xl shadow-sm border border-gray-100 hover:shadow-md transition-shadow flex items-start space-x-4">
            <div className="p-3 bg-gray-50 rounded-lg">
              {s.icon}
            </div>
            <div>
              <h3 className="font-semibold text-gray-900">{s.title}</h3>
              <p className="text-sm text-gray-500 mt-1">{s.desc}</p>
            </div>
          </div>
        ))}
      </div>

      <div className="mt-12 grid grid-cols-1 md:grid-cols-2 gap-8">
        <div className="relative rounded-2xl overflow-hidden h-64 group cursor-pointer">
          <img 
            src="https://picsum.photos/800/400?random=1" 
            alt="Scenic Route" 
            className="w-full h-full object-cover transition-transform duration-700 group-hover:scale-105"
          />
          <div className="absolute inset-0 bg-gradient-to-t from-black/70 to-transparent flex flex-col justify-end p-8">
            <h3 className="text-white text-2xl font-bold mb-2">探索西藏</h3>
            <p className="text-white/80">体验世界上海拔最高的铁路旅程，感受纯净高原之美。</p>
          </div>
        </div>
        <div className="relative rounded-2xl overflow-hidden h-64 group cursor-pointer">
          <img 
            src="https://picsum.photos/800/400?random=2" 
            alt="High Speed" 
            className="w-full h-full object-cover transition-transform duration-700 group-hover:scale-105"
          />
           <div className="absolute inset-0 bg-gradient-to-t from-black/70 to-transparent flex flex-col justify-end p-8">
            <h3 className="text-white text-2xl font-bold mb-2">复兴号极速体验</h3>
            <p className="text-white/80">京沪高铁标杆列车，全程仅需4.5小时，舒适便捷。</p>
          </div>
        </div>
      </div>
    </div>
  );
};

export default Features;
