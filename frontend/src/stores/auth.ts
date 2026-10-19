import { defineStore } from 'pinia'
import { computed, ref } from 'vue'
import { fetchMe, login as apiLogin, register as apiRegister } from '@/api/auth'
import { TOKEN_KEY } from '@/utils/constants'
import type { AuthResponse, AuthUser } from '@/types/auth'

export const useAuthStore = defineStore('auth', () => {
  const token = ref<string>(localStorage.getItem(TOKEN_KEY) ?? '')
  const user = ref<AuthUser | null>(null)

  const isAuthenticated = computed(() => Boolean(token.value))

  function setAuth(resp: AuthResponse) {
    token.value = resp.token
    user.value = resp.user
    localStorage.setItem(TOKEN_KEY, resp.token)
  }

  async function login(username: string, password: string) {
    const resp = await apiLogin(username, password)
    setAuth(resp)
  }

  async function register(username: string, password: string) {
    const resp = await apiRegister(username, password)
    setAuth(resp)
  }

  /** 刷新后恢复登录态：用本地 token 拉取当前用户 */
  async function restoreSession() {
    if (!token.value) return
    try {
      user.value = await fetchMe()
    } catch {
      clearAuth()
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
    login,
    register,
    restoreSession,
    logout,
  }
})
