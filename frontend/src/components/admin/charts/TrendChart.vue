<script setup lang="ts">
import { computed } from 'vue'
import type { ChartData, ChartOptions } from 'chart.js'
import { Chart as VueChart } from 'vue-chartjs'
import {
  formatNumber,
  readTheme,
  registerCharts,
  withAlpha,
} from './theme'

interface TrendPoint {
  date: string
  newDocs: number
  qaCount: number
}

const props = withDefaults(
  defineProps<{
    points: TrendPoint[]
    days: number
    ariaLabel: string
    height?: number
  }>(),
  { height: 260 },
)

registerCharts()

const theme = readTheme()

const showAllLabels = computed(() => props.days <= 7)

const empty = computed(
  () =>
    props.points.length === 0 ||
    props.points.every((point) => point.newDocs === 0 && point.qaCount === 0),
)

function formatDay(date: string): string {
  const text = String(date ?? '')
  return text.length >= 10 ? text.slice(5, 10) : text
}

const labels = computed(() => props.points.map((point) => formatDay(point.date)))

const totalNewDocs = computed(() =>
  props.points.reduce((sum, point) => sum + (Number.isFinite(point.newDocs) ? point.newDocs : 0), 0),
)

const totalQa = computed(() =>
  props.points.reduce((sum, point) => sum + (Number.isFinite(point.qaCount) ? point.qaCount : 0), 0),
)

const summary = computed(() => {
  if (empty.value) return `${props.ariaLabel}：暂无数据`
  const start = props.points[0]?.date ?? ''
  const end = props.points[props.points.length - 1]?.date ?? ''
  return `${props.ariaLabel}：${start} 至 ${end}，新增文档合计 ${formatNumber(
    totalNewDocs.value,
  )} 篇，问答合计 ${formatNumber(totalQa.value)} 次`
})

const chartData = computed<ChartData<'bar' | 'line', number[], string>>(() => ({
  labels: labels.value,
  datasets: [
    {
      type: 'bar',
      label: '新增文档',
      data: props.points.map((point) => point.newDocs),
      backgroundColor: withAlpha(theme.brand, 0.45),
      hoverBackgroundColor: withAlpha(theme.brand, 0.65),
      borderWidth: 0,
      borderRadius: 2,
      borderSkipped: false,
      categoryPercentage: 0.75,
      barPercentage: 0.55,
    },
    {
      type: 'line',
      label: '问答次数',
      data: props.points.map((point) => point.qaCount),
      borderColor: theme.ink700,
      backgroundColor: 'transparent',
      borderWidth: 1.5,
      tension: 0.3,
      fill: false,
      pointRadius: 2.5,
      pointHoverRadius: 4,
      pointBackgroundColor: theme.ink700,
      pointBorderColor: theme.surface,
      pointBorderWidth: 1,
    },
  ],
}))

const chartOptions: ChartOptions<'bar'> = {
  responsive: true,
  maintainAspectRatio: false,
  layout: { padding: 0 },
  interaction: { mode: 'index', intersect: false },
  scales: {
    x: {
      grid: { display: false },
      border: { display: false },
      ticks: {
        autoSkip: false,
        maxRotation: 0,
        minRotation: 0,
        color: theme.ink500,
        font: { size: 11 },
        callback: (value, index) =>
          showAllLabels.value || index % 5 === 0 ? labels.value[index] ?? '' : '',
      },
    },
    y: {
      beginAtZero: true,
      grid: {
        color: withAlpha(theme.hairline, 0.6),
        drawTicks: false,
        lineWidth: 1,
      },
      border: { display: false },
      ticks: {
        maxTicksLimit: 4,
        precision: 0,
        color: theme.ink500,
        font: { size: 11 },
        padding: 6,
      },
    },
  },
  plugins: {
    legend: {
      position: 'top',
      align: 'end',
      labels: {
        usePointStyle: true,
        boxWidth: 8,
        boxHeight: 8,
        color: theme.ink500,
        font: { size: 11 },
        padding: 12,
      },
    },
    tooltip: {
      displayColors: false,
      backgroundColor: theme.ink900,
      titleColor: theme.surface,
      bodyColor: theme.surface,
      padding: 8,
      callbacks: {
        title: (context) => labels.value[context[0]?.dataIndex ?? 0] ?? '',
        label: (context) => {
          const unit = context.datasetIndex === 0 ? '篇' : '次'
          return `${context.dataset.label ?? ''}：${formatNumber(Number(context.raw))} ${unit}`
        },
      },
    },
  },
}
</script>

<template>
  <div class="trend" role="img" :aria-label="ariaLabel">
    <span class="sr-only">{{ summary }}</span>

    <div v-if="!empty" class="trend__plot" :style="{ height: `${height}px` }">
      <VueChart type="bar" :data="chartData" :options="chartOptions" />
    </div>

    <p v-else class="chart-empty" :style="{ minHeight: `${height}px` }">暂无数据</p>
  </div>
</template>

<style scoped>
.trend {
  position: relative;
}

.trend__plot {
  width: 100%;
}

.chart-empty {
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
