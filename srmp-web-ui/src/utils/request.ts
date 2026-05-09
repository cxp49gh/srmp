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
        const msg = data.message || '请求失败'
        ElMessage.error(msg)
        const err = new Error(msg) as Error & { biz?: typeof data }
        err.biz = data
        return Promise.reject(err)
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

const request = createRequest(Number(import.meta.env.VITE_API_TIMEOUT || 60000))

// AI / Outline 同步 / 向量化这类长任务的前端等待时间必须大于后端 read-timeout，
// 否则后端仍在生成，浏览器侧已经 ECONNABORTED。
export const aiRequest = createRequest(Number(import.meta.env.VITE_AI_TIMEOUT || 300000))
export const longRequest = createRequest(Number(import.meta.env.VITE_LONG_TIMEOUT || 300000))
export default request
