import { API_BASE, authHeaders } from './http';
import type { PassengerApi, PassengerSavePayload } from '../types';

const USER_BASE = `${API_BASE}/user`;

function parseResult<T>(json: { code: number; message?: string | null; data: T }): T {
  if (json.code !== 200) {
    throw new Error(json.message || '请求失败');
  }
  return json.data;
}

function ensureOk(json: { code: number; message?: string | null }): void {
  if (json.code !== 200) {
    throw new Error(json.message || '请求失败');
  }
}

export async function listPassengers(): Promise<PassengerApi[]> {
  const response = await fetch(`${USER_BASE}/passengers`, {
    method: 'GET',
    headers: authHeaders(),
  });
  const json = await response.json();
  return parseResult(json);
}

export async function addPassenger(payload: PassengerSavePayload): Promise<number> {
  const response = await fetch(`${USER_BASE}/passengers`, {
    method: 'POST',
    headers: authHeaders(),
    body: JSON.stringify(payload),
  });
  const json = await response.json();
  return parseResult(json);
}

export async function deletePassenger(id: number): Promise<void> {
  const response = await fetch(`${USER_BASE}/passengers/${id}`, {
    method: 'DELETE',
    headers: authHeaders(),
  });
  const json = await response.json();
  ensureOk(json);
}

export async function updatePassenger(id: number, payload: PassengerSavePayload): Promise<void> {
  const response = await fetch(`${USER_BASE}/passengers/${id}`, {
    method: 'PUT',
    headers: authHeaders(),
    body: JSON.stringify(payload),
  });
  const json = await response.json();
  ensureOk(json);
}
