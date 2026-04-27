import axios, { AxiosError, AxiosInstance, InternalAxiosRequestConfig } from 'axios'
import { ElMessage } from 'element-plus'

function createRequest(timeout: number): AxiosInstance {
  const instance = axios.create({
    baseURL: import.meta.env.VITE_API_BASE_URL || '',
    timeout
  })

  instance.interceptors.request.use((config: InternalAxiosRequestConfig) => {
    config.headers = config.headers || {}
    const tenantId = import.meta.env.VITE_TENANT_ID || 'default'
    if (typeof (config.headers as any).set === 'function') {
      ;(config.headers as any).set('X-Tenant-Id', tenantId)
    } else {
      ;(config.headers as any)['X-Tenant-Id'] = tenantId
    }
    return config
  })

  instance.interceptors.response.use(
    (response) => {
      const data = response.data
      if (data && typeof data.code !== 'undefined') {
        if (data.code === 0) return data.data
        ElMessage.error(data.message || '请求失败')
        return Promise.reject(new Error(data.message || '请求失败'))
      }
      return data
    },
    (error: AxiosError) => {
      const message = error.code === 'ECONNABORTED'
        ? '请求超时，请稍后在 AI 调用监控中查看 trace 日志'
        : (error.message || '网络异常')
      ElMessage.error(message)
      return Promise.reject(error)
    }
  )
  return instance
}

const request = createRequest(30000)
export const aiRequest = createRequest(Number(import.meta.env.VITE_AI_TIMEOUT || 120000))
export default request
