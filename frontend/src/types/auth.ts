export interface AuthUser {
  id: number
  username: string
  role: string
}

export interface AuthResponse {
  token: string
  user: AuthUser
}
