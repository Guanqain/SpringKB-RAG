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
    <div class="label">本会话提问（{{ entries.length }}）</div>
    <div class="chips">
      <button
        v-for="(e, i) in entries"
        :key="e.id"
        type="button"
        class="chip"
        :class="{ cur: e.id === activeId }"
        :title="e.question"
        @click="emit('select', e.id)"
      >
        <span class="no">{{ entries.length - i }}</span>
        {{ e.question }}
      </button>
    </div>
  </div>
</template>

<style scoped>
.hist {
  margin-top: 4px;
}

.label {
  font-size: 12px;
  color: var(--ink-500);
  margin-bottom: 8px;
}

.chips {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
}

.chip {
  max-width: 260px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  border: 1px solid var(--ink-200);
  background: #fff;
  color: var(--ink-600);
  border-radius: 999px;
  padding: 5px 12px;
  font-size: 12px;
  font-family: inherit;
  cursor: pointer;
  transition: border-color 0.12s ease, background 0.12s ease, color 0.12s ease;
}

.chip:hover {
  border-color: var(--brand-400);
  color: var(--brand-700);
  background: var(--brand-50);
}

.chip.cur {
  border-color: var(--brand-400);
  background: var(--brand-50);
  color: var(--brand-700);
  font-weight: 600;
}

.no {
  font-family: ui-monospace, Consolas, monospace;
  color: var(--ink-400);
  margin-right: 4px;
}
</style>
