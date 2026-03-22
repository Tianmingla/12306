import { LoginRequest, LoginResponse, UserInfoResponse } from '../types';

const API_BASE_URL = 'http://localhost:8080/api/user';

export const sendLoginSms = async (phone: string): Promise<void> => {
  const response = await fetch(`${API_BASE_URL}/sms/send`, {
    method: 'POST',
    headers: {
      'Content-Type': 'application/json',
    },
    body: JSON.stringify({ phone }),
  });

  const data = await response.json();
  if (data.code === 200) {
    return;
  }
  throw new Error(data.message || '发送验证码失败');
};

export const login = async (request: LoginRequest): Promise<LoginResponse> => {
  const response = await fetch(`${API_BASE_URL}/login`, {
    method: 'POST',
    headers: {
      'Content-Type': 'application/json',
    },
    body: JSON.stringify(request),
  });

  const data = await response.json();
  if (data.code === 200) {
    localStorage.setItem('token', data.data.token);
    return data.data;
  }
  throw new Error(data.message || '登录失败');
};

export const getUserInfo = async (): Promise<UserInfoResponse> => {
  const token = localStorage.getItem('token');
  if (!token) {
    throw new Error('No token found');
  }

  const response = await fetch(`${API_BASE_URL}/info`, {
    method: 'GET',
    headers: {
      'Authorization': `Bearer ${token}`,
    },
  });

  const data = await response.json();
  if (data.code === 200) {
    return data.data;
  }
  throw new Error(data.message || 'Failed to fetch user info');
};

export const logout = () => {
  localStorage.removeItem('token');
};
