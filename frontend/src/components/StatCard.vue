<script setup lang="ts">
import { computed } from 'vue'

const props = defineProps<{
  label: string
  value: number
  hint?: string
  tone?: 'primary' | 'muted' | 'success' | 'danger'
}>()

const toneClass = computed(() => `stat-card--${props.tone ?? 'primary'}`)
</script>

<template>
  <div class="stat-card" :class="toneClass">
    <span class="stat-label">{{ label }}</span>
    <span class="stat-value mono">{{ value.toLocaleString() }}</span>
    <span v-if="hint" class="stat-hint">{{ hint }}</span>
  </div>
</template>

<style scoped>
.stat-card {
  background: var(--kb-surface);
  border: 1px solid var(--kb-border);
  border-radius: 10px;
  padding: 18px 20px;
  display: flex;
  flex-direction: column;
  gap: 6px;
  transition: box-shadow 0.2s ease, transform 0.2s ease;
}

.stat-card:hover {
  box-shadow: 0 8px 24px rgba(15, 118, 110, 0.1);
  transform: translateY(-2px);
}

.stat-label {
  font-size: 13px;
  color: var(--kb-text-sub);
}

.stat-value {
  font-size: 30px;
  font-weight: 700;
  line-height: 1.1;
  color: var(--kb-text);
}

.stat-card--primary .stat-value {
  color: var(--kb-primary);
}

.stat-card--success .stat-value {
  color: #059669;
}

.stat-card--danger .stat-value {
  color: var(--kb-danger);
}

.stat-card--muted .stat-value {
  color: var(--kb-text);
}

.stat-hint {
  font-size: 12px;
  color: var(--kb-text-sub);
}
</style>
