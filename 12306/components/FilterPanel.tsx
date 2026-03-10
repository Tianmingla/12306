
import React from 'react';
import { FilterOptions } from '../types';

interface FilterPanelProps {
  filters: FilterOptions;
  onChange: (filters: FilterOptions) => void;
  isOpen: boolean;
  onClose: () => void;
}

const FilterPanel: React.FC<FilterPanelProps> = ({ filters, onChange, isOpen, onClose }) => {
  if (!isOpen) return null;

  const toggleTrainType = (type: string) => {
    const newTypes = filters.trainTypes.includes(type)
      ? filters.trainTypes.filter(t => t !== type)
      : [...filters.trainTypes, type];
    onChange({ ...filters, trainTypes: newTypes });
  };

  const toggleTime = (time: string) => {
    const newTimes = filters.departureTime.includes(time)
      ? filters.departureTime.filter(t => t !== time)
      : [...filters.departureTime, time];
    onChange({ ...filters, departureTime: newTimes });
  };

  return (
    <>
      {/* Backdrop */}
      <div className="fixed inset-0 z-40 bg-transparent" onClick={onClose} />
      
      {/* Panel */}
      <div className="absolute top-full right-0 mt-2 w-72 bg-white rounded-xl shadow-xl border border-gray-200 z-50 p-5 animate-fade-in-down origin-top-right">
        <div className="space-y-6">
          {/* Train Type */}
          <div>
            <h4 className="text-sm font-bold text-gray-800 mb-3">车次类型</h4>
            <div className="grid grid-cols-2 gap-2">
              {['G-高铁', 'D-动车', 'Z-直达', 'K-普快'].map(label => {
                const code = label.split('-')[0];
                const isSelected = filters.trainTypes.includes(code);
                return (
                  <button
                    key={code}
                    onClick={() => toggleTrainType(code)}
                    className={`text-sm py-1.5 px-3 rounded-lg border transition-all ${
                      isSelected 
                        ? 'bg-blue-50 border-blue-500 text-blue-700' 
                        : 'bg-white border-gray-200 text-gray-600 hover:border-gray-300'
                    }`}
                  >
                    {label}
                  </button>
                );
              })}
            </div>
          </div>

          {/* Time */}
          <div>
            <h4 className="text-sm font-bold text-gray-800 mb-3">出发时段</h4>
            <div className="grid grid-cols-2 gap-2">
              {[
                { label: '00:00 - 06:00', val: '0-6' },
                { label: '06:00 - 12:00', val: '6-12' },
                { label: '12:00 - 18:00', val: '12-18' },
                { label: '18:00 - 24:00', val: '18-24' },
              ].map(item => {
                const isSelected = filters.departureTime.includes(item.val);
                return (
                  <button
                    key={item.val}
                    onClick={() => toggleTime(item.val)}
                    className={`text-sm py-1.5 px-3 rounded-lg border transition-all ${
                      isSelected 
                        ? 'bg-blue-50 border-blue-500 text-blue-700' 
                        : 'bg-white border-gray-200 text-gray-600 hover:border-gray-300'
                    }`}
                  >
                    {item.label}
                  </button>
                );
              })}
            </div>
          </div>
        </div>

        <div className="mt-6 pt-4 border-t border-gray-100 flex justify-between">
           <button 
             onClick={() => onChange({ trainTypes: [], departureTime: [] })}
             className="text-sm text-gray-500 hover:text-gray-800"
           >
             重置
           </button>
           <button 
             onClick={onClose}
             className="text-sm font-medium text-blue-600 hover:text-blue-700"
           >
             完成
           </button>
        </div>
      </div>
    </>
  );
};

export default FilterPanel;
