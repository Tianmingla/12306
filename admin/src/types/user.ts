// 用户相关类型定义

// 用户信息
export interface User {
  id: number
  username: string
  phone: string
  email?: string
  status: UserStatus
  createTime: string
  updateTime: string
}

// 用户状态
export enum UserStatus {
  NORMAL = 0,    // 正常
  DISABLED = 1,  // 禁用
}

// 乘车人信息
export interface Passenger {
  id: number
  userId: number
  realName: string
  idCardType: IdCardType
  idCardNumber: string
  passengerType: PassengerType
  phone?: string
  createTime: string
  updateTime: string
}

// 证件类型
export enum IdCardType {
  ID_CARD = 0,        // 身份证
  PASSPORT = 1,       // 护照
  HK_MACAU_PASS = 2,  // 港澳通行证
  TAIWAN_PASS = 3,    // 台湾通行证
}

// 乘客类型
export enum PassengerType {
  ADULT = 0,      // 成人
  CHILD = 1,      // 儿童
  STUDENT = 2,    // 学生
  DISABILITY = 3, // 残疾军人
}

// 登录请求
export interface LoginRequest {
  username: string
  password: string
}

// 登录响应
export interface LoginResponse {
  token: string
  userInfo: User
}

// 用户查询参数
export interface UserQueryParams {
  pageNum?: number
  pageSize?: number
  keyword?: string
  status?: UserStatus
  startTime?: string
  endTime?: string
}
