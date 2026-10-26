<script setup lang="ts">
import { computed, nextTick, onBeforeUnmount, onMounted, ref } from 'vue'
import { ElMessageBox } from 'element-plus'
import { ask, fetchVersions } from '@/api/qa'
import {
  appendConversationMessage,
  createConversation,
  deleteConversation,
  fetchConversation,
  fetchConversations,
  renameConversation,
} from '@/api/me'
import { useAuthStore } from '@/stores/auth'
import type { QaHistoryEntry, SourceRef } from '@/types/qa'
import type { ConversationDetail, ConversationSummary } from '@/types/me'
import AskInput from '@/components/qa/AskInput.vue'
import AnswerCard from '@/components/qa/AnswerCard.vue'
import FallbackCard from '@/components/qa/FallbackCard.vue'
import HistoryList from '@/components/qa/HistoryList.vue'
import SourceCard from '@/components/qa/SourceCard.vue'
import SourceOverlay from '@/components/qa/SourceOverlay.vue'

type ViewState = 'idle' | 'loading' | 'answered' | 'fallback' | 'error'

// 示例问法选用经真实数据验证可命中（topScore ≥ 0.5 阈值）的措辞；
// 中文短问法（如「createApp 是做什么的？」）相似度低于阈值会触发兜底，演示体验差
const EXAMPLES = [
  'how to create a vue app with createApp',
  'How to configure actuator endpoints in Spring Boot',
  'How to use ChatClient in Spring AI',
]

const auth = useAuthStore()
const state = ref<ViewState>('idle')
const versions = ref<string[]>([])
const history = ref<QaHistoryEntry[]>([])
const currentId = ref<number | null>(null)
const lastAttempt = ref<{ question: string; version?: string } | null>(null)
const errorMsg = ref('')
const highlightIdx = ref<number | null>(null)
const askRef = ref<InstanceType<typeof AskInput>>()
const streamRef = ref<HTMLElement>()

/** 点击来源卡展开的悬浮引用详情 */
const activeSource = ref<SourceRef | null>(null)

// 已登录用户的会话（持久化在后端）；匿名用户仍用内存 history
const conversations = ref<ConversationSummary[]>([])
const conversationId = ref<number | null>(null)
const conversationLoading = ref(false)

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
  if (auth.isAuthenticated) {
    try {
      await loadConversationList()
      if (conversations.value.length) {
        await selectConversation(conversations.value[0].id)
      }
    } catch {
      /* 会话加载失败不影响问答本身 */
    }
  }
})

onBeforeUnmount(() => {
  if (hlTimer) window.clearTimeout(hlTimer)
})

function toEntries(detail: ConversationDetail): QaHistoryEntry[] {
  return detail.messages.map((m) => ({
    id: m.id,
    question: m.question,
    version: m.version ?? undefined,
    result: { found: m.found, answer: m.answer, sources: m.sources },
    at: Date.parse(m.createdAt.replace(' ', 'T')) || Date.now(),
  }))
}

/** 用后端返回的会话详情覆盖当前视图（消息已按窗口裁剪） */
function applyDetail(detail: ConversationDetail) {
  conversationId.value = detail.id
  history.value = toEntries(detail)
  const last = history.value[history.value.length - 1]
  if (last) {
    currentId.value = last.id
    state.value = last.result.found ? 'answered' : 'fallback'
    lastAttempt.value = { question: last.question, version: last.version }
  } else {
    currentId.value = null
    state.value = 'idle'
    lastAttempt.value = null
  }
}

async function loadConversationList() {
  conversations.value = await fetchConversations()
}

async function selectConversation(id: number) {
  conversationLoading.value = true
  try {
    applyDetail(await fetchConversation(id))
  } catch {
    /* 404（已被清理）/网络错误：回到列表刷新状态 */
    await loadConversationList()
  } finally {
    conversationLoading.value = false
  }
}

function onConversationChange(event: Event) {
  const raw = (event.target as HTMLSelectElement).value
  if (!raw) {
    startNewConversation()
    return
  }
  void selectConversation(Number(raw))
}

/** 新建会话：先清空视图，首次提问后再落库，避免产生空会话 */
function startNewConversation() {
  conversationId.value = null
  history.value = []
  currentId.value = null
  state.value = 'idle'
  errorMsg.value = ''
  lastAttempt.value = null
  nextTick(() => askRef.value?.focus())
}

async function renameCurrent() {
  if (conversationId.value === null) return
  const currentTitle = conversations.value.find((c) => c.id === conversationId.value)?.title ?? '会话'
  try {
    const { value } = await ElMessageBox.prompt('输入新的会话名称', '重命名会话', {
      inputValue: currentTitle,
      confirmButtonText: '保存',
      cancelButtonText: '取消',
      inputValidator: (v) => (v && v.trim() ? true : '名称不能为空'),
    })
    await renameConversation(conversationId.value, value.trim())
    await loadConversationList()
  } catch {
    /* 用户取消 */
  }
}

async function removeCurrent() {
  if (conversationId.value === null) return
  try {
    await ElMessageBox.confirm('删除后该会话的问答记录将不可恢复', '删除会话', {
      type: 'warning',
      confirmButtonText: '删除',
      cancelButtonText: '取消',
    })
    await deleteConversation(conversationId.value)
    await loadConversationList()
    if (conversations.value.length) {
      await selectConversation(conversations.value[0].id)
    } else {
      startNewConversation()
    }
  } catch {
    /* 用户取消 */
  }
}

async function submit(question: string, version?: string) {
  if (state.value === 'loading') return
  activeSource.value = null
  lastAttempt.value = { question, version }
  state.value = 'loading'
  errorMsg.value = ''
  try {
    const result = await ask(question, version)
    if (auth.isAuthenticated) {
      try {
        let id = conversationId.value
        if (id === null) {
          const created = await createConversation(question.slice(0, 60))
          id = created.id
        }
        applyDetail(await appendConversationMessage(id, {
          question,
          answer: result.answer,
          found: result.found,
          version,
          sources: result.sources,
        }))
        await loadConversationList()
        return
      } catch {
        /* 持久化失败不阻断本次回答展示，退回本地渲染 */
      }
    }
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
  activeSource.value = null
  currentId.value = id
  state.value = entry.result.found ? 'answered' : 'fallback'
}

/** 点击来源卡：展开可拖动 / 可缩放的悬浮引用详情 */
function openSource(source: SourceRef) {
  activeSource.value = source
}

/** 引用角标点击：滚动到对应来源卡并触发一次陶土色脉冲高亮 */
function onCite(n: number) {
  streamRef.value?.querySelector<HTMLElement>(`[data-source="${n}"]`)
    ?.scrollIntoView({ block: 'nearest', behavior: 'smooth' })

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
      <header class="head">
        <h1 class="title">智能问答</h1>
        <p class="sub">自然语言提问，精准引用溯源</p>
      </header>

      <!-- 会话管理：登录用户可切换 / 新建 / 重命名 / 删除，历史来自后端持久化 -->
      <div v-if="auth.isAuthenticated" class="conv">
        <select class="conv__select" :value="conversationId ?? ''" aria-label="切换会话" @change="onConversationChange">
          <option value="">新会话（未保存）</option>
          <option v-for="c in conversations" :key="c.id" :value="c.id">
            {{ c.title }}（{{ c.messageCount }}）
          </option>
        </select>
        <button class="chip" type="button" @click="startNewConversation">新建</button>
        <button class="chip" type="button" :disabled="conversationId === null" @click="renameCurrent">重命名</button>
        <button class="chip" type="button" :disabled="conversationId === null" @click="removeCurrent">删除</button>
        <span v-if="conversationLoading" class="conv__hint">正在载入会话...</span>
      </div>
      <p v-else class="conv__hint">登录后会自动保存会话历史，切换页面也不会丢失</p>

      <HistoryList v-if="history.length" :entries="history" :active-id="currentId" @select="showEntry" />

      <div ref="streamRef" class="stream">
        <!-- 空态：一句引导 + 三个示例 chip -->
        <div v-if="state === 'idle'" class="empty">
          <p class="empty__lead">问点什么，或者从下面挑一个</p>
          <div class="chips">
            <button v-for="q in EXAMPLES" :key="q" type="button" class="chip" @click="submit(q)">
              {{ q }}
            </button>
          </div>
        </div>

        <!-- 加载：三行灰条 + 一个脉冲点（不使用转圈加载器） -->
        <div v-else-if="state === 'loading'" class="skeleton" role="status" aria-label="正在检索">
          <span class="sk-dot" />
          <div class="sk-lines">
            <span class="sk-line w80" />
            <span class="sk-line w95" />
            <span class="sk-line w60" />
          </div>
        </div>

        <!-- 错误态 -->
        <div v-else-if="state === 'error'" class="err">
          <b>出了点问题</b>
          <p>{{ errorMsg }}</p>
          <button type="button" class="txt-btn" @click="retry">重试</button>
        </div>

        <template v-else-if="current">
          <!-- 用户问题：靠右浅色气泡 -->
          <div class="msg-user">
            <div class="bubble">
              {{ current.question }}
              <span v-if="current.version" class="bubble__ver num">{{ current.version }}</span>
            </div>
          </div>

          <!-- 答案：靠左、无气泡，直接排在纸面上 -->
          <div class="msg-answer">
            <FallbackCard v-if="state === 'fallback'" @refine="refine" @version="pickVersion" />

            <template v-else>
              <AnswerCard
                :answer="current.result.answer"
                :total-sources="current.result.sources.length"
                :animate="isLatest(current)"
                @cite="onCite"
              />

              <section v-if="current.result.sources.length" class="sources">
                <div class="sec-label">引用来源（{{ current.result.sources.length }}）</div>
                <div class="sources__row">
                  <SourceCard
                    v-for="s in current.result.sources"
                    :key="s.index"
                    :data-source="s.index"
                    :source="s"
                    :highlight="highlightIdx === s.index"
                    @open="openSource"
                  />
                </div>
              </section>

              <!-- 追问建议：复用示例问法作为可点击的 UI 建议（非业务数据） -->
              <section class="followup">
                <div class="sec-label">继续追问</div>
                <div class="chips">
                  <button v-for="q in EXAMPLES" :key="q" type="button" class="chip" @click="submit(q)">
                    {{ q }}
                  </button>
                </div>
              </section>
            </template>
          </div>
        </template>
      </div>

      <!-- 输入区：视口吸附到底部，外层留出等高空间，不遮挡内容 -->
      <div class="composer">
        <AskInput ref="askRef" :versions="versions" :loading="state === 'loading'" @submit="submit" />
      </div>

      <SourceOverlay :source="activeSource" @close="activeSource = null" />
    </div>
  </div>
</template>

<style scoped>
.qa-page {
  flex: 1;
  min-height: 0;
  display: flex;
  flex-direction: column;
  background: var(--page-bg);
  color: var(--ink-700);
  padding: var(--sp-10) var(--sp-6) 0;
}

/* 阅读栏：整页最大宽度 --measure（760px）居中，两侧留白充足 */
.wrap {
  flex: 1;
  width: 100%;
  max-width: var(--measure);
  margin: 0 auto;
  display: flex;
  flex-direction: column;
  gap: var(--sp-6);
}

.head {
  padding-top: var(--sp-2);
}

.title {
  font-size: var(--fs-2xl);
  font-weight: 600;
  color: var(--ink-900);
}

.sub {
  margin-top: var(--sp-1);
  font-size: var(--fs-sm);
  color: var(--ink-500);
}

/* ===== 会话管理条 ===== */
.conv {
  display: flex;
  align-items: center;
  flex-wrap: wrap;
  gap: var(--sp-2);
}

.conv__select {
  min-width: 0;
  max-width: 360px;
  padding: 6px 10px;
  border: 1px solid var(--hairline);
  border-radius: var(--r-md);
  background: var(--surface);
  font-size: var(--fs-sm);
  font-family: inherit;
  color: var(--ink-700);
}

.conv__select:focus {
  outline: none;
  box-shadow: var(--ring);
}

.conv .chip[disabled] {
  opacity: 0.45;
  cursor: not-allowed;
}

.conv__hint {
  font-size: var(--fs-xs);
  color: var(--ink-400);
}

.stream {
  flex: 1;
  display: flex;
  flex-direction: column;
  gap: var(--sp-6);
  min-width: 0;
}

/* ===== 空态 ===== */
.empty {
  padding: var(--sp-6) 0 var(--sp-2);
}

.empty__lead {
  font-size: var(--fs-lg);
  color: var(--ink-600);
  margin-bottom: var(--sp-4);
}

/* ===== 示例 / 追问 chips（hairline 描边，hover 陶土描边） ===== */
.chips {
  display: flex;
  flex-wrap: wrap;
  gap: var(--sp-2);
}

.chip {
  display: inline-flex;
  align-items: center;
  padding: 6px 14px;
  border-radius: 100px;
  border: 1px solid var(--hairline);
  background: var(--surface);
  font-size: var(--fs-sm);
  color: var(--ink-600);
  font-family: inherit;
  cursor: pointer;
  transition:
    border-color var(--dur-fast) var(--ease-out),
    color var(--dur-fast) var(--ease-out);
}

.chip:hover {
  border-color: var(--brand-600);
  color: var(--brand-600);
}

/* ===== 加载骨架：三行灰条 + 一个脉冲点 ===== */
.skeleton {
  display: flex;
  align-items: flex-start;
  gap: var(--sp-3);
  padding: var(--sp-4) 0;
}

.sk-dot {
  flex-shrink: 0;
  width: 8px;
  height: 8px;
  margin-top: 6px;
  border-radius: 50%;
  background: var(--brand-600);
}

.sk-lines {
  flex: 1;
  display: flex;
  flex-direction: column;
  gap: var(--sp-3);
}

.sk-line {
  display: block;
  height: 12px;
  border-radius: var(--r-sm);
  background: var(--ink-200);
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

/* 脉冲点仅存在于加载期间 → 循环脉冲仅限加载态 */
@media (prefers-reduced-motion: no-preference) {
  .sk-dot {
    animation: sk-pulse 1.2s ease-in-out infinite;
  }

  @keyframes sk-pulse {
    0%,
    100% {
      opacity: 0.3;
    }
    50% {
      opacity: 1;
    }
  }
}

/* ===== 错误态 ===== */
.err {
  padding: var(--sp-5) 0;
}

.err b {
  font-size: var(--fs-lg);
  font-weight: 600;
  color: var(--ink-900);
}

.err p {
  margin: var(--sp-2) 0 var(--sp-4);
  font-size: var(--fs-sm);
  color: var(--ink-500);
}

.txt-btn {
  padding: 0;
  font-size: var(--fs-md);
  color: var(--ink-500);
  cursor: pointer;
  text-decoration: underline;
  text-decoration-color: var(--hairline-strong);
  text-underline-offset: 3px;
  transition: color var(--dur-fast) var(--ease-out);
}

.txt-btn:hover {
  color: var(--brand-600);
  text-decoration-color: var(--brand-600);
}

/* ===== 用户问题：靠右浅色气泡 ===== */
.msg-user {
  display: flex;
  justify-content: flex-end;
}

.bubble {
  max-width: 72%;
  padding: var(--sp-3) var(--sp-4);
  background: var(--ink-100);
  border-radius: var(--r-lg);
  color: var(--ink-800);
  font-size: var(--fs-lg);
  line-height: 1.7;
  word-break: break-word;
}

.bubble__ver {
  margin-left: var(--sp-2);
  font-size: var(--fs-xs);
  color: var(--ink-500);
}

/* 气泡出现：--dur-slow 内轻微位移 + 淡入（仅状态变化，不做逐张入场） */
@media (prefers-reduced-motion: no-preference) {
  .bubble {
    animation: bubble-in var(--dur-slow) var(--ease-out) 1;
  }

  @keyframes bubble-in {
    from {
      opacity: 0;
      transform: translateY(6px);
    }
    to {
      opacity: 1;
      transform: none;
    }
  }
}

/* ===== 答案：靠左、无气泡，直接排在纸面上 ===== */
.msg-answer {
  display: flex;
  flex-direction: column;
  gap: var(--sp-5);
  min-width: 0;
}

.sec-label {
  font-size: var(--fs-sm);
  font-weight: 600;
  color: var(--ink-500);
  margin-bottom: var(--sp-3);
}

/* 来源卡横向排列（窄屏换行） */
.sources__row {
  display: flex;
  flex-wrap: wrap;
  gap: var(--sp-3);
}

/* ===== 输入区：视口吸附 ===== */
.composer {
  position: sticky;
  bottom: 0;
  z-index: 10;
  padding: var(--sp-3) 0 var(--sp-5);
  background: var(--page-bg);
  border-top: 1px solid var(--hairline);
}

@media (max-width: 768px) {
  .qa-page {
    padding: var(--sp-6) var(--sp-4) 0;
  }

  .bubble {
    max-width: 86%;
  }
}
</style>
