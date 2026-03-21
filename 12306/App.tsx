
import React, { useState, useEffect } from 'react';
import Navbar from './components/Navbar';
import SearchWidget from './components/SearchWidget';
import TrainList from './components/TrainList';
import Features from './components/Features';
import AIAssistant from './components/AIAssistant';
import LoginModal from './components/LoginModal';
import { AppView, SearchParams } from './types';
import { getUserInfo } from './services/userService';

const App: React.FC = () => {
  const [currentView, setCurrentView] = useState<AppView>(AppView.HOME);
  const [searchParams, setSearchParams] = useState<SearchParams | null>(null);
  const [isLoginOpen, setIsLoginOpen] = useState(false);
  const [isLoggedIn, setIsLoggedIn] = useState(false);

  const [userName, setUserName] = useState<string>('');

  useEffect(() => {
    const checkLoginStatus = async () => {
      try {
        // TODO: Implement token refresh mechanism if token is expired
        const userInfo = await getUserInfo();
        setIsLoggedIn(true);
        setUserName(userInfo.username);
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
    setCurrentView(view);
  };

  const handleLogout = () => {
    localStorage.removeItem('token');
    setIsLoggedIn(false);
    setUserName('');
  };

  return (
    <div className="min-h-screen bg-gray-50 font-sans text-gray-900 pb-20">
      <Navbar 
        onNavigate={handleNavigate} 
        onLoginClick={() => setIsLoginOpen(true)}
        isLoggedIn={isLoggedIn}
        userName={userName}
        onLogout={handleLogout}
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
          />
        </div>
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
            setUserName(userInfo.username);
          } catch (e) {
            console.error(e);
          }
        }}
      />
    </div>
  );
};

export default App;
