
import React, { useState, useRef, useEffect } from 'react';
import { ArrowRightLeft, Calendar, MapPin, Search, ArrowLeftRight } from 'lucide-react';
import { SearchParams } from '../types';
import CitySelector from './CitySelector';

interface SearchWidgetProps {
  onSearch: (params: SearchParams) => void;
}

const POPULAR_CITIES = ['上海', '北京', '广州', '深圳', '成都', '杭州', '西安'];

type SearchType = 'oneWay' | 'roundTrip' | 'transfer';

const SearchWidget: React.FC<SearchWidgetProps> = ({ onSearch }) => {
  const [from, setFrom] = useState('上海');
  const [to, setTo] = useState('北京');
  const [date, setDate] = useState(new Date().toISOString().split('T')[0]);
  const [returnDate, setReturnDate] = useState(new Date(Date.now() + 3 * 24 * 60 * 60 * 1000).toISOString().split('T')[0]);
  const [midStation, setMidStation] = useState('');
  const [onlyHighSpeed, setOnlyHighSpeed] = useState(false);
  const [searchType, setSearchType] = useState<SearchType>('oneWay');
  const [activeCitySelector, setActiveCitySelector] = useState<'from' | 'to' | 'mid' | null>(null);

  const fromRef = useRef<HTMLDivElement>(null);
  const toRef = useRef<HTMLDivElement>(null);
  const midRef = useRef<HTMLDivElement>(null);

  // Close selectors when clicking outside
  useEffect(() => {
    const handleClickOutside = (event: MouseEvent) => {
      if (
        fromRef.current && !fromRef.current.contains(event.target as Node) &&
        toRef.current && !toRef.current.contains(event.target as Node) &&
        midRef.current && !midRef.current.contains(event.target as Node)
      ) {
        setActiveCitySelector(null);
      }
    };
    document.addEventListener('mousedown', handleClickOutside);
    return () => document.removeEventListener('mousedown', handleClickOutside);
  }, []);

  const handleSwap = () => {
    setFrom(to);
    setTo(from);
  };

  const handleSearch = () => {
    const params: SearchParams = {
      from,
      to,
      date,
      onlyHighSpeed,
      searchType,
    };

    if (searchType === 'roundTrip') {
      params.returnDate = returnDate;
    }

    if (searchType === 'transfer' && midStation) {
      params.midStation = midStation;
    }

    onSearch(params);
  };

  return (
    <div className="bg-white rounded-2xl shadow-xl p-6 md:p-8 max-w-4xl mx-auto -mt-24 relative z-10 border border-gray-100">

      {/* Tabs */}
      <div className="flex space-x-6 mb-6 border-b border-gray-100 pb-4">
        <button
          onClick={() => setSearchType('oneWay')}
          className={`${searchType === 'oneWay' ? 'text-blue-600 font-bold border-b-2 border-blue-600' : 'text-gray-500 hover:text-blue-600'} font-medium transition-colors px-2 pb-4 -mb-4.5`}
        >
          单程
        </button>
        <button
          onClick={() => setSearchType('roundTrip')}
          className={`${searchType === 'roundTrip' ? 'text-blue-600 font-bold border-b-2 border-blue-600' : 'text-gray-500 hover:text-blue-600'} font-medium transition-colors px-2 pb-4 -mb-4.5`}
        >
          往返
        </button>
        <button
          onClick={() => setSearchType('transfer')}
          className={`${searchType === 'transfer' ? 'text-blue-600 font-bold border-b-2 border-blue-600' : 'text-gray-500 hover:text-blue-600'} font-medium transition-colors px-2 pb-4 -mb-4.5`}
        >
          中转
        </button>
      </div>

      <div className="grid grid-cols-1 md:grid-cols-12 gap-4 items-center">
        {/* From Station */}
        <div className="md:col-span-3 relative group" ref={fromRef}>
          <label className="text-xs text-gray-400 font-medium mb-1 block uppercase tracking-wider">出发地</label>
          <div
            className="flex items-center border-b-2 border-gray-200 group-focus-within:border-blue-500 transition-colors pb-1 cursor-pointer"
          >
            <MapPin className="h-5 w-5 text-gray-400 mr-2 group-focus-within:text-blue-500" />
            <input
              type="text"
              value={from}
              onChange={(e) => setFrom(e.target.value)}
              onFocus={() => setActiveCitySelector('from')}
              className="w-full text-2xl font-bold text-gray-800 focus:outline-none placeholder-gray-300"
              placeholder="城市"
            />
          </div>
          {activeCitySelector === 'from' && (
            <CitySelector
              onSelect={setFrom}
              onClose={() => setActiveCitySelector(null)}
            />
          )}
        </div>

        {/* Swap Button */}
        <div className="md:col-span-1 flex justify-center py-2 md:py-0">
          <button
            onClick={handleSwap}
            className="p-2 rounded-full hover:bg-gray-100 text-blue-500 transition-transform hover:rotate-180 duration-300"
          >
            <ArrowRightLeft className="h-6 w-6" />
          </button>
        </div>

        {/* To Station */}
        <div className="md:col-span-3 relative group" ref={toRef}>
          <label className="text-xs text-gray-400 font-medium mb-1 block uppercase tracking-wider">目的地</label>
          <div className="flex items-center border-b-2 border-gray-200 group-focus-within:border-blue-500 transition-colors pb-1">
            <MapPin className="h-5 w-5 text-gray-400 mr-2 group-focus-within:text-blue-500" />
            <input
              type="text"
              value={to}
              onChange={(e) => setTo(e.target.value)}
              onFocus={() => setActiveCitySelector('to')}
              className="w-full text-2xl font-bold text-gray-800 focus:outline-none placeholder-gray-300"
              placeholder="城市"
            />
          </div>
          {activeCitySelector === 'to' && (
            <CitySelector
              onSelect={setTo}
              onClose={() => setActiveCitySelector(null)}
            />
          )}
        </div>

        {/* Date or Mid Station */}
        {searchType === 'transfer' ? (
          <div className="md:col-span-3 relative group" ref={midRef}>
            <label className="text-xs text-gray-400 font-medium mb-1 block uppercase tracking-wider">中转站（可选）</label>
            <div className="flex items-center border-b-2 border-gray-200 group-focus-within:border-blue-500 transition-colors pb-1">
              <MapPin className="h-5 w-5 text-gray-400 mr-2 group-focus-within:text-blue-500" />
              <input
                type="text"
                value={midStation}
                onChange={(e) => setMidStation(e.target.value)}
                onFocus={() => setActiveCitySelector('mid')}
                className="w-full text-lg font-semibold text-gray-800 focus:outline-none bg-transparent placeholder-gray-300"
                placeholder="自动推荐"
              />
            </div>
            {activeCitySelector === 'mid' && (
              <CitySelector
                onSelect={setMidStation}
                onClose={() => setActiveCitySelector(null)}
              />
            )}
          </div>
        ) : (
          <div className="md:col-span-3 relative group">
            <label className="text-xs text-gray-400 font-medium mb-1 block uppercase tracking-wider">
              {searchType === 'roundTrip' ? '去程日期' : '出发日期'}
            </label>
            <div className="flex items-center border-b-2 border-gray-200 group-focus-within:border-blue-500 transition-colors pb-1">
              <Calendar className="h-5 w-5 text-gray-400 mr-2 group-focus-within:text-blue-500" />
              <input
                type="date"
                value={date}
                onChange={(e) => setDate(e.target.value)}
                className="w-full text-lg font-semibold text-gray-800 focus:outline-none bg-transparent"
              />
            </div>
          </div>
        )}

        {/* Search Button */}
        <div className="md:col-span-2">
          <button
            onClick={handleSearch}
            className="w-full h-14 bg-gradient-to-r from-blue-600 to-blue-500 hover:from-blue-700 hover:to-blue-600 text-white rounded-xl font-bold text-lg shadow-lg shadow-blue-500/30 transition-all transform hover:-translate-y-0.5 flex items-center justify-center"
          >
            <Search className="h-5 w-5 mr-2" />
            查询
          </button>
        </div>
      </div>

      {/* Return Date Row for Round Trip */}
      {searchType === 'roundTrip' && (
        <div className="mt-4 grid grid-cols-1 md:grid-cols-12 gap-4 items-center">
          <div className="md:col-span-3 md:col-start-1 relative group">
            <label className="text-xs text-gray-400 font-medium mb-1 block uppercase tracking-wider">返程日期</label>
            <div className="flex items-center border-b-2 border-gray-200 group-focus-within:border-blue-500 transition-colors pb-1">
              <Calendar className="h-5 w-5 text-gray-400 mr-2 group-focus-within:text-blue-500" />
              <input
                type="date"
                value={returnDate}
                onChange={(e) => setReturnDate(e.target.value)}
                className="w-full text-lg font-semibold text-gray-800 focus:outline-none bg-transparent"
              />
            </div>
          </div>
        </div>
      )}

      {/* Date Row for Transfer */}
      {searchType === 'transfer' && (
        <div className="mt-4 grid grid-cols-1 md:grid-cols-12 gap-4 items-center">
          <div className="md:col-span-3 relative group">
            <label className="text-xs text-gray-400 font-medium mb-1 block uppercase tracking-wider">出发日期</label>
            <div className="flex items-center border-b-2 border-gray-200 group-focus-within:border-blue-500 transition-colors pb-1">
              <Calendar className="h-5 w-5 text-gray-400 mr-2 group-focus-within:text-blue-500" />
              <input
                type="date"
                value={date}
                onChange={(e) => setDate(e.target.value)}
                className="w-full text-lg font-semibold text-gray-800 focus:outline-none bg-transparent"
              />
            </div>
          </div>
        </div>
      )}

      {/* Options */}
      <div className="mt-6 flex flex-wrap gap-4 items-center">
        <label className="flex items-center space-x-2 cursor-pointer select-none">
          <div className="relative">
            <input
              type="checkbox"
              className="sr-only peer"
              checked={onlyHighSpeed}
              onChange={(e) => setOnlyHighSpeed(e.target.checked)}
            />
            <div className="w-10 h-6 bg-gray-200 rounded-full peer peer-checked:bg-blue-600 transition-colors"></div>
            <div className="absolute left-1 top-1 bg-white w-4 h-4 rounded-full transition-transform peer-checked:translate-x-4"></div>
          </div>
          <span className="text-sm font-medium text-gray-600">只看高铁/动车</span>
        </label>

        <div className="hidden md:block w-px h-4 bg-gray-300 mx-2"></div>

        <div className="flex space-x-2 overflow-x-auto scrollbar-hide">
          {POPULAR_CITIES.slice(0, 6).map(city => (
            <button
              key={city}
              onClick={() => setTo(city)}
              className="text-xs px-3 py-1 bg-gray-50 text-gray-600 rounded-full hover:bg-blue-50 hover:text-blue-600 transition-colors whitespace-nowrap"
            >
              去{city}
            </button>
          ))}
        </div>
      </div>
    </div>
  );
};

export default SearchWidget;
