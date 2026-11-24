import axios from 'axios'
import { ElMessage } from 'element-plus'

/** 登录态存在这两个 key 里，和参考项目（ai-vue）一致 */
export const TOKEN_KEY = 'token'
export const USER_KEY = 'userInfo'

export const http = axios.create({ baseURL: '/api', timeout: 120000 })

/**
 * 请求拦截器：每个请求自动带上 token。
 * 后端 AuthFilter 读的就是请求头里的 token，读不到或过期就返回 401。
 */
http.interceptors.request.use((config) => {
  const token = localStorage.getItem(TOKEN_KEY)
  if (token) {
    config.headers.set(TOKEN_KEY, token)
  }
  return config
})

/**
 * 响应拦截器：只做两件事
 * 1. 401（登录过期）→ 清掉本地登录态，跳登录页
 * 2. 其它错误 → 弹一句人话
 *
 * 登录/注册接口的 401 是「密码错」，属于业务失败，不能当成过期处理，
 * 否则用户输错密码会被"跳转到登录页"，看起来像页面抽风。
 */
http.interceptors.response.use(
  (response) => response,
  (error) => {
    const status = error.response?.status
    const url: string = error.config?.url ?? ''
    const isAuthEntry = url.includes('/auth/login') || url.includes('/auth/register')

    if (status === 401 && !isAuthEntry) {
      localStorage.removeItem(TOKEN_KEY)
      localStorage.removeItem(USER_KEY)
      ElMessage.error('登录已过期，请重新登录')
      if (!window.location.pathname.includes('/auth/login')) {
        window.location.href = '/app/auth/login'
      }
      return Promise.reject(error)
    }

    const data = error.response?.data
    if (status === 429) {
      const retry = error.response.headers?.['retry-after'] || data?.retryAfterSeconds
      ElMessage.error(`请求过于频繁，请 ${retry || '几'} 秒后重试`)
    } else if (data?.msg || data?.error) {
      ElMessage.error(data.msg || data.error)
    } else if (error.code === 'ECONNABORTED') {
      ElMessage.error('请求超时，请稍后重试')
    } else {
      ElMessage.error(error.message || '请求失败')
    }
    return Promise.reject(error)
  }
)
