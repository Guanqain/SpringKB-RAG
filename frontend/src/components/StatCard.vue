<script setup lang="ts">
import { computed } from 'vue'

const props = defineProps<{
  label: string
  value: number
  hint?: string
  tone?: 'primary' | 'muted' | 'success' | 'danger'
}>()

const toneClass = computed(() => `stat-card--${props.tone ?? 'primary'}`)
// 信号表达：仅「异常 / 待办」类信号（danger）落在陶土小圆点与数字上，不做彩色底块
const hasSignal = computed(() => props.tone === 'danger')
</script>

<template>
  <div class="stat-card" :class="toneClass">
    <span class="stat-label">
      <span v-if="hasSignal" class="stat-dot" aria-hidden="true" />
      {{ label }}
    </span>
    <span class="stat-value num">{{ value.toLocaleString() }}</span>
    <span v-if="hint" class="stat-hint">{{ hint }}</span>
  </div>
</template>

<style scoped>
.stat-card {
  background: var(--surface);
  border: 1px solid var(--hairline);
  border-radius: var(--r-lg);
  padding: var(--sp-5);
  display: flex;
  flex-direction: column;
  gap: var(--sp-1);
  transition: border-color var(--dur-fast) var(--ease-out);
}

.stat-card:hover {
  border-color: var(--hairline-strong);
}

.stat-label {
  display: inline-flex;
  align-items: center;
  gap: var(--sp-2);
  font-size: var(--fs-sm);
  color: var(--ink-500);
}

.stat-dot {
  width: 6px;
  height: 6px;
  border-radius: 50%;
  background: var(--brand-600);
  flex: none;
}

.stat-value {
  font-family: var(--font-num);
  font-variant-numeric: tabular-nums;
  font-size: var(--fs-4xl);
  font-weight: 600;
  line-height: 1.15;
  color: var(--ink-900);
}

.stat-card--danger .stat-value {
  color: var(--brand-600);
}

.stat-hint {
  font-size: var(--fs-xs);
  color: var(--ink-500);
}
</style>
