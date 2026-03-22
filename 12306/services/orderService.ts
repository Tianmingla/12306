import { API_BASE, authHeaders } from './http';
import type { OrderDetailVO, PayOrderVO } from '../types';

const ORDER_BASE = `${API_BASE}/order`;

export async function getOrderDetail(orderSn: string): Promise<OrderDetailVO> {
  const response = await fetch(`${ORDER_BASE}/detail/${encodeURIComponent(orderSn)}`, {
    method: 'GET',
    headers: authHeaders(),
  });
  const json = await response.json();
  if (json.code !== 200) {
    throw new Error(json.message || '加载订单失败');
  }
  return json.data;
}

export async function payOrder(orderSn: string): Promise<PayOrderVO> {
  const response = await fetch(`${ORDER_BASE}/pay`, {
    method: 'POST',
    headers: authHeaders(),
    body: JSON.stringify({ orderSn }),
  });
  const json = await response.json();
  if (json.code !== 200) {
    throw new Error(json.message || '发起支付失败');
  }
  return json.data;
}

export function submitAlipayForm(payFormHtml: string): void {
  const wrapper = document.createElement('div');
  wrapper.innerHTML = payFormHtml;
  wrapper.style.display = 'none';
  document.body.appendChild(wrapper);
  const form = wrapper.querySelector('form');
  if (form) {
    form.submit();
  }
}
