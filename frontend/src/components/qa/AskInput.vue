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
  <div ref="root" class="ask">
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
          {{ version || '全部版本' }} <span class="caret">▾</span>
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
      <button type="button" class="send" :disabled="loading || !question.trim()" @click="doSubmit">
        {{ loading ? '检索中…' : '提问' }}
      </button>
    </div>
  </div>
</template>

<style scoped>
/* 输入条：白面 + hairline，focus 出现 1px 陶土 ring（--ring） */
.ask {
  background: var(--surface);
  border: 1px solid var(--hairline);
  border-radius: var(--r-lg);
  padding: var(--sp-3) var(--sp-4);
  transition:
    box-shadow var(--dur-fast) var(--ease-out),
    border-color var(--dur-fast) var(--ease-out);
}

.ask:focus-within {
  box-shadow: var(--ring);
}

.ta {
  width: 100%;
  resize: none;
  border: none;
  border-radius: var(--r-md);
  background: transparent;
  color: var(--ink-800);
  padding: 2px 0;
  font-size: var(--fs-lg);
  line-height: 1.7;
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
  gap: var(--sp-3);
  margin-top: var(--sp-2);
  padding-top: var(--sp-2);
  border-top: 1px solid var(--hairline);
  flex-wrap: wrap;
}

.vselect {
  position: relative;
}

/* 版本下拉：次要控件（文字 + 小箭头，无边框感） */
.pill {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  border: none;
  background: transparent;
  padding: 2px 2px;
  font-size: var(--fs-sm);
  color: var(--ink-500);
  cursor: pointer;
  font-family: inherit;
  transition: color var(--dur-fast) var(--ease-out);
}

.pill:hover,
.pill.on {
  color: var(--ink-900);
}

.caret {
  font-size: 10px;
  line-height: 1;
}

.vmenu {
  position: absolute;
  z-index: 20;
  bottom: calc(100% + 6px);
  left: 0;
  min-width: 140px;
  max-height: 220px;
  overflow: auto;
  border-radius: var(--r-md);
  border: 1px solid var(--hairline);
  background: var(--surface);
  padding: 4px;
  box-shadow: var(--shadow-overlay-sm);
}

.opt {
  display: block;
  width: 100%;
  text-align: left;
  border: none;
  background: transparent;
  color: var(--ink-700);
  font-size: var(--fs-sm);
  font-family: inherit;
  padding: 7px 10px;
  border-radius: var(--r-sm);
  cursor: pointer;
}

.opt:hover {
  background: var(--surface-sunken);
}

.opt.cur {
  color: var(--brand-600);
  font-weight: 600;
}

.drop-enter-active,
.drop-leave-active {
  transition:
    opacity var(--dur-fast) var(--ease-out),
    transform var(--dur-fast) var(--ease-out);
}

.drop-enter-from,
.drop-leave-to {
  opacity: 0;
  transform: translateY(-4px);
}

/* 发送：深墨实底（--ink-900），hover --ink-800 */
.send {
  margin-left: auto;
  border: none;
  border-radius: var(--r-md);
  padding: 8px 18px;
  font-size: var(--fs-sm);
  font-weight: 600;
  color: #fff;
  font-family: inherit;
  background: var(--ink-900);
  cursor: pointer;
  transition: background-color var(--dur-fast) var(--ease-out);
}

.send:hover:not(:disabled) {
  background: var(--ink-800);
}

.send:active:not(:disabled) {
  background: var(--ink-700);
}

.send:disabled {
  opacity: 0.45;
  cursor: not-allowed;
}
</style>
