import { http } from './http'

/** userType 沿用参考项目的约定：1 = 普通用户，2 = 管理员 */
export interface UserInfo {
  id: string
  username: string
  displayName: string
  role: 'ADMIN' | 'USER'
  userType: 1 | 2
  mustChangePassword: boolean
}

export interface LoginData {
  token: string
  userInfo: UserInfo
}

/** 验证码：image 是可以直接放进 img src 的 data URL，答案在服务端，前端拿不到 */
export interface CaptchaIssue {
  captchaId: string
  image: string
  expireSeconds: number
}

/** 后端统一返回 {code,msg,data}，这里把 data 掏出来给上层 */
interface Wrapped<T> {
  code: string
  msg: string
  data: T
}

export async function fetchCaptcha(): Promise<CaptchaIssue> {
  const { data } = await http.get<Wrapped<CaptchaIssue>>('/auth/captcha')
  return data.data
}

export async function login(
  username: string,
  password: string,
  captchaId: string,
  captchaCode: string
): Promise<LoginData> {
  const { data } = await http.post<Wrapped<LoginData>>('/auth/login', {
    username,
    password,
    captchaId,
    captchaCode
  })
  return data.data
}

export async function register(username: string, password: string, displayName?: string): Promise<LoginData> {
  const { data } = await http.post<Wrapped<LoginData>>('/auth/register', { username, password, displayName })
  return data.data
}

export async function logout(): Promise<void> {
  await http.post('/auth/logout')
}

export async function fetchMe(): Promise<UserInfo> {
  const { data } = await http.get<Wrapped<{ userInfo: UserInfo }>>('/auth/me')
  return data.data.userInfo
}

export async function changePassword(oldPassword: string, newPassword: string): Promise<UserInfo> {
  const { data } = await http.post<Wrapped<{ userInfo: UserInfo }>>('/auth/password', { oldPassword, newPassword })
  return data.data.userInfo
}
