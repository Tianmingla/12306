
import React, { useState, useEffect } from 'react';
import { Clock, ChevronDown, ChevronUp, Filter, AlertCircle, RefreshCw, MapPin, X } from 'lucide-react';
import { TrainTicket, SearchParams, FilterOptions } from '../types';
import { searchTickets, getTrainRouteDetails } from '../services/ticketService';
import BookingModal from './BookingModal';
import FilterPanel from './FilterPanel';

interface TrainListProps {
  searchParams: SearchParams;
  onBack: () => void;
  onPurchaseSuccess?: (orderSn: string) => void;
}

// --- Mock Data & Types for Stopovers ---
interface Stopover {
  station: string;
  arrive: string;
  depart: string;
  stopTime: string; // e.g., "3分"
}

const StopoverModal: React.FC<{
  isOpen: boolean;
  onClose: () => void;
  trainNumber: string;
  from: string;
  to: string;
}> = ({ isOpen, onClose, trainNumber, from, to }) => {
  const [stops, setStops] = useState<import('../types').TrainStationDetail[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    if (!isOpen) return;
    
    const fetchRouteDetails = async () => {
      setLoading(true);
      setError(null);
      try {
        const stations = await getTrainRouteDetails(trainNumber);
        setStops(stations);
      } catch (err: any) {
        setError(err.message || '获取列车时刻表失败');
      } finally {
        setLoading(false);
      }
    };

    fetchRouteDetails();
  }, [isOpen, trainNumber]);

  if (!isOpen) return null;

  const formatTime = (time: string | null) => {
    if (!time) return '----';
    const date = new Date(time);
    return date.toLocaleTimeString('en-GB', { hour12: false, hour: '2-digit', minute: '2-digit' });
  };

  return (
    <div className="fixed inset-0 z-[60] flex items-center justify-center p-4 bg-black/50 backdrop-blur-sm animate-fade-in">
      <div className="bg-white rounded-xl w-full max-w-md shadow-2xl overflow-hidden flex flex-col max-h-[80vh]">
        <div className="bg-blue-600 p-4 flex justify-between items-center text-white">
          <h3 className="font-bold text-lg">{trainNumber} 次列车时刻表</h3>
          <button onClick={onClose} className="hover:bg-blue-700 p-1 rounded transition-colors">
            <X className="h-5 w-5" />
          </button>
        </div>
        <div className="flex-1 overflow-y-auto p-4">
           {loading ? (
             <div className="flex justify-center items-center h-32">
               <div className="w-8 h-8 border-4 border-blue-600 border-t-transparent rounded-full animate-spin"></div>
             </div>
           ) : error ? (
             <div className="text-center text-red-500 py-8">{error}</div>
           ) : (
             <>
               {/* Table Header */}
               <div className="grid grid-cols-4 text-sm font-medium text-gray-500 mb-2 px-2">
                 <div>车站</div>
                 <div>到达</div>
                 <div>出发</div>
                 <div className="text-right">停留</div>
               </div>
               {/* Timeline */}
               <div className="relative">
                 {/* Vertical Line */}
                 <div className="absolute left-[5px] top-3 bottom-3 w-0.5 bg-gray-200"></div>
                 
                 {stops.map((stop, idx) => (
                   <div key={idx} className="grid grid-cols-4 text-sm py-3 items-center relative group hover:bg-gray-50 rounded px-2">
                     {/* Dot */}
                     <div className={`absolute left-0 w-3 h-3 rounded-full border-2 ${
                       idx === 0 || idx === stops.length - 1 ? 'border-blue-500 bg-white' : 'border-gray-300 bg-gray-100'
                     } z-10`}></div>
                     
                     <div className={`pl-4 font-medium ${idx === 0 || idx === stops.length - 1 ? 'text-blue-600' : 'text-gray-800'}`}>
                       {stop.stationName}
                     </div>
                     <div className="text-gray-600">{stop.arrivalTime}</div>
                     <div className="text-gray-600">{stop.departureTime}</div>
                     <div className="text-right text-gray-400 text-xs">
                       {idx === 0 ? '始发' : (idx === stops.length - 1 ? '终点' : `${stop.stopoverTime || 0}分`)}
                     </div>
                   </div>
                 ))}
               </div>
             </>
           )}
        </div>
      </div>
    </div>
  );
};

const TrainList: React.FC<TrainListProps> = ({ searchParams, onBack, onPurchaseSuccess }) => {
  const [tickets, setTickets] = useState<TrainTicket[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  
  // States for interaction
  const [selectedTicketForBooking, setSelectedTicketForBooking] = useState<TrainTicket | null>(null);
  const [expandedTicketId, setExpandedTicketId] = useState<string | null>(null);
  const [stopoverInfo, setStopoverInfo] = useState<{isOpen: boolean, trainNumber: string} | null>(null);

  // Filter State
  const [isFilterOpen, setIsFilterOpen] = useState(false);
  const [filters, setFilters] = useState<FilterOptions>({
    trainTypes: [],
    departureTime: []
  });

  const fetchTickets = async () => {
    setLoading(true);
    setError(null);
    try {
      const data = await searchTickets(searchParams);
      setTickets(data);
    } catch (err: any) {
      setError(err.message || "加载车次失败，请检查服务是否正常。");
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => {
    fetchTickets();
  }, [searchParams]);

  // Frontend Filtering
  const filteredTickets = tickets.filter(ticket => {
    if (filters.trainTypes.length > 0) {
      if (!filters.trainTypes.includes(ticket.type)) return false;
    }
    if (filters.departureTime.length > 0) {
      const hour = parseInt(ticket.departureTime.split(':')[0], 10);
      const matchesTime = filters.departureTime.some(range => {
        const [start, end] = range.split('-').map(Number);
        return hour >= start && hour < end;
      });
      if (!matchesTime) return false;
    }
    return true;
  });

  const activeFilterCount = filters.trainTypes.length + filters.departureTime.length;

  const handleTrainNumberClick = (e: React.MouseEvent, trainNumber: string) => {
    e.stopPropagation(); // Critical: prevent card expansion
    setStopoverInfo({ isOpen: true, trainNumber });
  };

  const toggleExpand = (id: string) => {
    setExpandedTicketId(prev => prev === id ? null : id);
  };

  const handleBook = (e: React.MouseEvent, ticket: TrainTicket) => {
    e.stopPropagation();
    setSelectedTicketForBooking(ticket);
  };

  // Helper to map English seat keys to Chinese labels
  const seatMap: Record<string, string> = {
    business: '商务座',
    first: '一等座',
    second: '二等座',
    standing: '无座'
  };

  return (
    <div className="max-w-5xl mx-auto px-4 py-8">
      {/* Header */}
      <div className="flex items-center justify-between mb-6">
        <div className="flex items-center space-x-4">
          <button onClick={onBack} className="text-gray-500 hover:text-blue-600 font-medium">
            ← 返回
          </button>
          <div>
            <h2 className="text-2xl font-bold text-gray-800">
              {searchParams.from} <span className="text-gray-400 mx-2">→</span> {searchParams.to}
            </h2>
            <p className="text-gray-500 text-sm">{searchParams.date}</p>
          </div>
        </div>
        <div className="flex space-x-2 relative">
          <button 
            onClick={() => setIsFilterOpen(!isFilterOpen)}
            className={`flex items-center px-4 py-2 border rounded-lg text-sm font-medium transition-colors ${
              activeFilterCount > 0 
                ? 'bg-blue-50 border-blue-500 text-blue-600' 
                : 'bg-white border-gray-200 hover:bg-gray-50'
            }`}
          >
            <Filter className="h-4 w-4 mr-2" /> 
            筛选 {activeFilterCount > 0 && `(${activeFilterCount})`}
          </button>
          
          <FilterPanel 
            isOpen={isFilterOpen} 
            onClose={() => setIsFilterOpen(false)}
            filters={filters}
            onChange={setFilters}
          />
        </div>
      </div>

      {/* Date Tabs */}
      <div className="flex space-x-2 mb-6 overflow-x-auto pb-2 scrollbar-hide">
        {[-1, 0, 1, 2, 3].map(offset => {
          const d = new Date(searchParams.date);
          d.setDate(d.getDate() + offset);
          const isSelected = offset === 0;
          return (
            <button 
              key={offset}
              className={`flex-shrink-0 px-4 py-2 rounded-lg text-sm font-medium transition-colors ${
                isSelected 
                  ? 'bg-blue-600 text-white shadow-md' 
                  : 'bg-white text-gray-600 hover:bg-gray-50 border border-gray-200'
              }`}
            >
              {d.toLocaleDateString('zh-CN', { month: 'numeric', day: 'numeric' })}
            </button>
          );
        })}
      </div>

      {/* Content */}
      <div className="space-y-4">
        {loading ? (
          Array.from({ length: 3 }).map((_, i) => (
            <div key={i} className="bg-white rounded-xl p-6 shadow-sm border border-gray-100 animate-pulse h-32"></div>
          ))
        ) : error ? (
          <div className="bg-red-50 text-red-600 p-8 rounded-xl text-center border border-red-100">
            <AlertCircle className="h-10 w-10 mx-auto mb-3 opacity-50" />
            <p className="font-medium">{error}</p>
            <button onClick={fetchTickets} className="mt-4 px-4 py-2 bg-white border border-red-200 text-red-600 rounded-lg text-sm font-medium hover:bg-red-50 flex items-center mx-auto">
              <RefreshCw className="h-4 w-4 mr-2" /> 重试
            </button>
          </div>
        ) : filteredTickets.length === 0 ? (
          <div className="bg-white p-12 rounded-xl text-center border border-gray-100">
             <p className="text-gray-500 text-lg">暂无符合条件的车次信息。</p>
             <button onClick={() => setFilters({ trainTypes: [], departureTime: [] })} className="mt-2 text-blue-600 hover:underline">
               清除筛选条件
             </button>
          </div>
        ) : (
          filteredTickets.map((ticket) => {
            const isExpanded = expandedTicketId === ticket.id;
            
            return (
              <div 
                key={ticket.id} 
                onClick={() => toggleExpand(ticket.id)}
                className={`bg-white rounded-xl shadow-sm border border-gray-100 transition-all duration-300 overflow-hidden cursor-pointer ${isExpanded ? 'ring-2 ring-blue-500/20 shadow-md' : 'hover:shadow-md'}`}
              >
                {/* Card Header (Always Visible) */}
                <div className="p-5 flex flex-col md:flex-row md:items-center justify-between gap-6">
                  
                  {/* Time & Station */}
                  <div className="flex items-center gap-8 min-w-[280px]">
                    <div className="text-center">
                      <div className="text-2xl font-bold text-gray-900">{ticket.departureTime}</div>
                      <div className="text-sm font-medium text-gray-600">{ticket.fromStation}</div>
                    </div>
                    
                    <div className="flex flex-col items-center justify-center min-w-[100px]">
                      {/* Train Number Button */}
                      <span 
                        onClick={(e) => handleTrainNumberClick(e, ticket.trainNumber)}
                        className="text-xs text-blue-600 font-medium border border-blue-200 px-2 py-0.5 rounded-full hover:bg-blue-50 transition-colors mb-1"
                        title="查看经停信息"
                      >
                        {ticket.trainNumber}
                      </span>
                      <div className="w-full h-[2px] bg-gray-200 relative my-1">
                        <div className="absolute right-0 -top-1 w-2 h-2 border-t-2 border-r-2 border-gray-200 rotate-45"></div>
                      </div>
                      <div className="flex items-center text-xs text-gray-400">
                        <Clock className="h-3 w-3 mr-1" />
                        {ticket.duration}
                      </div>
                    </div>

                    <div className="text-center">
                      <div className="text-2xl font-bold text-gray-900">{ticket.arrivalTime}</div>
                      <div className="text-sm font-medium text-gray-600">{ticket.toStation}</div>
                    </div>
                  </div>

                  {/* Quick Seat Preview (Collapsed Only) */}
                  <div className="flex-1 hidden md:flex flex-wrap gap-x-6 gap-y-2 text-sm text-gray-500">
                     {/* We only show a summary here now, detail is in expansion */}
                     <span className="text-gray-400 text-xs">点击查看 {Object.keys(ticket.seatsAvailable).length} 个席位详情</span>
                  </div>

                  {/* Price & Toggle Action */}
                  <div className="flex items-center justify-between md:flex-col md:items-end gap-2 md:min-w-[120px]">
                    <div className="text-right">
                      <span className="text-xs text-gray-400 mr-1">起</span>
                      <span className="text-xs text-gray-400 align-top">¥</span>
                      <span className="text-3xl font-bold text-orange-500">200</span>
                    </div>
                    <div className="text-gray-400 flex items-center text-sm">
                       {isExpanded ? <ChevronUp className="h-5 w-5" /> : <ChevronDown className="h-5 w-5" />}
                    </div>
                  </div>
                </div>

                {/* Expanded Seat Details */}
                <div className={`bg-gray-50 border-t border-gray-100 transition-all duration-300 ease-in-out ${isExpanded ? 'max-h-96 opacity-100' : 'max-h-0 opacity-0'}`}>
                  <div className="p-4 space-y-3">
                    {/* Render rows for each seat type */}
                    {Object.entries(ticket.seatsAvailable).map(([type, count]) => (
                      <div key={type} className="flex items-center justify-between bg-white p-3 rounded-lg border border-gray-100 hover:border-blue-200 transition-colors">
                         <div className="w-1/4 font-medium text-gray-800 flex items-center">
                           {seatMap[type] || type}
                         </div>
                         <div className="w-1/4 font-bold text-orange-500 text-lg">
                           <span className="text-xs">¥</span>200
                         </div>
                         <div className="w-1/4">
                            <span className={`text-sm ${Number(count) > 0 ? 'text-green-600' : 'text-gray-400'}`}>
                              {Number(count) > 0 ? (Number(count) > 20 ? '有票' : `${count}张`) : '无票'}
                            </span>
                         </div>
                         <div className="w-1/4 text-right">
                           <button 
                             onClick={(e) => handleBook(e, ticket)}
                             disabled={Number(count) <= 0}
                             className="px-5 py-1.5 bg-orange-500 text-white rounded text-sm font-medium hover:bg-orange-600 disabled:bg-gray-300 disabled:cursor-not-allowed transition-colors"
                           >
                             预订
                           </button>
                         </div>
                      </div>
                    ))}
                  </div>
                </div>
              </div>
            );
          })
        )}
      </div>

      <BookingModal 
        ticket={selectedTicketForBooking} 
        onClose={() => setSelectedTicketForBooking(null)}
        travelDate={searchParams.date}
        onPurchaseSuccess={onPurchaseSuccess}
      />
      
      {stopoverInfo && (
        <StopoverModal 
          isOpen={stopoverInfo.isOpen}
          onClose={() => setStopoverInfo(null)}
          trainNumber={stopoverInfo.trainNumber}
          from={searchParams.from}
          to={searchParams.to}
        />
      )}
    </div>
  );
};

export default TrainList;
