import http from './http'
import type {
  AuthResponse,
  AuthUser,
  CodeLoginResult,
  EmailCodeResult,
  EmailCodePurpose,
} from '@/types/auth'

/** 登录：identifier 可为用户名或邮箱 */
export function login(identifier: string, password: string): Promise<AuthResponse> {
  return http.post('/auth/login', { username: identifier, password })
}

/** 注册：邮箱 + 验证码必填 */
export function register(
  username: string,
  email: string,
  password: string,
  code: string,
): Promise<AuthResponse> {
  return http.post('/auth/register', { username, email, password, code })
}

/** 发送邮箱验证码（注册 / 登录 / 绑定邮箱） */
export function sendEmailCode(
  email: string,
  purpose: EmailCodePurpose,
): Promise<EmailCodeResult> {
  return http.post<EmailCodeResult>('/auth/email-code', { email, purpose })
}

/** 验证码登录（免密）；邮箱未注册时返回 action='needs_profile' */
export function loginByCode(email: string, code: string): Promise<CodeLoginResult> {
  return http.post<CodeLoginResult>('/auth/login/code', { email, code })
}

/** 验证码登录发现邮箱未注册时，补全用户名与密码直接建号并登录 */
export function registerByEmail(
  email: string,
  code: string,
  username: string,
  password: string,
): Promise<AuthResponse> {
  return http.post<AuthResponse>('/auth/register/email', { email, code, username, password })
}

export function fetchMe(): Promise<AuthUser> {
  return http.get('/auth/me')
}

/** 忘记密码：发送重置验证码（后端对未注册邮箱返回一致响应，避免账号探测） */
export function requestPasswordCode(email: string): Promise<EmailCodeResult> {
  return http.post<EmailCodeResult>('/auth/password/code', { email })
}

/** 忘记密码：用邮箱验证码设置新密码 */
export function resetPassword(email: string, code: string, newPassword: string): Promise<null> {
  return http.post('/auth/password/reset', { email, code, newPassword })
}
