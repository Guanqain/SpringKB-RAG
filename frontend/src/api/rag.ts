import http from './http'
import type { RagTrace } from '@/types/rag'

/**
 * 检索链路诊断（GET /api/qa/debug）：只跑检索侧，不调用 Chat 模型。
 * version 为空表示不按版本过滤。
 */
export function fetchRagTrace(
  q: string,
  version?: string,
  topK = 5,
): Promise<RagTrace> {
  return http.get<RagTrace>('/qa/debug', {
    params: { q, version: version || undefined, topK },
  })
}
