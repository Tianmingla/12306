
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
  ORDER_DETAIL = 'ORDER_DETAIL',
  ORDER_HISTORY = 'ORDER_HISTORY',
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

/** 与 user-service PassengerVO 一致 */
export interface PassengerApi {
  id: number;
  realName: string;
  idCardType: number;
  idCardNumber: string;
  passengerType: number;
  phone?: string | null;
}

export interface PassengerSavePayload {
  realName: string;
  idCardType: number;
  idCardNumber: string;
  passengerType: number;
  phone?: string;
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
  /** 登录账号手机号 */
  account: string;
  /** 乘车人主键 id 列表（与 user-service 一致） */
  IDCardCodelist: number[];
  seatTypelist: string[];
  chooseSeats?: string[];
  trainNum: string;
  startStation: string;
  endStation: string;
  date: string;
}

export interface TicketDTO {
  account?: string;
  trainNum?: string;
  startStation?: string;
  endStation?: string;
  date?: string;
  items?: Array<{
    passengerId: number;
    seatNum: string;
    seatType: number;
    carriageNum: string;
  }>;
}

export interface PurchaseTicketResponse {
  code: number;
  message: string | null;
  data: {
    status: string;
    orderSn: string;
    totalAmount?: string | number | null;
    ticketDTO?: TicketDTO | null;
  };
  requestId: string;
}

export interface OrderItemVO {
  id: number;
  passengerId: number | null;
  passengerName: string | null;
  idCardMasked: string;
  carriageNumber: string | null;
  seatNumber: string | null;
  seatType: number | null;
  amount: string | number | null;
}

export interface OrderDetailVO {
  orderSn: string;
  username: string;
  trainNumber: string;
  startStation: string;
  endStation: string;
  runDate: string | null;
  totalAmount: string | number | null;
  status: number;
  statusText: string;
  items: OrderItemVO[];
}

export interface PayOrderVO {
  orderSn: string;
  payFormHtml: string | null;
  hint?: string | null;
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
  phone: string;
  smsCode: string;
}

export interface LoginResponse {
  token: string;
  phone?: string;
  userId?: string;
}

export interface UserInfoResponse {
  phone: string;
  userId: string;
  role: string;
}

export interface OrderListVO {
  orderSn: string;
  trainNumber: string;
  startStation: string;
  endStation: string;
  runDate: string | null;
  totalAmount: string | number | null;
  status: number;
  statusText: string;
  passengerCount: number;
}
