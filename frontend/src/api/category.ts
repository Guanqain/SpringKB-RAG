import http from './http'
import type { Category } from '@/types/admin'

/** GET /api/kb/categories：知识库技术分类（公开，含各自版本） */
export function fetchKbCategories(): Promise<Category[]> {
  return http.get<Category[]>('/kb/categories')
}

/** GET /api/admin/categories：管理端技术分类（含 private 与未激活文档） */
export function fetchAdminCategories(): Promise<Category[]> {
  return http.get<Category[]>('/admin/categories')
}

/** POST /api/admin/categories：新增技术分类（product 为标识，displayName 为展示名） */
export function createCategory(product: string, displayName: string): Promise<void> {
  return http.post('/admin/categories', { product, displayName })
}
