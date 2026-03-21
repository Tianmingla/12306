
export interface TrainTicket {
  id: string;
  trainNumber: string;
  fromStation: string;
  toStation: string;
  departureTime: string;
  arrivalTime: string;
  duration: string;
  price: number;
  seatsAvailable: {
    business: number | string;
    first: number | string;
    second: number | string;
    standing: number | string;
  };
  type: 'G' | 'D' | 'K' | 'Z';
}

export interface ChatMessage {
  id: string;
  role: 'user' | 'model';
  text: string;
  timestamp: Date;
  isThinking?: boolean;
}

export interface Station {
  code: string;
  name: string;
  city: string;
}

export enum AppView {
  HOME = 'HOME',
  SEARCH_RESULTS = 'SEARCH_RESULTS',
}

export interface SearchParams {
  from: string;
  to: string;
  date: string;
  onlyHighSpeed: boolean;
}

export interface Passenger {
  id: string;
  name: string;
  idCard: string;
  type: 'adult' | 'student' | 'child';
}

export interface FilterOptions {
  trainTypes: string[];
  departureTime: string[]; // '0-6', '6-12', '12-18', '18-24'
}

// Backend API Interfaces
export interface ApiSegment {
  id: number;
  trainId: number;
  trainNumber: string;
  departureStation: string;
  arrivalStation: string;
  startTime: string | null;
  endTime: string | null;
  startRegion: string;
  endRegion: string;
}

export interface ApiRoute {
  planId: string | null;
  transferCount: number;
  totalDurationMinutes: number;
  firstDepartureTime: string | null;
  finalArrivalTime: string | null;
  remainingTicketNumMap: Record<string, number>;
  segments: ApiSegment[];
}

export interface ApiResponse {
  code: number;
  message: string | null;
  data: ApiRoute[];
  requestId: string;
}

export interface PurchaseTicketRequest {
  account: string;
  IDCardCodelist: number[];
  seatTypelist: string[];
  chooseSeats?: string[];
  trainNum: string;
  startStation: string;
  endStation: string;
  date: string;
}

export interface PurchaseTicketResponse {
  code: number;
  message: string | null;
  data: {
    status: string;
    orderSn: string;
    ticketDO: any; // TODO: define TicketDO type if needed
  };
  requestId: string;
}

export interface TrainStationDetail {
  stationName: string;
  arrivalTime: string | null;
  departureTime: string | null;
  stopoverTime: number | null;
}

export interface TrainRouteDetailsResponse {
  code: number;
  message: string | null;
  data: TrainStationDetail[];
  requestId: string;
}

export interface LoginRequest {
  username: string;
  password?: string; // Optional if using other methods
}

export interface LoginResponse {
  token: string;
}

export interface UserInfoResponse {
  username: string;
  role: string;
  // Add other user fields as needed
}
