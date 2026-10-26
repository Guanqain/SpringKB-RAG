<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, ref, watch } from 'vue'
import type { SourceRef } from '@/types/qa'

/**
 * 引用悬浮卡：点击来源卡后在独立浮层展开。
 * - 拖动标题栏移动，右下角手柄缩放（固定 4:3 长宽比）
 * - 可收缩为标题栏、可关闭，Esc 关闭
 * - Teleport 到 body，避免被问答页 sticky 输入区裁剪
 */
const props = defineProps<{
  source: SourceRef | null
}>()

const emit = defineEmits<{
  close: []
}>()

const RATIO = 3 / 4 // 高 / 宽，固定 4:3
const MARGIN = 12
const HEADER_H = 48
const MIN_W = 300
const MAX_W = 680
const DEFAULT_W = 440

const rootRef = ref<HTMLElement>()
const x = ref(0)
const y = ref(0)
const width = ref(DEFAULT_W)
const collapsed = ref(false)
const dragging = ref(false)
const resizing = ref(false)
let hasPosition = false

const open = computed(() => props.source !== null)
const height = computed(() => (collapsed.value ? HEADER_H : Math.round(width.value * RATIO)))

const rootStyle = computed(() => ({
  left: `${x.value}px`,
  top: `${y.value}px`,
  width: `${width.value}px`,
  height: `${height.value}px`,
}))

function clampWidth(value: number) {
  const max = Math.max(MIN_W, Math.min(MAX_W, window.innerWidth - MARGIN * 2))
  return Math.min(Math.max(value, MIN_W), max)
}

function clampPosition() {
  const w = width.value
  const h = collapsed.value ? HEADER_H : Math.round(width.value * RATIO)
  const maxX = Math.max(MARGIN, window.innerWidth - w - MARGIN)
  const maxY = Math.max(MARGIN, window.innerHeight - h - MARGIN)
  x.value = Math.min(Math.max(x.value, MARGIN), maxX)
  y.value = Math.min(Math.max(y.value, MARGIN), maxY)
}

/** 首次展开：水平居中、垂直略偏上，避免遮住输入区 */
function center() {
  width.value = clampWidth(DEFAULT_W)
  x.value = Math.max(MARGIN, Math.round((window.innerWidth - width.value) / 2))
  y.value = Math.max(MARGIN, Math.round((window.innerHeight - height.value) / 2 - 40))
}

watch(
  () => props.source,
  (s) => {
    if (!s) return
    collapsed.value = false
    if (!hasPosition) {
      center()
      hasPosition = true
    } else {
      width.value = clampWidth(width.value)
      clampPosition()
    }
  },
  { immediate: true },
)

/* ===== 拖动 ===== */
let dragStart: { px: number; py: number; x: number; y: number } | null = null

function onDragStart(e: PointerEvent) {
  if (!open.value || e.button !== 0) return
  if ((e.target as HTMLElement).closest('button, a')) return
  dragStart = { px: e.clientX, py: e.clientY, x: x.value, y: y.value }
  dragging.value = true
  window.addEventListener('pointermove', onDragMove)
  window.addEventListener('pointerup', onDragEnd)
  e.preventDefault()
}

function onDragMove(e: PointerEvent) {
  if (!dragStart) return
  x.value = dragStart.x + (e.clientX - dragStart.px)
  y.value = dragStart.y + (e.clientY - dragStart.py)
  clampPosition()
}

function onDragEnd() {
  dragging.value = false
  dragStart = null
  window.removeEventListener('pointermove', onDragMove)
  window.removeEventListener('pointerup', onDragEnd)
}

/* ===== 缩放（右下角手柄，长宽比固定） ===== */
let resizeStart: { px: number; py: number; w: number } | null = null

function onResizeStart(e: PointerEvent) {
  if (collapsed.value || e.button !== 0) return
  resizeStart = { px: e.clientX, py: e.clientY, w: width.value }
  resizing.value = true
  window.addEventListener('pointermove', onResizeMove)
  window.addEventListener('pointerup', onResizeEnd)
  e.preventDefault()
  e.stopPropagation()
}

function onResizeMove(e: PointerEvent) {
  if (!resizeStart) return
  const dx = e.clientX - resizeStart.px
  const dy = e.clientY - resizeStart.py
  // 同时参考横向与纵向位移，保持 4:3 比例
  width.value = clampWidth(resizeStart.w + Math.max(dx, dy / RATIO))
  clampPosition()
}

function onResizeEnd() {
  resizing.value = false
  resizeStart = null
  window.removeEventListener('pointermove', onResizeMove)
  window.removeEventListener('pointerup', onResizeEnd)
}

function toggleCollapse() {
  collapsed.value = !collapsed.value
  clampPosition()
}

/* ===== 视口与键盘 ===== */
function onViewportResize() {
  if (!open.value) return
  width.value = clampWidth(width.value)
  clampPosition()
}

function onKeydown(e: KeyboardEvent) {
  if (e.key === 'Escape' && open.value) emit('close')
}

onMounted(() => {
  window.addEventListener('resize', onViewportResize)
  window.addEventListener('keydown', onKeydown)
})

onBeforeUnmount(() => {
  window.removeEventListener('resize', onViewportResize)
  window.removeEventListener('keydown', onKeydown)
  window.removeEventListener('pointermove', onDragMove)
  window.removeEventListener('pointerup', onDragEnd)
  window.removeEventListener('pointermove', onResizeMove)
  window.removeEventListener('pointerup', onResizeEnd)
})
</script>

<template>
  <Teleport to="body">
    <div
      v-if="source"
      ref="rootRef"
      class="float"
      :class="{ 'float--collapsed': collapsed, 'float--dragging': dragging, 'float--resizing': resizing }"
      :style="rootStyle"
      role="dialog"
      aria-modal="false"
      :aria-label="`引用详情：${source.title}`"
    >
      <header class="float__head" @pointerdown="onDragStart">
        <span
          class="badge"
          :class="source.source === 'official' ? 'badge--official' : 'badge--community'"
        >{{ source.source === 'official' ? '官方' : '社区' }}</span>
        <span class="float__title" :title="source.title">{{ source.title }}</span>
        <div class="float__actions" @pointerdown.stop>
          <button
            class="float__icon-btn"
            type="button"
            :aria-label="collapsed ? '展开' : '收缩'"
            :title="collapsed ? '展开' : '收缩'"
            @click="toggleCollapse"
          >{{ collapsed ? '▢' : '—' }}</button>
          <button
            class="float__icon-btn"
            type="button"
            aria-label="关闭"
            title="关闭"
            @click="emit('close')"
          >×</button>
        </div>
      </header>

      <div v-if="!collapsed" class="float__body">
        <div class="float__meta">
          <span v-if="source.version" class="num">{{ source.version }}</span>
          <span class="float__path">
            {{ [source.section, source.headingPath].filter(Boolean).join(' › ') || '—' }}
          </span>
          <span class="float__score num" :title="`相关度 ${source.score.toFixed(2)}`">
            {{ source.score.toFixed(2) }}
          </span>
        </div>

        <p class="float__snippet">{{ source.snippet || '暂无片段摘要' }}</p>

        <footer class="float__foot">
          <router-link
            v-if="source.docId"
            class="float__link"
            :to="{ name: 'kb-doc', params: { id: source.docId } }"
            @click="emit('close')"
          >查看完整文档 →</router-link>
          <a
            v-if="source.originalUrl"
            class="float__link float__link--muted"
            :href="source.originalUrl"
            target="_blank"
            rel="noopener noreferrer"
          >打开原文 ↗</a>
          <span class="float__hint">拖动标题栏移动 · 右下角缩放</span>
        </footer>
      </div>

      <div
        v-if="!collapsed"
        class="float__resize"
        title="拖动调整大小（固定 4:3）"
        @pointerdown="onResizeStart"
      />
    </div>
  </Teleport>
</template>

<style scoped>
.float {
  position: fixed;
  z-index: 1100;
  display: flex;
  flex-direction: column;
  min-width: 300px;
  background: var(--surface);
  border: 1px solid var(--hairline-strong);
  border-radius: var(--r-lg);
  box-shadow: var(--shadow-overlay);
  overflow: hidden;
  animation: float-in var(--dur-base) var(--ease-out) 1;
  will-change: left, top, width, height;
}

.float--dragging,
.float--resizing {
  user-select: none;
  cursor: grabbing;
}

@keyframes float-in {
  from {
    opacity: 0;
    transform: translateY(6px) scale(0.98);
  }
  to {
    opacity: 1;
    transform: none;
  }
}

/* 标题栏：拖动区（按钮区域已阻止冒泡） */
.float__head {
  display: flex;
  align-items: center;
  gap: var(--sp-2);
  flex-shrink: 0;
  height: 48px;
  padding: 0 var(--sp-2) 0 var(--sp-4);
  border-bottom: 1px solid var(--hairline);
  background: var(--surface-sunken);
  cursor: grab;
  touch-action: none;
}

.float--collapsed .float__head {
  border-bottom: none;
}

.float--dragging .float__head {
  cursor: grabbing;
}

.float__title {
  flex: 1;
  min-width: 0;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  font-size: var(--fs-md);
  font-weight: 600;
  color: var(--ink-900);
}

.float__actions {
  display: flex;
  align-items: center;
  gap: 2px;
  flex-shrink: 0;
}

.float__icon-btn {
  width: 28px;
  height: 28px;
  display: grid;
  place-items: center;
  border-radius: var(--r-sm);
  font-size: var(--fs-lg);
  line-height: 1;
  color: var(--ink-500);
  transition:
    background-color var(--dur-fast) var(--ease-out),
    color var(--dur-fast) var(--ease-out);
}

.float__icon-btn:hover {
  background: var(--ink-100);
  color: var(--ink-900);
}

.float__body {
  flex: 1;
  min-height: 0;
  display: flex;
  flex-direction: column;
  gap: var(--sp-3);
  padding: var(--sp-4);
  overflow: hidden;
}

.float__meta {
  display: flex;
  align-items: center;
  gap: var(--sp-2);
  font-size: var(--fs-xs);
  color: var(--ink-500);
}

.float__path {
  flex: 1;
  min-width: 0;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.float__score {
  flex-shrink: 0;
  color: var(--ink-400);
}

.float__snippet {
  flex: 1;
  min-height: 0;
  margin: 0;
  padding: var(--sp-3);
  border-radius: var(--r-md);
  background: var(--surface-sunken);
  font-size: var(--fs-sm);
  line-height: 1.75;
  color: var(--ink-700);
  white-space: pre-wrap;
  word-break: break-word;
  overflow-y: auto;
}

.float__foot {
  display: flex;
  align-items: center;
  flex-wrap: wrap;
  gap: var(--sp-3);
  flex-shrink: 0;
}

.float__link {
  font-size: var(--fs-sm);
  color: var(--brand-600);
  text-decoration: none;
}

.float__link:hover {
  text-decoration: underline;
  text-underline-offset: 2px;
}

.float__link--muted {
  color: var(--ink-500);
}

.float__hint {
  margin-left: auto;
  font-size: var(--fs-xs);
  color: var(--ink-400);
  user-select: none;
}

/* 右下角缩放手柄：两条斜线暗示可拖拽 */
.float__resize {
  position: absolute;
  right: 0;
  bottom: 0;
  width: 18px;
  height: 18px;
  cursor: nwse-resize;
  touch-action: none;
  background:
    linear-gradient(135deg, transparent 0 55%, var(--ink-300) 55% 62%, transparent 62%),
    linear-gradient(135deg, transparent 0 75%, var(--ink-400) 75% 82%, transparent 82%);
  border-bottom-right-radius: var(--r-lg);
}

.float__resize:hover {
  background:
    linear-gradient(135deg, transparent 0 55%, var(--brand-600) 55% 62%, transparent 62%),
    linear-gradient(135deg, transparent 0 75%, var(--brand-600) 75% 82%, transparent 82%);
}

@media (max-width: 560px) {
  .float {
    min-width: 0;
  }

  .float__hint {
    display: none;
  }
}
</style>
