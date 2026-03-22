import { LoginRequest, LoginResponse, UserInfoResponse } from '../types';
import { API_BASE, authHeaders } from './http';

const USER_BASE = `${API_BASE}/user`;

export const sendLoginSms = async (phone: string): Promise<void> => {
  const response = await fetch(`${USER_BASE}/sms/send`, {
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
  const response = await fetch(`${USER_BASE}/login`, {
    method: 'POST',
    headers: {
      'Content-Type': 'application/json',
    },
    body: JSON.stringify(request),
  });

  const data = await response.json();
  if (data.code === 200) {
    localStorage.setItem('token', data.data.token);
    if (data.data.phone) {
      localStorage.setItem('userPhone', data.data.phone);
    }
    return data.data;
  }
  throw new Error(data.message || '登录失败');
};

export const getUserInfo = async (): Promise<UserInfoResponse> => {
  const response = await fetch(`${USER_BASE}/info`, {
    method: 'GET',
    headers: authHeaders(),
  });

  const data = await response.json();
  if (data.code === 200) {
    if (data.data?.phone) {
      localStorage.setItem('userPhone', data.data.phone);
    }
    return data.data;
  }
  throw new Error(data.message || 'Failed to fetch user info');
};

export const logout = () => {
  localStorage.removeItem('token');
  localStorage.removeItem('userPhone');
};
