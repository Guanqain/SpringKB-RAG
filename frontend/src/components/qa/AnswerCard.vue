<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import { marked } from 'marked'
import DOMPurify from 'dompurify'

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

/** 角标 HTML：只由数字与常量拼接，注入在净化之后，安全 */
function citeTag(n: number): string {
  if (n < 1 || n > props.totalSources) return `[${n}]`
  return `<sup class="cite" data-n="${n}" role="button" title="查看来源 ${n}">${n}</sup>`
}

/**
 * 把 HTML 中的 [n]（含模型按 prompt 追加的「（官方）/（社区）」后缀）替换为行内上标角标。
 * 代码块与行内代码内的 [n] 不做替换，避免破坏示例代码。
 */
function linkifyCitations(html: string): string {
  // split 的捕获组为代码区，落在奇数下标，原样跳过
  return html
    .split(/(<pre[\s\S]*?<\/pre>|<code[\s\S]*?<\/code>)/g)
    .map((seg, i) => {
      if (i % 2 === 1) return seg
      return seg
        .replace(/\[(\d+)\]\s*[（(]\s*(?:官方|社区)\s*[)）]/g, (_m, n: string) => citeTag(Number(n)))
        .replace(/\[(\d+)\]/g, (_m, n: string) => citeTag(Number(n)))
    })
    .join('')
}

/**
 * 整段答案一次性渲染：marked → DOMPurify → 后处理角标。
 * 不能按 [n] 切段分别 marked.parse —— 每段都会被 <p> 包裹成块级元素，导致角标与后文强制换行。
 */
const html = computed(() => {
  const md = props.answer.replace(/\n{3,}/g, '\n\n')
  const clean = DOMPurify.sanitize(marked.parse(md) as string, PURIFY_CFG) as unknown as string
  return linkifyCitations(clean)
})

/** 事件委托：v-html 内的角标无法直接绑定 @click */
function onClick(e: MouseEvent) {
  const el = (e.target as HTMLElement).closest<HTMLElement>('.cite')
  if (!el) return
  const n = Number(el.dataset.n)
  if (n) emit('cite', n)
}

/** 结果到达时的轻微淡入（--dur-slow 内 opacity + 轻微位移），不做逐张入场 */
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
    <!-- 整段 HTML（已 DOMPurify 净化），角标由 click 委托处理 -->
    <div class="md-body" v-html="html" @click="onClick" />
  </div>
</template>

<style scoped>
/* ===== 结果到达的轻微淡入（状态变化动效，非逐张入场） ===== */
.md-wrap {
  opacity: 0;
  transform: translateY(4px);
  transition:
    opacity var(--dur-slow) var(--ease-out),
    transform var(--dur-slow) var(--ease-out);
}

.md-wrap.show {
  opacity: 1;
  transform: none;
}
</style>

<!-- 排版样式（非 scoped：v-html 插入的 HTML 不被 scoped 属性命中；纸面阅读配色） -->
<style>
.qa-page .md-body {
  color: var(--ink-700);
  font-size: var(--fs-lg);
  line-height: 1.7;
}

.qa-page .md-body h1,
.qa-page .md-body h2,
.qa-page .md-body h3,
.qa-page .md-body h4 {
  font-weight: 600;
  color: var(--ink-900);
  margin: 20px 0 8px;
}

.qa-page .md-body h1 {
  font-size: var(--fs-2xl);
}

.qa-page .md-body h2 {
  font-size: var(--fs-xl);
}

.qa-page .md-body h3,
.qa-page .md-body h4 {
  font-size: var(--fs-lg);
}

.qa-page .md-body p {
  margin: 10px 0;
}

.qa-page .md-body ul,
.qa-page .md-body ol {
  margin: 10px 0;
  padding-left: 22px;
}

.qa-page .md-body li {
  margin: 4px 0;
}

.qa-page .md-body ul li {
  list-style: disc;
}

.qa-page .md-body ol li {
  list-style: decimal;
}

.qa-page .md-body blockquote {
  margin: 12px 0;
  padding: 8px 14px;
  border-left: 2px solid var(--hairline-strong);
  background: var(--surface-sunken);
  border-radius: 0 var(--r-sm) var(--r-sm) 0;
  color: var(--ink-600);
}

.qa-page .md-body hr {
  border: none;
  height: 1px;
  background: var(--hairline);
  margin: 16px 0;
}

/* 行内代码 */
.qa-page .md-body code {
  font-family: var(--font-num);
  font-size: var(--fs-sm);
  padding: 2px 6px;
  border-radius: 4px;
  background: var(--ink-100);
  color: var(--ink-800);
}

/* 代码块（深墨底，纸面上唯一的深色块） */
.qa-page .md-body pre {
  margin: 12px 0;
  padding: 12px 14px;
  border-radius: var(--r-md);
  background: var(--ink-900);
  overflow-x: auto;
}

.qa-page .md-body pre code {
  padding: 0;
  background: transparent;
  color: var(--ink-100);
  font-size: var(--fs-sm);
  line-height: 1.7;
}

/* 表格：hairline 分隔，无彩色表头 */
.qa-page .md-body table {
  width: 100%;
  border-collapse: collapse;
  margin: 12px 0;
  font-size: var(--fs-md);
}

.qa-page .md-body th,
.qa-page .md-body td {
  border: 1px solid var(--hairline);
  padding: 6px 10px;
  text-align: left;
}

.qa-page .md-body th {
  background: var(--surface-sunken);
  color: var(--ink-600);
  font-weight: 600;
}

/* 链接：纸面下划线，hover 转陶土 */
.qa-page .md-body a {
  color: var(--ink-700);
  text-decoration: underline;
  text-decoration-color: var(--hairline-strong);
  text-underline-offset: 2px;
}

.qa-page .md-body a:hover {
  color: var(--brand-600);
  text-decoration-color: var(--brand-600);
}

/* 强调 */
.qa-page .md-body strong {
  color: var(--ink-900);
  font-weight: 600;
}

.qa-page .md-body em {
  font-style: italic;
}

/* ===== 引用角标：行内极小上标（不换行、不占行） ===== */
.qa-page .md-body .cite {
  display: inline;
  margin: 0 1px;
  padding: 0 1px;
  border: none;
  background: none;
  font-family: var(--font-num);
  font-size: 11px;
  font-variant-numeric: tabular-nums;
  font-weight: 600;
  line-height: 1;
  vertical-align: super;
  color: var(--ink-500);
  cursor: pointer;
  transition: color var(--dur-fast) var(--ease-out);
}

.qa-page .md-body .cite:hover {
  color: var(--brand-600);
  text-decoration: underline;
  text-decoration-color: var(--brand-600);
  text-underline-offset: 2px;
}
</style>
