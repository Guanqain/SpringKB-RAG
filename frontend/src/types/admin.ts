/** 技术分类：product 为标识，displayName 为展示名，versions 由文档动态聚合 */
export interface Category {
  product: string
  displayName: string
  documentCount: number
  versions: string[]
}

/** 成员管理列表项（ADMIN 可见） */
export interface Member {
  id: number
  username: string
  email: string | null
  role: string
  createdAt: string
}

export type Role = 'ADMIN' | 'MEMBER'

/** PATCH /api/admin/documents/{id}/meta */
export interface DocumentMetaPayload {
  product?: string
  version?: string
  visibility?: 'team' | 'private'
}

/** GET/PUT /api/admin/settings */
export interface AppSettings {
  teamName: string
  allowAnonymous: boolean
}
