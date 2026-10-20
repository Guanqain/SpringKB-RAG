import http from './http'
import type { QaResult } from '@/types/qa'

/** 提问（POST /api/qa/ask）：topK 走后端默认 5；version 为空时不传 */
export function ask(question: string, version?: string): Promise<QaResult> {
  return http.post<QaResult>('/qa/ask', {
    question,
    version: version || undefined,
  })
}

/** active 文档去重版本列表（GET /api/qa/versions，公开） */
export function fetchVersions(): Promise<string[]> {
  return http.get<string[]>('/qa/versions')
}
