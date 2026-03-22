
import React, { useEffect, useState } from 'react';
import { X, QrCode, Smartphone } from 'lucide-react';
import { login, sendLoginSms } from '../services/userService';

interface LoginModalProps {
  isOpen: boolean;
  onClose: () => void;
  onLoginSuccess: () => void;
}

const LoginModal: React.FC<LoginModalProps> = ({ isOpen, onClose, onLoginSuccess }) => {
  const [loginMethod, setLoginMethod] = useState<'scan' | 'account'>('scan');
  const [isLoading, setIsLoading] = useState(false);
  const [phone, setPhone] = useState('');
  const [smsCode, setSmsCode] = useState('');
  const [error, setError] = useState('');
  const [smsCooldown, setSmsCooldown] = useState(0);

  useEffect(() => {
    if (smsCooldown <= 0) return;
    const t = window.setInterval(() => {
      setSmsCooldown((c) => (c <= 1 ? 0 : c - 1));
    }, 1000);
    return () => window.clearInterval(t);
  }, [smsCooldown]);

  if (!isOpen) return null;

  const handleSendSms = async () => {
    setError('');
    try {
      await sendLoginSms(phone);
      setSmsCooldown(60);
    } catch (err: unknown) {
      setError(err instanceof Error ? err.message : '发送失败');
    }
  };

  const handleLogin = async (e: React.FormEvent) => {
    e.preventDefault();
    setIsLoading(true);
    setError('');

    try {
      await login({ phone, smsCode });
      setIsLoading(false);
      onLoginSuccess();
      onClose();
    } catch (err: unknown) {
      setIsLoading(false);
      setError(err instanceof Error ? err.message : '登录失败，请检查手机号与验证码');
    }
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
           <div className="w-full p-8">
             <div className="text-center mb-8">
               <h2 className="text-2xl font-bold text-gray-800">欢迎登录12306</h2>
               <p className="text-sm text-gray-500 mt-2">官方购票 安全出行</p>
             </div>

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
                 手机验证码登录
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
                 {error && <div className="text-red-500 text-sm text-center">{error}</div>}
                 <div className="space-y-1">
                   <div className="relative">
                     <Smartphone className="absolute left-3 top-3 h-5 w-5 text-gray-400" />
                     <input 
                       type="tel" 
                       value={phone}
                       onChange={(e) => setPhone(e.target.value)}
                       placeholder="11位手机号"
                       maxLength={11}
                       className="w-full pl-10 pr-4 py-2.5 bg-gray-50 border border-gray-200 rounded-lg focus:outline-none focus:ring-2 focus:ring-blue-500/20 focus:border-blue-500 transition-all"
                     />
                   </div>
                 </div>
                 <div className="flex gap-2">
                   <div className="relative flex-1">
                     <input 
                       type="text" 
                       inputMode="numeric"
                       value={smsCode}
                       onChange={(e) => setSmsCode(e.target.value)}
                       placeholder="短信验证码"
                       maxLength={6}
                       className="w-full px-4 py-2.5 bg-gray-50 border border-gray-200 rounded-lg focus:outline-none focus:ring-2 focus:ring-blue-500/20 focus:border-blue-500 transition-all"
                     />
                   </div>
                   <button
                     type="button"
                     onClick={handleSendSms}
                     disabled={smsCooldown > 0 || !/^1[3-9]\d{9}$/.test(phone.trim())}
                     className="shrink-0 px-3 py-2.5 text-sm font-medium rounded-lg border border-blue-600 text-blue-600 hover:bg-blue-50 disabled:opacity-40 disabled:cursor-not-allowed"
                   >
                     {smsCooldown > 0 ? `${smsCooldown}s` : '获取验证码'}
                   </button>
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
                 <p className="text-xs text-gray-400 text-center">
                   开发环境默认验证码为 123456（见后端日志 [MOCK SMS]）
                 </p>
               </form>
             )}
           </div>
        </div>
      </div>
    </div>
  );
};

export default LoginModal;
