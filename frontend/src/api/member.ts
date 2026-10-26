import http from './http'
import type { Member, Role } from '@/types/admin'

/** GET /api/admin/members */
export function fetchMembers(): Promise<Member[]> {
  return http.get<Member[]>('/admin/members')
}

/** PATCH /api/admin/members/{id}/role：ADMIN / MEMBER */
export function updateMemberRole(id: number, role: Role): Promise<Member> {
  return http.patch<Member>(`/admin/members/${id}/role`, { role })
}
