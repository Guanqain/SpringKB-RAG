import http from './http'
import type { Category, DocumentMetaPayload } from '@/types/admin'
import type { PageResult } from '@/types/kb'
import type {
  AdminStats,
  BatchApproveResult,
  Chunk,
  Document,
  DocumentQuery,
  ImportRequest,
  ImportResult,
  ReviewItem,
  ReviewQuery,
  ScanLogItem,
  ScanRule,
  ScanRulePayload,
} from '@/types/document'

export function listDocuments(
  params?: DocumentQuery,
): Promise<PageResult<Document>> {
  return http.get<PageResult<Document>>('/admin/documents', { params })
}

/** 概览聚合：总文档 / 启用 / 待审 / chunk 数 / 来源与 product 分布 */
export function fetchAdminStats(): Promise<AdminStats> {
  return http.get<AdminStats>('/admin/stats')
}

export function importDocument(payload: ImportRequest): Promise<ImportResult> {
  return http.post('/admin/documents/import', payload)
}

export function setDocumentActive(
  docId: string,
  active: boolean,
): Promise<Document> {
  return http.patch(`/admin/documents/${docId}/active`, { active })
}

export function deleteDocument(docId: string): Promise<null> {
  return http.delete(`/admin/documents/${docId}`)
}

export function getDocumentChunks(docId: string): Promise<Chunk[]> {
  return http.get(`/admin/documents/${docId}/chunks`)
}

/** 编辑文档元数据：技术分类 / 版本 / 可见性（同步向量 metadata） */
export function updateDocumentMeta(
  docId: string,
  payload: DocumentMetaPayload,
): Promise<Document> {
  return http.patch<Document>(`/admin/documents/${docId}/meta`, payload)
}

/** 技术分类列表（管理端，含 private 与未激活文档） */
export function listAdminCategories(): Promise<Category[]> {
  return http.get<Category[]>('/admin/categories')
}

export function createAdminCategory(product: string, displayName: string): Promise<void> {
  return http.post('/admin/categories', { product, displayName })
}

/** 内容审核队列（status 默认 pending，可选 approved / rejected；支持分类/作者/关键词筛选） */
export function listReviews(params?: ReviewQuery): Promise<ReviewItem[]> {
  return http.get<ReviewItem[]>('/admin/reviews', { params })
}

/** 批量通过：驳回仍需逐条填写理由，失败项按 docId 返回原因 */
export function batchApproveReviews(ids: string[]): Promise<BatchApproveResult> {
  return http.post<BatchApproveResult>('/admin/reviews/batch-approve', { ids })
}

/** 审核通过：文档进入知识库并可被问答引用 */
export function approveReview(docId: string, note?: string): Promise<null> {
  return http.post(`/admin/reviews/${docId}/approve`, { note: note ?? '' })
}

/** 审核驳回：必须填写理由（作者可见） */
export function rejectReview(docId: string, note: string): Promise<null> {
  return http.post(`/admin/reviews/${docId}/reject`, { note })
}

// ---------- 脱敏规则与拦截留痕 ----------

/** 脱敏规则列表（内置 + 自定义） */
export function listScanRules(): Promise<ScanRule[]> {
  return http.get<ScanRule[]>('/admin/scan-rules')
}

/** 新增自定义规则（如工号 / 内网 IP / 内部域名） */
export function createScanRule(payload: ScanRulePayload): Promise<ScanRule> {
  return http.post<ScanRule>('/admin/scan-rules', payload)
}

/** 修改规则：开关 / 标签（自定义规则还可改正则） */
export function updateScanRule(
  code: string,
  payload: ScanRulePayload,
): Promise<ScanRule> {
  return http.patch<ScanRule>(`/admin/scan-rules/${code}`, payload)
}

/** 删除自定义规则（内置规则只能关闭） */
export function deleteScanRule(code: string): Promise<void> {
  return http.delete<void>(`/admin/scan-rules/${code}`)
}

/** 脱敏拦截留痕（只含规则类型与命中数量，不含明文） */
export function listScanLogs(limit = 100): Promise<ScanLogItem[]> {
  return http.get<ScanLogItem[]>('/admin/scan-logs', { params: { limit } })
}
