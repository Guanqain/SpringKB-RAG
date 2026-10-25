import http from './http'
import type { KbDocCard, KbDocDetail, KbOverview, KbTreeNode, PageResult } from '@/types/kb'

export function fetchKbOverview(): Promise<KbOverview> {
  return http.get<KbOverview>('/kb/overview')
}

export function fetchKbTree(): Promise<KbTreeNode[]> {
  return http.get<KbTreeNode[]>('/kb/tree')
}

export function fetchKbDocs(params?: {
  product?: string
  version?: string
  source?: string
  keyword?: string
  page?: number
  size?: number
}): Promise<PageResult<KbDocCard>> {
  return http.get<PageResult<KbDocCard>>('/kb/docs', { params })
}

export function fetchKbDoc(id: string): Promise<KbDocDetail> {
  return http.get<KbDocDetail>(`/kb/doc/${id}`)
}