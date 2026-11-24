import { defineStore } from 'pinia'
import { login as loginApi, logout as logoutApi, register as registerApi, type UserInfo } from '@/api/auth'
import { TOKEN_KEY, USER_KEY } from '@/api/http'

/**
 * 登录态。
 *
 * 数据来源分两层，别混：
 *   localStorage  → 刷新页面后还在（持久层）
 *   Pinia state   → 页面运行期间读写它（响应层）
 *
 * 初始化时直接从 localStorage 读，所以不需要启动时请求 /auth/me 做"水合"，
 * 刷新页面不会出现"先跳登录页再跳回来"的闪烁。
 */
function readUserInfo(): UserInfo | null {
  try {
    return JSON.parse(localStorage.getItem(USER_KEY) || 'null')
  } catch {
    return null
  }
}

export const useAuthStore = defineStore('auth', {
  state: () => ({
    token: localStorage.getItem(TOKEN_KEY) || '',
    userInfo: readUserInfo(),
    loading: false
  }),

  getters: {
    isLoggedIn: (state) => !!state.token && !!state.userInfo,
    isAdmin: (state) => state.userInfo?.userType === 2,
    displayName: (state) => state.userInfo?.displayName || state.userInfo?.username || '',
    /** 登录后去哪：管理员进后台，普通用户进对话 */
    homePath: (state) => (state.userInfo?.userType === 2 ? '/admin/knowledge' : '/chat')
  },

  actions: {
    /** 登录成功：token 和 userInfo 都落到 localStorage。第 3、4 个参数是验证码 */
    async signIn(username: string, password: string, captchaId: string, captchaCode: string) {
      this.loading = true
      try {
        const data = await loginApi(username, password, captchaId, captchaCode)
        localStorage.setItem(TOKEN_KEY, data.token)
        localStorage.setItem(USER_KEY, JSON.stringify(data.userInfo))
        this.token = data.token
        this.userInfo = data.userInfo
        return data.userInfo
      } finally {
        this.loading = false
      }
    },

    /** 注册成功：后端直接返回 token，等于注册完就登录了 */
    async signUp(username: string, password: string, displayName?: string) {
      this.loading = true
      try {
        const data = await registerApi(username, password, displayName)
        localStorage.setItem(TOKEN_KEY, data.token)
        localStorage.setItem(USER_KEY, JSON.stringify(data.userInfo))
        this.token = data.token
        this.userInfo = data.userInfo
        return data.userInfo
      } finally {
        this.loading = false
      }
    },

    async signOut() {
      try {
        await logoutApi()
      } catch {
        /* token 已失效也照常清本地 */
      }
      this.clear()
    },

    clear() {
      localStorage.removeItem(TOKEN_KEY)
      localStorage.removeItem(USER_KEY)
      this.token = ''
      this.userInfo = null
    },

    setUserInfo(info: UserInfo) {
      localStorage.setItem(USER_KEY, JSON.stringify(info))
      this.userInfo = info
    }
  }
})
