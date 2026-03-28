import axios, { AxiosInstance, AxiosRequestConfig, AxiosResponse } from 'axios'
import { Message } from '@arco-design/web-vue'
import { useUserStore } from '@/store/user'

// 统一响应结构
export interface Result<T = any> {
  code: number
  message: string | null
  data: T
  requestId?: string
}

// 分页响应（支持游标分页和普通分页）
export interface PageResult<T = any> {
  list: T[]
  total: number
  // 游标分页字段
  nextId?: number
  hasMore?: boolean
  // 普通分页字段
  pageNum?: number
  pageSize?: number
}

// 分页请求参数（游标分页）
export interface PageParams {
  lastId?: number
  pageSize?: number
  keyword?: string
}

// 普通分页请求参数
export interface PageParamsNormal {
  pageNum?: number
  pageSize?: number
  keyword?: string
}

// 创建axios实例
const service: AxiosInstance = axios.create({
  baseURL: 'http://localhost:8080/api',
  timeout: 15000,
  headers: {
    'Content-Type': 'application/json',
  },
})

// 请求拦截器
service.interceptors.request.use(
  (config) => {
    const userStore = useUserStore()
    if (userStore.token) {
      config.headers['Authorization'] = `Bearer ${userStore.token}`
    }
    return config
  },
  (error) => {
    return Promise.reject(error)
  }
)

// 响应拦截器
service.interceptors.response.use(
  (response: AxiosResponse<Result>) => {
    const res = response.data

    // 根据业务code判断
    if (res.code === 200 || res.code === 0) {
      return res as any
    }

    // token过期
    if (res.code === 401) {
      const userStore = useUserStore()
      userStore.logout()
      window.location.href = '/login'
      return Promise.reject(new Error('登录已过期，请重新登录'))
    }

    // 业务错误
    Message.error(res.message || '请求失败')
    return Promise.reject(new Error(res.message || '请求失败'))
  },
  (error) => {
    Message.error(error.message || '网络错误')
    return Promise.reject(error)
  }
)

// 封装请求方法
export const request = {
  get<T = any>(url: string, config?: AxiosRequestConfig): Promise<Result<T>> {
    return service.get(url, config)
  },
  post<T = any>(url: string, data?: any, config?: AxiosRequestConfig): Promise<Result<T>> {
    return service.post(url, data, config)
  },
  put<T = any>(url: string, data?: any, config?: AxiosRequestConfig): Promise<Result<T>> {
    return service.put(url, data, config)
  },
  delete<T = any>(url: string, config?: AxiosRequestConfig): Promise<Result<T>> {
    return service.delete(url, config)
  },
}

export default service
