
import { SearchParams, TrainTicket, ApiResponse, ApiRoute } from '../types';

const API_BASE_URL = 'http://localhost:8080/api';

/**
 * Helper to format ISO date string to HH:mm
 */
const formatTime = (isoString: string | null): string => {
  if (!isoString) return '--:--';
  try {
    const date = new Date(isoString);
    return date.toLocaleTimeString('en-GB', { hour12: false, hour: '2-digit', minute: '2-digit' }); // Use en-GB for HH:mm format
  } catch (e) {
    return '--:--';
  }
};

/**
 * Adapts backend route data to frontend TrainTicket model
 */
const adaptRouteToTicket = (route: ApiRoute): TrainTicket | null => {
  // We currently only handle direct trains (single segment) or display the first segment's info for simplicity
  const segment = route.segments[0];
  if (!segment) return null;

  const seats = route.remainingTicketNumMap || {};

  // Mock price logic based on train type
  // Backend data doesn't provide price yet, so we estimate based on type
  const isHighSpeed = segment.trainNumber.startsWith('G') || segment.trainNumber.startsWith('D');
  const basePrice = isHighSpeed ? 553 : 156; 
  // Add some randomness so it looks realistic but consistent enough for demo
  const mockPrice = basePrice + (segment.id % 50); 

  // Format Duration
  const h = Math.floor(route.totalDurationMinutes / 60);
  const m = route.totalDurationMinutes % 60;
  const durationStr = `${h}小时${m}分`;

  return {
    id: segment.id.toString(),
    trainNumber: segment.trainNumber,
    fromStation: segment.departureStation,
    toStation: segment.arrivalStation,
    // Use the formatted time string directly from API route object if available
    departureTime: route.firstDepartureTime || formatTime(segment.startTime),
    arrivalTime: route.finalArrivalTime || formatTime(segment.endTime),
    duration: durationStr,
    price: mockPrice,
    type: segment.trainNumber.startsWith('G') ? 'G' : 
          segment.trainNumber.startsWith('D') ? 'D' : 
          segment.trainNumber.startsWith('Z') ? 'Z' : 'K',
    seatsAvailable: {
      business: seats['商务座'] ?? 0,
      first: seats['一等座'] ?? seats['软卧'] ?? 0, // Fallback Soft Sleeper to First class slot for display
      second: seats['二等座'] ?? seats['硬卧'] ?? seats['硬座'] ?? 0, // Fallback others to Second class slot
      standing: seats['无座'] ?? 0,
    }
  };
};

export const searchTickets = async (params: SearchParams): Promise<TrainTicket[]> => {
  try {
    const queryParams = new URLSearchParams({
      from: params.from,
      to: params.to,
      date: params.date,
    });

    const response = await fetch(`${API_BASE_URL}/ticket/search?${queryParams.toString()}`);
    
    if (!response.ok) {
      throw new Error(`API Error: ${response.statusText}`);
    }

    const json: ApiResponse = await response.json();

    if (json.code !== 200) {
      throw new Error(json.message || 'Unknown API error');
    }

    // Transform data
    const validTickets = json.data
      .map(adaptRouteToTicket)
      .filter((t): t is TrainTicket => t !== null);

    // Filter high speed if requested
    if (params.onlyHighSpeed) {
      return validTickets.filter(t => t.type === 'G' || t.type === 'D');
    }

    return validTickets;

  } catch (error) {
    console.error("Failed to fetch tickets:", error);
    // Return empty array or throw depending on how you want UI to handle it
    throw error;
  }
};
