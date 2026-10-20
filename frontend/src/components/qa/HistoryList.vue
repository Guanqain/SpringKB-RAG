<script setup lang="ts">
import type { QaHistoryEntry } from '@/types/qa'

defineProps<{
  entries: QaHistoryEntry[]
  activeId: number | null
}>()

const emit = defineEmits<{
  select: [id: number]
}>()
</script>

<template>
  <div v-if="entries.length" class="hist">
    <div class="hist__label">本会话提问（{{ entries.length }}）</div>
    <div class="hist__list">
      <button
        v-for="(e, i) in entries"
        :key="e.id"
        type="button"
        class="item"
        :class="{ cur: e.id === activeId }"
        :title="e.question"
        @click="emit('select', e.id)"
      >
        <span class="no num">{{ i + 1 }}</span>
        <span class="hist__q">{{ e.question }}</span>
      </button>
    </div>
  </div>
</template>

<style scoped>
/* 次级列表：hairline 分隔，无卡片阴影、无彩色底 */
.hist {
  margin-bottom: var(--sp-2);
}

.hist__label {
  font-size: var(--fs-xs);
  color: var(--ink-400);
  margin-bottom: var(--sp-2);
}

.hist__list {
  display: flex;
  flex-direction: column;
}

.item {
  display: flex;
  align-items: baseline;
  gap: var(--sp-2);
  width: 100%;
  text-align: left;
  padding: 7px var(--sp-3);
  border-left: 2px solid transparent;
  color: var(--ink-600);
  font-size: var(--fs-sm);
  line-height: 1.7;
  transition: color var(--dur-fast) var(--ease-out), border-color var(--dur-fast) var(--ease-out);
}

.item + .item {
  border-top: 1px solid var(--hairline);
}

.item:hover {
  color: var(--ink-900);
}

.item.cur {
  border-left-color: var(--brand-600);
  color: var(--brand-600);
  font-weight: 600;
}

.no {
  flex-shrink: 0;
  font-size: var(--fs-xs);
  color: var(--ink-400);
}

.hist__q {
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
</style>
