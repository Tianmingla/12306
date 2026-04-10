
export interface TrainTicket {
  id: string;
  trainNumber: string;
  fromStation: string;
  toStation: string;
  departureTime: string;
  arrivalTime: string;
  duration: string;
  price: number;
  seatsAvailable: Record<string, number>;
  prices?: Record<string, number>;
  type: 'G' | 'D' | 'K' | 'Z';
  /** 中转次数，0 = 直达 */
  transferCount?: number;
  /** 多段行程详情（中转时有值） */
  segments?: TicketSegment[];
}

/** 单段行程信息 */
export interface TicketSegment {
  trainNumber: string;
  fromStation: string;
  toStation: string;
  departureTime: string;
  arrivalTime: string;
  duration: string;
  seatsAvailable: Record<string, number>;
  prices: Record<string, number>;
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
  STATION_SCREEN = 'STATION_SCREEN',
  WAITLIST = 'WAITLIST',
  STATION_GUIDE = 'STATION_GUIDE',
  TRAVEL_GUIDE = 'TRAVEL_GUIDE',
}

export interface SearchParams {
  from: string;
  to: string;
  date: string;
  onlyHighSpeed: boolean;
  // 往返相关
  searchType: 'oneWay' | 'roundTrip' | 'transfer';
  returnDate?: string; // 返程日期（往返时使用）
  midStation?: string; // 中转站（中转时使用）
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
  /** 每段的余票 Map，索引对应 segments */
  remainingTicketNumMap: Record<string, number>[];
  /** 每段的票价 Map，索引对应 segments */
  priceMap: Record<string, number>[];
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
  seatType: string|number | null;
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

// ============= 车站大屏相关类型 =============

export interface StationScreenTrain {
  trainNumber: string;
  trainType: number;
  trainTypeName: string;
  terminalStation: string;
  departureTime: string;
  estimatedDepartureTime: string | null;
  delayStatus: number;
  delayStatusText: string;
  delayMinutes: number;
  checkInStatus: number;
  checkInStatusText: string;
  waitingRoom: string;
  checkInGate: string;
  platform: string;
  remainingTimeDesc: string;
}

export interface StationScreenResponse {
  stationId: number;
  stationName: string;
  stationCode: string;
  currentTime: string;
  currentDate: string;
  totalTrainsToday: number;
  onTimeRate: number;
  trains: StationScreenTrain[];
  announcements: string[];
}

// ============= 候补购票相关类型 =============

export interface WaitlistCreateRequest {
  trainNumber: string;
  startStation: string;
  endStation: string;
  travelDate: string;
  seatTypes: number[];
  passengerIds: number[];
  prepayAmount: number;
  deadline: string;
}

export interface WaitlistOrderVO {
  id: number;
  waitlistSn: string;
  trainNumber: string;
  startStation: string;
  endStation: string;
  travelDate: string;
  seatTypesText: string;
  prepayAmount: number;
  deadline: string;
  status: number;
  statusText: string;
  fulfilledOrderSn: string | null;
  createTime: string;
  queuePosition: number;
  successRate: number;
}
