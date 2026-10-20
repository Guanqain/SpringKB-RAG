<script setup lang="ts">
import { nextTick, onBeforeUnmount, onMounted, ref } from 'vue'

const props = defineProps<{
  versions: string[]
  loading: boolean
}>()

const emit = defineEmits<{
  submit: [question: string, version?: string]
}>()

const question = ref('')
const version = ref('')
const versionOpen = ref(false)
const ta = ref<HTMLTextAreaElement>()
const root = ref<HTMLElement>()

function resize() {
  const el = ta.value
  if (!el) return
  el.style.height = 'auto'
  el.style.height = `${Math.min(el.scrollHeight, 160)}px`
}

function doSubmit() {
  const q = question.value.trim()
  if (!q || props.loading) return
  emit('submit', q, version.value || undefined)
  question.value = ''
  nextTick(resize)
}

function onKeydown(e: KeyboardEvent) {
  // Enter 提问，Shift+Enter 换行
  if (e.key === 'Enter' && !e.shiftKey) {
    e.preventDefault()
    doSubmit()
  }
}

function onDocClick(e: MouseEvent) {
  if (root.value && !root.value.contains(e.target as Node)) versionOpen.value = false
}

function pick(v: string) {
  version.value = v
  versionOpen.value = false
}

function focus() {
  ta.value?.focus()
}

function openVersion() {
  versionOpen.value = true
}

defineExpose({ focus, openVersion })

onMounted(() => document.addEventListener('click', onDocClick))
onBeforeUnmount(() => document.removeEventListener('click', onDocClick))
</script>

<template>
  <div ref="root" class="ask glass">
    <textarea
      ref="ta"
      v-model="question"
      class="ta"
      rows="2"
      placeholder="问问 Spring / Vue 文档里的问题…（Enter 提问，Shift+Enter 换行）"
      :disabled="loading"
      @keydown="onKeydown"
      @input="resize"
    />
    <div class="row">
      <div class="vselect">
        <button type="button" class="pill" :class="{ on: versionOpen }" @click.stop="versionOpen = !versionOpen">
          版本：{{ version || '全部' }} ▾
        </button>
        <transition name="drop">
          <div v-if="versionOpen" class="vmenu">
            <button type="button" class="opt" :class="{ cur: version === '' }" @click="pick('')">全部版本</button>
            <button
              v-for="v in versions"
              :key="v"
              type="button"
              class="opt"
              :class="{ cur: version === v }"
              @click="pick(v)"
            >
              {{ v }}
            </button>
          </div>
        </transition>
      </div>
      <span class="hint">答案附真实引用 · 未收录会明确告知</span>
      <button type="button" class="btn" :disabled="loading || !question.trim()" @click="doSubmit">
        <span v-if="loading" class="spinner" />
        {{ loading ? '检索中…' : '提问 →' }}
      </button>
    </div>
  </div>
</template>

<style scoped>
.ask {
  padding: var(--sp-4);
}

.ta {
  width: 100%;
  resize: none;
  border: none;
  border-radius: var(--r-md);
  background: transparent;
  color: var(--ink-800);
  padding: 4px 2px;
  font: var(--fs-lg) / 1.6 inherit;
  font-family: inherit;
  outline: none;
}

.ta::placeholder {
  color: var(--ink-400);
}

.ta:disabled {
  opacity: 0.6;
}

.row {
  display: flex;
  align-items: center;
  gap: var(--sp-2);
  margin-top: var(--sp-3);
  padding-top: var(--sp-3);
  border-top: 1px solid var(--ink-100);
  flex-wrap: wrap;
}

.vselect {
  position: relative;
}

.pill {
  border: 1px solid var(--ink-200);
  border-radius: var(--r-md);
  padding: 4px 12px;
  font-size: var(--fs-sm);
  color: var(--ink-600);
  background: #fff;
  cursor: pointer;
  font-family: inherit;
  transition: border-color 0.12s ease, color 0.12s ease;
}

.pill.on,
.pill:hover {
  border-color: var(--brand-400);
  color: var(--brand-700);
}

.vmenu {
  position: absolute;
  z-index: 20;
  top: calc(100% + 6px);
  left: 0;
  min-width: 140px;
  max-height: 220px;
  overflow: auto;
  border-radius: var(--r-md);
  border: 1px solid var(--ink-200);
  background: #fff;
  padding: 4px;
  box-shadow: var(--shadow-lg);
}

.opt {
  display: block;
  width: 100%;
  text-align: left;
  border: none;
  background: transparent;
  color: var(--ink-700);
  font-size: 13px;
  font-family: inherit;
  padding: 7px 10px;
  border-radius: var(--r-sm);
  cursor: pointer;
}

.opt:hover {
  background: var(--brand-50);
}

.opt.cur {
  color: var(--brand-700);
  font-weight: 600;
}

.drop-enter-active,
.drop-leave-active {
  transition: opacity 0.12s ease, transform 0.12s ease;
}

.drop-enter-from,
.drop-leave-to {
  opacity: 0;
  transform: translateY(-4px);
}

.hint {
  font-size: var(--fs-sm);
  color: var(--ink-400);
}

.btn {
  margin-left: auto;
  border: none;
  border-radius: var(--r-md);
  padding: 8px 18px;
  font-size: var(--fs-sm);
  font-weight: 700;
  color: #fff;
  font-family: inherit;
  background: var(--grad-brand);
  cursor: pointer;
  display: inline-flex;
  align-items: center;
  gap: 6px;
  transition: box-shadow 0.12s ease, transform 0.12s ease;
}

.btn:hover:not(:disabled) {
  box-shadow: 0 4px 18px rgba(249, 115, 22, 0.45);
  transform: translateY(-1px);
}

.btn:disabled {
  opacity: 0.55;
  cursor: not-allowed;
}

.spinner {
  width: 12px;
  height: 12px;
  border: 2px solid rgba(255, 255, 255, 0.4);
  border-top-color: #fff;
  border-radius: 50%;
  animation: spin 0.7s linear infinite;
}

@keyframes spin {
  to {
    transform: rotate(360deg);
  }
}
</style>
