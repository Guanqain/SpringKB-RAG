import { marked } from 'marked'
import DOMPurify from 'dompurify'

// GFM + 代码围栏；breaks 关闭（单个换行不转 <br>）
marked.setOptions({
  gfm: true,
  breaks: false,
})

/** DOMPurify 白名单：Markdown 安全渲染所需标签与属性。
 *  链接协议默认拒绝 javascript:/data:/vbscript:，仅放行 http/https/mailto/相对路径 */
const PURIFY_CFG: import('dompurify').Config = {
  ALLOWED_TAGS: [
    'h1', 'h2', 'h3', 'h4', 'h5', 'h6',
    'p', 'br', 'hr',
    'strong', 'em', 'del', 'u', 'sub', 'sup', 'code', 'pre', 'blockquote',
    'ul', 'ol', 'li',
    'table', 'thead', 'tbody', 'tr', 'th', 'td',
    'a', 'img',
    'span', 'div',
  ],
  ALLOWED_ATTR: [
    'href', 'target', 'rel', 'title',
    'src', 'alt', 'width', 'height',
    'class', 'lang',
  ],
  FORBID_TAGS: ['script', 'iframe', 'form', 'input', 'textarea', 'button'],
  FORBID_ATTR: ['onclick', 'onerror', 'onload', 'onmouseover', 'style'],
}

/** Markdown → 净化后的 HTML（知识库文档详情 / 写文档实时预览复用）。 */
export function renderMarkdown(md: string): string {
  if (!md) return ''
  return DOMPurify.sanitize(marked.parse(md.replace(/\n{3,}/g, '\n\n')) as string, PURIFY_CFG) as unknown as string
}