<script setup lang="ts">
import { computed } from 'vue'
import type { ChartData, ChartOptions, Plugin } from 'chart.js'
import { Doughnut } from 'vue-chartjs'
import {
  formatNumber,
  formatPct,
  monoTones,
  readTheme,
  registerCharts,
} from './theme'

interface DonutItem {
  label: string
  count: number
  pct: number
}

const props = withDefaults(
  defineProps<{
    items: DonutItem[]
    ariaLabel: string
    height?: number
  }>(),
  { height: 220 },
)

registerCharts()

const theme = readTheme()

const total = computed(() =>
  props.items.reduce((sum, item) => sum + (Number.isFinite(item.count) ? item.count : 0), 0),
)

const empty = computed(() => props.items.length === 0 || total.value <= 0)

const colors = computed(() => monoTones(props.items.length))

const summary = computed(() => {
  if (empty.value) return `${props.ariaLabel}：暂无数据`
  const detail = props.items
    .map((item) => `${item.label} ${formatNumber(item.count)}（${formatPct(item.pct)}）`)
    .join('，')
  return `${props.ariaLabel}：${detail}`
})

const chartData = computed<ChartData<'doughnut', number[], string>>(() => ({
  labels: props.items.map((item) => item.label),
  datasets: [
    {
      data: props.items.map((item) => item.count),
      backgroundColor: colors.value,
      borderWidth: 0,
      spacing: 2,
      hoverOffset: 0,
    },
  ],
}))

const chartOptions: ChartOptions<'doughnut'> = {
  responsive: true,
  maintainAspectRatio: false,
  cutout: '68%',
  layout: { padding: 0 },
  plugins: {
    legend: { display: false },
    tooltip: {
      displayColors: false,
      backgroundColor: theme.ink900,
      titleColor: theme.surface,
      bodyColor: theme.surface,
      padding: 8,
      callbacks: {
        title: (context) => props.items[context[0]?.dataIndex ?? 0]?.label ?? '',
        label: (context) => {
          const item = props.items[context.dataIndex]
          if (!item) return ''
          return `${formatNumber(item.count)} · ${formatPct(item.pct)}`
        },
      },
    },
  },
}

// 中心总量：自定义 plugin 绘制等宽数字，不额外引入 datalabels 插件
const centerTotalPlugin: Plugin<'doughnut'> = {
  id: 'springkbCenterTotal',
  afterDraw(chart) {
    const { ctx, chartArea } = chart
    if (!chartArea) return

    const value = total.value
    if (!value) return

    const size = Math.max(
      16,
      Math.min(26, Math.round((chartArea.bottom - chartArea.top) * 0.18)),
    )

    ctx.save()
    ctx.textAlign = 'center'
    ctx.textBaseline = 'middle'
    ctx.fillStyle = theme.ink900
    ctx.font = `600 ${size}px ${theme.fontFamily}`
    ctx.fillText(
      formatNumber(value),
      (chartArea.left + chartArea.right) / 2,
      (chartArea.top + chartArea.bottom) / 2,
    )
    ctx.restore()
  },
}
</script>

<template>
  <div class="donut" role="img" :aria-label="ariaLabel">
    <span class="sr-only">{{ summary }}</span>

    <template v-if="!empty">
      <div class="donut__plot" :style="{ width: `${height}px`, height: `${height}px` }">
        <Doughnut :data="chartData" :options="chartOptions" :plugins="[centerTotalPlugin]" />
      </div>

      <ul class="donut__legend">
        <li v-for="(item, index) in items" :key="item.label" class="donut__item">
          <span
            class="donut__swatch"
            aria-hidden="true"
            :style="{ backgroundColor: colors[index] ?? 'var(--ink-300)' }"
          />
          <span class="donut__label" :title="item.label">{{ item.label }}</span>
          <span class="donut__count num">{{ formatNumber(item.count) }}</span>
          <span class="donut__pct num">{{ formatPct(item.pct) }}</span>
        </li>
      </ul>
    </template>

    <p v-else class="chart-empty" :style="{ minHeight: `${height}px` }">暂无数据</p>
  </div>
</template>

<style scoped>
.donut {
  display: flex;
  align-items: center;
  gap: var(--sp-5);
  flex-wrap: wrap;
}

.donut__plot {
  flex: none;
}

.donut__legend {
  list-style: none;
  flex: 1 1 180px;
  min-width: 160px;
  display: flex;
  flex-direction: column;
  gap: var(--sp-2);
}

.donut__item {
  display: grid;
  grid-template-columns: 8px minmax(0, 1fr) auto 52px;
  align-items: center;
  gap: var(--sp-2);
  font-size: var(--fs-sm);
  color: var(--ink-700);
}

.donut__swatch {
  width: 8px;
  height: 8px;
  border-radius: 2px;
}

.donut__label {
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.donut__count {
  font-weight: 600;
  color: var(--ink-900);
}

.donut__pct {
  color: var(--ink-500);
  text-align: right;
}

.chart-empty {
  flex: 1 1 100%;
  display: flex;
  align-items: center;
  justify-content: center;
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
