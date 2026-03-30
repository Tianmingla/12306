
import { StationScreenResponse, WaitlistOrderVO, WaitlistCreateRequest } from '../types';
import { API_BASE, authHeaders } from './http';

/**
 * 获取车站大屏信息
 */
export const getStationScreen = async (stationName: string): Promise<StationScreenResponse> => {
  const response = await fetch(`${API_BASE}/ticket/screen/station/${encodeURIComponent(stationName)}`, {
    headers: authHeaders()
  });

  if (!response.ok) {
    throw new Error(`API Error: ${response.statusText}`);
  }

  const json = await response.json();
  if (json.code !== 200) {
    throw new Error(json.message || '获取车站大屏信息失败');
  }

  return json.data;
};

/**
 * 创建候补订单
 */
export const createWaitlist = async (request: WaitlistCreateRequest): Promise<string> => {
  const response = await fetch(`${API_BASE}/order/waitlist/create`, {
    method: 'POST',
    headers: authHeaders(),
    body: JSON.stringify(request),
  });

  if (!response.ok) {
    throw new Error(`API Error: ${response.statusText}`);
  }

  const json = await response.json();
  if (json.code !== 200) {
    throw new Error(json.message || '创建候补订单失败');
  }

  return json.data;
};

/**
 * 取消候补订单
 */
export const cancelWaitlist = async (waitlistSn: string): Promise<void> => {
  const response = await fetch(`${API_BASE}/order/waitlist/cancel/${waitlistSn}`, {
    method: 'POST',
    headers: authHeaders(),
  });

  if (!response.ok) {
    throw new Error(`API Error: ${response.statusText}`);
  }

  const json = await response.json();
  if (json.code !== 200) {
    throw new Error(json.message || '取消候补订单失败');
  }
};

/**
 * 获取候补订单列表
 */
export const getWaitlistOrders = async (): Promise<WaitlistOrderVO[]> => {
  const response = await fetch(`${API_BASE}/order/waitlist/list`, {
    headers: authHeaders()
  });

  if (!response.ok) {
    throw new Error(`API Error: ${response.statusText}`);
  }

  const json = await response.json();
  if (json.code !== 200) {
    throw new Error(json.message || '获取候补订单列表失败');
  }

  return json.data || [];
};

/**
 * 获取候补订单详情
 */
export const getWaitlistDetail = async (waitlistSn: string): Promise<WaitlistOrderVO> => {
  const response = await fetch(`${API_BASE}/order/waitlist/detail/${waitlistSn}`, {
    headers: authHeaders()
  });

  if (!response.ok) {
    throw new Error(`API Error: ${response.statusText}`);
  }

  const json = await response.json();
  if (json.code !== 200) {
    throw new Error(json.message || '获取候补订单详情失败');
  }

  return json.data;
};
