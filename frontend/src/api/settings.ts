import http from './http'
import type { AppSettings } from '@/types/admin'

/** GET /api/admin/settings：团队名称与匿名访问开关（ADMIN） */
export function fetchAdminSettings(): Promise<AppSettings> {
  return http.get<AppSettings>('/admin/settings')
}

/** PUT /api/admin/settings：保存后立即生效（ADMIN） */
export function updateAdminSettings(payload: AppSettings): Promise<AppSettings> {
  return http.put<AppSettings>('/admin/settings', payload)
}
