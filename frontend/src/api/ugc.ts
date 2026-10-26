import http from './http'
import type { ScanResult, UgcPublishResult } from '@/types/kb'

export interface UgcPublishPayload {
  title: string
  markdown: string
  /** 技术分类（product），可自定义 */
  product?: string
  version?: string
  section?: string
  visibility?: 'team' | 'private'
}

/** 发布 Markdown 文档 */
export function publishUgcDocument(
  payload: UgcPublishPayload,
): Promise<UgcPublishResult> {
  return http.post<UgcPublishResult>('/ugc/documents', payload)
}

export interface UgcUploadMeta {
  title?: string
  product?: string
  version?: string
  section?: string
  visibility?: 'team' | 'private'
}

/**
 * 上传 .txt / .md 文件转为文档（≤10MB）。
 * multipart 字段与后端 UgcController#upload 的 @RequestParam 一一对应。
 */
export function uploadUgcDocument(
  file: File,
  meta: UgcUploadMeta,
): Promise<UgcPublishResult> {
  const form = new FormData()
  form.append('file', file)
  if (meta.title) form.append('title', meta.title)
  if (meta.product) form.append('product', meta.product)
  if (meta.version) form.append('version', meta.version)
  if (meta.section) form.append('section', meta.section)
  if (meta.visibility) form.append('visibility', meta.visibility)

  return http.post<UgcPublishResult>('/ugc/documents/upload', form, {
    headers: { 'Content-Type': 'multipart/form-data' },
    timeout: 60000,
  })
}

/** 发布前脱敏预检：只扫描不落库；私有文档恒返回 clean */
export function scanUgcDocument(payload: UgcPublishPayload): Promise<ScanResult> {
  return http.post<ScanResult>('/ugc/scan', payload)
}

/** 编辑并重新提交：删除旧文档后重新入库；团队文档重新进入待审队列 */
export function updateUgcDocument(
  docId: string,
  payload: UgcPublishPayload,
): Promise<UgcPublishResult> {
  return http.put<UgcPublishResult>(`/ugc/documents/${docId}`, payload)
}

/** 删除自己的文档（作者本人） */
export function deleteUgcDocument(docId: string): Promise<null> {
  return http.delete(`/ugc/documents/${docId}`)
}

/** 切换可见性：作者或管理员可操作；返回切换后的审核状态（private→team 为 pending） */
export function setUgcVisibility(
  docId: string,
  visibility: 'team' | 'private',
): Promise<string> {
  return http.patch<string>(`/ugc/documents/${docId}/visibility`, { visibility })
}
