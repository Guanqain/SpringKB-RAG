import type { SourceRef } from '@/types/qa'

/** GET /api/me/conversations 会话列表项 */
export interface ConversationSummary {
  id: number
  title: string
  messageCount: number
  updatedAt: string
}

/** 会话内一轮问答 */
export interface ConversationMessage {
  id: number
  question: string
  answer: string
  found: boolean
  version: string | null
  sources: SourceRef[]
  createdAt: string
}

/** GET /api/me/conversations/{id} 会话详情 */
export interface ConversationDetail {
  id: number
  title: string
  updatedAt: string
  messages: ConversationMessage[]
}

/** 点赞/收藏后的最新状态 */
export interface InteractionResult {
  likeCount: number
  favoriteCount: number
  liked: boolean
  favorited: boolean
}

/** POST /api/me/conversations/{id}/messages 请求体 */
export interface AppendMessagePayload {
  question: string
  answer: string
  found: boolean
  version?: string
  sources: SourceRef[]
}
