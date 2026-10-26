import { defineStore } from 'pinia'
import { computed, ref } from 'vue'
import {
  fetchMe,
  login as apiLogin,
  loginByCode as apiLoginByCode,
  register as apiRegister,
  registerByEmail as apiRegisterByEmail,
} from '@/api/auth'
import { TOKEN_KEY } from '@/utils/constants'
import type { AuthResponse, AuthUser, CodeLoginResult } from '@/types/auth'

export const useAuthStore = defineStore('auth', () => {
  const token = ref<string>(localStorage.getItem(TOKEN_KEY) ?? '')
  const user = ref<AuthUser | null>(null)

  const isAuthenticated = computed(() => Boolean(token.value))
  // 后端角色为大写 ADMIN/MEMBER（坑 #29），统一归一化后比较
  const isAdmin = computed(() => user.value?.role?.toUpperCase() === 'ADMIN')

  function setAuth(resp: AuthResponse) {
    token.value = resp.token
    user.value = resp.user
    localStorage.setItem(TOKEN_KEY, resp.token)
  }

  /** Setup 初始化成功后直接写入登录态 */
  function applyAuth(resp: AuthResponse) {
    setAuth(resp)
  }

  async function login(identifier: string, password: string) {
    const resp = await apiLogin(identifier, password)
    setAuth(resp)
  }

  /**
   * 验证码登录：已注册邮箱直接写登录态；未注册时返回 needs_profile 交给页面引导补充信息，
   * 此时**不**写入登录态（token 为空），避免半登录状态。
   */
  async function loginByCode(email: string, code: string): Promise<CodeLoginResult> {
    const resp = await apiLoginByCode(email, code)
    if (resp.action === 'login' && resp.token && resp.user) {
      setAuth({ token: resp.token, user: resp.user })
    }
    return resp
  }

  /** 验证码登录发现邮箱未注册：补全用户名 + 密码后建号并登录 */
  async function registerByEmail(
    email: string,
    code: string,
    username: string,
    password: string,
  ) {
    const resp = await apiRegisterByEmail(email, code, username, password)
    setAuth(resp)
  }

  async function register(
    username: string,
    email: string,
    password: string,
    code: string,
  ) {
    const resp = await apiRegister(username, email, password, code)
    setAuth(resp)
  }

  /**
   * 刷新后恢复登录态：用本地 token 拉取当前用户。
   * 只有明确的登录态失效（HTTP 401 / 业务码 401）才清空本地登录态；
   * 网络中断、后端 5xx 等临时故障必须保留 token，否则用户会在正常操作中被莫名登出。
   */
  async function restoreSession() {
    if (!token.value) return
    try {
      user.value = await fetchMe()
    } catch (err) {
      const status = (err as { response?: { status?: number } }).response?.status
      const code = (err as { code?: number }).code
      if (status === 401 || code === 401) {
        clearAuth()
      }
    }
  }

  function clearAuth() {
    token.value = ''
    user.value = null
    localStorage.removeItem(TOKEN_KEY)
  }

  function logout() {
    clearAuth()
  }

  return {
    token,
    user,
    isAuthenticated,
    isAdmin,
    applyAuth,
    login,
    loginByCode,
    register,
    registerByEmail,
    restoreSession,
    logout,
  }
})
