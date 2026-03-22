/** 与网关 / 后端统一的 API 前缀，供各 *Service 复用 */
export const API_BASE = 'http://localhost:8080/api';

export function authHeaders(): HeadersInit {
  const token = localStorage.getItem('token');
  return {
    'Content-Type': 'application/json',
    ...(token ? { Authorization: `Bearer ${token}` } : {}),
  };
}
