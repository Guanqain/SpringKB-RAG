/** 检索链路诊断（GET /api/qa/debug）响应类型 */

export interface RagTraceStage {
  key: string
  label: string
  /** OK / PASS / BLOCKED / PARTIAL */
  status: string
  elapsedMs: number
  detail: Record<string, unknown>
}

export interface RagCandidate {
  id: string
  /** pgvector 原始余弦相似度 */
  score: number
  /** 权威性加权后的分数（official + boost） */
  boostedScore: number
  authority: string | null
  version: string | null
  title: string | null
  section: string | null
  headingPath: string | null
  docId: string | null
  /** 是否能在 kb_chunk/kb_document 回源到 */
  resolved: boolean
  preview: string
}

export interface RagDecision {
  found: boolean
  topScore: number
  threshold: number
  chatInvoked: boolean
  note: string
}

export interface RagTrace {
  traceId: string
  query: string
  version: string | null
  topK: number
  totalElapsedMs: number
  stages: RagTraceStage[]
  candidates: RagCandidate[]
  decision: RagDecision
}
