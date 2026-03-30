
import React, { useState, useEffect, useRef } from 'react';
import { Monitor, Clock, MapPin, Train, AlertCircle, CheckCircle, XCircle, ArrowLeft } from 'lucide-react';
import { StationScreenResponse, StationScreenTrain } from '../types';
import { getStationScreen } from '../services/stationService';

interface StationScreenPageProps {
  onBack: () => void;
}

const StationScreenPage: React.FC<StationScreenPageProps> = ({ onBack }) => {
  const [stationName, setStationName] = useState('北京南');
  const [screenData, setScreenData] = useState<StationScreenResponse | null>(null);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    loadScreenData();
    // 每30秒刷新一次
    const interval = setInterval(loadScreenData, 30000);
    return () => clearInterval(interval);
  }, [stationName]);

  const loadScreenData = async () => {
    setLoading(true);
    setError(null);
    try {
      const data = await getStationScreen(stationName);
      setScreenData(data);
    } catch (e: any) {
      setError(e.message || '加载失败');
    } finally {
      setLoading(false);
    }
  };

  const getDelayStatusColor = (status: number) => {
    switch (status) {
      case 0: return 'text-green-400'; // 正点
      case 1: return 'text-yellow-400'; // 晚点
      default: return 'text-gray-400';
    }
  };

  const getCheckInStatusBadge = (status: number) => {
    switch (status) {
      case 0: return <span className="px-2 py-1 bg-gray-500/30 text-gray-300 rounded text-xs">未开始</span>;
      case 1: return <span className="px-2 py-1 bg-green-500/30 text-green-300 rounded text-xs animate-pulse">检票中</span>;
      case 2: return <span className="px-2 py-1 bg-red-500/30 text-red-300 rounded text-xs">停止检票</span>;
      case 3: return <span className="px-2 py-1 bg-blue-500/30 text-blue-300 rounded text-xs">已开车</span>;
      default: return null;
    }
  };

  return (
    <div className="min-h-screen bg-slate-900 text-white">
      {/* 玻璃效果头部 */}
      <div className="relative overflow-hidden">
        {/* 背景渐变 */}
        <div className="absolute inset-0 bg-gradient-to-br from-blue-900/50 via-slate-800 to-purple-900/30"></div>

        {/* 玻璃模糊层 */}
        <div className="relative backdrop-blur-sm bg-white/5 border-b border-white/10">
          <div className="max-w-7xl mx-auto px-4 py-4">
            <div className="flex items-center justify-between">
              <div className="flex items-center space-x-4">
                <button
                  onClick={onBack}
                  className="p-2 hover:bg-white/10 rounded-lg transition-colors"
                >
                  <ArrowLeft className="h-5 w-5" />
                </button>
                <Monitor className="h-6 w-6 text-blue-400" />
                <div>
                  <h1 className="text-xl font-bold">{screenData?.stationName || stationName}站 - 车站大屏</h1>
                  <p className="text-sm text-gray-400">实时列车信息展示</p>
                </div>
              </div>

              <div className="flex items-center space-x-4">
                <div className="flex items-center space-x-2">
                  <input
                    type="text"
                    value={stationName}
                    onChange={(e) => setStationName(e.target.value)}
                    placeholder="输入车站名称"
                    className="px-4 py-2 bg-white/10 border border-white/20 rounded-lg text-white placeholder-gray-400 focus:outline-none focus:ring-2 focus:ring-blue-500"
                  />
                  <button
                    onClick={loadScreenData}
                    className="px-4 py-2 bg-blue-600 hover:bg-blue-700 rounded-lg transition-colors"
                  >
                    查询
                  </button>
                </div>

                <div className="text-right">
                  <div className="text-2xl font-mono font-bold">{screenData?.currentTime || '--:--'}</div>
                  <div className="text-xs text-gray-400">{screenData?.currentDate || ''}</div>
                </div>
              </div>
            </div>
          </div>
        </div>
      </div>

      {/* 统计卡片 */}
      <div className="max-w-7xl mx-auto px-4 py-6">
        <div className="grid grid-cols-1 md:grid-cols-3 gap-4 mb-6">
          <div className="backdrop-blur-md bg-white/5 rounded-xl p-6 border border-white/10">
            <div className="flex items-center justify-between">
              <div>
                <p className="text-gray-400 text-sm">今日发车</p>
                <p className="text-3xl font-bold mt-1">{screenData?.totalTrainsToday || 0}</p>
              </div>
              <Train className="h-10 w-10 text-blue-400 opacity-50" />
            </div>
          </div>

          <div className="backdrop-blur-md bg-white/5 rounded-xl p-6 border border-white/10">
            <div className="flex items-center justify-between">
              <div>
                <p className="text-gray-400 text-sm">正点率</p>
                <p className="text-3xl font-bold mt-1 text-green-400">{screenData?.onTimeRate || 100}%</p>
              </div>
              <CheckCircle className="h-10 w-10 text-green-400 opacity-50" />
            </div>
          </div>

          <div className="backdrop-blur-md bg-white/5 rounded-xl p-6 border border-white/10">
            <div className="flex items-center justify-between">
              <div>
                <p className="text-gray-400 text-sm">待发列车</p>
                <p className="text-3xl font-bold mt-1">{screenData?.trains?.length || 0}</p>
              </div>
              <Clock className="h-10 w-10 text-yellow-400 opacity-50" />
            </div>
          </div>
        </div>

        {/* 公告栏 */}
        {screenData?.announcements && screenData.announcements.length > 0 && (
          <div className="backdrop-blur-md bg-yellow-500/10 rounded-xl p-4 border border-yellow-500/20 mb-6">
            <div className="flex items-start space-x-3">
              <AlertCircle className="h-5 w-5 text-yellow-400 flex-shrink-0 mt-0.5" />
              <div className="flex-1">
                {screenData.announcements.map((msg, i) => (
                  <p key={i} className="text-yellow-200 text-sm">{msg}</p>
                ))}
              </div>
            </div>
          </div>
        )}

        {/* 列车列表 */}
        {loading ? (
          <div className="text-center py-12">
            <div className="animate-spin rounded-full h-12 w-12 border-b-2 border-blue-400 mx-auto"></div>
            <p className="text-gray-400 mt-4">加载中...</p>
          </div>
        ) : error ? (
          <div className="text-center py-12">
            <XCircle className="h-12 w-12 text-red-400 mx-auto" />
            <p className="text-red-400 mt-4">{error}</p>
            <button onClick={loadScreenData} className="mt-4 px-4 py-2 bg-blue-600 rounded-lg">
              重试
            </button>
          </div>
        ) : (
          <div className="backdrop-blur-md bg-white/5 rounded-xl border border-white/10 overflow-hidden">
            {/* 表头 */}
            <div className="grid grid-cols-12 gap-2 px-4 py-3 bg-white/5 border-b border-white/10 text-sm font-medium text-gray-400">
              <div className="col-span-2">车次</div>
              <div className="col-span-2">终到站</div>
              <div className="col-span-1">发车</div>
              <div className="col-span-1">状态</div>
              <div className="col-span-1">检票</div>
              <div className="col-span-1">候车室</div>
              <div className="col-span-1">检票口</div>
              <div className="col-span-1">站台</div>
              <div className="col-span-2">备注</div>
            </div>

            {/* 列车行 */}
            <div className="divide-y divide-white/5">
              {screenData?.trains?.map((train, index) => (
                <div
                  key={`${train.trainNumber}-${index}`}
                  className="grid grid-cols-12 gap-2 px-4 py-4 hover:bg-white/5 transition-colors items-center"
                >
                  <div className="col-span-2">
                    <div className="flex items-center space-x-2">
                      <span className={`px-2 py-0.5 rounded text-xs font-bold ${
                        train.trainType === 0 ? 'bg-red-500/20 text-red-300' :
                        train.trainType === 1 ? 'bg-blue-500/20 text-blue-300' :
                        'bg-gray-500/20 text-gray-300'
                      }`}>
                        {train.trainNumber}
                      </span>
                    </div>
                    <div className="text-xs text-gray-500 mt-1">{train.trainTypeName}</div>
                  </div>

                  <div className="col-span-2">
                    <div className="flex items-center space-x-1">
                      <MapPin className="h-3 w-3 text-gray-500" />
                      <span>{train.terminalStation}</span>
                    </div>
                  </div>

                  <div className="col-span-1">
                    <span className="font-mono text-lg">{train.departureTime}</span>
                    {train.estimatedDepartureTime && train.estimatedDepartureTime !== train.departureTime && (
                      <div className="text-xs text-yellow-400">预计 {train.estimatedDepartureTime}</div>
                    )}
                  </div>

                  <div className="col-span-1">
                    <span className={getDelayStatusColor(train.delayStatus)}>
                      {train.delayStatusText}
                    </span>
                    {train.delayMinutes > 0 && (
                      <div className="text-xs text-yellow-400">+{train.delayMinutes}分</div>
                    )}
                  </div>

                  <div className="col-span-1">
                    {getCheckInStatusBadge(train.checkInStatus)}
                  </div>

                  <div className="col-span-1 text-sm">{train.waitingRoom}</div>
                  <div className="col-span-1 text-sm font-mono">{train.checkInGate}</div>
                  <div className="col-span-1 text-sm">{train.platform}</div>
                  <div className="col-span-2 text-xs text-gray-400">{train.remainingTimeDesc}</div>
                </div>
              ))}
            </div>
          </div>
        )}
      </div>
    </div>
  );
};

export default StationScreenPage;
