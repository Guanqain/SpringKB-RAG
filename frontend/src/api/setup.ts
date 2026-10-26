import http from './http'
import type { AuthResponse } from '@/types/auth'
import type { SetupInitPayload, SetupStatus } from '@/types/setup'

/** GET /api/setup/status：是否已初始化、团队信息、匿名访问开关 */
export function fetchSetupStatus(): Promise<SetupStatus> {
  return http.get<SetupStatus>('/setup/status')
}

/** POST /api/setup/init：仅在无用户时有效，成功后返回 JWT 并自动登录 */
export function initSetup(payload: SetupInitPayload): Promise<AuthResponse> {
  return http.post<AuthResponse>('/setup/init', payload)
}
