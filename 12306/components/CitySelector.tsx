
import React, { useState } from 'react';

interface CitySelectorProps {
  onSelect: (city: string) => void;
  onClose: () => void;
}

// TODO: Fetch real city data from backend instead of hardcoding
const HOT_CITIES = ['北京', '上海', '广州', '深圳', '杭州', '天津', '南京', '武汉', '西安', '成都', '重庆', '长沙'];
const TABS = [
  { label: '热门', data: HOT_CITIES },
  { label: 'ABCDE', data: ['鞍山', '安庆', '蚌埠', '北京', '保定', '长春', '长沙', '成都', '重庆', '大连', '东莞'] },
  { label: 'FGHIJ', data: ['福州', '抚顺', '阜阳', '广州', '贵阳', '桂林', '哈尔滨', '杭州', '合肥', '济南', '嘉兴'] },
  { label: 'KLMNO', data: ['昆明', '兰州', '连云港', '洛阳', '绵阳', '南昌', '南京', '南宁', '宁波'] },
  { label: 'PQRST', data: ['青岛', '秦皇岛', '上海', '沈阳', '深圳', '石家庄', '苏州', '太原', '天津'] },
  { label: 'UVWXYZ', data: ['武汉', '无锡', '厦门', '西安', '徐州', '烟台', '扬州', '郑州', '珠海'] },
];

const CitySelector: React.FC<CitySelectorProps> = ({ onSelect, onClose }) => {
  const [activeTab, setActiveTab] = useState('热门');

  return (
    <div className="absolute top-full left-0 mt-2 w-[400px] bg-white rounded-xl shadow-2xl border border-gray-200 z-50 overflow-hidden animate-fade-in">
      {/* Header Tabs */}
      <div className="flex bg-gray-50 border-b border-gray-200">
        {TABS.map((tab) => (
          <button
            key={tab.label}
            onClick={() => setActiveTab(tab.label)}
            className={`flex-1 py-2 text-xs font-medium transition-colors ${
              activeTab === tab.label 
                ? 'bg-white text-blue-600 border-t-2 border-blue-600' 
                : 'text-gray-500 hover:text-blue-600'
            }`}
          >
            {tab.label}
          </button>
        ))}
      </div>
      
      {/* City Grid */}
      <div className="p-4 grid grid-cols-4 gap-2 max-h-60 overflow-y-auto">
        {TABS.find(t => t.label === activeTab)?.data.map((city) => (
          <button
            key={city}
            onClick={() => {
              onSelect(city);
              onClose();
            }}
            className="text-sm py-1.5 px-2 rounded hover:bg-blue-50 hover:text-blue-600 text-left transition-colors truncate"
          >
            {city}
          </button>
        ))}
      </div>

      {/* Footer */}
      <div className="bg-gray-50 p-2 border-t border-gray-200 flex justify-end">
        <button onClick={onClose} className="text-xs text-gray-500 hover:text-gray-800">
          关闭面板
        </button>
      </div>
    </div>
  );
};

export default CitySelector;
