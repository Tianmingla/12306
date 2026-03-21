import { LoginRequest, LoginResponse, UserInfoResponse } from '../types';

const API_BASE_URL = 'http://localhost:8080/api/user'; // Assuming gateway is on 8080

export const login = async (request: LoginRequest): Promise<LoginResponse> => {
  try {
    const response = await fetch(`${API_BASE_URL}/login`, {
      method: 'POST',
      headers: {
        'Content-Type': 'application/json',
      },
      body: JSON.stringify(request),
    });

    if (!response.ok) {
      throw new Error('Login failed');
    }

    const data = await response.json();
    if (data.code === 200) {
      // Store token in localStorage
      localStorage.setItem('token', data.data.token);
      return data.data;
    } else {
      throw new Error(data.message || 'Login failed');
    }
  } catch (error) {
    console.error('Error during login:', error);
    throw error;
  }
};

export const getUserInfo = async (): Promise<UserInfoResponse> => {
  try {
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

    if (!response.ok) {
      throw new Error('Failed to fetch user info');
    }

    const data = await response.json();
    if (data.code === 200) {
      return data.data;
    } else {
      throw new Error(data.message || 'Failed to fetch user info');
    }
  } catch (error) {
    console.error('Error fetching user info:', error);
    throw error;
  }
};

export const logout = () => {
  localStorage.removeItem('token');
};
