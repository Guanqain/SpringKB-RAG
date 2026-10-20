<script setup lang="ts">
import type { SourceRef } from '@/types/qa'

const props = defineProps<{
  source: SourceRef
  highlight: boolean
}>()
</script>

<template>
  <div class="src" :class="{ hl: props.highlight }">
    <div class="src__head">
      <span class="badge" :class="props.source.source === 'official' ? 'badge--official' : 'badge--community'">
        {{ props.source.source === 'official' ? '官方' : '社区' }}
      </span>
      <span v-if="props.source.version" class="src__ver num">{{ props.source.version }}</span>
      <span class="src__score num">{{ props.source.score.toFixed(2) }}</span>
    </div>
    <div class="src__title">{{ props.source.title }}</div>
    <div class="src__meta">
      {{ [props.source.section, props.source.headingPath].filter(Boolean).join(' › ') || '—' }}
    </div>
    <p class="src__snip">{{ props.source.snippet }}</p>
    <!-- 原文链接依赖 §9 C6（SourceRef 无 originalUrl）：契约未就绪 → 禁用态 + 提示，不伪造 URL -->
    <span class="src__open" title="原文链接待后端就绪">打开原文</span>
  </div>
</template>

<style scoped>
/* 横向紧凑卡：1px hairline 描边、无阴影、无 hover 抬升 */
.src {
  display: flex;
  flex-direction: column;
  min-width: 0;
  flex: 1 1 220px;
  border: 1px solid var(--hairline);
  border-radius: var(--r-lg);
  background: var(--surface);
  padding: var(--sp-4);
}

/* 引用定位：一次性陶土脉冲环（--brand-400 → --brand-600），单次高光，不循环 */
.src.hl {
  animation: src-pulse 1.2s var(--ease-out) 1;
}

@keyframes src-pulse {
  0% {
    outline: 2px solid var(--brand-400);
    outline-offset: 2px;
  }
  55% {
    outline: 2px solid var(--brand-600);
    outline-offset: 2px;
  }
  100% {
    outline: 2px solid transparent;
    outline-offset: 2px;
  }
}

.src__head {
  display: flex;
  align-items: center;
  gap: var(--sp-2);
}

.src__ver {
  font-size: var(--fs-xs);
  color: var(--ink-500);
}

.src__score {
  margin-left: auto;
  font-size: var(--fs-xs);
  color: var(--ink-400);
}

.src__title {
  margin-top: var(--sp-2);
  font-weight: 600;
  font-size: var(--fs-md);
  color: var(--ink-900);
}

.src__meta {
  margin-top: 2px;
  font-size: var(--fs-xs);
  color: var(--ink-500);
}

.src__snip {
  margin: var(--sp-2) 0 0;
  font-size: var(--fs-sm);
  line-height: 1.7;
  color: var(--ink-500);
  display: -webkit-box;
  -webkit-line-clamp: 3;
  -webkit-box-orient: vertical;
  overflow: hidden;
}

/* 「打开原文」：C6 未就绪 → 禁用态文本，不可点击 */
.src__open {
  margin-top: var(--sp-3);
  font-size: var(--fs-sm);
  color: var(--ink-400);
  cursor: not-allowed;
  user-select: none;
}
</style>
