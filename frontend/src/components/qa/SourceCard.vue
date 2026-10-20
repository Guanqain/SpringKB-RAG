<script setup lang="ts">
import type { SourceRef } from '@/types/qa'

const props = defineProps<{
  source: SourceRef
  highlight: boolean
}>()
</script>

<template>
  <div class="gb" :class="[props.source.source, { hl: props.highlight }]">
    <div class="inner">
      <div class="head">
        <span class="tag" :class="props.source.source">
          {{ props.source.source === 'official' ? '官方' : '社区' }}
        </span>
        <span v-if="props.source.version" class="vt">{{ props.source.version }}</span>
        <span class="score">{{ props.source.score.toFixed(2) }}</span>
      </div>
      <div class="ti">{{ props.source.title }}</div>
      <div class="me">
        {{ [props.source.section, props.source.headingPath].filter(Boolean).join(' › ') || '—' }}
      </div>
      <p class="sn">{{ props.source.snippet }}</p>
    </div>
  </div>
</template>

<style scoped>
/* 细线描边 = 外层低饱和渐变底 + 内层玻璃面
 * 渐变饱和度降低 → 不再喧宾夺主，视觉焦点回到答案卡 */
.gb {
  padding: 1px;
  border-radius: 13px;
  transition: transform 0.12s ease, box-shadow 0.18s ease;
}

.gb:hover {
  transform: translateY(-2px);
  box-shadow: var(--shadow-md);
}

/* official：暖橘细描边 */
.gb.official {
  background: linear-gradient(135deg, rgba(249, 115, 22, 0.6), rgba(253, 186, 116, 0.45));
}

/* community：青紫细描边 */
.gb.community {
  background: linear-gradient(135deg, rgba(8, 145, 178, 0.5), rgba(124, 58, 237, 0.45));
}

/* 引用定位：聚焦脉冲（仅外框高亮，不影响内部） */
.gb.hl {
  animation: qa-pulse 1.2s ease;
  box-shadow: 0 0 0 0 rgba(249, 115, 22, 0.5);
}

@keyframes qa-pulse {
  0% {
    box-shadow: 0 0 0 0 rgba(249, 115, 22, 0.55);
  }
  100% {
    box-shadow: 0 0 0 10px rgba(249, 115, 22, 0);
  }
}

.inner {
  border-radius: 12px;
  background: #fff;
  padding: 12px 14px;
}

.head {
  display: flex;
  align-items: center;
  gap: 8px;
}

/* 标签：从彩色圆底 → 低饱和透明底 + 细边，文字提亮 */
.tag {
  display: inline-block;
  border-radius: 4px;
  padding: 0 7px;
  font-size: 11px;
  font-weight: 600;
  line-height: 17px;
}

.tag.official {
  color: var(--brand-700);
  border: 1px solid var(--brand-200);
  background: var(--brand-50);
}

.tag.community {
  color: #0e7490;
  border: 1px solid rgba(8, 145, 178, 0.28);
  background: #ecfeff;
}

/* 版本 chip：更低调 */
.vt {
  display: inline-block;
  border-radius: 3px;
  padding: 0 6px;
  font-size: 11px;
  border: 1px solid var(--ink-200);
  color: var(--ink-500);
  background: var(--ink-50);
  font-family: ui-monospace, Consolas, monospace;
  line-height: 16px;
}

/* 相似度：移到右侧但更暗，退居辅助信息 */
.score {
  margin-left: auto;
  font-size: 11px;
  color: var(--ink-400);
  font-family: ui-monospace, Consolas, monospace;
}

/* 标题 */
.ti {
  font-weight: 700;
  margin-top: var(--sp-1);
  font-size: var(--fs-md);
  color: var(--ink-900);
}

/* heading path */
.me {
  font-size: 12px;
  color: var(--ink-500);
  margin-top: 2px;
}

/* snippet */
.sn {
  font-size: 12px;
  color: var(--ink-600);
  margin: 6px 0 0;
  display: -webkit-box;
  -webkit-line-clamp: 2;
  -webkit-box-orient: vertical;
  overflow: hidden;
}
</style>
