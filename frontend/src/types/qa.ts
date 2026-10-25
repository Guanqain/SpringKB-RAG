/** 与后端 rag.SourceRef record 一一对应的引用溯源条目 */
export interface SourceRef {
  /** 1..n，与答案中的 [n] 编号一一对应 */
  index: number
  /** 权威性：official / community */
  source: 'official' | 'community'
  version: string
  title: string
  section: string
  headingPath: string
  score: number
  /** 文档级聚合后的文档主键 */
  docId: string
  /** 原文外链；无法可靠重建时为 null（前端据此隐藏「打开原文」） */
  originalUrl: string | null
  /** 原文片段（后端已截断 ≤800 字符） */
  snippet: string
}

/** 与后端 rag.QaResult record 对应：found=false 时 answer 为兜底文案、sources 为空 */
export interface QaResult {
  found: boolean
  answer: string
  sources: SourceRef[]
}

/** 问答页会话历史条目（仅内存，不持久化） */
export interface QaHistoryEntry {
  id: number
  question: string
  version?: string
  result: QaResult
  at: number
}
