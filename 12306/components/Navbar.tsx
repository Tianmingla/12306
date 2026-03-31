
import React, { useEffect, useRef, useState } from 'react';
import { Train, User, Globe, Menu, ChevronDown, FileText } from 'lucide-react';
import { AppView } from '../types';

interface NavbarProps {
  onNavigate: (view: AppView) => void;
  onLoginClick: () => void;
  isLoggedIn: boolean;
  userName?: string;
  onLogout?: () => void;
  onOpenPassengerManage?: () => void;
}

const Navbar: React.FC<NavbarProps> = ({ onNavigate, onLoginClick, isLoggedIn, userName, onLogout, onOpenPassengerManage }) => {
  const [menuOpen, setMenuOpen] = useState(false);
  const menuRef = useRef<HTMLDivElement>(null);

  useEffect(() => {
    const handler = (e: MouseEvent) => {
      if (menuRef.current && !menuRef.current.contains(e.target as Node)) {
        setMenuOpen(false);
      }
    };
    document.addEventListener('mousedown', handler);
    return () => document.removeEventListener('mousedown', handler);
  }, []);

  return (
    <nav className="bg-white/80 backdrop-blur-md sticky top-0 z-40 border-b border-gray-100 shadow-sm">
      <div className="max-w-7xl mx-auto px-4 sm:px-6 lg:px-8">
        <div className="flex justify-between items-center h-16">
          <div
            className="flex items-center cursor-pointer group"
            onClick={() => onNavigate(AppView.HOME)}
          >
            <div className="bg-blue-600 p-2 rounded-lg group-hover:bg-blue-700 transition-colors">
              <Train className="h-6 w-6 text-white" />
            </div>
            <span className="ml-3 text-xl font-bold bg-clip-text text-transparent bg-gradient-to-r from-blue-700 to-blue-500">
              铁路出行
            </span>
          </div>

          <div className="hidden md:flex space-x-8">
            <button
              onClick={() => onNavigate(AppView.HOME)}
              className="text-gray-600 hover:text-blue-600 font-medium transition-colors"
            >
              首页
            </button>
            <a href="#" className="text-gray-600 hover:text-blue-600 font-medium transition-colors">车票预订</a>
            <a href="#" className="text-gray-600 hover:text-blue-600 font-medium transition-colors">会员服务</a>
            <button
              onClick={() => onNavigate(AppView.TRAVEL_GUIDE)}
              className="text-gray-600 hover:text-blue-600 font-medium transition-colors"
            >
              出行指南
            </button>
          </div>

          <div className="flex items-center space-x-4">
            <div className="hidden sm:flex items-center text-gray-500 hover:text-blue-600 cursor-pointer text-sm">
              <Globe className="h-4 w-4 mr-1" />
              <span>简体中文</span>
            </div>

            {isLoggedIn ? (
               <div className="relative flex items-center" ref={menuRef}>
                 <button
                   type="button"
                   onClick={() => setMenuOpen((v) => !v)}
                   className="flex items-center space-x-2 text-blue-600 cursor-pointer rounded-lg hover:bg-blue-50/80 px-2 py-1 -mr-2"
                 >
                   <div className="w-8 h-8 rounded-full bg-blue-100 flex items-center justify-center border border-blue-200">
                     <User className="h-5 w-5" />
                   </div>
                   <span className="hidden sm:inline font-medium max-w-[120px] truncate">{userName || '用户'}</span>
                   <ChevronDown className={`h-4 w-4 transition-transform ${menuOpen ? 'rotate-180' : ''}`} />
                 </button>
                 {menuOpen && (
                   <div className="absolute right-0 top-full mt-1 w-48 rounded-xl border border-gray-100 bg-white shadow-lg py-1 z-50">
                     <button
                       type="button"
                       className="w-full text-left px-4 py-2.5 text-sm text-gray-700 hover:bg-gray-50 flex items-center"
                       onClick={() => {
                         setMenuOpen(false);
                         onOpenPassengerManage?.();
                       }}
                     >
                       乘车人管理
                     </button>
                     <button
                       type="button"
                       className="w-full text-left px-4 py-2.5 text-sm text-gray-700 hover:bg-gray-50 flex items-center"
                       onClick={() => {
                         setMenuOpen(false);
                         onNavigate(AppView.ORDER_HISTORY);
                       }}
                     >
                       <FileText className="h-4 w-4 mr-2" />
                       我的订单
                     </button>
                     <button
                       type="button"
                       className="w-full text-left px-4 py-2.5 text-sm text-gray-700 hover:bg-gray-50"
                       onClick={() => {
                         setMenuOpen(false);
                         onLogout?.();
                       }}
                     >
                       退出登录
                     </button>
                   </div>
                 )}
               </div>
            ) : (
              <button
                onClick={onLoginClick}
                className="flex items-center space-x-2 text-gray-600 hover:text-blue-600 transition-colors"
              >
                <div className="w-8 h-8 rounded-full bg-gray-100 flex items-center justify-center border border-gray-200">
                  <User className="h-5 w-5" />
                </div>
                <span className="hidden sm:inline font-medium">登录 / 注册</span>
              </button>
            )}

            <button type="button" className="md:hidden text-gray-600">
              <Menu className="h-6 w-6" />
            </button>
          </div>
        </div>
      </div>
    </nav>
  );
};

export default Navbar;
