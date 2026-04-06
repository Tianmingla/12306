
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

  // Now remainingTicketNumMap and priceMap are arrays (one per segment)
  const seatsArray = route.remainingTicketNumMap || [];
  const pricesArray = route.priceMap || [];

  // Merge all segments' seats and prices
  const mergedSeats: Record<string, number> = {};
  const mergedPrices: Record<string, number> = {};

  seatsArray.forEach(seats => {
    Object.entries(seats).forEach(([key, value]) => {
      // For remaining tickets, take the minimum across all segments
      if (mergedSeats[key] === undefined || value < mergedSeats[key]) {
        mergedSeats[key] = value;
      }
    });
  });

  pricesArray.forEach(prices => {
    Object.entries(prices).forEach(([key, value]) => {
      // For prices, sum across all segments
      mergedPrices[key] = (mergedPrices[key] || 0) + (value || 0);
    });
  });

  const h = Math.floor(route.totalDurationMinutes / 60);
  const m = route.totalDurationMinutes % 60;
  const durationStr = `${h}小时${m}分`;

  // For transfer routes, use first departure and last arrival stations
  const firstSegment = route.segments[0];
  const lastSegment = route.segments[route.segments.length - 1];

  return {
    id: route.planId || segment.id.toString(),
    trainNumber: route.segments.length > 1
      ? `${firstSegment.trainNumber} → ${lastSegment.trainNumber}`
      : segment.trainNumber,
    fromStation: firstSegment.departureStation,
    toStation: lastSegment.arrivalStation,
    departureTime: route.firstDepartureTime || formatTime(firstSegment.startTime),
    arrivalTime: route.finalArrivalTime || formatTime(lastSegment.endTime),
    duration: durationStr,
    price: mergedPrices['二等座'] || mergedPrices['硬座'] || 0,
    type: firstSegment.trainNumber.startsWith('G') ? 'G' :
          firstSegment.trainNumber.startsWith('D') ? 'D' :
          firstSegment.trainNumber.startsWith('Z') ? 'Z' : 'K',
    seatsAvailable: {
      business: mergedSeats['商务座'] ?? 0,
      first: mergedSeats['一等座'] ?? mergedSeats['软卧'] ?? 0,
      second: mergedSeats['二等座'] ?? mergedSeats['硬卧'] ?? mergedSeats['硬座'] ?? 0,
      standing: mergedSeats['无座'] ?? 0,
    },
    prices: mergedPrices,
  };
};

export const searchTickets = async (params: SearchParams): Promise<TrainTicket[]> => {
  const queryParams = new URLSearchParams({
    from: params.from,
    to: params.to,
    date: params.date,
  });

  // Add mid station for transfer search
  if (params.searchType === 'transfer' && params.midStation) {
    queryParams.set('mid', params.midStation);
  }

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
