import axios, { AxiosError } from 'axios'
import { ElMessage } from 'element-plus'

const request = axios.create({
  baseURL: import.meta.env.VITE_API_BASE_URL || '',
  timeout: 30000
})

request.interceptors.request.use((config) => {
  config.headers = config.headers || {}
  config.headers['X-Tenant-Id'] = import.meta.env.VITE_TENANT_ID || 'default'
  return config
})

request.interceptors.response.use(
  (response) => {
    const data = response.data
    if (data && typeof data.code !== 'undefined') {
      if (data.code === 0) {
        return data.data
      }
      ElMessage.error(data.message || '请求失败')
      return Promise.reject(new Error(data.message || '请求失败'))
    }
    return data
  },
  (error: AxiosError) => {
    ElMessage.error(error.message || '网络异常')
    return Promise.reject(error)
  }
)

export default request