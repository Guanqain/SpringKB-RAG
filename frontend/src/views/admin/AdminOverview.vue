<script setup lang="ts">
// 管理后台 · 概览：GET /api/admin/overview?days=7|30（只消费聚合值，不拉全量文档行）
import { computed, onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import StatCard from '@/components/StatCard.vue'
import BarList from '@/components/admin/charts/BarList.vue'
import DonutChart from '@/components/admin/charts/DonutChart.vue'
import SplitBar from '@/components/admin/charts/SplitBar.vue'
import TrendChart from '@/components/admin/charts/TrendChart.vue'
import { formatNumber, formatPct } from '@/components/admin/charts/theme'
import { fetchAdminOverview, listAdminCategories } from '@/api/document'
import type { Category } from '@/types/admin'
import type {
  AdminOverview,
  Cards,
  ContentStats,
  EngagementStats,
  GovernanceStats,
  HealthStats,
  OtherBucket,
  ProductSlice,
  QaStats,
  Sections,
  TodoSummary,
  TrendSeries,
  UserStats,
} from '@/types/document'

interface BarItem {
  label: string
  count: number
  pct: number
}

interface Slice {
  label: string
  count: number
}

type DeltaDirection = 'up' | 'down' | 'flat' | 'none'

interface DeltaResult {
  text: string
  direction: DeltaDirection
}

interface TodoEntry {
  key: string
  label: string
  value: number
  desc: string
  to: string
}

/** 口径说明：分子 / 分母 / 时间窗，均与 AdminService.overview 的 SQL 一致。 */
const METRIC_HINTS: Record<string, { title: string; text: string }> = {
  totalDocs: {
    title: '文档总数',
    text: 'kb_document 行数；含启用与停用，不含已删除文档。',
  },
  chunks: {
    title: 'Chunk 总数',
    text: '所有文档 chunk_count 之和；每个 chunk 对应一条向量。',
  },
  activeRate: {
    title: '活跃率',
    text: '启用文档数 ÷ 文档总数（active=true）；总数为 0 时记 0。',
  },
  pendingReview: {
    title: '待审',
    text: "review_status='pending' 的文档数；不含已通过（approved）与已驳回（rejected）。",
  },
  windowQa: {
    title: '窗口内问答数',
    text: '近 N 天 kb_message 行数；环比对比上一个等长 N 天窗口。',
  },
  foundRate: {
    title: '问答命中率',
    text: '窗口内 found=true 的问答数 ÷ 窗口内问答总数（kb_message）；不含窗口外记录。',
  },
  users: {
    title: '用户数',
    text: 'kb_user 总行数；角色分为管理员（ADMIN）与成员（MEMBER）。',
  },
  windowNewDocs: {
    title: '窗口内新增文档',
    text: '近 N 天 created_at 的新增文档数；环比对比上一个等长 N 天窗口，上一窗口为 0 时显示「—」。',
  },
  contentPanel: {
    title: '内容资产口径',
    text: 'kb_document 全量聚合；窗口类指标按 created_at 过滤，新鲜度按 last_modified 距今天数分段。',
  },
  contentProducts: {
    title: '技术分布',
    text: '按 kb_document.product 分组计数，前 8 项展示，其余聚合为「其他」；数字为文档篇数。',
  },
  contentOrigins: {
    title: '来源分布',
    text: '按 kb_document.origin 分组计数，前 8 项 + 其他；数字为文档篇数。',
  },
  contentDocTypes: {
    title: '文档类型',
    text: '按 kb_document.doc_type 分组计数；占比 = 该类型文档数 ÷ 全部文档数。',
  },
  contentLanguages: {
    title: '语言分布',
    text: '按 kb_document.language 分组计数；占比 = 该语言文档数 ÷ 全部文档数。',
  },
  contentFreshness: {
    title: '新鲜度',
    text: '按 kb_document.last_modified 距今天数分段：≤30 / 31~180 / 181~365 / >365 天；last_modified 为空计入「未知」。',
  },
  contentAux: {
    title: '内容辅助指标',
    text: '平均 Chunk/篇 = avg(chunk_count)；空 Chunk 文档 = chunk_count=0；平均年龄 = avg(now - last_modified，缺失时用 fetched_at)；今日新增 = created_at 为当天。',
  },
  governancePanel: {
    title: '审核合规口径',
    text: 'kb_document.review_status 与 kb_scan_log 的窗口内聚合；只展示规则类型与计数，不回显命中明文。',
  },
  reviewStatus: {
    title: '审核状态',
    text: '按 kb_document.review_status 计数与占比：approved / pending / rejected；占比分母为全部文档。',
  },
  reviewMetrics: {
    title: '审核指标',
    text: '通过率 = approved ÷ (approved + rejected)，不含 pending；平均审核时长 = avg(reviewed_at - created_at)，仅 reviewed_at 非空；最久待审 = 待审文档 max(now - updated_at) 天数；未读通知 = kb_notification.read=false。',
  },
  scanRules: {
    title: '脱敏规则 Top 5',
    text: '窗口内按 kb_scan_log.rule_type 分组的命中行数 Top 5；只统计规则类型，不回显命中内容。',
  },
  scanActions: {
    title: '拦截动作',
    text: '窗口内按 kb_scan_log.action 分组计数：publish / upload / update / visibility / admin_meta / precheck。',
  },
  scanSummary: {
    title: '扫描摘要',
    text: '窗口内 kb_scan_log 行数（windowBlocks）、去重 doc_title 数（blockedTitles）、sum(hit_count)（hits）。',
  },
  qaPanel: {
    title: '问答检索口径',
    text: 'kb_conversation / kb_message 聚合；窗口与环比均按 kb_message.created_at 过滤。',
  },
  qaTrend: {
    title: '趋势',
    text: '每日新增文档 = kb_document.created_at 当日行数；每日问答次数 = kb_message.created_at 当日行数，缺失日期补 0。',
  },
  qaHitRate: {
    title: '窗口内命中率',
    text: '窗口内 found=true ÷ 窗口内问答数；未命中 = found=false，不含窗口外记录。',
  },
  qaBlindspots: {
    title: '知识盲区 Top 5',
    text: '窗口内 found=false 的问题按原文（截断 60 字）计数 Top 5；不返回、不展示用户名。',
  },
  qaAux: {
    title: '问答辅助指标',
    text: 'conversations / messages 为总行数；windowQa 为窗口内问答数；avgSources = sources 数组长度均值；activeAskers = 窗口内去重提问人数。',
  },
  usersPanel: {
    title: '用户互动口径',
    text: 'kb_user 全量 + 窗口内 kb_user.created_at；活跃用户取窗口内提问与发布 UGC 的去重人数。',
  },
  userRoles: {
    title: '角色分布',
    text: '按 kb_user.role 分组计数（ADMIN / MEMBER）；占比分母为用户总数。',
  },
  userWindow: {
    title: '窗口内用户',
    text: '新增用户 = 窗口内 kb_user.created_at 行数；活跃用户 = 窗口内提问或发布 UGC 的去重人数；环比对比上一个等长窗口。',
  },
  userContributors: {
    title: '贡献者 Top 5',
    text: '按 kb_document.author 分组计数 Top 5（仅 author 非空的 UGC 文档）；占比分母为 Top 5 合计。',
  },
  userFavorites: {
    title: '收藏 Top 5',
    text: 'kb_doc_favorite 按 doc_id 分组计数 Top 5，标题取自 kb_document.title。',
  },
  healthPanel: {
    title: '系统健康口径',
    text: 'vector_store / kb_chunk / pg_database_size 与三张表的 max(time)；无数据时不臆造时间。',
  },
  healthCoverage: {
    title: '向量覆盖率',
    text: '覆盖率 = vector_store 行数 ÷ sum(chunk_count)；chunk 为 0 时记 0，不返回 NaN。',
  },
  healthRuntime: {
    title: '运行信息',
    text: '数据库体积 = pg_database_size(current_database())；最近导入 = max(kb_document.fetched_at)；最近扫描 = max(kb_scan_log.created_at)；最近问答 = max(kb_message.created_at)。',
  },
}

const router = useRouter()

const dayOptions: (7 | 30)[] = [7, 30]
const days = ref<7 | 30>(7)
const data = ref<AdminOverview | null>(null)
const displayNames = ref<Record<string, string>>({})
const loading = ref(false)
const initialLoading = ref(true)
const error = ref('')

const rangeLabel = computed(() => `近 ${days.value} 天`)

function emptyContent(): ContentStats {
  return {
    totalDocs: 0,
    activeDocs: 0,
    inactiveDocs: 0,
    chunks: 0,
    avgChunksPerDoc: 0,
    emptyChunkDocs: 0,
    products: [],
    otherProducts: { count: 0, total: 0 },
    origins: [],
    otherOrigins: { count: 0, total: 0 },
    docTypes: [],
    languages: [],
    versions: [],
    otherVersions: { count: 0, total: 0 },
    visibility: { team: 0, 'private': 0 },
    freshness: {
      within30Days: 0,
      days31To180: 0,
      days181To365: 0,
      over365Days: 0,
      unknown: 0,
    },
    avgAgeDays: 0,
    todayNewDocs: 0,
    windowNewDocs: 0,
    prevWindowNewDocs: 0,
  }
}

function emptySections(): Sections {
  return {
    content: emptyContent(),
    governance: {
      reviewStatus: [],
      approvalRate: 0,
      avgReviewHours: 0,
      pendingOldestDays: 0,
      rejectedStale: 0,
      scan: {
        windowBlocks: 0,
        blockedTitles: 0,
        prevWindowBlocks: 0,
        hits: 0,
        byRule: [],
        byAction: [],
      },
      notificationsUnread: 0,
    },
    qa: {
      conversations: 0,
      messages: 0,
      windowQa: 0,
      prevWindowQa: 0,
      foundRate: 0,
      noAnswerRate: 0,
      avgSources: 0,
      activeAskers: 0,
      blindspotsTop: [],
    },
    users: {
      total: 0,
      admins: 0,
      members: 0,
      newInWindow: 0,
      prevNewInWindow: 0,
      activeInWindow: 0,
      prevActiveInWindow: 0,
      topContributors: [],
      roles: [],
    },
    engagement: { likes: 0, favorites: 0, topFavorites: [] },
    health: {
      vectorRows: 0,
      coverageRate: 0,
      dbSizeBytes: 0,
      lastIngestedAt: null,
      lastScanAt: null,
      lastQaAt: null,
    },
  }
}

const cards = computed<Cards>(
  () =>
    data.value?.cards ?? {
      totalDocs: 0,
      chunks: 0,
      activeRate: 0,
      pendingReview: 0,
      windowQa: 0,
      foundRate: 0,
      users: 0,
      windowNewDocs: 0,
    },
)
const sections = computed<Sections>(() => data.value?.sections ?? emptySections())
const content = computed(() => sections.value.content)
const governance = computed(() => sections.value.governance)
const qa = computed(() => sections.value.qa)
const users = computed(() => sections.value.users)
const engagement = computed(() => sections.value.engagement)
const health = computed(() => sections.value.health)
const trend = computed<TrendSeries>(() => data.value?.trend ?? { days: days.value, points: [] })
const todos = computed<TodoSummary>(
  () =>
    data.value?.todos ?? {
      pendingReview: 0,
      rejectedStale: 0,
      scanBlocks: 0,
      blindspots: 0,
    },
)

// ---------- 环比与格式化 ----------

function delta(curr: number, prev: number): DeltaResult {
  if (!Number.isFinite(curr) || !Number.isFinite(prev) || prev === 0) {
    return { text: '—', direction: 'none' }
  }
  const change = ((curr - prev) / prev) * 100
  if (!Number.isFinite(change)) {
    return { text: '—', direction: 'none' }
  }
  const rounded = Math.round(Math.abs(change))
  if (rounded === 0) {
    return { text: '较上一周期 0%', direction: 'flat' }
  }
  return {
    text: `较上一周期 ${change > 0 ? '▲' : '▼'} ${rounded}%`,
    direction: change > 0 ? 'up' : 'down',
  }
}

function toPercent(ratio: number): number {
  return Number.isFinite(ratio) ? ratio * 100 : 0
}

function fixed(value: number, digits = 1): string {
  return (Number.isFinite(value) ? value : 0).toFixed(digits)
}

function pad2(value: number): string {
  return String(value).padStart(2, '0')
}

function formatAbsolute(raw: string | null): string {
  if (!raw) return '—'
  const date = new Date(raw)
  if (Number.isNaN(date.getTime())) return '—'
  return `${date.getFullYear()}-${pad2(date.getMonth() + 1)}-${pad2(date.getDate())} ${pad2(
    date.getHours(),
  )}:${pad2(date.getMinutes())}`
}

function relativeText(raw: string | null): string {
  if (!raw) return '—'
  const date = new Date(raw)
  if (Number.isNaN(date.getTime())) return '—'
  const diff = Date.now() - date.getTime()
  if (diff < 0) return '刚刚'
  const minutes = Math.floor(diff / 60000)
  if (minutes < 1) return '刚刚'
  if (minutes < 60) return `${minutes} 分钟前`
  const hours = Math.floor(minutes / 60)
  if (hours < 24) return `${hours} 小时前`
  const dayCount = Math.floor(hours / 24)
  if (dayCount < 30) return `${dayCount} 天前`
  return formatAbsolute(raw)
}

function formatBytes(bytes: number): string {
  if (!Number.isFinite(bytes) || bytes <= 0) return '—'
  const gb = bytes / 1024 ** 3
  if (gb >= 1) return `${gb.toFixed(2)} GB`
  return `${(bytes / 1024 ** 2).toFixed(1)} MB`
}

function clip(text: string, max = 60): string {
  const value = text || '（空问题）'
  return value.length > max ? `${value.slice(0, max)}…` : value
}

// ---------- 维度映射 ----------

function reviewLabel(status: string): string {
  const labels: Record<string, string> = {
    approved: '已通过',
    pending: '待审',
    rejected: '已驳回',
  }
  return labels[status] ?? status
}

function roleLabel(role: string): string {
  const labels: Record<string, string> = { ADMIN: '管理员', MEMBER: '成员' }
  return labels[role] ?? role
}

function docTypeLabel(type: string): string {
  const labels: Record<string, string> = {
    guide: '指南',
    api: 'API',
    tutorial: '教程',
    reference: '参考',
    howto: '操作指南',
    concept: '概念',
    faq: 'FAQ',
  }
  return labels[type] ?? type
}

function languageLabel(language: string): string {
  const labels: Record<string, string> = { en: '英文', zh: '中文', ja: '日文', ko: '韩文' }
  return labels[language] ?? language
}

function ruleLabel(code: string): string {
  const labels: Record<string, string> = {
    private_key: '私钥',
    cloud_key: '云访问密钥 / 令牌',
    jwt: 'JWT 令牌',
    credential: '账号口令 / 密钥字段',
    connection_string: '数据库 / 中间件连接串',
    id_card: '身份证号',
    bank_card: '银行卡号',
    phone: '手机号',
  }
  return labels[code] ?? code ?? '未知规则'
}

function actionLabel(action: string): string {
  const labels: Record<string, string> = {
    publish: '发布',
    upload: '上传',
    update: '更新',
    visibility: '可见性变更',
    admin_meta: '管理端元数据',
    precheck: '发布前预检',
  }
  return labels[action] ?? action
}

// ---------- 图表数据（统一归一化为 {label,count,pct}） ----------

function toBars(
  rows: ProductSlice[],
  total: number,
  labelOf: (value: string) => string,
): BarItem[] {
  const denominator = total > 0 ? total : 0
  return rows.map((row) => ({
    label: labelOf(row.product),
    count: row.count,
    pct: denominator > 0 ? (row.count / denominator) * 100 : 0,
  }))
}

function sliceBars(items: Slice[], denominator?: number): BarItem[] {
  const total =
    denominator ??
    items.reduce((sum, item) => sum + (Number.isFinite(item.count) ? item.count : 0), 0)
  return items.map((item) => ({
    label: item.label,
    count: item.count,
    pct: total > 0 ? (item.count / total) * 100 : 0,
  }))
}

function toOther(bucket: OtherBucket): { label: string; count: number; total: number } | undefined {
  return bucket && bucket.count > 0
    ? { label: '其他', count: bucket.count, total: bucket.total }
    : undefined
}

const productBars = computed<BarItem[]>(() =>
  toBars(content.value.products, content.value.totalDocs, (value) => {
    if (!value) return '未标注'
    return displayNames.value[value] ?? value
  }),
)
const productOther = computed(() => toOther(content.value.otherProducts))

const originBars = computed<BarItem[]>(() =>
  toBars(content.value.origins, content.value.totalDocs, (value) => value || '未知来源'),
)
const originOther = computed(() => toOther(content.value.otherOrigins))

const docTypeBars = computed<BarItem[]>(() =>
  sliceBars(
    content.value.docTypes.map((row) => ({
      label: docTypeLabel(row.product || '未标注'),
      count: row.count,
    })),
    content.value.totalDocs,
  ),
)

const languageBars = computed<BarItem[]>(() =>
  sliceBars(
    content.value.languages.map((row) => ({
      label: languageLabel(row.product || '未标注'),
      count: row.count,
    })),
    content.value.totalDocs,
  ),
)

const freshnessBars = computed<BarItem[]>(() => {
  const freshness = content.value.freshness
  return sliceBars(
    [
      { label: '≤30 天', count: freshness.within30Days },
      { label: '31~180 天', count: freshness.days31To180 },
      { label: '181~365 天', count: freshness.days181To365 },
      { label: '>365 天', count: freshness.over365Days },
      { label: '未知', count: freshness.unknown },
    ],
    content.value.totalDocs,
  )
})

const reviewDonut = computed<BarItem[]>(() => {
  const rows = governance.value.reviewStatus
  const total = rows.reduce((sum, row) => sum + (Number.isFinite(row.count) ? row.count : 0), 0)
  return rows.map((row) => ({
    label: reviewLabel(row.status),
    count: row.count,
    pct: total > 0 ? (row.count / total) * 100 : 0,
  }))
})

const ruleBars = computed<BarItem[]>(() =>
  sliceBars(
    governance.value.scan.byRule.map((row) => ({
      label: ruleLabel(row.ruleType),
      count: row.count,
    })),
  ),
)

const actionBars = computed<BarItem[]>(() =>
  sliceBars(
    governance.value.scan.byAction.map((row) => ({
      label: actionLabel(row.action),
      count: row.count,
    })),
  ),
)

const qaDonut = computed<BarItem[]>(() => {
  const total = qa.value.windowQa
  const found = Math.round(total * qa.value.foundRate)
  const missing = Math.max(0, total - found)
  return [
    { label: '命中', count: found, pct: total > 0 ? (found / total) * 100 : 0 },
    { label: '未命中', count: missing, pct: total > 0 ? (missing / total) * 100 : 0 },
  ]
})

const roleBars = computed<BarItem[]>(() =>
  sliceBars(
    users.value.roles.map((row) => ({ label: roleLabel(row.role), count: row.count })),
    users.value.total,
  ),
)

const contributorBars = computed<BarItem[]>(() =>
  sliceBars(
    users.value.topContributors.map((row) => ({
      label: row.username || '未署名',
      count: row.count,
    })),
  ),
)

// ---------- 环比文案 ----------

const newDocsDelta = computed(() => delta(content.value.windowNewDocs, content.value.prevWindowNewDocs))
const qaDelta = computed(() => delta(qa.value.windowQa, qa.value.prevWindowQa))
const scanDelta = computed(() =>
  delta(governance.value.scan.windowBlocks, governance.value.scan.prevWindowBlocks),
)
const newUsersDelta = computed(() =>
  delta(users.value.newInWindow, users.value.prevNewInWindow),
)
const activeUsersDelta = computed(() =>
  delta(users.value.activeInWindow, users.value.prevActiveInWindow),
)

const activeRatePct = computed(() => toPercent(cards.value.activeRate))
const foundRatePct = computed(() => toPercent(cards.value.foundRate))
const approvalRatePct = computed(() => toPercent(governance.value.approvalRate))

const coveragePct = computed(() => {
  const raw = toPercent(health.value.coverageRate)
  return Math.min(100, Math.max(0, Number.isFinite(raw) ? raw : 0))
})
const coverageText = computed(() => formatPct(coveragePct.value))
const coverageAria = computed(() => coveragePct.value.toFixed(1))
const dbSizeText = computed(() => formatBytes(health.value.dbSizeBytes))

const updatedAtText = computed(() => {
  const raw = data.value?.meta.generatedAt
  if (!raw) return '—'
  const date = new Date(raw)
  if (Number.isNaN(date.getTime())) return '—'
  return `${pad2(date.getHours())}:${pad2(date.getMinutes())}`
})

const todoEntries = computed<TodoEntry[]>(() => [
  {
    key: 'pending',
    label: '待审',
    value: todos.value.pendingReview,
    desc: '等待审核的文档',
    to: '/admin/moderation',
  },
  {
    key: 'rejected',
    label: '驳回待改',
    value: todos.value.rejectedStale,
    desc: '驳回超过 7 天未更新',
    to: '/admin/review',
  },
  {
    key: 'scan',
    label: `${rangeLabel.value}拦截`,
    value: todos.value.scanBlocks,
    desc: `拦截次数 · ${scanDelta.value.text}`,
    to: '/admin/scan-rules',
  },
  {
    key: 'blindspots',
    label: '知识盲区',
    value: todos.value.blindspots,
    desc: `${rangeLabel.value}未命中的问答`,
    to: '/admin/blindspots',
  },
])

// ---------- 请求 ----------

async function load(): Promise<void> {
  if (loading.value) return
  loading.value = true
  error.value = ''
  try {
    const [overview, categories] = await Promise.all([
      fetchAdminOverview(days.value),
      listAdminCategories().catch(() => [] as Category[]),
    ])
    data.value = overview
    displayNames.value = Object.fromEntries(
      categories.map((category) => [category.product, category.displayName || category.product]),
    )
  } catch {
    error.value = '概览数据加载失败，已保留上次结果，可重试。'
  } finally {
    loading.value = false
    initialLoading.value = false
  }
}

function switchDays(value: 7 | 30): void {
  if (days.value === value) return
  days.value = value
  void load()
}

function refresh(): void {
  void load()
}

function go(path: string): void {
  router.push(path)
}

onMounted(load)
</script>

<template>
  <div class="overview">
    <header class="overview-head">
      <div class="overview-head__text">
        <h2 class="overview-head__title">概览</h2>
        <p class="overview-head__sub">全系统状态看板 · {{ rangeLabel }}统计窗口</p>
      </div>

      <div class="overview-head__tools">
        <div class="seg" role="group" aria-label="统计时间窗">
          <button
            v-for="option in dayOptions"
            :key="option"
            type="button"
            class="seg__btn"
            :class="{ 'seg__btn--active': days === option }"
            :aria-pressed="days === option"
            @click="switchDays(option)"
          >
            近 {{ option }} 天
          </button>
        </div>

        <button type="button" class="toolbar-btn" :disabled="loading" @click="refresh">
          <span
            class="toolbar-btn__icon"
            :class="{ 'toolbar-btn__icon--spin': loading }"
            aria-hidden="true"
          >
            ↻
          </span>
          {{ loading ? '刷新中' : '刷新' }}
        </button>

        <span class="overview-head__time num">数据更新于 {{ updatedAtText }}</span>
      </div>
    </header>

    <div v-if="error" class="error-bar" role="alert">
      <span>{{ error }}</span>
      <button type="button" class="error-bar__retry" @click="refresh">重试</button>
    </div>

    <div v-if="initialLoading" class="skeleton" aria-busy="true" aria-label="概览加载中">
      <div class="skeleton__todo">
        <div v-for="n in 4" :key="n" class="skeleton__block skeleton__block--todo" />
      </div>
      <div class="skeleton__cards">
        <div v-for="n in 8" :key="n" class="skeleton__block skeleton__block--card" />
      </div>
      <div class="skeleton__panels">
        <div v-for="n in 4" :key="n" class="skeleton__block skeleton__block--panel" />
      </div>
    </div>

    <template v-else-if="data">
      <section class="todo-bar" aria-label="待办事项">
        <button
          v-for="item in todoEntries"
          :key="item.key"
          type="button"
          class="todo-item"
          :class="{ 'todo-item--zero': item.value === 0 }"
          :aria-label="`${item.label} ${item.value}，${item.desc}，前往${item.to}`"
          @click="go(item.to)"
        >
          <span class="todo-value num">{{ formatNumber(item.value) }}</span>
          <span class="todo-label">{{ item.label }}</span>
          <span class="todo-desc" :title="item.desc">{{ item.desc }}</span>
        </button>
      </section>

      <section v-if="cards.totalDocs === 0" class="panel empty-state" aria-label="空知识库引导">
        <p class="empty__title">知识库还是空的 —— 先导入一篇，概览才有东西可算</p>
        <p class="empty__hint">
          文档一进来，内容资产、审核合规、问答与系统健康指标就会在这里自动汇总。
        </p>
        <el-button type="primary" @click="go('/admin/import')">去导入文档</el-button>
      </section>

      <template v-else>
        <div class="card-grid">
          <StatCard
            label="文档总数"
            :value="cards.totalDocs"
            :hint="`${rangeLabel}新增 ${formatNumber(content.windowNewDocs)}`"
            :delta="newDocsDelta"
            :tooltip="METRIC_HINTS.totalDocs.text"
            clickable
            @click="go('/admin/documents')"
          />
          <StatCard
            label="Chunk 总数"
            :value="cards.chunks"
            :hint="`平均每篇 ${fixed(content.avgChunksPerDoc)}`"
            :tooltip="METRIC_HINTS.chunks.text"
            clickable
            @click="go('/admin/documents')"
          />
          <StatCard
            label="活跃率"
            :value="activeRatePct"
            suffix="%"
            :decimals="1"
            hint="启用文档 ÷ 文档总数"
            :tooltip="METRIC_HINTS.activeRate.text"
            clickable
            @click="go('/admin/documents')"
          />
          <StatCard
            label="待审"
            :value="cards.pendingReview"
            tone="danger"
            hint="等待审核的文档"
            :tooltip="METRIC_HINTS.pendingReview.text"
            clickable
            @click="go('/admin/moderation')"
          />
          <StatCard
            label="窗口内问答数"
            :value="cards.windowQa"
            :delta="qaDelta"
            :hint="`命中率 ${formatPct(foundRatePct)}`"
            :tooltip="METRIC_HINTS.windowQa.text"
            clickable
            @click="go('/admin/qa-stats')"
          />
          <StatCard
            label="问答命中率"
            :value="foundRatePct"
            suffix="%"
            :decimals="1"
            hint="窗口内 found=true ÷ 问答数"
            :tooltip="METRIC_HINTS.foundRate.text"
            clickable
            @click="go('/admin/qa-stats')"
          />
          <StatCard
            label="用户数"
            :value="cards.users"
            :hint="`${rangeLabel}活跃 ${formatNumber(users.activeInWindow)} 人`"
            :tooltip="METRIC_HINTS.users.text"
            clickable
            @click="go('/admin/members')"
          />
          <StatCard
            label="窗口内新增文档"
            :value="cards.windowNewDocs"
            :delta="newDocsDelta"
            :hint="rangeLabel"
            :tooltip="METRIC_HINTS.windowNewDocs.text"
            clickable
            @click="go('/admin/documents')"
          />
        </div>

        <div class="panel-grid">
          <section class="panel" aria-label="内容资产">
            <div class="panel-head">
              <h3 class="panel-title">内容资产</h3>
              <el-tooltip :content="METRIC_HINTS.contentPanel.text" placement="top" :show-after="120">
                <span
                  class="hint-dot"
                  tabindex="0"
                  role="note"
                  :aria-label="`口径说明：${METRIC_HINTS.contentPanel.text}`"
                >
                  ?
                </span>
              </el-tooltip>
            </div>

            <div class="sub-block">
              <div class="sub-head">
                <h4 class="sub-title">技术分布</h4>
                <el-tooltip
                  :content="METRIC_HINTS.contentProducts.text"
                  placement="top"
                  :show-after="120"
                >
                  <span
                    class="hint-dot"
                    tabindex="0"
                    role="note"
                    :aria-label="`口径说明：${METRIC_HINTS.contentProducts.text}`"
                  >
                    ?
                  </span>
                </el-tooltip>
              </div>
              <BarList
                :items="productBars"
                ariaLabel="技术分布（Top 8 + 其他）"
                :other="productOther"
                @other-click="go('/admin/documents')"
              />
            </div>

            <div class="sub-block">
              <div class="sub-head">
                <h4 class="sub-title">来源分布</h4>
                <el-tooltip
                  :content="METRIC_HINTS.contentOrigins.text"
                  placement="top"
                  :show-after="120"
                >
                  <span
                    class="hint-dot"
                    tabindex="0"
                    role="note"
                    :aria-label="`口径说明：${METRIC_HINTS.contentOrigins.text}`"
                  >
                    ?
                  </span>
                </el-tooltip>
              </div>
              <BarList
                :items="originBars"
                ariaLabel="来源分布（Top 8 + 其他）"
                :other="originOther"
                @other-click="go('/admin/documents')"
              />
            </div>

            <div class="sub-block">
              <div class="sub-head">
                <h4 class="sub-title">文档类型</h4>
                <el-tooltip
                  :content="METRIC_HINTS.contentDocTypes.text"
                  placement="top"
                  :show-after="120"
                >
                  <span
                    class="hint-dot"
                    tabindex="0"
                    role="note"
                    :aria-label="`口径说明：${METRIC_HINTS.contentDocTypes.text}`"
                  >
                    ?
                  </span>
                </el-tooltip>
              </div>
              <SplitBar :items="docTypeBars" ariaLabel="文档类型分布" />
            </div>

            <div class="sub-block">
              <div class="sub-head">
                <h4 class="sub-title">语言分布</h4>
                <el-tooltip
                  :content="METRIC_HINTS.contentLanguages.text"
                  placement="top"
                  :show-after="120"
                >
                  <span
                    class="hint-dot"
                    tabindex="0"
                    role="note"
                    :aria-label="`口径说明：${METRIC_HINTS.contentLanguages.text}`"
                  >
                    ?
                  </span>
                </el-tooltip>
              </div>
              <SplitBar :items="languageBars" ariaLabel="语言分布" />
            </div>

            <div class="sub-block">
              <div class="sub-head">
                <h4 class="sub-title">内容新鲜度</h4>
                <el-tooltip
                  :content="METRIC_HINTS.contentFreshness.text"
                  placement="top"
                  :show-after="120"
                >
                  <span
                    class="hint-dot"
                    tabindex="0"
                    role="note"
                    :aria-label="`口径说明：${METRIC_HINTS.contentFreshness.text}`"
                  >
                    ?
                  </span>
                </el-tooltip>
              </div>
              <SplitBar :items="freshnessBars" ariaLabel="内容新鲜度分段" />
            </div>

            <div class="sub-block">
              <div class="sub-head">
                <h4 class="sub-title">辅助指标</h4>
                <el-tooltip
                  :content="METRIC_HINTS.contentAux.text"
                  placement="top"
                  :show-after="120"
                >
                  <span
                    class="hint-dot"
                    tabindex="0"
                    role="note"
                    :aria-label="`口径说明：${METRIC_HINTS.contentAux.text}`"
                  >
                    ?
                  </span>
                </el-tooltip>
              </div>
              <div class="mini-row">
                <div class="mini">
                  <span class="mini__label">平均 Chunk / 篇</span>
                  <span class="mini__value num">{{ fixed(content.avgChunksPerDoc) }}</span>
                </div>
                <div class="mini">
                  <span class="mini__label">空 Chunk 文档</span>
                  <span class="mini__value num">{{ formatNumber(content.emptyChunkDocs) }}</span>
                </div>
                <div class="mini">
                  <span class="mini__label">平均年龄</span>
                  <span class="mini__value num">{{ fixed(content.avgAgeDays) }} 天</span>
                </div>
                <div class="mini">
                  <span class="mini__label">今日新增</span>
                  <span class="mini__value num">{{ formatNumber(content.todayNewDocs) }}</span>
                </div>
              </div>
            </div>
          </section>

          <section class="panel" aria-label="审核合规">
            <div class="panel-head">
              <h3 class="panel-title">审核合规</h3>
              <el-tooltip
                :content="METRIC_HINTS.governancePanel.text"
                placement="top"
                :show-after="120"
              >
                <span
                  class="hint-dot"
                  tabindex="0"
                  role="note"
                  :aria-label="`口径说明：${METRIC_HINTS.governancePanel.text}`"
                >
                  ?
                </span>
              </el-tooltip>
            </div>

            <div class="sub-block">
              <div class="sub-head">
                <h4 class="sub-title">审核状态</h4>
                <el-tooltip
                  :content="METRIC_HINTS.reviewStatus.text"
                  placement="top"
                  :show-after="120"
                >
                  <span
                    class="hint-dot"
                    tabindex="0"
                    role="note"
                    :aria-label="`口径说明：${METRIC_HINTS.reviewStatus.text}`"
                  >
                    ?
                  </span>
                </el-tooltip>
              </div>
              <DonutChart :items="reviewDonut" ariaLabel="审核状态分布" :height="170" />
            </div>

            <div class="sub-block">
              <div class="sub-head">
                <h4 class="sub-title">审核指标</h4>
                <el-tooltip
                  :content="METRIC_HINTS.reviewMetrics.text"
                  placement="top"
                  :show-after="120"
                >
                  <span
                    class="hint-dot"
                    tabindex="0"
                    role="note"
                    :aria-label="`口径说明：${METRIC_HINTS.reviewMetrics.text}`"
                  >
                    ?
                  </span>
                </el-tooltip>
              </div>
              <div class="mini-row">
                <div class="mini">
                  <span class="mini__label">通过率</span>
                  <span class="mini__value num">{{ formatPct(approvalRatePct) }}</span>
                </div>
                <div class="mini">
                  <span class="mini__label">平均审核时长</span>
                  <span class="mini__value num">{{ fixed(governance.avgReviewHours) }} 小时</span>
                </div>
                <div class="mini">
                  <span class="mini__label">最久待审</span>
                  <span class="mini__value num">{{ fixed(governance.pendingOldestDays) }} 天</span>
                </div>
                <div class="mini">
                  <span class="mini__label">未读通知</span>
                  <span class="mini__value num">{{ formatNumber(governance.notificationsUnread) }}</span>
                </div>
              </div>
            </div>

            <div class="sub-block">
              <div class="sub-head">
                <h4 class="sub-title">脱敏规则 Top 5</h4>
                <el-tooltip :content="METRIC_HINTS.scanRules.text" placement="top" :show-after="120">
                  <span
                    class="hint-dot"
                    tabindex="0"
                    role="note"
                    :aria-label="`口径说明：${METRIC_HINTS.scanRules.text}`"
                  >
                    ?
                  </span>
                </el-tooltip>
              </div>
              <BarList
                v-if="ruleBars.length > 0"
                :items="ruleBars"
                ariaLabel="脱敏规则命中 Top 5"
                :max-items="5"
              />
              <p v-else class="inline-empty">{{ rangeLabel }}无拦截记录</p>
            </div>

            <div class="sub-block">
              <div class="sub-head">
                <h4 class="sub-title">拦截动作</h4>
                <el-tooltip :content="METRIC_HINTS.scanActions.text" placement="top" :show-after="120">
                  <span
                    class="hint-dot"
                    tabindex="0"
                    role="note"
                    :aria-label="`口径说明：${METRIC_HINTS.scanActions.text}`"
                  >
                    ?
                  </span>
                </el-tooltip>
              </div>
              <SplitBar v-if="actionBars.length > 0" :items="actionBars" ariaLabel="拦截动作分布" />
              <p v-else class="inline-empty">{{ rangeLabel }}无拦截记录</p>
            </div>

            <div class="sub-block">
              <div class="sub-head">
                <h4 class="sub-title">扫描摘要</h4>
                <el-tooltip :content="METRIC_HINTS.scanSummary.text" placement="top" :show-after="120">
                  <span
                    class="hint-dot"
                    tabindex="0"
                    role="note"
                    :aria-label="`口径说明：${METRIC_HINTS.scanSummary.text}`"
                  >
                    ?
                  </span>
                </el-tooltip>
              </div>
              <div class="mini-row">
                <div class="mini">
                  <span class="mini__label">窗口拦截</span>
                  <span class="mini__value num">{{ formatNumber(governance.scan.windowBlocks) }}</span>
                </div>
                <div class="mini">
                  <span class="mini__label">去重标题</span>
                  <span class="mini__value num">{{ formatNumber(governance.scan.blockedTitles) }}</span>
                </div>
                <div class="mini">
                  <span class="mini__label">命中次数</span>
                  <span class="mini__value num">{{ formatNumber(governance.scan.hits) }}</span>
                </div>
              </div>
            </div>
          </section>

          <section class="panel" aria-label="问答检索">
            <div class="panel-head">
              <h3 class="panel-title">问答检索</h3>
              <el-tooltip :content="METRIC_HINTS.qaPanel.text" placement="top" :show-after="120">
                <span
                  class="hint-dot"
                  tabindex="0"
                  role="note"
                  :aria-label="`口径说明：${METRIC_HINTS.qaPanel.text}`"
                >
                  ?
                </span>
              </el-tooltip>
            </div>

            <div class="sub-block">
              <div class="sub-head">
                <h4 class="sub-title">趋势</h4>
                <el-tooltip :content="METRIC_HINTS.qaTrend.text" placement="top" :show-after="120">
                  <span
                    class="hint-dot"
                    tabindex="0"
                    role="note"
                    :aria-label="`口径说明：${METRIC_HINTS.qaTrend.text}`"
                  >
                    ?
                  </span>
                </el-tooltip>
              </div>
              <TrendChart
                :points="trend.points"
                :days="trend.days"
                :ariaLabel="`${rangeLabel}新增文档与问答次数趋势`"
                :height="220"
              />
            </div>

            <div class="sub-block">
              <div class="sub-head">
                <h4 class="sub-title">窗口内命中率</h4>
                <el-tooltip :content="METRIC_HINTS.qaHitRate.text" placement="top" :show-after="120">
                  <span
                    class="hint-dot"
                    tabindex="0"
                    role="note"
                    :aria-label="`口径说明：${METRIC_HINTS.qaHitRate.text}`"
                  >
                    ?
                  </span>
                </el-tooltip>
              </div>
              <DonutChart :items="qaDonut" ariaLabel="窗口内问答命中率" :height="160" />
            </div>

            <div class="sub-block">
              <div class="sub-head">
                <h4 class="sub-title">知识盲区 Top 5</h4>
                <el-tooltip
                  :content="METRIC_HINTS.qaBlindspots.text"
                  placement="top"
                  :show-after="120"
                >
                  <span
                    class="hint-dot"
                    tabindex="0"
                    role="note"
                    :aria-label="`口径说明：${METRIC_HINTS.qaBlindspots.text}`"
                  >
                    ?
                  </span>
                </el-tooltip>
              </div>
              <ul v-if="qa.blindspotsTop.length > 0" class="rank-list">
                <li v-for="(item, index) in qa.blindspotsTop" :key="`${index}-${item.question}`">
                  <button
                    type="button"
                    class="rank-row"
                    :aria-label="`${item.question}，出现 ${item.count} 次，查看知识盲区`"
                    @click="go('/admin/blindspots')"
                  >
                    <span class="rank-text" :title="item.question">{{ clip(item.question) }}</span>
                    <span class="rank-count num">{{ formatNumber(item.count) }}</span>
                  </button>
                </li>
              </ul>
              <p v-else class="inline-empty">{{ rangeLabel }}无未命中的问答</p>
            </div>

            <div class="sub-block">
              <div class="sub-head">
                <h4 class="sub-title">辅助指标</h4>
                <el-tooltip :content="METRIC_HINTS.qaAux.text" placement="top" :show-after="120">
                  <span
                    class="hint-dot"
                    tabindex="0"
                    role="note"
                    :aria-label="`口径说明：${METRIC_HINTS.qaAux.text}`"
                  >
                    ?
                  </span>
                </el-tooltip>
              </div>
              <div class="mini-row">
                <div class="mini">
                  <span class="mini__label">会话总数</span>
                  <span class="mini__value num">{{ formatNumber(qa.conversations) }}</span>
                </div>
                <div class="mini">
                  <span class="mini__label">消息总数</span>
                  <span class="mini__value num">{{ formatNumber(qa.messages) }}</span>
                </div>
                <div class="mini">
                  <span class="mini__label">窗口内问答</span>
                  <span class="mini__value num">{{ formatNumber(qa.windowQa) }}</span>
                </div>
                <div class="mini">
                  <span class="mini__label">平均引用数</span>
                  <span class="mini__value num">{{ fixed(qa.avgSources) }}</span>
                </div>
                <div class="mini">
                  <span class="mini__label">活跃提问人</span>
                  <span class="mini__value num">{{ formatNumber(qa.activeAskers) }}</span>
                </div>
              </div>
            </div>
          </section>

          <section class="panel" aria-label="用户互动">
            <div class="panel-head">
              <h3 class="panel-title">用户互动</h3>
              <el-tooltip :content="METRIC_HINTS.usersPanel.text" placement="top" :show-after="120">
                <span
                  class="hint-dot"
                  tabindex="0"
                  role="note"
                  :aria-label="`口径说明：${METRIC_HINTS.usersPanel.text}`"
                >
                  ?
                </span>
              </el-tooltip>
            </div>

            <div class="sub-block">
              <div class="sub-head">
                <h4 class="sub-title">角色分布</h4>
                <el-tooltip :content="METRIC_HINTS.userRoles.text" placement="top" :show-after="120">
                  <span
                    class="hint-dot"
                    tabindex="0"
                    role="note"
                    :aria-label="`口径说明：${METRIC_HINTS.userRoles.text}`"
                  >
                    ?
                  </span>
                </el-tooltip>
              </div>
              <SplitBar :items="roleBars" ariaLabel="用户角色分布" />
            </div>

            <div class="sub-block">
              <div class="sub-head">
                <h4 class="sub-title">窗口内用户</h4>
                <el-tooltip :content="METRIC_HINTS.userWindow.text" placement="top" :show-after="120">
                  <span
                    class="hint-dot"
                    tabindex="0"
                    role="note"
                    :aria-label="`口径说明：${METRIC_HINTS.userWindow.text}`"
                  >
                    ?
                  </span>
                </el-tooltip>
              </div>
              <div class="mini-row">
                <div class="mini">
                  <span class="mini__label">用户总数</span>
                  <span class="mini__value num">{{ formatNumber(users.total) }}</span>
                </div>
                <div class="mini">
                  <span class="mini__label">{{ rangeLabel }}新增</span>
                  <span class="mini__value num">{{ formatNumber(users.newInWindow) }}</span>
                  <span class="mini__delta num" :class="`mini__delta--${newUsersDelta.direction}`">
                    {{ newUsersDelta.text }}
                  </span>
                </div>
                <div class="mini">
                  <span class="mini__label">{{ rangeLabel }}活跃</span>
                  <span class="mini__value num">{{ formatNumber(users.activeInWindow) }}</span>
                  <span class="mini__delta num" :class="`mini__delta--${activeUsersDelta.direction}`">
                    {{ activeUsersDelta.text }}
                  </span>
                </div>
              </div>
            </div>

            <div class="sub-block">
              <div class="sub-head">
                <h4 class="sub-title">贡献者 Top 5</h4>
                <el-tooltip
                  :content="METRIC_HINTS.userContributors.text"
                  placement="top"
                  :show-after="120"
                >
                  <span
                    class="hint-dot"
                    tabindex="0"
                    role="note"
                    :aria-label="`口径说明：${METRIC_HINTS.userContributors.text}`"
                  >
                    ?
                  </span>
                </el-tooltip>
              </div>
              <BarList
                v-if="contributorBars.length > 0"
                :items="contributorBars"
                ariaLabel="贡献者 Top 5（按文档数）"
                :max-items="5"
              />
              <p v-else class="inline-empty">暂无贡献者</p>
            </div>

            <div class="sub-block">
              <div class="sub-head">
                <h4 class="sub-title">收藏 Top 5</h4>
                <el-tooltip
                  :content="METRIC_HINTS.userFavorites.text"
                  placement="top"
                  :show-after="120"
                >
                  <span
                    class="hint-dot"
                    tabindex="0"
                    role="note"
                    :aria-label="`口径说明：${METRIC_HINTS.userFavorites.text}`"
                  >
                    ?
                  </span>
                </el-tooltip>
              </div>
              <ul v-if="engagement.topFavorites.length > 0" class="rank-list">
                <li
                  v-for="item in engagement.topFavorites"
                  :key="item.docId"
                  class="rank-item"
                >
                  <span class="rank-text" :title="item.title || '（无标题文档）'">
                    {{ item.title || '（无标题文档）' }}
                  </span>
                  <span class="rank-count num">{{ formatNumber(item.count) }}</span>
                </li>
              </ul>
              <p v-else class="inline-empty">暂无收藏</p>
            </div>
          </section>

          <section class="panel panel--wide" aria-label="系统健康">
            <div class="panel-head">
              <h3 class="panel-title">系统健康</h3>
              <el-tooltip :content="METRIC_HINTS.healthPanel.text" placement="top" :show-after="120">
                <span
                  class="hint-dot"
                  tabindex="0"
                  role="note"
                  :aria-label="`口径说明：${METRIC_HINTS.healthPanel.text}`"
                >
                  ?
                </span>
              </el-tooltip>
            </div>

            <div class="health-grid">
              <div class="coverage">
                <div class="coverage__head">
                  <span class="coverage__label">
                    向量覆盖率
                    <el-tooltip
                      :content="METRIC_HINTS.healthCoverage.text"
                      placement="top"
                      :show-after="120"
                    >
                      <span
                        class="hint-dot"
                        tabindex="0"
                        role="note"
                        :aria-label="`口径说明：${METRIC_HINTS.healthCoverage.text}`"
                      >
                        ?
                      </span>
                    </el-tooltip>
                  </span>
                  <span class="coverage__value num">{{ coverageText }}</span>
                </div>
                <div
                  class="coverage__track"
                  role="progressbar"
                  aria-valuemin="0"
                  aria-valuemax="100"
                  :aria-valuenow="coverageAria"
                  :aria-label="`向量覆盖率 ${coverageText}`"
                >
                  <span class="coverage__fill" :style="{ width: `${coveragePct}%` }" />
                </div>
                <p class="coverage__meta num">
                  {{ formatNumber(health.vectorRows) }} 向量 / {{ formatNumber(content.chunks) }} chunks
                </p>
              </div>

              <div class="runtime">
                <div class="runtime__head">
                  <h4 class="sub-title">运行信息</h4>
                  <el-tooltip
                    :content="METRIC_HINTS.healthRuntime.text"
                    placement="top"
                    :show-after="120"
                  >
                    <span
                      class="hint-dot"
                      tabindex="0"
                      role="note"
                      :aria-label="`口径说明：${METRIC_HINTS.healthRuntime.text}`"
                    >
                      ?
                    </span>
                  </el-tooltip>
                </div>
                <ul class="runtime__list">
                  <li>
                    <span class="runtime__label">数据库体积</span>
                    <span class="num">{{ dbSizeText }}</span>
                  </li>
                  <li>
                    <span class="runtime__label">最近导入</span>
                    <span
                      class="runtime__value"
                      :title="formatAbsolute(health.lastIngestedAt)"
                    >
                      {{ relativeText(health.lastIngestedAt) }}
                    </span>
                  </li>
                  <li>
                    <span class="runtime__label">最近扫描</span>
                    <span class="runtime__value" :title="formatAbsolute(health.lastScanAt)">
                      {{ relativeText(health.lastScanAt) }}
                    </span>
                  </li>
                  <li>
                    <span class="runtime__label">最近问答</span>
                    <span class="runtime__value" :title="formatAbsolute(health.lastQaAt)">
                      {{ relativeText(health.lastQaAt) }}
                    </span>
                  </li>
                </ul>
              </div>
            </div>
          </section>
        </div>
      </template>
    </template>
  </div>
</template>

<style scoped>
.overview {
  display: flex;
  flex-direction: column;
  gap: var(--sp-4);
  min-width: 0;
  padding-bottom: var(--sp-8);
}

/* ---------- 工具栏 ---------- */

.overview-head {
  display: flex;
  align-items: flex-end;
  justify-content: space-between;
  gap: var(--sp-4);
  flex-wrap: wrap;
}

.overview-head__title {
  font-size: var(--fs-3xl);
  font-weight: 600;
  color: var(--ink-900);
  letter-spacing: -0.01em;
  margin: 0;
}

.overview-head__sub {
  margin-top: var(--sp-1);
  font-size: var(--fs-sm);
  color: var(--ink-500);
}

.overview-head__tools {
  display: flex;
  align-items: center;
  gap: var(--sp-3);
  flex-wrap: wrap;
}

.seg {
  display: inline-flex;
  border: 1px solid var(--hairline);
  border-radius: var(--r-md);
  overflow: hidden;
  background: var(--surface);
}

.seg__btn {
  padding: var(--sp-1) var(--sp-3);
  font-size: var(--fs-sm);
  color: var(--ink-600);
  background: transparent;
  transition:
    background-color var(--dur-fast) var(--ease-out),
    color var(--dur-fast) var(--ease-out);
}

.seg__btn + .seg__btn {
  border-left: 1px solid var(--hairline);
}

.seg__btn:hover {
  background: var(--surface-sunken);
  color: var(--ink-900);
}

.seg__btn--active,
.seg__btn--active:hover {
  background: var(--ink-900);
  color: var(--surface);
}

.toolbar-btn {
  display: inline-flex;
  align-items: center;
  gap: var(--sp-2);
  padding: var(--sp-1) var(--sp-3);
  border: 1px solid var(--hairline);
  border-radius: var(--r-md);
  background: var(--surface);
  color: var(--ink-700);
  font-size: var(--fs-sm);
  transition:
    border-color var(--dur-fast) var(--ease-out),
    color var(--dur-fast) var(--ease-out);
}

.toolbar-btn:hover:not(:disabled) {
  border-color: var(--ink-400);
  color: var(--ink-900);
}

.toolbar-btn:disabled {
  opacity: 0.6;
  cursor: not-allowed;
}

.toolbar-btn__icon {
  display: inline-block;
  font-size: var(--fs-md);
  line-height: 1;
}

.toolbar-btn__icon--spin {
  animation: spin 900ms linear infinite;
}

@keyframes spin {
  to {
    transform: rotate(360deg);
  }
}

.overview-head__time {
  font-size: var(--fs-xs);
  color: var(--ink-500);
  white-space: nowrap;
}

/* ---------- 错误条 ---------- */

.error-bar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: var(--sp-3);
  padding: var(--sp-2) var(--sp-4);
  border: 1px solid var(--brand-200);
  border-radius: var(--r-md);
  background: var(--brand-50);
  color: var(--brand-700);
  font-size: var(--fs-sm);
}

.error-bar__retry {
  flex: none;
  color: var(--brand-700);
  font-weight: 600;
  text-decoration: underline;
  text-underline-offset: 2px;
}

/* ---------- 待办条 ---------- */

.todo-bar {
  display: grid;
  grid-template-columns: repeat(4, minmax(0, 1fr));
  background: var(--surface);
  border: 1px solid var(--hairline);
  border-radius: var(--r-lg);
  overflow: hidden;
}

.todo-item {
  display: flex;
  flex-direction: column;
  gap: 2px;
  min-width: 0;
  padding: var(--sp-4) var(--sp-5);
  text-align: left;
  background: transparent;
  transition: background-color var(--dur-fast) var(--ease-out);
}

.todo-item + .todo-item {
  border-left: 1px solid var(--hairline);
}

.todo-item:hover {
  background: var(--surface-sunken);
}

.todo-item--zero {
  opacity: 0.55;
}

.todo-value {
  font-family: var(--font-num);
  font-variant-numeric: tabular-nums;
  font-size: var(--fs-3xl);
  font-weight: 600;
  line-height: 1.15;
  color: var(--ink-900);
}

.todo-item--zero .todo-value {
  color: var(--ink-500);
}

.todo-label {
  font-size: var(--fs-sm);
  font-weight: 600;
  color: var(--ink-700);
}

.todo-desc {
  font-size: var(--fs-xs);
  color: var(--ink-500);
  overflow: hidden;
  white-space: nowrap;
  text-overflow: ellipsis;
}

/* ---------- 核心指标卡 ---------- */

.card-grid {
  display: grid;
  grid-template-columns: repeat(4, minmax(0, 1fr));
  gap: var(--sp-3);
}

/* ---------- 分区面板 ---------- */

.panel-grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: var(--sp-4);
  align-items: start;
}

.panel {
  min-width: 0;
  background: var(--surface);
  border: 1px solid var(--hairline);
  border-radius: var(--r-lg);
  padding: var(--sp-5);
}

.panel--wide {
  grid-column: 1 / -1;
}

.panel-head {
  display: flex;
  align-items: center;
  gap: var(--sp-2);
  padding-bottom: var(--sp-3);
  border-bottom: 1px solid var(--hairline);
}

.panel-title {
  font-size: var(--fs-lg);
  font-weight: 600;
  color: var(--ink-900);
  margin: 0;
}

.sub-block {
  margin-top: var(--sp-4);
}

.sub-block + .sub-block {
  margin-top: var(--sp-4);
  padding-top: var(--sp-4);
  border-top: 1px solid var(--hairline);
}

.sub-head {
  display: flex;
  align-items: center;
  gap: var(--sp-2);
  margin-bottom: var(--sp-3);
}

.sub-title {
  font-size: var(--fs-sm);
  font-weight: 600;
  color: var(--ink-700);
  margin: 0;
}

.hint-dot {
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

.hint-dot:hover,
.hint-dot:focus-visible {
  color: var(--brand-600);
  border-color: var(--brand-600);
}

/* ---------- 小卡 ---------- */

.mini-row {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(110px, 1fr));
  gap: var(--sp-2);
}

.mini {
  display: flex;
  flex-direction: column;
  gap: 2px;
  min-width: 0;
  padding: var(--sp-2) var(--sp-3);
  background: var(--surface-sunken);
  border: 1px solid var(--hairline);
  border-radius: var(--r-md);
}

.mini__label {
  font-size: var(--fs-xs);
  color: var(--ink-500);
}

.mini__value {
  font-size: var(--fs-xl);
  font-weight: 600;
  line-height: 1.25;
  color: var(--ink-900);
}

.mini__delta {
  font-size: 11px;
  color: var(--ink-500);
}

.mini__delta--up {
  color: var(--ink-700);
}

.mini__delta--down {
  color: var(--brand-600);
}

/* ---------- 自绘列表 ---------- */

.rank-list {
  list-style: none;
  display: flex;
  flex-direction: column;
}

.rank-list li + li {
  border-top: 1px solid var(--hairline);
}

.rank-row,
.rank-item {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: var(--sp-3);
  width: 100%;
  min-width: 0;
  padding: var(--sp-2) 0;
  text-align: left;
  font-size: var(--fs-sm);
  color: var(--ink-700);
}

.rank-row {
  border-radius: var(--r-sm);
  transition: background-color var(--dur-fast) var(--ease-out);
}

.rank-row:hover {
  background: var(--surface-sunken);
}

.rank-text {
  min-width: 0;
  overflow: hidden;
  white-space: nowrap;
  text-overflow: ellipsis;
}

.rank-count {
  flex: none;
  font-weight: 600;
  color: var(--ink-900);
}

.inline-empty {
  padding: var(--sp-3) 0;
  font-size: var(--fs-sm);
  color: var(--ink-500);
}

/* ---------- 系统健康 ---------- */

.health-grid {
  display: grid;
  grid-template-columns: minmax(0, 1.1fr) minmax(0, 1fr);
  gap: var(--sp-6);
  margin-top: var(--sp-4);
}

.coverage__head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: var(--sp-3);
  font-size: var(--fs-sm);
  color: var(--ink-600);
}

.coverage__label {
  display: inline-flex;
  align-items: center;
  gap: var(--sp-2);
}

.coverage__value {
  font-size: var(--fs-xl);
  font-weight: 600;
  color: var(--ink-900);
}

.coverage__track {
  height: 8px;
  margin-top: var(--sp-3);
  border-radius: 999px;
  background: var(--ink-100);
  overflow: hidden;
}

.coverage__fill {
  display: block;
  height: 100%;
  border-radius: 999px;
  background: var(--ink-700);
  transition: width var(--dur-slow) var(--ease-out);
}

.coverage__meta {
  margin-top: var(--sp-2);
  font-size: var(--fs-xs);
  color: var(--ink-500);
}

.runtime__head {
  display: flex;
  align-items: center;
  gap: var(--sp-2);
  margin-bottom: var(--sp-2);
}

.runtime__list {
  list-style: none;
  display: flex;
  flex-direction: column;
  gap: var(--sp-2);
}

.runtime__list li {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: var(--sp-3);
  padding-bottom: var(--sp-2);
  border-bottom: 1px solid var(--hairline);
  font-size: var(--fs-sm);
  color: var(--ink-700);
}

.runtime__list li:last-child {
  padding-bottom: 0;
  border-bottom: none;
}

.runtime__label {
  color: var(--ink-500);
}

.runtime__value {
  min-width: 0;
  overflow: hidden;
  white-space: nowrap;
  text-overflow: ellipsis;
}

/* ---------- 空态 / 骨架 ---------- */

.empty-state {
  padding: var(--sp-12) var(--sp-6);
  text-align: center;
}

.empty__title {
  font-size: var(--fs-lg);
  font-weight: 600;
  color: var(--ink-900);
}

.empty__hint {
  margin: var(--sp-2) 0 var(--sp-6);
  font-size: var(--fs-sm);
  color: var(--ink-500);
}

.skeleton {
  display: flex;
  flex-direction: column;
  gap: var(--sp-4);
}

.skeleton__todo,
.skeleton__cards,
.skeleton__panels {
  display: grid;
  gap: var(--sp-3);
}

.skeleton__todo {
  grid-template-columns: repeat(4, minmax(0, 1fr));
}

.skeleton__cards {
  grid-template-columns: repeat(4, minmax(0, 1fr));
}

.skeleton__panels {
  grid-template-columns: repeat(2, minmax(0, 1fr));
}

.skeleton__block {
  background: var(--ink-100);
  border: 1px solid var(--hairline);
  border-radius: var(--r-lg);
  animation: skeleton-pulse 1.4s ease-in-out infinite;
}

.skeleton__block--todo {
  height: 84px;
}

.skeleton__block--card {
  height: 110px;
}

.skeleton__block--panel {
  height: 240px;
}

@keyframes skeleton-pulse {
  0%,
  100% {
    opacity: 1;
  }
  50% {
    opacity: 0.55;
  }
}

@media (prefers-reduced-motion: reduce) {
  .skeleton__block,
  .toolbar-btn__icon--spin {
    animation: none;
  }
}

/* ---------- 响应式 ---------- */

@media (max-width: 1180px) {
  .card-grid,
  .skeleton__cards {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }
}

@media (max-width: 1100px) {
  .panel-grid,
  .skeleton__panels {
    grid-template-columns: minmax(0, 1fr);
  }

  .panel--wide {
    grid-column: auto;
  }
}

@media (max-width: 900px) {
  .todo-bar,
  .skeleton__todo {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }

  .todo-item:nth-child(odd) {
    border-left: none;
  }

  .todo-item:nth-child(n + 3) {
    border-top: 1px solid var(--hairline);
  }

  .health-grid {
    grid-template-columns: minmax(0, 1fr);
    gap: var(--sp-4);
  }
}

@media (max-width: 640px) {
  .overview-head {
    align-items: flex-start;
    flex-direction: column;
  }

  .card-grid,
  .skeleton__cards {
    grid-template-columns: minmax(0, 1fr);
  }

  .todo-bar,
  .skeleton__todo {
    grid-template-columns: minmax(0, 1fr);
  }

  .todo-item + .todo-item {
    border-left: none;
    border-top: 1px solid var(--hairline);
  }
}
</style>
