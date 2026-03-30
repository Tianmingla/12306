import React, { useState } from 'react';
import {
  MapPin, Navigation, Clock, Phone, Coffee, Utensils,
  ShoppingBag, ParkingSquare, Accessibility, Armchair, Toilet,
  ArrowLeft, Search, ChevronRight, Info
} from 'lucide-react';

interface Facility {
  id: string;
  name: string;
  icon: React.ReactNode;
  location: string;
  floor: string;
  distance: string;
  available: boolean;
}

interface StationInfo {
  name: string;
  code: string;
  address: string;
  phone: string;
  floors: FloorInfo[];
}

interface FloorInfo {
  floor: string;
  description: string;
  facilities: string[];
}

const StationGuidePage: React.FC<{ onBack: () => void }> = ({ onBack }) => {
  const [stationName, setStationName] = useState('北京南');
  const [searchQuery, setSearchQuery] = useState('');
  const [activeCategory, setActiveCategory] = useState<string>('all');

  // 模拟车站信息
  const stationInfo: StationInfo = {
    name: '北京南站',
    code: 'BJN',
    address: '北京市丰台区永外大街12号',
    phone: '010-51867999',
    floors: [
      { floor: 'B2', description: '地铁站厅', facilities: ['地铁4号线', '地铁14号线'] },
      { floor: 'B1', description: '出站层', facilities: ['出租车', '网约车', '公交枢纽'] },
      { floor: '1F', description: '地面层', facilities: ['进站口', '售票厅', '安检'] },
      { floor: '2F', description: '候车层', facilities: ['候车大厅', '检票口', '商业区'] },
    ]
  };

  const facilities: Facility[] = [
    { id: '1', name: '候车大厅', icon: <Clock className="h-5 w-5" />, location: '2F中央', floor: '2F', distance: '步行5分钟', available: true },
    { id: '2', name: '售票处', icon: <MapPin className="h-5 w-5" />, location: '1F东侧', floor: '1F', distance: '步行3分钟', available: true },
    { id: '3', name: '餐饮区', icon: <Utensils className="h-5 w-5" />, location: '2F南北两侧', floor: '2F', distance: '步行8分钟', available: true },
    { id: '4', name: '便利店', icon: <ShoppingBag className="h-5 w-5" />, location: '2F候车区', floor: '2F', distance: '步行2分钟', available: true },
    { id: '5', name: '洗手间', icon: <Toilet className="h-5 w-5" />, location: '每层均有', floor: '全楼层', distance: '步行1分钟', available: true },
    { id: '6', name: '停车场', icon: <ParkingSquare className="h-5 w-5" />, location: 'B1-B3', floor: 'B1-B3', distance: '步行10分钟', available: true },
    { id: '7', name: '无障碍电梯', icon: <Armchair className="h-5 w-5" />, location: '东西两侧', floor: '全楼层', distance: '步行3分钟', available: true },
    { id: '8', name: '无障碍服务', icon: <Accessibility className="h-5 w-5" />, location: '服务台', floor: '1F', distance: '步行5分钟', available: true },
    { id: '9', name: '咖啡厅', icon: <Coffee className="h-5 w-5" />, location: '2F北广场', floor: '2F', distance: '步行6分钟', available: true },
    { id: '10', name: '问询处', icon: <Info className="h-5 w-5" />, location: '2F中央', floor: '2F', distance: '步行4分钟', available: true },
  ];

  const categories = [
    { id: 'all', name: '全部', icon: <Navigation className="h-4 w-4" /> },
    { id: 'transport', name: '交通', icon: <MapPin className="h-4 w-4" /> },
    { id: 'food', name: '餐饮', icon: <Utensils className="h-4 w-4" /> },
    { id: 'service', name: '服务', icon: <Phone className="h-4 w-4" /> },
    { id: 'accessible', name: '无障碍', icon: <Accessibility className="h-4 w-4" /> },
  ];

  const filteredFacilities = facilities.filter(f => {
    const matchesSearch = f.name.toLowerCase().includes(searchQuery.toLowerCase()) ||
                          f.location.toLowerCase().includes(searchQuery.toLowerCase());
    if (activeCategory === 'all') return matchesSearch;
    if (activeCategory === 'food') return matchesSearch && ['餐饮区', '咖啡厅', '便利店'].includes(f.name);
    if (activeCategory === 'transport') return matchesSearch && ['停车场', '售票处'].includes(f.name);
    if (activeCategory === 'service') return matchesSearch && ['洗手间', '问询处', '候车大厅'].includes(f.name);
    if (activeCategory === 'accessible') return matchesSearch && ['无障碍电梯', '无障碍服务'].includes(f.name);
    return matchesSearch;
  });

  return (
    <div className="min-h-screen bg-gray-50">
      {/* 头部 */}
      <div className="bg-gradient-to-r from-blue-600 to-indigo-600 text-white">
        <div className="max-w-4xl mx-auto px-4 py-6">
          <div className="flex items-center space-x-4 mb-6">
            <button onClick={onBack} className="p-2 hover:bg-white/10 rounded-lg">
              <ArrowLeft className="h-5 w-5" />
            </button>
            <div>
              <h1 className="text-xl font-bold">车站引导</h1>
              <p className="text-sm text-blue-100">快速定位站内设施</p>
            </div>
          </div>

          {/* 搜索框 */}
          <div className="relative">
            <Search className="absolute left-4 top-1/2 -translate-y-1/2 h-5 w-5 text-gray-400" />
            <input
              type="text"
              value={searchQuery}
              onChange={(e) => setSearchQuery(e.target.value)}
              placeholder="搜索设施或位置..."
              className="w-full pl-12 pr-4 py-3 bg-white rounded-xl text-gray-900 placeholder-gray-400 focus:outline-none focus:ring-2 focus:ring-blue-300"
            />
          </div>
        </div>
      </div>

      <div className="max-w-4xl mx-auto px-4 py-6">
        {/* 车站信息卡片 */}
        <div className="bg-white rounded-xl shadow-sm border border-gray-100 p-6 mb-6">
          <div className="flex items-start justify-between">
            <div>
              <div className="flex items-center space-x-2">
                <h2 className="text-lg font-bold text-gray-900">{stationInfo.name}</h2>
                <span className="px-2 py-0.5 bg-blue-100 text-blue-700 rounded text-xs font-mono">
                  {stationInfo.code}
                </span>
              </div>
              <p className="text-gray-500 text-sm mt-1">{stationInfo.address}</p>
              <div className="flex items-center text-sm text-gray-500 mt-2">
                <Phone className="h-4 w-4 mr-1" />
                {stationInfo.phone}
              </div>
            </div>
            <button className="px-4 py-2 bg-blue-600 text-white rounded-lg text-sm hover:bg-blue-700">
              导航到站
            </button>
          </div>

          {/* 楼层分布 */}
          <div className="mt-6">
            <h3 className="text-sm font-medium text-gray-700 mb-3">楼层分布</h3>
            <div className="grid grid-cols-4 gap-2">
              {stationInfo.floors.map((floor) => (
                <div
                  key={floor.floor}
                  className="bg-gray-50 rounded-lg p-3 text-center hover:bg-gray-100 cursor-pointer transition-colors"
                >
                  <div className="text-lg font-bold text-blue-600">{floor.floor}</div>
                  <div className="text-xs text-gray-500 mt-1">{floor.description}</div>
                </div>
              ))}
            </div>
          </div>
        </div>

        {/* 分类筛选 */}
        <div className="flex space-x-2 mb-6 overflow-x-auto pb-2">
          {categories.map((cat) => (
            <button
              key={cat.id}
              onClick={() => setActiveCategory(cat.id)}
              className={`flex items-center space-x-1 px-4 py-2 rounded-full text-sm whitespace-nowrap transition-colors ${
                activeCategory === cat.id
                  ? 'bg-blue-600 text-white'
                  : 'bg-white text-gray-600 border border-gray-200 hover:border-blue-300'
              }`}
            >
              {cat.icon}
              <span>{cat.name}</span>
            </button>
          ))}
        </div>

        {/* 设施列表 */}
        <div className="space-y-3">
          {filteredFacilities.map((facility) => (
            <div
              key={facility.id}
              className="bg-white rounded-xl shadow-sm border border-gray-100 p-4 flex items-center justify-between hover:shadow-md transition-shadow"
            >
              <div className="flex items-center space-x-4">
                <div className={`p-3 rounded-lg ${facility.available ? 'bg-blue-50 text-blue-600' : 'bg-gray-100 text-gray-400'}`}>
                  {facility.icon}
                </div>
                <div>
                  <h3 className="font-medium text-gray-900">{facility.name}</h3>
                  <div className="flex items-center space-x-2 text-sm text-gray-500 mt-1">
                    <span className="px-1.5 py-0.5 bg-gray-100 rounded text-xs">{facility.floor}</span>
                    <span>{facility.location}</span>
                  </div>
                </div>
              </div>
              <div className="flex items-center space-x-4">
                <span className="text-sm text-gray-500">{facility.distance}</span>
                <ChevronRight className="h-5 w-5 text-gray-400" />
              </div>
            </div>
          ))}
        </div>

        {filteredFacilities.length === 0 && (
          <div className="text-center py-12">
            <Search className="h-12 w-12 text-gray-300 mx-auto" />
            <p className="text-gray-500 mt-4">未找到相关设施</p>
          </div>
        )}
      </div>
    </div>
  );
};

export default StationGuidePage;
