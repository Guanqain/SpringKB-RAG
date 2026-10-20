<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import { marked } from 'marked'
import DOMPurify from 'dompurify'

interface Token {
  type: 'text' | 'cite'
  value: string
  n?: number
}

interface RenderedPiece {
  kind: 'md' | 'cite'
  /** DOMPurify 净化后的 HTML 字符串，仅 kind='md' 时有值 */
  html?: string
  /** 原始 text（用于回退） */
  text?: string
  n?: number
}

const props = defineProps<{
  answer: string
  totalSources: number
  /** 入场动画（reduce-motion 或非最新条目时关闭） */
  animate: boolean
}>()

const emit = defineEmits<{
  cite: [n: number]
}>()

/** marked 配置：GFM + 代码围栏 */
marked.setOptions({
  gfm: true,
  breaks: false,
})

/** DOMPurify 白名单：Markdown 安全渲染所需标签与属性
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

/** LLM 返回的 Markdown 预处理：归一化换行、保护代码块内的 [n] 角标不被误拆 */
function normalizeMd(raw: string): string {
  // 合并多余空行（3+ → 2），稳定版面
  return raw.replace(/\n{3,}/g, '\n\n')
}

/** 把答案拆为 text / [n] 引用 token——[n] 必须在 marked 之前拆出，
 *  否则会被当作链接/文本语法吞掉 */
const tokens = computed<Token[]>(() => {
  const list: Token[] = []
  const re = /\[(\d+)\]/g
  let last = 0
  let m: RegExpExecArray | null
  while ((m = re.exec(props.answer)) !== null) {
    if (m.index > last) list.push({ type: 'text', value: props.answer.slice(last, m.index) })
    list.push({ type: 'cite', value: m[1], n: Number(m[1]) })
    last = m.index + m[0].length
  }
  if (last < props.answer.length) list.push({ type: 'text', value: props.answer.slice(last) })
  return list
})

/** 每个 text token 独立走 marked + DOMPurify；cite token 保留为 button */
const pieces = computed<RenderedPiece[]>(() => {
  return tokens.value.map<RenderedPiece>((t) => {
    if (t.type === 'cite') {
      return { kind: 'cite', n: t.n, text: t.value }
    }
    const mdHtml = marked.parse(normalizeMd(t.value)) as string
    // DOMPurify.sanitize 返回 TrustedHTML（全局安全类型），v-html 接受；
    // 我们的类型签名统一用 string，所以显式转一下
    const cleaned = DOMPurify.sanitize(mdHtml, PURIFY_CFG) as unknown as string
    return { kind: 'md', html: cleaned }
  })
})

/** fade-in 入场：整段渲染后 opacity 过渡，替代原逐字打字机 */
const visible = ref(false)
watch(
  () => props.answer,
  () => {
    visible.value = false
    const reduce = window.matchMedia('(prefers-reduced-motion: reduce)').matches
    if (!props.animate || reduce) {
      visible.value = true
      return
    }
    requestAnimationFrame(() => {
      requestAnimationFrame(() => {
        visible.value = true
      })
    })
  },
  { immediate: true },
)
</script>

<template>
  <div class="md-wrap" :class="{ show: visible }">
    <template v-for="(p, i) in pieces" :key="i">
      <!-- Markdown HTML 块（已 DOMPurify 净化） -->
      <span v-if="p.kind === 'md' && p.html" class="md-blk" v-html="p.html" />
      <!-- 引用角标（可点击 button） -->
      <button
        v-else-if="p.kind === 'cite' && p.n && p.n <= totalSources"
        type="button"
        class="cite"
        :title="`查看来源 ${p.n}`"
        @click="emit('cite', p.n)"
      >
        {{ p.n }}
      </button>
      <span v-else-if="p.kind === 'cite'" class="cite-plain">&nbsp;[{{ p.text }}]&nbsp;</span>
    </template>
  </div>
</template>

<style scoped>
/* ===== 入场 fade-in ===== */
.md-wrap {
  opacity: 0;
  transition: opacity 0.28s ease;
}
.md-wrap.show {
  opacity: 1;
}

/* ===== Markdown 排版（浅色底可读） ===== */
/* scoped 无法穿透 v-html，以下 .md-blk 样式由非 scoped 的全局样式接管；
 * 此处仅作用域内可生效的部分 */
.md-blk {
  display: inline;
}

/* ===== 引用角标 ===== */
.cite {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  min-width: 18px;
  height: 18px;
  border-radius: var(--r-sm);
  border: 1px solid var(--brand-200);
  background: var(--brand-50);
  color: var(--brand-700);
  font-size: 11px;
  font-weight: 700;
  font-family: ui-monospace, Consolas, monospace;
  margin: 0 2px;
  cursor: pointer;
  vertical-align: super;
  transition: background 0.12s ease, transform 0.12s ease;
}

.cite:hover {
  background: var(--brand-100);
  transform: scale(1.15);
}

.cite-plain {
  color: var(--ink-400);
  font-family: ui-monospace, Consolas, monospace;
  font-size: 12px;
}
</style>

<!-- Markdown 排版样式（非 scoped，穿透 v-html 渲染的 HTML 结构；浅色底可读） -->
<style>
.qa-page .md-wrap .md-blk,
.qa-page .md-wrap p,
.qa-page .md-wrap h1,
.qa-page .md-wrap h2,
.qa-page .md-wrap h3,
.qa-page .md-wrap h4,
.qa-page .md-wrap ul,
.qa-page .md-wrap ol,
.qa-page .md-wrap li,
.qa-page .md-wrap blockquote,
.qa-page .md-wrap hr {
  color: var(--ink-700);
  font-size: 14px;
  line-height: 1.8;
}

.qa-page .md-wrap h1 {
  font-size: 17px;
  font-weight: 700;
  color: var(--ink-900);
  margin: 14px 0 6px;
  padding-bottom: 4px;
  border-bottom: 1px solid var(--ink-100);
}

.qa-page .md-wrap h2 {
  font-size: 15px;
  font-weight: 700;
  margin: 12px 0 4px;
  color: var(--brand-600);
}

.qa-page .md-wrap h3 {
  font-size: 14px;
  font-weight: 700;
  margin: 10px 0 4px;
  color: var(--brand-600);
}

.qa-page .md-wrap p {
  margin: 6px 0;
}

.qa-page .md-wrap ul,
.qa-page .md-wrap ol {
  margin: 6px 0;
  padding-left: 18px;
}

.qa-page .md-wrap li {
  margin: 2px 0;
}

.qa-page .md-wrap ul li {
  list-style: disc;
}

.qa-page .md-wrap ol li {
  list-style: decimal;
}

.qa-page .md-wrap blockquote {
  margin: 8px 0;
  padding: 6px 12px;
  border-left: 3px solid var(--brand-400);
  background: var(--brand-50);
  border-radius: 0 6px 6px 0;
  color: var(--ink-600);
}

.qa-page .md-wrap hr {
  border: none;
  height: 1px;
  background: var(--ink-100);
  margin: 12px 0;
}

/* 行内代码 */
.qa-page .md-wrap code {
  font-family: ui-monospace, Consolas, monospace;
  font-size: 13px;
  padding: 2px 6px;
  border-radius: 4px;
  background: var(--ink-100);
  color: var(--brand-700);
}

/* 代码块（保留深色底，与白卡形成层次） */
.qa-page .md-wrap pre {
  margin: 8px 0;
  padding: 12px 14px;
  border-radius: 8px;
  background: var(--ink-900);
  border: 1px solid var(--ink-800);
  overflow-x: auto;
}

.qa-page .md-wrap pre code {
  padding: 0;
  background: transparent;
  color: var(--ink-100);
  font-size: 12.5px;
  line-height: 1.6;
}

/* 表格 */
.qa-page .md-wrap table {
  width: 100%;
  border-collapse: collapse;
  margin: 8px 0;
  font-size: 13px;
}

.qa-page .md-wrap th,
.qa-page .md-wrap td {
  border: 1px solid var(--ink-200);
  padding: 5px 10px;
  text-align: left;
}

.qa-page .md-wrap th {
  background: var(--brand-50);
  color: var(--brand-700);
  font-weight: 600;
}

.qa-page .md-wrap a {
  color: var(--brand-600);
  text-decoration: none;
  border-bottom: 1px dotted var(--brand-300);
}

.qa-page .md-wrap a:hover {
  color: var(--brand-700);
  border-bottom-style: solid;
}

/* 加粗/斜体 */
.qa-page .md-wrap strong {
  color: var(--brand-700);
  font-weight: 700;
}

.qa-page .md-wrap em {
  color: var(--qa-rose);
}
</style>
