import http from './http'
import type { AuthResponse, AuthUser } from '@/types/auth'

export function login(username: string, password: string): Promise<AuthResponse> {
  return http.post('/auth/login', { username, password })
}

export function register(username: string, password: string): Promise<AuthResponse> {
  return http.post('/auth/register', { username, password })
}

export function fetchMe(): Promise<AuthUser> {
  return http.get('/auth/me')
}
