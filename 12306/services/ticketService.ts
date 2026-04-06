
import { SearchParams, TrainTicket, ApiResponse, ApiRoute, TicketSegment } from '../types';
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
 * Format duration minutes to string
 */
const formatDuration = (minutes: number): string => {
  const h = Math.floor(minutes / 60);
  const m = minutes % 60;
  return `${h}小时${m}分`;
};

/**
 * Get train type from train number
 */
const getTrainType = (trainNumber: string): 'G' | 'D' | 'K' | 'Z' => {
  return trainNumber.startsWith('G') ? 'G' :
         trainNumber.startsWith('D') ? 'D' :
         trainNumber.startsWith('Z') ? 'Z' : 'K';
};

/**
 * Map Chinese seat names to English keys
 */
const mapSeatsToEnglish = (seats: Record<string, number>): Record<string, number> => ({
  business: seats['商务座'] ?? 0,
  first: seats['一等座'] ?? seats['软卧'] ?? 0,
  second: seats['二等座'] ?? seats['硬卧'] ?? seats['硬座'] ?? 0,
  standing: seats['无座'] ?? 0,
});

/**
 * Adapts backend route data to frontend TrainTicket model
 */
const adaptRouteToTicket = (route: ApiRoute): TrainTicket | null => {
  const firstSegment = route.segments[0];
  if (!firstSegment) return null;

  const seatsArray = route.remainingTicketNumMap || [];
  const pricesArray = route.priceMap || [];
  const isTransfer = route.segments.length > 1;

  // Build segment details
  const ticketSegments: TicketSegment[] = route.segments.map((seg, idx) => {
    const seats = seatsArray[idx] || {};
    const prices = pricesArray[idx] || {};

    // Calculate segment duration (approximate from times)
    const depTime = seg.startTime ? formatTime(seg.startTime) : '00:00';
    const arrTime = seg.endTime ? formatTime(seg.endTime) : '00:00';

    return {
      trainNumber: seg.trainNumber,
      fromStation: seg.departureStation,
      toStation: seg.arrivalStation,
      departureTime: depTime,
      arrivalTime: arrTime,
      duration: '', // Will be calculated if needed
      seatsAvailable: seats,
      prices: prices,
      type: getTrainType(seg.trainNumber),
    };
  });

  // For merged seats: take minimum across all segments (limited by the tightest segment)
  const mergedSeats: Record<string, number> = {};
  seatsArray.forEach(seats => {
    Object.entries(seats).forEach(([key, value]) => {
      if (mergedSeats[key] === undefined || value < mergedSeats[key]) {
        mergedSeats[key] = value;
      }
    });
  });

  // For merged prices: sum across all segments
  const mergedPrices: Record<string, number> = {};
  pricesArray.forEach(prices => {
    Object.entries(prices).forEach(([key, value]) => {
      mergedPrices[key] = (mergedPrices[key] || 0) + (value || 0);
    });
  });

  const lastSegment = route.segments[route.segments.length - 1];

  return {
    id: route.planId || firstSegment.id.toString(),
    trainNumber: isTransfer
      ? `${firstSegment.trainNumber} → ${lastSegment.trainNumber}`
      : firstSegment.trainNumber,
    fromStation: firstSegment.departureStation,
    toStation: lastSegment.arrivalStation,
    departureTime: route.firstDepartureTime || formatTime(firstSegment.startTime),
    arrivalTime: route.finalArrivalTime || formatTime(lastSegment.endTime),
    duration: formatDuration(route.totalDurationMinutes),
    price: mergedPrices['二等座'] || mergedPrices['硬座'] || 0,
    type: getTrainType(firstSegment.trainNumber),
    seatsAvailable: mergedSeats,
    prices: mergedPrices,
    transferCount: route.transferCount,
    segments: ticketSegments,
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
