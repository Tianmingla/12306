
import React, { useState } from 'react';
import { X, User, Lock, QrCode, Smartphone } from 'lucide-react';

interface LoginModalProps {
  isOpen: boolean;
  onClose: () => void;
  onLoginSuccess: () => void;
}

const LoginModal: React.FC<LoginModalProps> = ({ isOpen, onClose, onLoginSuccess }) => {
  const [loginMethod, setLoginMethod] = useState<'scan' | 'account'>('scan');
  const [isLoading, setIsLoading] = useState(false);

  if (!isOpen) return null;

  const handleLogin = (e: React.FormEvent) => {
    e.preventDefault();
    setIsLoading(true);
    // Mock network request
    setTimeout(() => {
      setIsLoading(false);
      onLoginSuccess();
      onClose();
    }, 1500);
  };

  return (
    <div className="fixed inset-0 z-50 flex items-center justify-center p-4 bg-black/50 backdrop-blur-sm animate-fade-in">
      <div className="bg-white rounded-2xl w-full max-w-md shadow-2xl overflow-hidden relative">
        <button 
          onClick={onClose}
          className="absolute top-4 right-4 p-1 rounded-full hover:bg-gray-100 text-gray-400 hover:text-gray-600 transition-colors"
        >
          <X className="h-5 w-5" />
        </button>

        <div className="flex h-full">
           {/* Main Content */}
           <div className="w-full p-8">
             <div className="text-center mb-8">
               <h2 className="text-2xl font-bold text-gray-800">欢迎登录12306</h2>
               <p className="text-sm text-gray-500 mt-2">官方购票 安全出行</p>
             </div>

             {/* Tabs */}
             <div className="flex justify-center mb-8 border-b border-gray-100">
               <button 
                 onClick={() => setLoginMethod('scan')}
                 className={`pb-3 px-6 text-sm font-medium transition-colors relative ${
                   loginMethod === 'scan' ? 'text-blue-600' : 'text-gray-500 hover:text-gray-700'
                 }`}
               >
                 扫码登录
                 {loginMethod === 'scan' && <div className="absolute bottom-0 left-0 w-full h-0.5 bg-blue-600 rounded-full" />}
               </button>
               <button 
                 onClick={() => setLoginMethod('account')}
                 className={`pb-3 px-6 text-sm font-medium transition-colors relative ${
                   loginMethod === 'account' ? 'text-blue-600' : 'text-gray-500 hover:text-gray-700'
                 }`}
               >
                 账号登录
                 {loginMethod === 'account' && <div className="absolute bottom-0 left-0 w-full h-0.5 bg-blue-600 rounded-full" />}
               </button>
             </div>

             {loginMethod === 'scan' ? (
               <div className="flex flex-col items-center py-4">
                 <div className="w-40 h-40 bg-gray-100 rounded-xl flex items-center justify-center mb-4 border-2 border-blue-100">
                   <QrCode className="h-24 w-24 text-gray-800" />
                 </div>
                 <p className="text-sm text-gray-500">打开 <span className="text-blue-600 font-medium">铁路12306 APP</span> 扫一扫</p>
                 <button 
                  onClick={() => {
                      setIsLoading(true);
                      setTimeout(() => {
                        setIsLoading(false);
                        onLoginSuccess();
                        onClose();
                      }, 1000);
                  }}
                  className="mt-6 text-blue-600 text-sm hover:underline"
                 >
                   (模拟: 点击此处直接登录)
                 </button>
               </div>
             ) : (
               <form onSubmit={handleLogin} className="space-y-4">
                 <div className="space-y-1">
                   <div className="relative">
                     <User className="absolute left-3 top-3 h-5 w-5 text-gray-400" />
                     <input 
                       type="text" 
                       placeholder="用户名/邮箱/手机号"
                       className="w-full pl-10 pr-4 py-2.5 bg-gray-50 border border-gray-200 rounded-lg focus:outline-none focus:ring-2 focus:ring-blue-500/20 focus:border-blue-500 transition-all"
                     />
                   </div>
                 </div>
                 <div className="space-y-1">
                   <div className="relative">
                     <Lock className="absolute left-3 top-3 h-5 w-5 text-gray-400" />
                     <input 
                       type="password" 
                       placeholder="密码"
                       className="w-full pl-10 pr-4 py-2.5 bg-gray-50 border border-gray-200 rounded-lg focus:outline-none focus:ring-2 focus:ring-blue-500/20 focus:border-blue-500 transition-all"
                     />
                   </div>
                 </div>
                 <button 
                   type="submit" 
                   disabled={isLoading}
                   className="w-full py-2.5 bg-blue-600 hover:bg-blue-700 text-white font-medium rounded-lg shadow-lg shadow-blue-500/30 transition-all transform active:scale-95 flex justify-center items-center"
                 >
                   {isLoading ? (
                     <div className="h-5 w-5 border-2 border-white border-t-transparent rounded-full animate-spin" />
                   ) : (
                     '登录'
                   )}
                 </button>
                 <div className="flex justify-between text-xs text-gray-500 pt-2">
                   <a href="#" className="hover:text-blue-600">注册账号</a>
                   <a href="#" className="hover:text-blue-600">忘记密码?</a>
                 </div>
               </form>
             )}
           </div>
        </div>
      </div>
    </div>
  );
};

export default LoginModal;
