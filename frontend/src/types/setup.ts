/** GET /api/setup/status */
export interface SetupStatus {
  initialized: boolean
  userCount: number
  teamName: string
  allowAnonymous: boolean
}

/** POST /api/setup/init */
export interface SetupInitPayload {
  teamName: string
  adminUsername: string
  adminEmail: string
  adminPassword: string
  code: string
  allowAnonymous: boolean
}
