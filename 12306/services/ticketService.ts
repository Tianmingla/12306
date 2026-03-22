
import { SearchParams, TrainTicket, ApiResponse, ApiRoute } from '../types';
import { API_BASE, authHeaders } from './http';

/**
 * Helper to format ISO date string to HH:mm
 */
const formatTime = (isoString: string | null): string => {
  if (!isoString) return '--:--';
  try {
    const date = new Date(isoString);
    return date.toLocaleTimeString('en-GB', { hour12: false, hour: '2-digit', minute: '2-digit' });
  } catch (e) {
    return '--:--';
  }
};

/**
 * Adapts backend route data to frontend TrainTicket model
 */
const adaptRouteToTicket = (route: ApiRoute): TrainTicket | null => {
  const segment = route.segments[0];
  if (!segment) return null;

  const seats = route.remainingTicketNumMap || {};

  const isHighSpeed = segment.trainNumber.startsWith('G') || segment.trainNumber.startsWith('D');
  const basePrice = isHighSpeed ? 553 : 156;
  const mockPrice = basePrice + (segment.id % 50);

  const h = Math.floor(route.totalDurationMinutes / 60);
  const m = route.totalDurationMinutes % 60;
  const durationStr = `${h}小时${m}分`;

  return {
    id: segment.id.toString(),
    trainNumber: segment.trainNumber,
    fromStation: segment.departureStation,
    toStation: segment.arrivalStation,
    departureTime: route.firstDepartureTime || formatTime(segment.startTime),
    arrivalTime: route.finalArrivalTime || formatTime(segment.endTime),
    duration: durationStr,
    price: mockPrice,
    type: segment.trainNumber.startsWith('G') ? 'G' :
          segment.trainNumber.startsWith('D') ? 'D' :
          segment.trainNumber.startsWith('Z') ? 'Z' : 'K',
    seatsAvailable: {
      business: seats['商务座'] ?? 0,
      first: seats['一等座'] ?? seats['软卧'] ?? 0,
      second: seats['二等座'] ?? seats['硬卧'] ?? seats['硬座'] ?? 0,
      standing: seats['无座'] ?? 0,
    }
  };
};

export const searchTickets = async (params: SearchParams): Promise<TrainTicket[]> => {
  const queryParams = new URLSearchParams({
    from: params.from,
    to: params.to,
    date: params.date,
  });

  const response = await fetch(`${API_BASE}/ticket/search?${queryParams.toString()}`, {
    headers: authHeaders()
  });

  if (!response.ok) {
    throw new Error(`API Error: ${response.statusText}`);
  }

  const json: ApiResponse = await response.json();

  if (json.code !== 200) {
    throw new Error(json.message || 'Unknown API error');
  }

  const validTickets = json.data
    .map(adaptRouteToTicket)
    .filter((t): t is TrainTicket => t !== null);

  if (params.onlyHighSpeed) {
    return validTickets.filter(t => t.type === 'G' || t.type === 'D');
  }

  return validTickets;
};

export const purchaseTicket = async (request: import('../types').PurchaseTicketRequest): Promise<import('../types').PurchaseTicketResponse> => {
  const response = await fetch(`${API_BASE}/ticket/purchase`, {
    method: 'POST',
    headers: authHeaders(),
    body: JSON.stringify(request),
  });

  if (!response.ok) {
    throw new Error(`API Error: ${response.statusText}`);
  }

  const json = await response.json();
  if (json.code !== 200) {
    throw new Error(json.message || 'Unknown API error');
  }

  return json;
};

export const getTrainRouteDetails = async (trainNum: string): Promise<import('../types').TrainStationDetail[]> => {
  const response = await fetch(`${API_BASE}/trainDetail/stations?trainNum=${encodeURIComponent(trainNum)}`, {
    headers: authHeaders()
  });

  if (!response.ok) {
    throw new Error(`API Error: ${response.statusText}`);
  }

  const json: import('../types').TrainRouteDetailsResponse = await response.json();
  if (json.code !== 200) {
    throw new Error(json.message || 'Unknown API error');
  }

  return json.data;
};
