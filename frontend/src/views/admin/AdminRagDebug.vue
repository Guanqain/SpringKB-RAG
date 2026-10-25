<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { fetchRagTrace } from '@/api/rag'
import { fetchVersions } from '@/api/qa'
import type { RagTrace, RagTraceStage } from '@/types/rag'

const query = ref('')
const version = ref('')
const topK = ref(5)
const versions = ref<string[]>([])
const trace = ref<RagTrace | null>(null)
const loading = ref(false)

onMounted(async () => {
  try {
    versions.value = await fetchVersions()
  } catch {
    versions.value = []
  }
})

async function run() {
  const q = query.value.trim()
  if (!q) {
    ElMessage.warning('先输入一个问题')
    return
  }
  loading.value = true
  try {
    trace.value = await fetchRagTrace(q, version.value, topK.value)
  } finally {
    loading.value = false
  }
}

function statusTone(status: string) {
  if (status === 'PASS') return 'ok'
  if (status === 'BLOCKED') return 'blocked'
  if (status === 'PARTIAL') return 'partial'
  return 'plain'
}

/** detail 的未知结构统一转成可读文本 */
function detailValue(value: unknown): string {
  if (value === null || value === undefined) return '—'
  if (Array.isArray(value)) return value.length ? value.join(', ') : '[]'
  if (typeof value === 'object') return JSON.stringify(value)
  return String(value)
}

function detailEntries(stage: RagTraceStage) {
  return Object.entries(stage.detail ?? {})
}
</script>

<template>
  <div>
    <div class="page-head">
      <h2 class="page-head__title">检索诊断</h2>
      <p class="page-head__sub">
        逐段观测 RAG 检索链路的数据与状态；诊断模式不调用 Chat 模型，不产生 token 消耗
      </p>
    </div>

    <section class="panel">
      <form class="form" @submit.prevent="run">
        <label class="field field--grow">
          <span class="field__label">问题</span>
          <el-input
            v-model="query"
            placeholder="例如：Spring AI"
            clearable
            @keyup.enter="run"
          />
        </label>
        <label class="field">
          <span class="field__label">版本</span>
          <el-select v-model="version" placeholder="全部版本" clearable class="field__select">
            <el-option v-for="v in versions" :key="v" :label="v" :value="v" />
          </el-select>
        </label>
        <label class="field field--tight">
          <span class="field__label">topK</span>
          <el-input-number v-model="topK" :min="1" :max="10" controls-position="right" />
        </label>
        <el-button type="primary" native-type="submit" :loading="loading">运行诊断</el-button>
      </form>
    </section>

    <template v-if="trace">
      <section class="summary">
        <div class="summary__cell">
          <span class="summary__label">traceId</span>
          <span class="summary__value num">{{ trace.traceId }}</span>
        </div>
        <div class="summary__cell">
          <span class="summary__label">总耗时</span>
          <span class="summary__value num">{{ trace.totalElapsedMs }} ms</span>
        </div>
        <div class="summary__cell">
          <span class="summary__label">最高相似度</span>
          <span class="summary__value num">{{ trace.decision.topScore }}</span>
        </div>
        <div class="summary__cell">
          <span class="summary__label">阈值</span>
          <span class="summary__value num">{{ trace.decision.threshold }}</span>
        </div>
        <div class="summary__cell summary__cell--wide">
          <span class="summary__label">结论</span>
          <span class="summary__value" :class="trace.decision.found ? 'text-ok' : 'text-blocked'">
            {{ trace.decision.found ? '命中，正式 ask 会调用模型生成' : '走兜底，不调用模型' }}
          </span>
        </div>
      </section>

      <section class="stages">
        <article
          v-for="(stage, i) in trace.stages"
          :key="stage.key"
          class="stage"
          :class="`stage--${statusTone(stage.status)}`"
        >
          <header class="stage__head">
            <span class="stage__no num">{{ i + 1 }}</span>
            <span class="stage__label">{{ stage.label }}</span>
            <span class="stage__status">{{ stage.status }}</span>
            <span class="stage__ms num">{{ stage.elapsedMs }} ms</span>
          </header>
          <dl class="stage__detail">
            <div v-for="[k, v] in detailEntries(stage)" :key="k" class="kv">
              <dt class="kv__k">{{ k }}</dt>
              <dd class="kv__v num">{{ detailValue(v) }}</dd>
            </div>
          </dl>
        </article>
      </section>

      <section class="panel panel--table">
        <h3 class="panel-title">
          初检候选（{{ trace.candidates.length }}）
          <span class="panel-title__hint">按 pgvector 原始相似度降序，boostedScore 为权威性加权后分数</span>
        </h3>
        <table class="tbl">
          <thead>
            <tr>
              <th class="col-no">#</th>
              <th class="col-score">score</th>
              <th class="col-score">boosted</th>
              <th class="col-title">标题</th>
              <th class="col-meta">版本</th>
              <th class="col-meta">权威性</th>
              <th class="col-state">回源</th>
              <th class="col-id">chunk id</th>
            </tr>
          </thead>
          <tbody>
            <tr v-for="(c, i) in trace.candidates" :key="c.id">
              <td class="num col-no">{{ i + 1 }}</td>
              <td class="num col-score">{{ c.score }}</td>
              <td class="num col-score" :class="{ 'text-ok': c.boostedScore !== c.score }">
                {{ c.boostedScore }}
              </td>
              <td class="col-title">
                <div class="cand-title">{{ c.title || '—' }}</div>
                <div class="cand-preview">{{ c.preview }}</div>
              </td>
              <td class="col-meta">{{ c.version || '—' }}</td>
              <td class="col-meta">{{ c.authority || '—' }}</td>
              <td class="col-state">
                <span :class="c.resolved ? 'text-ok' : 'text-blocked'">
                  {{ c.resolved ? '命中' : '缺失' }}
                </span>
              </td>
              <td class="col-id num">{{ c.id }}</td>
            </tr>
          </tbody>
        </table>
      </section>
    </template>

    <section v-else class="panel empty">
      <p class="empty__title">还没有运行过诊断 —— 输入一个问题，看看链路在哪一步停下来</p>
      <p class="empty__hint">
        会依次展示：问题向量化 → 向量检索 → 阈值判定 → 权威性重排 → 回源查询。
      </p>
    </section>
  </div>
</template>

<style scoped>
.panel {
  background: var(--surface);
  border: 1px solid var(--hairline);
  border-radius: var(--r-lg);
  padding: var(--sp-5);
}

.form {
  display: flex;
  align-items: flex-end;
  gap: var(--sp-4);
  flex-wrap: wrap;
}

.field {
  display: flex;
  flex-direction: column;
  gap: var(--sp-2);
  width: 200px;
}

.field--grow {
  flex: 1;
  min-width: 260px;
  width: auto;
}

.field--tight {
  width: 110px;
}

.field__label {
  font-size: var(--fs-xs);
  color: var(--ink-500);
}

.field__select {
  width: 100%;
}

.summary {
  display: grid;
  grid-template-columns: repeat(4, minmax(0, 1fr)) 2fr;
  margin-top: var(--sp-4);
  background: var(--surface);
  border: 1px solid var(--hairline);
  border-radius: var(--r-lg);
  overflow: hidden;
}

.summary__cell {
  padding: var(--sp-4) var(--sp-5);
  border-right: 1px solid var(--hairline);
  display: flex;
  flex-direction: column;
  gap: 4px;
}

.summary__cell:last-child {
  border-right: none;
}

.summary__label {
  font-size: var(--fs-xs);
  color: var(--ink-400);
}

.summary__value {
  font-size: var(--fs-md);
  color: var(--ink-900);
  font-weight: 600;
}

.summary__cell--wide .summary__value {
  font-weight: 600;
}

.text-ok {
  color: var(--success);
}

.text-blocked {
  color: var(--danger);
}

.stages {
  display: grid;
  grid-template-columns: repeat(5, minmax(0, 1fr));
  gap: var(--sp-3);
  margin-top: var(--sp-4);
}

.stage {
  background: var(--surface);
  border: 1px solid var(--hairline);
  border-radius: var(--r-lg);
  padding: var(--sp-4);
  border-top: 2px solid var(--ink-300);
}

.stage--ok {
  border-top-color: var(--success);
}

.stage--blocked {
  border-top-color: var(--danger);
}

.stage--partial {
  border-top-color: var(--warning);
}

.stage__head {
  display: flex;
  align-items: center;
  gap: var(--sp-2);
  padding-bottom: var(--sp-3);
  margin-bottom: var(--sp-3);
  border-bottom: 1px solid var(--hairline);
}

.stage__no {
  width: 20px;
  height: 20px;
  border-radius: 50%;
  display: grid;
  place-items: center;
  font-size: 11px;
  background: var(--ink-100);
  color: var(--ink-600);
  flex-shrink: 0;
}

.stage__label {
  font-size: var(--fs-sm);
  font-weight: 600;
  color: var(--ink-900);
}

.stage__status {
  margin-left: auto;
  font-size: 10px;
  font-weight: 600;
  letter-spacing: 0.04em;
  color: var(--ink-400);
}

.stage--ok .stage__status {
  color: var(--success);
}

.stage--blocked .stage__status {
  color: var(--danger);
}

.stage--partial .stage__status {
  color: var(--warning);
}

.stage__ms {
  font-size: var(--fs-xs);
  color: var(--ink-400);
}

.stage__detail {
  display: flex;
  flex-direction: column;
  gap: 6px;
}

.kv {
  display: flex;
  justify-content: space-between;
  gap: var(--sp-2);
  font-size: var(--fs-xs);
}

.kv__k {
  color: var(--ink-400);
  flex-shrink: 0;
}

.kv__v {
  color: var(--ink-700);
  text-align: right;
  word-break: break-word;
}

.panel--table {
  margin-top: var(--sp-4);
  padding: 0;
  overflow: hidden;
}

.panel-title {
  font-size: var(--fs-md);
  font-weight: 600;
  color: var(--ink-900);
  margin: 0;
  padding: var(--sp-4) var(--sp-5);
  border-bottom: 1px solid var(--hairline);
}

.panel-title__hint {
  margin-left: var(--sp-2);
  font-size: var(--fs-xs);
  font-weight: 400;
  color: var(--ink-400);
}

.tbl {
  width: 100%;
  border-collapse: collapse;
  font-size: var(--fs-sm);
}

.tbl th {
  text-align: left;
  font-weight: 600;
  font-size: var(--fs-xs);
  color: var(--ink-500);
  padding: var(--sp-3) var(--sp-4);
  border-bottom: 1px solid var(--hairline);
  white-space: nowrap;
}

.tbl td {
  padding: var(--sp-3) var(--sp-4);
  border-bottom: 1px solid var(--hairline);
  vertical-align: top;
  color: var(--ink-700);
}

.tbl tbody tr:last-child td {
  border-bottom: none;
}

.col-no {
  width: 38px;
  color: var(--ink-400);
}

.col-score {
  width: 84px;
}

.col-meta {
  width: 84px;
  white-space: nowrap;
}

.col-state {
  width: 64px;
  white-space: nowrap;
}

.col-id {
  max-width: 220px;
  color: var(--ink-400);
  font-size: var(--fs-xs);
  word-break: break-all;
}

.cand-title {
  font-weight: 600;
  color: var(--ink-900);
}

.cand-preview {
  margin-top: 2px;
  font-size: var(--fs-xs);
  color: var(--ink-400);
  line-height: 1.6;
  display: -webkit-box;
  -webkit-line-clamp: 2;
  -webkit-box-orient: vertical;
  overflow: hidden;
}

.empty {
  margin-top: var(--sp-4);
  padding: var(--sp-12) var(--sp-6);
  text-align: center;
}

.empty__title {
  font-size: var(--fs-lg);
  font-weight: 600;
  color: var(--ink-900);
}

.empty__hint {
  margin-top: var(--sp-2);
  font-size: var(--fs-sm);
  color: var(--ink-500);
}

@media (max-width: 1280px) {
  .stages {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }

  .summary {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }

  .summary__cell {
    border-bottom: 1px solid var(--hairline);
  }
}

@media (max-width: 720px) {
  .stages,
  .summary {
    grid-template-columns: 1fr;
  }
}
</style>
