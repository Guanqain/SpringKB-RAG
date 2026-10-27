<script setup lang="ts">
import { computed } from 'vue'
import type { ChartData, ChartOptions } from 'chart.js'
import { Bar } from 'vue-chartjs'
import {
  formatNumber,
  formatPct,
  readTheme,
  registerCharts,
} from './theme'

interface BarItem {
  label: string
  count: number
  pct: number
}

interface OtherItem {
  label: string
  count: number
  total: number
}

const props = withDefaults(
  defineProps<{
    items: BarItem[]
    ariaLabel: string
    other?: OtherItem
    maxItems?: number
  }>(),
  { maxItems: 8 },
)

const emit = defineEmits<{
  (event: 'other-click'): void
}>()

registerCharts()

const ROW_HEIGHT = 34

const theme = readTheme()

const visibleItems = computed(() => props.items.slice(0, Math.max(0, props.maxItems)))

const visibleTotal = computed(() =>
  visibleItems.value.reduce(
    (sum, item) => sum + (Number.isFinite(item.count) ? item.count : 0),
    0,
  ),
)

const empty = computed(() => visibleItems.value.length === 0 || visibleTotal.value <= 0)

const otherPct = computed(() => {
  if (!props.other) return 0
  const overall = visibleTotal.value + props.other.total
  return overall > 0 ? (props.other.total / overall) * 100 : 0
})

const plotHeight = computed(() => visibleItems.value.length * ROW_HEIGHT)

const summary = computed(() => {
  if (empty.value) return `${props.ariaLabel}：暂无数据`
  const detail = visibleItems.value
    .map((item) => `${item.label} ${formatNumber(item.count)}`)
    .join('，')
  const otherText = props.other
    ? `，${props.other.label} ${props.other.count} 项合计 ${formatNumber(props.other.total)} 篇`
    : ''
  return `${props.ariaLabel}：${detail}${otherText}`
})

const chartData = computed<ChartData<'bar', number[], string>>(() => ({
  labels: visibleItems.value.map((item) => item.label),
  datasets: [
    {
      data: visibleItems.value.map((item) => item.count),
      backgroundColor: theme.ink700,
      hoverBackgroundColor: theme.ink900,
      borderWidth: 0,
      borderRadius: 2,
      borderSkipped: false,
      barThickness: 9,
    },
  ],
}))

const chartOptions: ChartOptions<'bar'> = {
  indexAxis: 'y',
  responsive: true,
  maintainAspectRatio: false,
  layout: { padding: 0 },
  scales: {
    x: {
      display: false,
      beginAtZero: true,
      grid: { display: false },
      border: { display: false },
    },
    y: {
      display: false,
      grid: { display: false },
      border: { display: false },
    },
  },
  plugins: {
    legend: { display: false },
    tooltip: {
      displayColors: false,
      backgroundColor: theme.ink900,
      titleColor: theme.surface,
      bodyColor: theme.surface,
      padding: 8,
      callbacks: {
        title: (context) => visibleItems.value[context[0]?.dataIndex ?? 0]?.label ?? '',
        label: (context) => {
          const item = visibleItems.value[context.dataIndex]
          if (!item) return ''
          return `${formatNumber(item.count)} 篇 · ${formatPct(item.pct)}`
        },
      },
    },
  },
}
</script>

<template>
  <div class="barlist" role="img" :aria-label="ariaLabel">
    <span class="sr-only">{{ summary }}</span>

    <template v-if="!empty">
      <div class="barlist__plot" aria-hidden="true" :style="{ height: `${plotHeight}px` }">
        <Bar :data="chartData" :options="chartOptions" />
      </div>

      <ul class="barlist__rows">
        <li
          v-for="item in visibleItems"
          :key="item.label"
          class="row"
          :style="{ height: `${ROW_HEIGHT}px` }"
        >
          <span class="row__label" :title="item.label">{{ item.label }}</span>
          <span class="row__bar" aria-hidden="true" />
          <span class="row__meta">
            <span class="row__value num">{{ formatNumber(item.count) }}</span>
            <span class="row__pct num">{{ formatPct(item.pct) }}</span>
          </span>
        </li>

        <li v-if="other" class="row" :style="{ height: `${ROW_HEIGHT}px` }">
          <button type="button" class="row__other" @click="emit('other-click')">
            <span class="row__label">{{ other.label }} {{ other.count }} 项</span>
            <span class="row__bar" aria-hidden="true">
              <span class="row__track">
                <span
                  class="row__fill"
                  :style="{ width: `${otherPct}%`, backgroundColor: theme.ink300 }"
                />
              </span>
            </span>
            <span class="row__meta">
              <span class="row__value num">合计 {{ formatNumber(other.total) }} 篇</span>
              <span class="row__pct num">{{ formatPct(otherPct) }}</span>
            </span>
          </button>
        </li>
      </ul>
    </template>

    <p v-else class="chart-empty">暂无数据</p>
  </div>
</template>

<style scoped>
.barlist {
  --label-w: 42%;
  --value-w: 120px;
  position: relative;
}

.barlist__plot {
  position: absolute;
  top: 0;
  left: calc(var(--label-w) + var(--sp-2));
  right: calc(var(--value-w) + var(--sp-2));
  z-index: 0;
}

.barlist__rows {
  position: relative;
  z-index: 1;
  list-style: none;
  margin: 0;
  padding: 0;
  pointer-events: none;
}

.row {
  display: flex;
  align-items: center;
  gap: var(--sp-2);
  font-size: var(--fs-sm);
  color: var(--ink-700);
}

.row__label {
  flex: 0 0 var(--label-w);
  max-width: var(--label-w);
  overflow: hidden;
  white-space: nowrap;
  text-overflow: ellipsis;
}

.row__bar {
  flex: 1 1 auto;
  min-width: 0;
  display: block;
}

.row__meta {
  flex: 0 0 var(--value-w);
  display: flex;
  align-items: center;
  justify-content: flex-end;
  gap: var(--sp-2);
}

.row__value {
  font-weight: 600;
  color: var(--ink-900);
  white-space: nowrap;
}

.row__pct {
  flex: 0 0 44px;
  text-align: right;
  color: var(--ink-500);
}

.row__other {
  display: flex;
  align-items: center;
  gap: var(--sp-2);
  width: 100%;
  height: 100%;
  pointer-events: auto;
  text-align: left;
  color: var(--ink-700);
  border-radius: var(--r-sm);
  transition: background-color var(--dur-fast) var(--ease-out);
}

.row__other:hover {
  background: var(--surface-sunken);
}

.row__track {
  display: block;
  height: 6px;
  border-radius: 3px;
  background: var(--ink-100);
  overflow: hidden;
}

.row__fill {
  display: block;
  height: 100%;
  border-radius: 3px;
}

.chart-empty {
  padding: var(--sp-6) 0;
  text-align: center;
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

@media (max-width: 640px) {
  .barlist {
    --label-w: 45%;
    --value-w: 100px;
  }
}
</style>
