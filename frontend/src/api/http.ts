import axios, { type AxiosInstance, type AxiosRequestConfig } from 'axios'
import { ElMessage } from 'element-plus'
import { TOKEN_KEY } from '@/utils/constants'
import type { ApiResponse } from '@/types/document'

const instance: AxiosInstance = axios.create({
  baseURL: '/api',
  timeout: 30000,
})

// 请求注入 JWT
instance.interceptors.request.use((config) => {
  const token = localStorage.getItem(TOKEN_KEY)
  if (token) {
    config.headers.Authorization = `Bearer ${token}`
  }
  return config
})

instance.interceptors.response.use(
  (resp) => {
    const body = resp.data as ApiResponse<unknown>
    if (body && typeof body === 'object' && 'code' in body) {
      if (body.code !== 0) {
        ElMessage.error(body.message || '请求失败')
        return Promise.reject(new Error(body.message))
      }
      // 拆包：响应体只保留 data 字段
      resp.data = body.data
      return resp
    }
    return resp
  },
  (err) => {
    if (err.response?.status === 401) {
      // 登录态失效：清空 token 并跳转登录页
      localStorage.removeItem(TOKEN_KEY)
      if (!window.location.pathname.startsWith('/login')) {
        window.location.href = '/login'
      }
    } else {
      const msg =
        err.response?.data?.message || err.message || '网络错误，请稍后重试'
      ElMessage.error(msg)
    }
    return Promise.reject(err)
  },
)

// 类型化封装：直接返回后端拆包后的 data 类型
async function request<T>(config: AxiosRequestConfig): Promise<T> {
  const resp = await instance.request<ApiResponse<T>>(config)
  return resp.data as T
}

const http = {
  get: <T>(url: string, config?: AxiosRequestConfig) =>
    request<T>({ ...config, method: 'get', url }),
  post: <T>(url: string, data?: unknown, config?: AxiosRequestConfig) =>
    request<T>({ ...config, method: 'post', url, data }),
  patch: <T>(url: string, data?: unknown, config?: AxiosRequestConfig) =>
    request<T>({ ...config, method: 'patch', url, data }),
  delete: <T>(url: string, config?: AxiosRequestConfig) =>
    request<T>({ ...config, method: 'delete', url }),
}

export default http
