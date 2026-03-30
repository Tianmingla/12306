import React, { useState } from 'react';
import {
  Backpack, Map, Clock, AlertTriangle, Umbrella, Stethoscope,
  Shield, ArrowLeft, ChevronRight, Star, ExternalLink,
  Phone, FileText, HelpCircle
} from 'lucide-react';

interface GuideItem {
  id: string;
  title: string;
  description: string;
  icon: React.ReactNode;
  category: string;
  tips?: string[];
}

interface QuickLink {
  title: string;
  url: string;
  icon: React.ReactNode;
}

const TravelGuidePage: React.FC<{ onBack: () => void }> = ({ onBack }) => {
  const [activeCategory, setActiveCategory] = useState<string>('all');

  const guideItems: GuideItem[] = [
    {
      id: '1',
      title: '购票指南',
      description: '了解购票流程、退改签规则',
      icon: <FileText className="h-5 w-5" />,
      category: 'ticket',
      tips: [
        '购票需使用本人有效身份证件',
        '学生票需携带学生证及优惠卡',
        '儿童票按身高标准购买',
        '改签需在开车前办理',
      ]
    },
    {
      id: '2',
      title: '进站流程',
      description: '安检、检票、候车全流程',
      icon: <Shield className="h-5 w-5" />,
      category: 'station',
      tips: [
        '建议提前1小时到达车站',
        '携带有效身份证件原件',
        '禁止携带易燃易爆物品',
        '液体需开封检查',
      ]
    },
    {
      id: '3',
      title: '行李规定',
      description: '携带物品尺寸与重量限制',
      icon: <Backpack className="h-5 w-5" />,
      category: 'luggage',
      tips: [
        '每件物品长宽高之和不超过160厘米',
        '杆状物品不超过200厘米',
        '重量不超过20千克',
        '贵重物品请随身携带',
      ]
    },
    {
      id: '4',
      title: '天气提示',
      description: '目的地天气与出行建议',
      icon: <Umbrella className="h-5 w-5" />,
      category: 'weather',
      tips: [
        '出发前关注目的地天气',
        '雨天备好雨具',
        '夏季注意防晒',
        '冬季注意保暖',
      ]
    },
    {
      id: '5',
      title: '安全须知',
      description: '乘车安全注意事项',
      icon: <AlertTriangle className="h-5 w-5" />,
      category: 'safety',
      tips: [
        '不要在车厢内吸烟',
        '紧急情况听从工作人员指挥',
        '保管好个人财物',
        '儿童需家长陪同',
      ]
    },
    {
      id: '6',
      title: '急救常识',
      description: '突发状况处理方法',
      icon: <Stethoscope className="h-5 w-5" />,
      category: 'safety',
      tips: [
        '身体不适及时联系列车员',
        '每节车厢配有急救箱',
        '紧急情况拨打12306',
        '患有疾病的乘客携带常用药',
      ]
    },
    {
      id: '7',
      title: '正晚点查询',
      description: '实时列车运行状态',
      icon: <Clock className="h-5 w-5" />,
      category: 'ticket',
      tips: [
        '通过12306 App查询实时状态',
        '晚点可免费退票',
        '关注车站公告',
      ]
    },
    {
      id: '8',
      title: '行程规划',
      description: '合理安排出行时间',
      icon: <Map className="h-5 w-5" />,
      category: 'plan',
      tips: [
        '预留足够中转时间',
        '了解目的地交通情况',
        '提前预订住宿',
        '保存重要联系电话',
      ]
    },
  ];

  const quickLinks: QuickLink[] = [
    { title: '12306官网', url: 'https://www.12306.cn', icon: <ExternalLink className="h-4 w-4" /> },
    { title: '客服热线', url: 'tel:12306', icon: <Phone className="h-4 w-4" /> },
    { title: '常见问题', url: '#', icon: <HelpCircle className="h-4 w-4" /> },
  ];

  const categories = [
    { id: 'all', name: '全部', icon: <Star className="h-4 w-4" /> },
    { id: 'ticket', name: '购票', icon: <FileText className="h-4 w-4" /> },
    { id: 'station', name: '车站', icon: <Shield className="h-4 w-4" /> },
    { id: 'luggage', name: '行李', icon: <Backpack className="h-4 w-4" /> },
    { id: 'safety', name: '安全', icon: <AlertTriangle className="h-4 w-4" /> },
    { id: 'plan', name: '规划', icon: <Map className="h-4 w-4" /> },
  ];

  const [expandedItem, setExpandedItem] = useState<string | null>(null);

  const filteredItems = guideItems.filter(item => {
    return activeCategory === 'all' || item.category === activeCategory;
  });

  return (
    <div className="min-h-screen bg-gray-50">
      {/* 头部 */}
      <div className="bg-gradient-to-r from-green-600 to-teal-600 text-white">
        <div className="max-w-4xl mx-auto px-4 py-6">
          <div className="flex items-center space-x-4 mb-4">
            <button onClick={onBack} className="p-2 hover:bg-white/10 rounded-lg">
              <ArrowLeft className="h-5 w-5" />
            </button>
            <div>
              <h1 className="text-xl font-bold">出行指南</h1>
              <p className="text-sm text-green-100">贴心出行攻略</p>
            </div>
          </div>

          {/* 快捷链接 */}
          <div className="flex space-x-3 mt-4">
            {quickLinks.map((link, idx) => (
              <a
                key={idx}
                href={link.url}
                className="flex items-center space-x-2 px-4 py-2 bg-white/20 rounded-lg text-sm hover:bg-white/30 transition-colors"
              >
                {link.icon}
                <span>{link.title}</span>
              </a>
            ))}
          </div>
        </div>
      </div>

      <div className="max-w-4xl mx-auto px-4 py-6">
        {/* 分类筛选 */}
        <div className="flex space-x-2 mb-6 overflow-x-auto pb-2">
          {categories.map((cat) => (
            <button
              key={cat.id}
              onClick={() => setActiveCategory(cat.id)}
              className={`flex items-center space-x-1 px-4 py-2 rounded-full text-sm whitespace-nowrap transition-colors ${
                activeCategory === cat.id
                  ? 'bg-green-600 text-white'
                  : 'bg-white text-gray-600 border border-gray-200 hover:border-green-300'
              }`}
            >
              {cat.icon}
              <span>{cat.name}</span>
            </button>
          ))}
        </div>

        {/* 指南列表 */}
        <div className="space-y-4">
          {filteredItems.map((item) => (
            <div
              key={item.id}
              className="bg-white rounded-xl shadow-sm border border-gray-100 overflow-hidden"
            >
              <div
                className="p-4 flex items-center justify-between cursor-pointer hover:bg-gray-50"
                onClick={() => setExpandedItem(expandedItem === item.id ? null : item.id)}
              >
                <div className="flex items-center space-x-4">
                  <div className="p-3 bg-green-50 text-green-600 rounded-lg">
                    {item.icon}
                  </div>
                  <div>
                    <h3 className="font-medium text-gray-900">{item.title}</h3>
                    <p className="text-sm text-gray-500 mt-0.5">{item.description}</p>
                  </div>
                </div>
                <ChevronRight
                  className={`h-5 w-5 text-gray-400 transition-transform ${
                    expandedItem === item.id ? 'rotate-90' : ''
                  }`}
                />
              </div>

              {expandedItem === item.id && item.tips && (
                <div className="px-4 pb-4 pt-0">
                  <div className="bg-gray-50 rounded-lg p-4">
                    <h4 className="text-sm font-medium text-gray-700 mb-2">温馨提示</h4>
                    <ul className="space-y-2">
                      {item.tips.map((tip, idx) => (
                        <li key={idx} className="flex items-start text-sm text-gray-600">
                          <span className="text-green-500 mr-2">•</span>
                          {tip}
                        </li>
                      ))}
                    </ul>
                  </div>
                </div>
              )}
            </div>
          ))}
        </div>

        {/* 底部提示 */}
        <div className="mt-8 bg-blue-50 rounded-xl p-6 border border-blue-100">
          <div className="flex items-start space-x-3">
            <HelpCircle className="h-5 w-5 text-blue-600 flex-shrink-0 mt-0.5" />
            <div>
              <h3 className="font-medium text-blue-900">需要更多帮助？</h3>
              <p className="text-sm text-blue-700 mt-1">
                拨打铁路客服热线 <span className="font-mono font-bold">12306</span> 获取人工服务
              </p>
            </div>
          </div>
        </div>
      </div>
    </div>
  );
};

export default TravelGuidePage;
