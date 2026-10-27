export interface Document {
  id: string
  title: string
  source: 'official' | 'community'
  /** 技术分类（product），可自定义 */
  product: string
  version: string
  visibility: 'team' | 'private'
  author: string | null
  /** approved 已通过（含无需审核）/ pending 待审核 / rejected 已驳回 */
  reviewStatus: string
  /** 驳回理由 */
  reviewNote: string | null
  active: boolean
  chunkCount: number
  createdAt: string
  updatedAt: string
}

/** GET /api/admin/reviews 审核队列项 */
export interface ReviewItem {
  id: string
  title: string
  product: string
  version: string
  author: string | null
  reviewStatus: string
  reviewNote: string | null
  reviewedBy: string | null
  reviewedAt: string
  submittedAt: string
  chunkCount: number
}

export interface Chunk {
  id: string
  content: string
  metadata: Record<string, string>
  score?: number
}

export interface ImportRequest {
  source: 'official' | 'community'
  /** 技术分类（product）；留空后端回落 manual */
  product?: string
  version: string
  title: string
  docKey: string
  text: string
  url?: string
  soQuestionId?: string
  soScore?: string
}

export interface ImportResult {
  imported: boolean
  skipped: boolean
  docId: string
}

/** 分页入参：page 从 1 起，size 由后端钳制在 1~200 */
export interface PageQuery {
  page?: number
  size?: number
}

export interface DocumentQuery extends PageQuery {
  source?: string
  product?: string
  version?: string
  active?: string
  keyword?: string
}

/** GET /api/admin/stats 概览聚合（按 product 的分布 + 全局指标） */
export interface AdminProductCount {
  product: string
  count: number
}

export interface AdminStats {
  total: number
  active: number
  pending: number
  chunks: number
  official: number
  community: number
  products: AdminProductCount[]
}

/** GET /api/admin/reviews 查询参数（status 默认 pending） */
export interface ReviewQuery {
  status?: string
  product?: string
  author?: string
  keyword?: string
}

/** POST /api/admin/reviews/batch-approve 结果 */
export interface BatchApproveResult {
  approved: number
  failed: number
  errors: string[]
}

/** GET /api/admin/scan-rules 脱敏规则（builtin=true 时只能开关/改标签） */
export interface ScanRule {
  code: string
  label: string
  pattern: string
  builtin: boolean
  enabled: boolean
}

/** POST/PATCH /api/admin/scan-rules 请求体 */
export interface ScanRulePayload {
  label?: string
  pattern?: string
  enabled?: boolean
}

/** GET /api/admin/scan-logs 拦截留痕（不含明文） */
export interface ScanLogItem {
  id: number
  username: string | null
  action: string
  docTitle: string | null
  ruleType: string
  hitCount: number
  createdAt: string
}

export interface ApiResponse<T> {
  code: number
  message: string
  data: T
}
