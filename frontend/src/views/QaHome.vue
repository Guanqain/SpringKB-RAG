<script setup lang="ts">
import { computed, nextTick, onBeforeUnmount, onMounted, ref } from 'vue'
import { ask, fetchVersions } from '@/api/qa'
import type { QaHistoryEntry } from '@/types/qa'
import AskInput from '@/components/qa/AskInput.vue'
import AnswerCard from '@/components/qa/AnswerCard.vue'
import FallbackCard from '@/components/qa/FallbackCard.vue'
import HistoryList from '@/components/qa/HistoryList.vue'
import SourceCard from '@/components/qa/SourceCard.vue'

type ViewState = 'idle' | 'loading' | 'answered' | 'fallback' | 'error'

// 示例问法选用经真实数据验证可命中（topScore ≥ 0.65 阈值）的措辞；
// 中文短问法（如「createApp 是做什么的？」）相似度低于阈值会触发兜底，演示体验差
const EXAMPLES = [
  'how to create a vue app with createApp',
  'How to configure actuator endpoints in Spring Boot',
  'How to use ChatClient in Spring AI',
]

const state = ref<ViewState>('idle')
const versions = ref<string[]>([])
const history = ref<QaHistoryEntry[]>([])
const currentId = ref<number | null>(null)
const lastAttempt = ref<{ question: string; version?: string } | null>(null)
const errorMsg = ref('')
const highlightIdx = ref<number | null>(null)
const askRef = ref<InstanceType<typeof AskInput>>()

let seq = 1
let hlTimer: number | undefined

const current = computed(() => history.value.find((e) => e.id === currentId.value) ?? null)
const isLatest = (entry: QaHistoryEntry) => history.value[history.value.length - 1]?.id === entry.id

onMounted(async () => {
  try {
    versions.value = await fetchVersions()
  } catch {
    /* 版本下拉静默降级为「全部版本」 */
  }
})

onBeforeUnmount(() => {
  if (hlTimer) window.clearTimeout(hlTimer)
})

async function submit(question: string, version?: string) {
  if (state.value === 'loading') return
  lastAttempt.value = { question, version }
  state.value = 'loading'
  errorMsg.value = ''
  try {
    const result = await ask(question, version)
    const entry: QaHistoryEntry = { id: seq++, question, version, result, at: Date.now() }
    history.value.push(entry)
    currentId.value = entry.id
    state.value = result.found ? 'answered' : 'fallback'
  } catch (err) {
    state.value = 'error'
    errorMsg.value = err instanceof Error ? err.message : '网络错误，请稍后重试'
  }
}

function retry() {
  if (lastAttempt.value) void submit(lastAttempt.value.question, lastAttempt.value.version)
}

function showEntry(id: number) {
  const entry = history.value.find((e) => e.id === id)
  if (!entry) return
  currentId.value = id
  state.value = entry.result.found ? 'answered' : 'fallback'
}

/** 引用角标点击：高亮对应来源卡（SourceCard 自带脉冲动画） */
function onCite(n: number) {
  // 先置空、下一帧再设值：保证同一角标连续点击时 .hl 类经历「移除→添加」，动画可重新触发
  // （若直接重复赋相同值，Vue diff 视为无变化，CSS animation 不会重播）
  highlightIdx.value = null
  if (hlTimer) window.clearTimeout(hlTimer)
  nextTick(() => {
    highlightIdx.value = n
    hlTimer = window.setTimeout(() => {
      highlightIdx.value = null
    }, 1300)
  })
}

function refine() {
  nextTick(() => askRef.value?.focus())
}

function pickVersion() {
  askRef.value?.openVersion()
}
</script>

<template>
  <div class="qa-page">
    <div class="wrap">
      <div class="head rise d1">
        <h1 class="title">智能问答</h1>
        <p class="sub">自然语言提问，精准引用溯源</p>
      </div>

      <div class="rise d1">
        <AskInput
          ref="askRef"
          :versions="versions"
          :loading="state === 'loading'"
          @submit="submit"
        />
      </div>

      <div v-if="state === 'idle'" class="examples rise d2">
        <span class="ex-label">试试：</span>
        <button v-for="q in EXAMPLES" :key="q" type="button" class="chip" @click="submit(q)">
          {{ q }}
        </button>
      </div>

      <div v-else-if="state === 'loading'" class="glass card skeleton rise d2">
        <div class="sk-line w80" />
        <div class="sk-line w95" />
        <div class="sk-line w60" />
      </div>

      <div v-else-if="state === 'error'" class="glass card err rise d2">
        <b>出了点问题</b>
        <p>{{ errorMsg }}</p>
        <button type="button" class="gh" @click="retry">重试</button>
      </div>

      <template v-else-if="current">
        <div v-if="state === 'fallback'" class="rise d2">
          <FallbackCard @refine="refine" @version="pickVersion" />
        </div>

        <template v-else>
          <div class="glass card answer rise d2">
            <div class="q-row">
              <span class="q-tag">问</span>
              <span class="q-text">{{ current.question }}</span>
              <span v-if="current.version" class="q-ver">{{ current.version }}</span>
            </div>
            <div class="divider" />
            <div class="a-row">
              <span class="q-tag a">答</span>
              <div class="a-body">
                <AnswerCard
                  :answer="current.result.answer"
                  :total-sources="current.result.sources.length"
                  :animate="isLatest(current)"
                  @cite="onCite"
                />
              </div>
            </div>
          </div>

          <section v-if="current.result.sources.length" class="rise d2">
            <div class="sec-label">引用来源（{{ current.result.sources.length }}）· 按加权顺序</div>
            <div class="sources">
              <SourceCard
                v-for="s in current.result.sources"
                :key="s.index"
                :source="s"
                :highlight="highlightIdx === s.index"
              />
            </div>
          </section>

          <!-- 相关推荐：后端能力（文档级聚合 sourcesV2 + 关联推荐）未就绪，按规范做空态占位，不伪造数据 -->
          <section class="rise d2">
            <div class="sec-label sec-label--icon">
              <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round">
                <path d="M9.663 17h4.673M12 3v1m6.364 1.636-.707.707M21 12h-1M4 12H3m3.343-5.657-.707-.707m2.828 9.9a5 5 0 1 1 7.072 0l-.548.547A3.374 3.374 0 0 0 14 18.469V19a2 2 0 1 1-4 0v-.531c0-.895-.356-1.754-.988-2.386z" />
              </svg>
              相关推荐
            </div>
            <div class="rec-empty">该能力待后端就绪，暂不提供推荐内容</div>
          </section>

          <!-- 追问建议：复用示例问法作为可点击的 UI 建议（非业务数据） -->
          <section class="rise d2">
            <div class="sec-label">继续追问：</div>
            <div class="chips">
              <button v-for="q in EXAMPLES" :key="q" type="button" class="chip" @click="submit(q)">
                {{ q }}
              </button>
            </div>
          </section>
        </template>
      </template>

      <div class="rise d3">
        <HistoryList :entries="history" :active-id="currentId" @select="showEntry" />
      </div>
    </div>
  </div>
</template>

<style scoped>
/* ===== 浅色暖调 · 作用域 token（重定义 --qa-* 供子组件与全局 Markdown 排版复用） ===== */
.qa-page {
  --qa-bg: var(--page-bg);
  --qa-glass: #ffffff;
  --qa-border: var(--ink-200);
  --qa-text: var(--ink-800);
  --qa-sub: var(--ink-500);
  --qa-primary: var(--brand-500);
  --qa-orange2: var(--brand-400);
  --qa-amber: var(--brand-600);
  --qa-rose: #e11d48;
  --qa-cyan: #0891b2;
  --qa-violet: #7c3aed;

  min-height: 100%;
  background: var(--page-bg);
  color: var(--ink-800);
  padding: var(--sp-12) var(--sp-6) var(--sp-16);
}

.wrap {
  max-width: 760px;
  margin: 0 auto;
  display: flex;
  flex-direction: column;
  gap: var(--sp-6);
}

/* ===== 页头（全局 NavBar 已提供品牌；此处仅居中标题区） ===== */
.head {
  text-align: center;
  margin-bottom: var(--sp-2);
}

.title {
  font-size: var(--fs-4xl);
  font-weight: 800;
  color: var(--ink-900);
  line-height: 1.25;
}

.sub {
  color: var(--ink-500);
  margin-top: var(--sp-2);
  font-size: var(--fs-md);
}

/* ===== 白卡基座（AskInput / FallbackCard 根元素同样吃到该作用域样式） ===== */
.glass {
  background: #fff;
  border: 1px solid var(--ink-200);
  border-radius: var(--r-xl);
  box-shadow: var(--shadow-lg);
}

.card {
  padding: var(--sp-5);
}

/* ===== 示例问题 / 追问 chips ===== */
.examples {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: var(--sp-2);
}

.chips {
  display: flex;
  flex-wrap: wrap;
  gap: var(--sp-2);
}

.ex-label,
.sec-label {
  font-size: var(--fs-sm);
  color: var(--ink-500);
  font-weight: 600;
}

.sec-label {
  text-transform: uppercase;
  letter-spacing: 0.05em;
  margin-bottom: var(--sp-3);
}

.sec-label--icon {
  display: flex;
  align-items: center;
  gap: var(--sp-2);
}

.sec-label--icon svg {
  color: var(--brand-500);
  flex-shrink: 0;
}

.chip {
  display: inline-flex;
  align-items: center;
  gap: var(--sp-1);
  padding: 6px 14px;
  border-radius: 100px;
  background: #fff;
  border: 1px solid var(--ink-200);
  font-size: var(--fs-sm);
  color: var(--ink-600);
  font-family: inherit;
  cursor: pointer;
  transition: all 0.18s var(--ease-out);
}

.chip:hover {
  border-color: var(--brand-400);
  color: var(--brand-700);
  background: var(--brand-50);
  box-shadow: var(--shadow-sm);
}

/* ===== 骨架屏 ===== */
.skeleton {
  min-height: 120px;
}

.sk-line {
  height: 12px;
  border-radius: 6px;
  margin: 14px 0;
  background: linear-gradient(90deg, var(--ink-100), var(--ink-200), var(--ink-100));
  background-size: 200% 100%;
}

.w80 {
  width: 80%;
}

.w95 {
  width: 95%;
}

.w60 {
  width: 60%;
}

@media (prefers-reduced-motion: no-preference) {
  .sk-line {
    animation: shimmer 1.2s linear infinite;
  }

  @keyframes shimmer {
    to {
      background-position: -200% 0;
    }
  }
}

/* ===== 错误态 ===== */
.err {
  text-align: center;
  padding: var(--sp-6);
}

.err b {
  font-size: 15px;
  color: var(--ink-900);
}

.err p {
  color: var(--ink-500);
  font-size: 13px;
  margin: 8px 0 14px;
}

.gh {
  border: 1px solid var(--ink-200);
  background: #fff;
  color: var(--ink-700);
  border-radius: var(--r-md);
  padding: 7px 14px;
  font-size: 13px;
  font-family: inherit;
  cursor: pointer;
  transition: border-color 0.12s ease, color 0.12s ease, background 0.12s ease;
}

.gh:hover {
  border-color: var(--brand-400);
  color: var(--brand-700);
  background: var(--brand-50);
}

/* ===== 回答卡 ===== */
.q-row,
.a-row {
  display: flex;
  gap: 10px;
  align-items: flex-start;
}

.q-tag {
  flex-shrink: 0;
  width: 22px;
  height: 22px;
  border-radius: 7px;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  font-size: 12px;
  font-weight: 700;
  color: var(--brand-700);
  background: var(--brand-50);
  border: 1px solid var(--brand-200);
}

.q-tag.a {
  color: #0e7490;
  background: #ecfeff;
  border-color: #a5f3fc;
}

.q-text {
  font-weight: 600;
  line-height: 1.6;
  color: var(--ink-900);
}

.q-ver {
  margin-left: auto;
  flex-shrink: 0;
  border: 1px solid var(--ink-200);
  border-radius: 999px;
  padding: 1px 9px;
  font-size: 11px;
  color: var(--ink-500);
  background: var(--ink-50);
  font-family: ui-monospace, Consolas, monospace;
}

.divider {
  height: 1px;
  background: var(--ink-100);
  margin: var(--sp-4) 0;
}

.a-body {
  min-width: 0;
  flex: 1;
}

.sources {
  display: flex;
  flex-direction: column;
  gap: var(--sp-2);
}

/* ===== 相关推荐空态占位 ===== */
.rec-empty {
  border: 1px dashed var(--ink-200);
  border-radius: var(--r-lg);
  background: #fff;
  padding: var(--sp-5);
  text-align: center;
  color: var(--ink-400);
  font-size: var(--fs-sm);
}

/* ===== 入场动画（stagger） ===== */
@media (prefers-reduced-motion: no-preference) {
  .rise {
    opacity: 0;
    animation: qa-up 0.5s ease forwards;
  }

  .d1 {
    animation-delay: 0.05s;
  }

  .d2 {
    animation-delay: 0.13s;
  }

  .d3 {
    animation-delay: 0.21s;
  }

  @keyframes qa-up {
    from {
      opacity: 0;
      transform: translateY(10px);
    }
    to {
      opacity: 1;
      transform: none;
    }
  }
}

@media (max-width: 768px) {
  .qa-page {
    padding: var(--sp-8) var(--sp-4) var(--sp-12);
  }

  .title {
    font-size: var(--fs-3xl);
  }
}
</style>
