import http from './http'
import type { KbDocCard } from '@/types/kb'
import type {
  AppendMessagePayload,
  ConversationDetail,
  ConversationSummary,
  InteractionResult,
} from '@/types/me'

/** GET /api/me/documents：我写过的文档（UGC 归属） */
export function fetchMyDocuments(): Promise<KbDocCard[]> {
  return http.get<KbDocCard[]>('/me/documents')
}

/** GET /api/me/favorites：我的收藏 */
export function fetchMyFavorites(): Promise<KbDocCard[]> {
  return http.get<KbDocCard[]>('/me/favorites')
}

/** 点赞 / 取消点赞（幂等） */
export function setDocLike(docId: string, on: boolean): Promise<InteractionResult> {
  return on ? http.post<InteractionResult>(`/me/docs/${docId}/like`) : http.delete<InteractionResult>(`/me/docs/${docId}/like`)
}

/** 收藏 / 取消收藏（幂等） */
export function setDocFavorite(docId: string, on: boolean): Promise<InteractionResult> {
  return on
    ? http.post<InteractionResult>(`/me/docs/${docId}/favorite`)
    : http.delete<InteractionResult>(`/me/docs/${docId}/favorite`)
}

/** 会话列表（含消息条数，按最近更新倒序） */
export function fetchConversations(): Promise<ConversationSummary[]> {
  return http.get<ConversationSummary[]>('/me/conversations')
}

/** 新建会话；title 为空后端取默认值 */
export function createConversation(title?: string): Promise<ConversationDetail> {
  return http.post<ConversationDetail>('/me/conversations', { title })
}

/** 会话详情（消息按时间正序，已按窗口裁剪） */
export function fetchConversation(id: number): Promise<ConversationDetail> {
  return http.get<ConversationDetail>(`/me/conversations/${id}`)
}

/** 重命名会话 */
export function renameConversation(id: number, title: string): Promise<ConversationDetail> {
  return http.patch<ConversationDetail>(`/me/conversations/${id}`, { title })
}

/** 删除会话（级联删除消息） */
export function deleteConversation(id: number): Promise<void> {
  return http.delete<void>(`/me/conversations/${id}`)
}

/** 追加一轮问答（后端裁剪窗口并刷新会话时间） */
export function appendConversationMessage(
  id: number,
  payload: AppendMessagePayload,
): Promise<ConversationDetail> {
  return http.post<ConversationDetail>(`/me/conversations/${id}/messages`, payload, { timeout: 30000 })
}
