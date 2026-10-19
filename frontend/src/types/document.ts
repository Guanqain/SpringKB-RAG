export interface Document {
  id: string
  title: string
  source: 'official' | 'community'
  version: string
  active: boolean
  chunkCount: number
  createdAt: string
  updatedAt: string
}

export interface Chunk {
  id: string
  content: string
  metadata: Record<string, string>
  score?: number
}

export interface ImportRequest {
  source: 'official' | 'community'
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

export interface DocumentQuery {
  source?: string
  version?: string
  active?: string
  keyword?: string
}

export interface ApiResponse<T> {
  code: number
  message: string
  data: T
}
