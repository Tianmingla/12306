
import React, { useState, useEffect } from 'react';
import Navbar from './components/Navbar';
import SearchWidget from './components/SearchWidget';
import TrainList from './components/TrainList';
import Features from './components/Features';
import AIAssistant from './components/AIAssistant';
import LoginModal from './components/LoginModal';
import { AppView, SearchParams } from './types';
import OrderDetailPage from './components/OrderDetailPage';
import OrderHistoryPage from './components/OrderHistoryPage';
import { getUserInfo, logout as userLogout } from './services/userService';
import PassengerManageModal from './components/PassengerManageModal';

const App: React.FC = () => {
  const [currentView, setCurrentView] = useState<AppView>(AppView.HOME);
  const [searchParams, setSearchParams] = useState<SearchParams | null>(null);
  const [isLoginOpen, setIsLoginOpen] = useState(false);
  const [isLoggedIn, setIsLoggedIn] = useState(false);

  const [userName, setUserName] = useState<string>('');
  const [passengerModalOpen, setPassengerModalOpen] = useState(false);
  const [orderDetailOrderSn, setOrderDetailOrderSn] = useState<string | null>(null);

  useEffect(() => {
    const params = new URLSearchParams(window.location.search);
    if (params.get('paid') === '1' && params.get('orderSn')) {
      const sn = params.get('orderSn')!;
      setOrderDetailOrderSn(sn);
      setCurrentView(AppView.ORDER_DETAIL);
      window.history.replaceState({}, '', window.location.pathname);
    }
  }, []);

  useEffect(() => {
    const checkLoginStatus = async () => {
      try {
        // TODO: Implement token refresh mechanism if token is expired
        const userInfo = await getUserInfo();
        setIsLoggedIn(true);
        setUserName(userInfo.phone);
      } catch (error) {
        setIsLoggedIn(false);
      }
    };
    checkLoginStatus();
  }, []);

  const handleSearch = (params: SearchParams) => {
    setSearchParams(params);
    setCurrentView(AppView.SEARCH_RESULTS);
  };

  const handleNavigate = (view: AppView) => {
    if (view !== AppView.ORDER_DETAIL) {
      setOrderDetailOrderSn(null);
    }
    setCurrentView(view);
  };

  const handleLogout = () => {
    userLogout();
    setIsLoggedIn(false);
    setUserName('');
  };

  const handleViewOrderDetail = (orderSn: string) => {
    setOrderDetailOrderSn(orderSn);
    setCurrentView(AppView.ORDER_DETAIL);
  };

  return (
    <div className="min-h-screen bg-gray-50 font-sans text-gray-900 pb-20">
      <Navbar
        onNavigate={handleNavigate}
        onLoginClick={() => setIsLoginOpen(true)}
        isLoggedIn={isLoggedIn}
        userName={userName}
        onLogout={handleLogout}
        onOpenPassengerManage={() => setPassengerModalOpen(true)}
      />

      {currentView === AppView.HOME && (
        <>
          {/* Hero Section */}
          <div className="relative h-[500px] w-full bg-slate-900 overflow-hidden">
            <img
              src="https://picsum.photos/1920/600?grayscale"
              alt="Train Station"
              className="absolute inset-0 w-full h-full object-cover opacity-60 mix-blend-overlay"
            />
            <div className="absolute inset-0 bg-gradient-to-r from-blue-900/90 to-blue-600/40"></div>
            <div className="relative max-w-7xl mx-auto px-4 sm:px-6 lg:px-8 h-full flex flex-col justify-center pb-24">
              <h1 className="text-4xl md:text-6xl font-extrabold text-white mb-4 tracking-tight">
                您的旅程 <br/>
                <span className="text-blue-300">从此开始</span>
              </h1>
              <p className="text-xl text-blue-100 max-w-xl">
                体验无缝连接的中国铁路出行服务。高速、舒适、准时，连接您与每一个目的地。
              </p>
            </div>
          </div>

          <SearchWidget onSearch={handleSearch} />
          <Features />
        </>
      )}

      {currentView === AppView.SEARCH_RESULTS && searchParams && (
        <div className="animate-fade-in">
          <TrainList
            searchParams={searchParams}
            onBack={() => setCurrentView(AppView.HOME)}
            onPurchaseSuccess={(orderSn) => {
              setOrderDetailOrderSn(orderSn);
              setCurrentView(AppView.ORDER_DETAIL);
            }}
          />
        </div>
      )}

      {currentView === AppView.ORDER_DETAIL && orderDetailOrderSn && (
        <OrderDetailPage
          orderSn={orderDetailOrderSn}
          onBack={() => {
            setOrderDetailOrderSn(null);
            setCurrentView(AppView.HOME);
          }}
        />
      )}

      {currentView === AppView.ORDER_HISTORY && (
        <OrderHistoryPage
          onBack={() => setCurrentView(AppView.HOME)}
          onViewDetail={handleViewOrderDetail}
        />
      )}

      {/* Footer */}
      <footer className="bg-white border-t border-gray-200 py-12 mt-auto">
        <div className="max-w-7xl mx-auto px-4 text-center">
          <p className="text-gray-400 text-sm">© 2024 铁路出行服务平台 版权所有.</p>
        </div>
      </footer>

      <AIAssistant />

      <LoginModal
        isOpen={isLoginOpen}
        onClose={() => setIsLoginOpen(false)}
        onLoginSuccess={async () => {
          setIsLoggedIn(true);
          try {
            const userInfo = await getUserInfo();
            setUserName(userInfo.phone);
          } catch (e) {
            console.error(e);
          }
        }}
      />

      <PassengerManageModal
        isOpen={passengerModalOpen}
        onClose={() => setPassengerModalOpen(false)}
      />
    </div>
  );
};

export default App;
