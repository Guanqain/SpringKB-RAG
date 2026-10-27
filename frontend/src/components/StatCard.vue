<script setup lang="ts">
import { computed } from 'vue'

interface DeltaText {
  text: string
  direction: 'up' | 'down' | 'flat' | 'none'
}

const props = withDefaults(
  defineProps<{
    label: string
    value: number
    hint?: string
    tone?: 'primary' | 'muted' | 'success' | 'danger'
    /** 百分比等数值的显示后缀，如 % */
    suffix?: string
    /** 数值保留小数位；默认 0 */
    decimals?: number
    /** 口径说明：右上角问号 tooltip 的文案 */
    tooltip?: string
    /** 环比文案；prev 为 0 时 direction='none'，文本为「—」 */
    delta?: DeltaText | null
    /** 卡片可点击跳转 */
    clickable?: boolean
  }>(),
  { decimals: 0 },
)

const emit = defineEmits<{
  (event: 'click'): void
}>()

const toneClass = computed(() => `stat-card--${props.tone ?? 'primary'}`)
// 信号表达：仅「异常 / 待办」类信号（danger）落在陶土小圆点与数字上，不做彩色底块
const hasSignal = computed(() => props.tone === 'danger')

const formattedValue = computed(() => {
  const value = Number.isFinite(props.value) ? props.value : 0
  return value.toLocaleString('zh-CN', {
    minimumFractionDigits: props.decimals,
    maximumFractionDigits: props.decimals,
  })
})

function activate(): void {
  if (props.clickable) emit('click')
}
</script>

<template>
  <component
    :is="clickable ? 'button' : 'div'"
    class="stat-card"
    :class="[toneClass, { 'stat-card--clickable': clickable }]"
    :type="clickable ? 'button' : undefined"
    @click="activate"
  >
    <div class="stat-top">
      <span class="stat-label">
        <span v-if="hasSignal" class="stat-dot" aria-hidden="true" />
        {{ label }}
      </span>
      <el-tooltip v-if="tooltip" :content="tooltip" placement="top" :show-after="120">
        <span class="stat-help" tabindex="0" role="note" :aria-label="`口径说明：${tooltip}`" @click.stop>?</span>
      </el-tooltip>
    </div>

    <span class="stat-value num">
      {{ formattedValue }}<span v-if="suffix" class="stat-suffix">{{ suffix }}</span>
    </span>

    <span v-if="delta" class="stat-delta num" :class="`stat-delta--${delta.direction}`">
      {{ delta.text }}
    </span>
    <span v-if="hint" class="stat-hint" :title="hint">{{ hint }}</span>
  </component>
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
  min-width: 0;
  text-align: left;
  transition: border-color var(--dur-fast) var(--ease-out);
}

.stat-card:hover {
  border-color: var(--hairline-strong);
}

.stat-card--clickable {
  cursor: pointer;
  width: 100%;
  font-family: inherit;
  color: inherit;
}

.stat-card--clickable:hover {
  border-color: var(--ink-400);
}

.stat-top {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: var(--sp-2);
  min-width: 0;
}

.stat-label {
  display: inline-flex;
  align-items: center;
  gap: var(--sp-2);
  font-size: var(--fs-sm);
  color: var(--ink-500);
  min-width: 0;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.stat-dot {
  width: 6px;
  height: 6px;
  border-radius: 50%;
  background: var(--brand-600);
  flex: none;
}

.stat-help {
  flex: none;
  width: 16px;
  height: 16px;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  border: 1px solid var(--hairline-strong);
  border-radius: 50%;
  font-size: 10px;
  line-height: 1;
  color: var(--ink-500);
  cursor: help;
  transition:
    color var(--dur-fast) var(--ease-out),
    border-color var(--dur-fast) var(--ease-out);
}

.stat-help:hover,
.stat-help:focus-visible {
  color: var(--brand-600);
  border-color: var(--brand-600);
}

.stat-value {
  font-family: var(--font-num);
  font-variant-numeric: tabular-nums;
  font-size: var(--fs-4xl);
  font-weight: 600;
  line-height: 1.15;
  color: var(--ink-900);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.stat-suffix {
  margin-left: 2px;
  font-size: var(--fs-xl);
  font-weight: 500;
  color: var(--ink-600);
}

.stat-card--danger .stat-value {
  color: var(--brand-600);
}

.stat-delta {
  font-size: var(--fs-xs);
  color: var(--ink-500);
}

.stat-delta--up {
  color: var(--ink-700);
}

.stat-delta--down {
  color: var(--brand-600);
}

.stat-hint {
  font-size: var(--fs-xs);
  color: var(--ink-500);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.stat-help:focus-visible {
  outline: 1px solid var(--brand-600);
  outline-offset: 2px;
}
</style>
