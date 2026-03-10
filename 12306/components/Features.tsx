
import React from 'react';
import { Coffee, ShieldCheck, Ticket, Wifi } from 'lucide-react';

const Features = () => {
  const services = [
    { icon: <Ticket className="h-6 w-6 text-blue-600" />, title: '智能订票', desc: '智能行程规划' },
    { icon: <ShieldCheck className="h-6 w-6 text-green-600" />, title: '出行保险', desc: '全方位出行保障' },
    { icon: <Coffee className="h-6 w-6 text-orange-600" />, title: '餐饮配送', desc: '高铁美食直达座位' },
    { icon: <Wifi className="h-6 w-6 text-purple-600" />, title: '列车Wi-Fi', desc: '高速网络全覆盖' },
  ];

  return (
    <div className="max-w-7xl mx-auto px-4 sm:px-6 lg:px-8 py-16">
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
