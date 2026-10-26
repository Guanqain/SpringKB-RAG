export interface AuthUser {
  id: number
  username: string
  role: string
  /** 注册后绑定邮箱（老数据可能为空） */
  email: string | null
}

export interface AuthResponse {
  token: string
  user: AuthUser
}

export type EmailCodePurpose = 'register' | 'login' | 'bind' | 'reset'

/** POST /api/auth/email-code；dev/test 环境回显 devCode */
export interface EmailCodeResult {
  expiresIn: number
  cooldown: number
  devCode?: string | null
}

/**
 * POST /api/auth/login/code 的结果：
 * - action='login'：token/user 有效，直接写入登录态
 * - action='needs_profile'：邮箱验证通过但未注册，前端引导补充用户名 + 密码建号
 */
export interface CodeLoginResult {
  action: 'login' | 'needs_profile'
  email: string
  token: string | null
  user: AuthUser | null
}
