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

/** GET /api/admin/overview 全系统状态概览（只含聚合值） */
export interface AdminOverview {
  cards: Cards
  sections: Sections
  trend: TrendSeries
  todos: TodoSummary
  meta: Meta
}

export interface Cards {
  totalDocs: number
  chunks: number
  activeRate: number
  pendingReview: number
  windowQa: number
  foundRate: number
  users: number
  windowNewDocs: number
}

export interface Sections {
  content: ContentStats
  governance: GovernanceStats
  qa: QaStats
  users: UserStats
  engagement: EngagementStats
  health: HealthStats
}

export interface ContentStats {
  totalDocs: number
  activeDocs: number
  inactiveDocs: number
  chunks: number
  avgChunksPerDoc: number
  emptyChunkDocs: number
  products: ProductSlice[]
  otherProducts: OtherBucket
  origins: ProductSlice[]
  otherOrigins: OtherBucket
  docTypes: ProductSlice[]
  languages: ProductSlice[]
  versions: ProductSlice[]
  otherVersions: OtherBucket
  visibility: VisibilityStats
  freshness: FreshnessStats
  avgAgeDays: number
  todayNewDocs: number
  windowNewDocs: number
  prevWindowNewDocs: number
}

/** product 字段承载维度值：product / origin / doc_type / language / version */
export interface ProductSlice {
  product: string
  count: number
}

/** Top N 之外：count=剩余项数，total=剩余文档数之和 */
export interface OtherBucket {
  count: number
  total: number
}

export interface VisibilityStats {
  team: number
  'private': number
}

export interface FreshnessStats {
  within30Days: number
  days31To180: number
  days181To365: number
  over365Days: number
  unknown: number
}

export interface ReviewStatusSlice {
  status: string
  count: number
  /** 0~1 占比 */
  pct: number
}

export interface ScanStats {
  windowBlocks: number
  blockedTitles: number
  prevWindowBlocks: number
  hits: number
  byRule: RuleHit[]
  byAction: ActionSlice[]
}

export interface RuleHit {
  ruleType: string
  count: number
}

export interface ActionSlice {
  action: string
  count: number
}

export interface GovernanceStats {
  reviewStatus: ReviewStatusSlice[]
  /** 0~1 通过率 */
  approvalRate: number
  avgReviewHours: number
  pendingOldestDays: number
  rejectedStale: number
  scan: ScanStats
  notificationsUnread: number
}

export interface QaStats {
  conversations: number
  messages: number
  windowQa: number
  prevWindowQa: number
  /** 0~1 命中率 */
  foundRate: number
  /** 0~1 未命中率 */
  noAnswerRate: number
  avgSources: number
  activeAskers: number
  blindspotsTop: BlindSpot[]
}

/** 知识盲区：只含问题原文（截断 60 字）与次数，不含用户名 */
export interface BlindSpot {
  question: string
  count: number
}

export interface TrendSeries {
  days: number
  points: TrendPoint[]
}

export interface TrendPoint {
  date: string
  newDocs: number
  qaCount: number
}

export interface UserStats {
  total: number
  admins: number
  members: number
  newInWindow: number
  prevNewInWindow: number
  activeInWindow: number
  prevActiveInWindow: number
  topContributors: ContributorSlice[]
  roles: RoleSlice[]
}

export interface ContributorSlice {
  username: string
  count: number
}

export interface RoleSlice {
  role: string
  count: number
}

export interface EngagementStats {
  likes: number
  favorites: number
  topFavorites: FavoriteSlice[]
}

export interface FavoriteSlice {
  docId: string
  title: string
  count: number
}

export interface HealthStats {
  vectorRows: number
  /** 0~1 向量覆盖率 */
  coverageRate: number
  dbSizeBytes: number
  lastIngestedAt: string | null
  lastScanAt: string | null
  lastQaAt: string | null
}

export interface TodoSummary {
  pendingReview: number
  rejectedStale: number
  scanBlocks: number
  blindspots: number
}

export interface Meta {
  windowDays: number
  generatedAt: string
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
