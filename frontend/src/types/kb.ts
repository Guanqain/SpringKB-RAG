/** GET /api/kb/docs 分页结果（page 从 1 起） */
export interface PageResult<T> {
  list: T[]
  total: number
  page: number
  size: number
}

/** GET /api/kb/overview 概览统计 */
export interface KbOverview {
  documentCount: number
  productCount: number
  versionCount: number
  sourceCount: number
}

/** GET /api/kb/tree 体系树节点（product ▸ version ▸ source 三级） */
export interface KbTreeNode {
  key: string
  label: string
  type: 'product' | 'version' | 'source'
  documentCount: number
  children: KbTreeNode[]
}

/** GET /api/kb/docs 文档卡列表项 */
export interface KbDocCard {
  id: string
  title: string
  product: string
  origin: string
  authority: string
  version: string
  section: string
  docType: string
  chunkCount: number
  updatedAt: string
  originalUrl: string | null
  /** UGC 文档的写入用户；爬虫/管理端导入为 null */
  author: string | null
}

/** GET /api/kb/doc/{id} 文档详情 */
export interface KbDocDetail {
  id: string
  title: string
  product: string
  origin: string
  authority: string
  version: string
  docKey: string
  section: string
  docType: string
  language: string
  chunkCount: number
  lastModified: string
  updatedAt: string
  originalUrl: string | null
  markdown: string
  author: string | null
  likeCount: number
  favoriteCount: number
  liked: boolean
  favorited: boolean
}

/** POST /api/ugc/documents 发布结果 */
export interface UgcPublishResult {
  docId: string
  chunkCount: number
}