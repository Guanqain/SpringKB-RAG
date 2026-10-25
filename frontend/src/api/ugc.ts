import http from './http'
import type { UgcPublishResult } from '@/types/kb'

export interface UgcPublishPayload {
  title: string
  markdown: string
  version?: string
  section?: string
}

export function publishUgcDocument(
  payload: UgcPublishPayload,
): Promise<UgcPublishResult> {
  return http.post<UgcPublishResult>('/ugc/documents', payload)
}