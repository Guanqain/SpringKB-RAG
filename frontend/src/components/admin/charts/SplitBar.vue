<script setup lang="ts">
import { computed } from 'vue'
import { formatNumber, formatPct, monoTones, readTheme } from './theme'

interface SplitItem {
  label: string
  count: number
  pct: number
  tone?: string
}

const props = withDefaults(
  defineProps<{
    items: SplitItem[]
    ariaLabel: string
    height?: number
  }>(),
  { height: 8 },
)

const theme = readTheme()

const total = computed(() =>
  props.items.reduce((sum, item) => sum + (Number.isFinite(item.count) ? item.count : 0), 0),
)

const empty = computed(() => props.items.length === 0 || total.value <= 0)

const colors = computed(() => {
  const tones = monoTones(props.items.length)
  return props.items.map((item, index) =>
    item.tone === 'brand' ? theme.brand : (tones[index] ?? theme.ink300),
  )
})

const summary = computed(() => {
  if (empty.value) return `${props.ariaLabel}：暂无数据`
  const detail = props.items
    .map((item) => `${item.label} ${formatNumber(item.count)}（${formatPct(item.pct)}）`)
    .join('，')
  return `${props.ariaLabel}：${detail}`
})
</script>

<template>
  <div class="splitbar" role="img" :aria-label="ariaLabel">
    <span class="sr-only">{{ summary }}</span>

    <template v-if="!empty">
      <div
        class="splitbar__track"
        aria-hidden="true"
        :style="{ height: `${height}px` }"
      >
        <span
          v-for="(item, index) in items"
          :key="item.label"
          class="splitbar__seg"
          :style="{ width: `${item.pct}%`, backgroundColor: colors[index] }"
          :title="`${item.label} · ${formatNumber(item.count)}（${formatPct(item.pct)}）`"
        />
      </div>

      <ul class="splitbar__legend">
        <li v-for="(item, index) in items" :key="item.label" class="splitbar__item">
          <span
            class="splitbar__swatch"
            aria-hidden="true"
            :style="{ backgroundColor: colors[index] }"
          />
          <span class="splitbar__label" :title="item.label">{{ item.label }}</span>
          <span class="splitbar__count num">{{ formatNumber(item.count) }}</span>
          <span class="splitbar__pct num">{{ formatPct(item.pct) }}</span>
        </li>
      </ul>
    </template>

    <p v-else class="chart-empty">暂无数据</p>
  </div>
</template>

<style scoped>
.splitbar {
  display: flex;
  flex-direction: column;
  gap: var(--sp-3);
}

.splitbar__track {
  display: flex;
  width: 100%;
  border-radius: 999px;
  overflow: hidden;
  background: var(--ink-100);
}

.splitbar__seg {
  display: block;
  height: 100%;
  flex: none;
}

.splitbar__seg + .splitbar__seg {
  border-left: 1px solid var(--surface);
}

.splitbar__legend {
  list-style: none;
  display: flex;
  flex-direction: column;
  gap: var(--sp-2);
}

.splitbar__item {
  display: grid;
  grid-template-columns: 8px minmax(0, 1fr) auto 52px;
  align-items: center;
  gap: var(--sp-2);
  font-size: var(--fs-sm);
  color: var(--ink-700);
}

.splitbar__swatch {
  width: 8px;
  height: 8px;
  border-radius: 2px;
}

.splitbar__label {
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.splitbar__count {
  font-weight: 600;
  color: var(--ink-900);
}

.splitbar__pct {
  color: var(--ink-500);
  text-align: right;
}

.chart-empty {
  color: var(--ink-500);
  font-size: var(--fs-sm);
}

.sr-only {
  position: absolute;
  width: 1px;
  height: 1px;
  padding: 0;
  margin: -1px;
  overflow: hidden;
  clip: rect(0, 0, 0, 0);
  white-space: nowrap;
  border: 0;
}
</style>
