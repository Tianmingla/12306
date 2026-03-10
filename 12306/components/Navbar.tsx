
import React from 'react';
import { Train, User, Globe, Menu } from 'lucide-react';
import { AppView } from '../types';

interface NavbarProps {
  onNavigate: (view: AppView) => void;
  onLoginClick: () => void;
  isLoggedIn: boolean;
}

const Navbar: React.FC<NavbarProps> = ({ onNavigate, onLoginClick, isLoggedIn }) => {
  return (
    <nav className="bg-white/80 backdrop-blur-md sticky top-0 z-40 border-b border-gray-100 shadow-sm">
      <div className="max-w-7xl mx-auto px-4 sm:px-6 lg:px-8">
        <div className="flex justify-between items-center h-16">
          {/* Logo */}
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

          {/* Desktop Nav */}
          <div className="hidden md:flex space-x-8">
            <button 
              onClick={() => onNavigate(AppView.HOME)} 
              className="text-gray-600 hover:text-blue-600 font-medium transition-colors"
            >
              首页
            </button>
            <a href="#" className="text-gray-600 hover:text-blue-600 font-medium transition-colors">车票预订</a>
            <a href="#" className="text-gray-600 hover:text-blue-600 font-medium transition-colors">会员服务</a>
            <a href="#" className="text-gray-600 hover:text-blue-600 font-medium transition-colors">出行指南</a>
          </div>

          {/* Right Actions */}
          <div className="flex items-center space-x-4">
            <div className="hidden sm:flex items-center text-gray-500 hover:text-blue-600 cursor-pointer text-sm">
              <Globe className="h-4 w-4 mr-1" />
              <span>简体中文</span>
            </div>
            
            {isLoggedIn ? (
               <div className="flex items-center space-x-2 text-blue-600 cursor-pointer">
                 <div className="w-8 h-8 rounded-full bg-blue-100 flex items-center justify-center border border-blue-200">
                   <User className="h-5 w-5" />
                 </div>
                 <span className="hidden sm:inline font-medium">张三</span>
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

            <button className="md:hidden text-gray-600">
              <Menu className="h-6 w-6" />
            </button>
          </div>
        </div>
      </div>
    </nav>
  );
};

export default Navbar;
