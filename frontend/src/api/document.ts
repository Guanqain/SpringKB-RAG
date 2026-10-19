import http from './http'
import type {
  Chunk,
  Document,
  DocumentQuery,
  ImportRequest,
  ImportResult,
} from '@/types/document'

export function listDocuments(
  params?: DocumentQuery,
): Promise<Document[]> {
  return http.get('/admin/documents', { params })
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
