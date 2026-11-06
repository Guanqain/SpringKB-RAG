<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import { fetchKbOverview, fetchKbTree } from '@/api/kb'
import type { KbOverview, KbTreeNode } from '@/types/kb'

// 首页：Hero + 统计条 + 产品线卡 + 功能区。
// 数据口径：公开的 GET /api/kb/overview 与 /api/kb/tree，拿不到就显示占位，不编造数字。

const router = useRouter()

const overview = ref<KbOverview | null>(null)
const tree = ref<KbTreeNode[]>([])
const failed = ref(false)

onMounted(async () => {
  const [ov, tr] = await Promise.allSettled([fetchKbOverview(), fetchKbTree()])
  if (ov.status === 'fulfilled') overview.value = ov.value
  else failed.value = true
  if (tr.status === 'fulfilled') tree.value = tr.value
})

/** 产品线分组（与知识库体系树的产品线 key 对齐），用于展示真实收录量 */
const SPRING_PRODUCTS = ['spring-framework', 'spring-boot', 'spring-security', 'spring-data-jpa', 'spring-ai']
const VUE_PRODUCTS = ['vue', 'vue-router', 'pinia', 'vite']

function countOf(keys: string[]): number | null {
  if (!tree.value.length) return null
  const total = tree.value
    .filter((p) => keys.includes(p.key))
    .reduce((sum, p) => sum + p.documentCount, 0)
  return total > 0 ? total : null
}

const springCount = computed(() => countOf(SPRING_PRODUCTS))
const vueCount = computed(() => countOf(VUE_PRODUCTS))

/** 真实数值仅在聚合成功后渲染，其余一律占位「—」 */
function realValue(fn: (data: KbOverview) => number): string {
  return overview.value ? String(fn(overview.value)) : '—'
}

interface StatItem {
  value: string
  label: string
  hint: string
  live: boolean
}

const stats = computed<StatItem[]>(() => {
  const aggHint = failed.value
    ? '聚合接口暂不可用'
    : overview.value
      ? '已入库并可检索'
      : '加载中…'
  return [
    { value: realValue((d) => d.documentCount), label: '文档总数', hint: aggHint, live: Boolean(overview.value) },
    { value: realValue((d) => d.productCount), label: '技术分类', hint: 'Spring / Vue 等产品线', live: Boolean(overview.value) },
    { value: realValue((d) => d.versionCount), label: '覆盖版本', hint: '问答时按版本隔离', live: Boolean(overview.value) },
    { value: realValue((d) => d.sourceCount), label: '来源渠道', hint: '官方文档 + 社区问答', live: Boolean(overview.value) },
  ]
})

const badgeText = computed(() => {
  if (failed.value) return '知识库聚合暂不可用'
  if (!overview.value) return '知识库数据加载中…'
  return `${overview.value.documentCount} 篇文档 · ${overview.value.productCount} 个技术分类`
})

// 示例问法沿用问答页既有英文问法（经真实数据验证可命中）
const EXAMPLES = [
  'how to create a vue app with createApp',
  'How to configure actuator endpoints in Spring Boot',
  'How to use ChatClient in Spring AI',
]

const question = ref('')

function goQa(text: string) {
  const q = text.trim()
  router.push(q ? { path: '/qa', query: { q } } : { path: '/qa' })
}
</script>

<template>
  <div class="home">
    <!-- Hero -->
    <section class="hero">
      <div class="container hero__inner">
        <div class="hero__badge">
          <span class="hero__badge-dot"></span>
          {{ badgeText }}
        </div>

        <h1 class="hero__title">
          问一句，<br /><em>答案带出处</em>
        </h1>
        <p class="hero__desc">
          面向团队的 Spring / Vue 私有知识库<br />
          一次提问只在指定版本内检索，官方文档优先，答不出来会直说
        </p>

        <form class="hero__search" @submit.prevent="goQa(question)">
          <svg
            class="hero__search-icon"
            width="22"
            height="22"
            viewBox="0 0 24 24"
            fill="none"
            stroke="currentColor"
            stroke-width="2"
            stroke-linecap="round"
            stroke-linejoin="round"
          >
            <circle cx="11" cy="11" r="8" />
            <path d="m21 21-4.35-4.35" />
          </svg>
          <input
            v-model="question"
            class="hero__search-input"
            placeholder="输入你的问题，或从下面挑一个"
          />
          <button class="hero__search-btn" type="submit">开始问答</button>
        </form>

        <div class="hero__chips">
          <span
            v-for="ex in EXAMPLES"
            :key="ex"
            class="chip"
            @click="goQa(ex)"
          >
            {{ ex }}
          </span>
        </div>
      </div>
    </section>

    <!-- Stats -->
    <section class="stats">
      <div class="container stats__grid">
        <div v-for="s in stats" :key="s.label" class="stat">
          <div class="stat__value">{{ s.value }}</div>
          <div class="stat__label">{{ s.label }}</div>
          <div class="stat__delta" :class="{ 'stat__delta--muted': !s.live }">
            {{ s.hint }}
          </div>
        </div>
      </div>
    </section>

    <!-- Products -->
    <section class="section">
      <div class="container">
        <div class="section__header">
          <div>
            <h2 class="section__title">收录了哪些文档</h2>
            <p class="section__subtitle">官方文档按产品线与版本归档，社区问答作为补充</p>
          </div>
          <router-link class="section__link" to="/kb">
            浏览全部
            <svg
              width="18"
              height="18"
              viewBox="0 0 24 24"
              fill="none"
              stroke="currentColor"
              stroke-width="2"
              stroke-linecap="round"
              stroke-linejoin="round"
            >
              <path d="M5 12h14" />
              <path d="m12 5 7 7-7 7" />
            </svg>
          </router-link>
        </div>

        <div class="products">
          <router-link class="product" to="/kb">
            <div class="product__cta">
              <svg
                width="18"
                height="18"
                viewBox="0 0 24 24"
                fill="none"
                stroke="currentColor"
                stroke-width="2"
                stroke-linecap="round"
                stroke-linejoin="round"
              >
                <path d="M5 12h14" />
                <path d="m12 5 7 7-7 7" />
              </svg>
            </div>
            <div class="product__icon">
              <svg
                width="28"
                height="28"
                viewBox="0 0 24 24"
                fill="none"
                stroke="currentColor"
                stroke-width="1.8"
              >
                <path d="M12 2 2 7l10 5 10-5-10-5z" />
                <path d="m2 17 10 5 10-5" />
                <path d="m2 12 10 5 10-5" />
              </svg>
            </div>
            <h3 class="product__title">Spring 生态</h3>
            <p class="product__desc">
              Framework、Boot、Security、Data JPA、AI 的官方参考文档，按版本分线收录。
            </p>
            <div class="product__tags">
              <span class="product__tag">Spring Framework</span>
              <span class="product__tag">Spring Boot</span>
              <span class="product__tag">Spring Security</span>
              <span class="product__tag">Spring Data JPA</span>
              <span class="product__tag">Spring AI</span>
            </div>
            <div class="product__meta">
              <span v-if="springCount !== null" class="product__meta-item num">
                {{ springCount }} 篇文档
              </span>
              <span v-else class="product__meta-item product__meta-item--muted">查看知识库</span>
            </div>
          </router-link>

          <router-link class="product" to="/kb">
            <div class="product__cta">
              <svg
                width="18"
                height="18"
                viewBox="0 0 24 24"
                fill="none"
                stroke="currentColor"
                stroke-width="2"
                stroke-linecap="round"
                stroke-linejoin="round"
              >
                <path d="M5 12h14" />
                <path d="m12 5 7 7-7 7" />
              </svg>
            </div>
            <div class="product__icon">
              <svg
                width="28"
                height="28"
                viewBox="0 0 24 24"
                fill="none"
                stroke="currentColor"
                stroke-width="1.8"
                stroke-linecap="round"
                stroke-linejoin="round"
              >
                <rect x="3" y="3" width="7" height="7" rx="1" />
                <rect x="14" y="3" width="7" height="7" rx="1" />
                <rect x="3" y="14" width="7" height="7" rx="1" />
                <rect x="14" y="14" width="7" height="7" rx="1" />
              </svg>
            </div>
            <h3 class="product__title">Vue 生态</h3>
            <p class="product__desc">
              Vue 3 与周边工具的官方文档，覆盖组合式 API、路由、状态管理与构建。
            </p>
            <div class="product__tags">
              <span class="product__tag">Vue 3</span>
              <span class="product__tag">Vue Router</span>
              <span class="product__tag">Pinia</span>
              <span class="product__tag">Vite</span>
            </div>
            <div class="product__meta">
              <span v-if="vueCount !== null" class="product__meta-item num">
                {{ vueCount }} 篇文档
              </span>
              <span v-else class="product__meta-item product__meta-item--muted">查看知识库</span>
            </div>
          </router-link>

          <router-link class="product" to="/kb">
            <div class="product__cta">
              <svg
                width="18"
                height="18"
                viewBox="0 0 24 24"
                fill="none"
                stroke="currentColor"
                stroke-width="2"
                stroke-linecap="round"
                stroke-linejoin="round"
              >
                <path d="M5 12h14" />
                <path d="m12 5 7 7-7 7" />
              </svg>
            </div>
            <div class="product__icon">
              <svg
                width="28"
                height="28"
                viewBox="0 0 24 24"
                fill="none"
                stroke="currentColor"
                stroke-width="1.8"
                stroke-linecap="round"
                stroke-linejoin="round"
              >
                <path d="M21 15a2 2 0 0 1-2 2H7l-4 4V5a2 2 0 0 1 2-2h14a2 2 0 0 1 2 2z" />
              </svg>
            </div>
            <h3 class="product__title">社区实践</h3>
            <p class="product__desc">
              Stack Overflow 高票问答作为官方之外的补充，回答里会标明来源是官方还是社区。
            </p>
            <div class="product__tags">
              <span class="product__tag">Stack Overflow</span>
              <span class="product__tag">实战问题</span>
            </div>
            <div class="product__meta">
              <span class="product__meta-item product__meta-item--muted">与官方文档同库检索</span>
            </div>
          </router-link>
        </div>
      </div>
    </section>

    <!-- Features -->
    <section class="section section--alt">
      <div class="container">
        <div class="section__header">
          <div>
            <h2 class="section__title">问答是怎么工作的</h2>
            <p class="section__subtitle">检索、加权、兜底、溯源，每一步都有明确边界</p>
          </div>
        </div>

        <div class="features">
          <div class="feature">
            <div class="feature__icon">
              <svg
                width="22"
                height="22"
                viewBox="0 0 24 24"
                fill="none"
                stroke="currentColor"
                stroke-width="2"
                stroke-linecap="round"
                stroke-linejoin="round"
              >
                <path d="M12 2 2 7l10 5 10-5-10-5z" />
                <path d="m2 17 10 5 10-5" />
                <path d="m2 12 10 5 10-5" />
              </svg>
            </div>
            <div>
              <h3 class="feature__title">版本隔离检索</h3>
              <p class="feature__desc">
                提问时选定版本，检索只在该版本的文档里召回。3.x 的写法和 4.2 的写法不会出现在同一个回答里。
              </p>
            </div>
          </div>

          <div class="feature">
            <div class="feature__icon">
              <svg
                width="22"
                height="22"
                viewBox="0 0 24 24"
                fill="none"
                stroke="currentColor"
                stroke-width="2"
                stroke-linecap="round"
                stroke-linejoin="round"
              >
                <path d="M12 22s8-4 8-10V5l-8-3-8 3v7c0 6 8 10 8 10z" />
              </svg>
            </div>
            <div>
              <h3 class="feature__title">官方优先，社区补充</h3>
              <p class="feature__desc">
                官方文档在排序时获得加权，社区答案不删除、只排在后面；每条引用都会标注「官方」或「社区」。
              </p>
            </div>
          </div>

          <div class="feature">
            <div class="feature__icon">
              <svg
                width="22"
                height="22"
                viewBox="0 0 24 24"
                fill="none"
                stroke="currentColor"
                stroke-width="2"
                stroke-linecap="round"
                stroke-linejoin="round"
              >
                <circle cx="12" cy="12" r="10" />
                <path d="M12 8v4" />
                <path d="M12 16h.01" />
              </svg>
            </div>
            <div>
              <h3 class="feature__title">答不出来就说不知道</h3>
              <p class="feature__desc">
                检索相似度低于阈值时直接返回「知识库中未找到相关答案」，不调用模型，也不编造。
              </p>
            </div>
          </div>

          <div class="feature">
            <div class="feature__icon">
              <svg
                width="22"
                height="22"
                viewBox="0 0 24 24"
                fill="none"
                stroke="currentColor"
                stroke-width="2"
                stroke-linecap="round"
                stroke-linejoin="round"
              >
                <path d="M14 2H6a2 2 0 0 0-2 2v16a2 2 0 0 0 2 2h12a2 2 0 0 0 2-2V8z" />
                <polyline points="14 2 14 8 20 8" />
                <line x1="16" y1="13" x2="8" y2="13" />
                <line x1="16" y1="17" x2="8" y2="17" />
              </svg>
            </div>
            <div>
              <h3 class="feature__title">每条结论都有出处</h3>
              <p class="feature__desc">
                回答里的 [1][2] 角标对应来源卡，可以看到文档、章节和原文片段，并打开原文或文档详情。
              </p>
            </div>
          </div>

          <div class="feature">
            <div class="feature__icon">
              <svg
                width="22"
                height="22"
                viewBox="0 0 24 24"
                fill="none"
                stroke="currentColor"
                stroke-width="2"
                stroke-linecap="round"
                stroke-linejoin="round"
              >
                <path d="M3 3h18v18H3z" />
                <path d="M3 9h18" />
                <path d="M9 21V9" />
              </svg>
            </div>
            <div>
              <h3 class="feature__title">知识库可以直接翻</h3>
              <p class="feature__desc">
                产品线 ▸ 版本 ▸ 来源三级导航，支持按标题筛选、阅读文档详情，也能点赞和收藏。
              </p>
            </div>
          </div>

          <div class="feature">
            <div class="feature__icon">
              <svg
                width="22"
                height="22"
                viewBox="0 0 24 24"
                fill="none"
                stroke="currentColor"
                stroke-width="2"
                stroke-linecap="round"
                stroke-linejoin="round"
              >
                <path d="M17 21v-2a4 4 0 0 0-4-4H5a4 4 0 0 0-4 4v2" />
                <circle cx="9" cy="7" r="4" />
                <path d="M23 21v-2a4 4 0 0 0-3-3.87" />
                <path d="M16 3.13a4 4 0 0 1 0 7.75" />
              </svg>
            </div>
            <div>
              <h3 class="feature__title">团队文档一起沉淀</h3>
              <p class="feature__desc">
                登录后可以发布 Markdown 文档，选择团队可见或仅自己可见；会话历史和收藏跨设备保留。
              </p>
            </div>
          </div>
        </div>
      </div>
    </section>
  </div>
</template>

<style scoped>
/* ========== Hero（纸面排版，无光斑 / 无网格背景）========== */
.hero {
  padding: 96px 0 88px;
}

.hero__inner {
  text-align: center;
  max-width: var(--measure);
  margin: 0 auto;
}

.hero__badge {
  display: inline-flex;
  align-items: center;
  gap: var(--sp-2);
  padding: 6px 14px;
  border-radius: 100px;
  background: var(--surface);
  border: 1px solid var(--hairline);
  font-size: var(--fs-sm);
  color: var(--ink-500);
  font-weight: 500;
  margin-bottom: var(--sp-6);
}

.hero__badge-dot {
  width: 7px;
  height: 7px;
  border-radius: 50%;
  background: var(--success);
}

.hero__title {
  font-size: var(--fs-5xl);
  font-weight: 600;
  color: var(--ink-900);
  line-height: 1.2;
  letter-spacing: -0.02em;
  margin-bottom: var(--sp-5);
}

.hero__title em {
  font-style: normal;
  color: inherit;
}

.hero__desc {
  font-size: var(--fs-xl);
  color: var(--ink-500);
  margin-bottom: var(--sp-10);
  line-height: 1.7;
}

.hero__search {
  background: var(--surface);
  border: 1px solid var(--hairline);
  border-radius: var(--r-lg);
  padding: 8px 8px 8px 24px;
  display: flex;
  align-items: center;
  gap: var(--sp-3);
  transition: box-shadow var(--dur-fast) var(--ease-out);
  max-width: 660px;
  margin: 0 auto var(--sp-5);
}

.hero__search:focus-within {
  box-shadow: var(--ring);
}

.hero__search-icon {
  color: var(--ink-400);
  flex-shrink: 0;
}

.hero__search-input {
  flex: 1;
  min-width: 0;
  border: none;
  outline: none;
  font-size: var(--fs-lg);
  background: transparent;
  color: var(--ink-900);
  padding: var(--sp-3) 0;
}

.hero__search-input::placeholder {
  color: var(--ink-400);
}

.hero__search-btn {
  padding: 14px 32px;
  border-radius: var(--r-md);
  background: var(--ink-900);
  color: #fff;
  font-weight: 600;
  font-size: var(--fs-md);
  transition: background-color var(--dur-fast) var(--ease-out);
}

.hero__search-btn:hover {
  background: var(--ink-800);
}

.hero__chips {
  display: flex;
  flex-wrap: wrap;
  justify-content: center;
  gap: var(--sp-2);
}

.chip {
  display: inline-flex;
  align-items: center;
  gap: var(--sp-1);
  padding: 7px 16px;
  border-radius: 100px;
  background: var(--surface);
  border: 1px solid var(--hairline);
  font-size: var(--fs-sm);
  color: var(--ink-600);
  cursor: pointer;
  transition:
    border-color var(--dur-fast) var(--ease-out),
    color var(--dur-fast) var(--ease-out);
}

.chip:hover {
  border-color: var(--brand-600);
  color: var(--brand-600);
}

/* ========== Stats Bar ========== */
.stats {
  padding: var(--sp-10) 0;
  background: var(--surface);
  border-top: 1px solid var(--hairline);
  border-bottom: 1px solid var(--hairline);
}

.stats__grid {
  display: grid;
  grid-template-columns: repeat(4, 1fr);
}

.stat {
  text-align: center;
  padding: var(--sp-2) var(--sp-6);
}

.stat + .stat {
  border-left: 1px solid var(--hairline);
}

.stat__value {
  font-family: var(--font-num);
  font-variant-numeric: tabular-nums;
  font-size: var(--fs-4xl);
  font-weight: 600;
  color: var(--ink-900);
  letter-spacing: -0.02em;
  line-height: 1.15;
}

.stat__label {
  font-size: var(--fs-sm);
  color: var(--ink-500);
  margin-top: var(--sp-1);
  font-weight: 500;
}

.stat__delta {
  font-size: var(--fs-xs);
  color: var(--ink-500);
  margin-top: var(--sp-1);
  font-weight: 500;
}

.stat__delta--muted {
  color: var(--ink-400);
}

/* ========== Sections ========== */
.section {
  padding: var(--sp-16) 0;
}

.section--alt {
  background: var(--page-bg);
}

.section__header {
  display: flex;
  justify-content: space-between;
  align-items: flex-end;
  margin-bottom: var(--sp-10);
}

.section__title {
  font-size: var(--fs-4xl);
  font-weight: 600;
  color: var(--ink-900);
  letter-spacing: -0.01em;
}

.section__subtitle {
  font-size: var(--fs-lg);
  color: var(--ink-500);
  margin-top: var(--sp-2);
}

.section__link {
  display: inline-flex;
  align-items: center;
  gap: var(--sp-1);
  font-size: var(--fs-md);
  font-weight: 600;
  color: var(--brand-600);
  transition: color var(--dur-fast) var(--ease-out);
}

.section__link:hover {
  color: var(--brand-700);
}

.section__link svg {
  transition: transform var(--dur-fast) var(--ease-out);
}

.section__link:hover svg {
  transform: translateX(2px);
}

/* ========== Product Cards（白面 + hairline，hover 只变描边与底色）========== */
.products {
  display: grid;
  grid-template-columns: repeat(3, 1fr);
  gap: var(--sp-6);
}

.product {
  display: block;
  color: inherit;
  background: var(--surface);
  border: 1px solid var(--hairline);
  border-radius: var(--r-lg);
  padding: var(--sp-8);
  transition:
    border-color var(--dur-fast) var(--ease-out),
    background-color var(--dur-fast) var(--ease-out);
  position: relative;
}

.product:hover {
  border-color: var(--ink-300);
  background: var(--surface-sunken);
}

.product__icon {
  width: 56px;
  height: 56px;
  border-radius: var(--r-md);
  display: grid;
  place-items: center;
  margin-bottom: var(--sp-5);
  background: var(--ink-100);
  border: 1px solid var(--hairline);
  color: var(--ink-600);
}

.product__title {
  font-size: var(--fs-2xl);
  font-weight: 600;
  color: var(--ink-900);
  margin-bottom: var(--sp-2);
}

.product__desc {
  font-size: var(--fs-md);
  color: var(--ink-500);
  margin-bottom: var(--sp-5);
  line-height: 1.7;
}

.product__tags {
  display: flex;
  flex-wrap: wrap;
  gap: var(--sp-2);
  margin-bottom: var(--sp-6);
}

.product__tag {
  padding: 3px 10px;
  border-radius: 100px;
  border: 1px solid var(--hairline);
  background: var(--surface-sunken);
  font-size: var(--fs-xs);
  color: var(--ink-600);
}

.product__meta {
  display: flex;
  gap: var(--sp-4);
  padding-top: var(--sp-5);
  border-top: 1px solid var(--hairline);
}

.product__meta-item {
  font-size: var(--fs-sm);
  color: var(--ink-500);
  display: flex;
  align-items: center;
  gap: var(--sp-1);
}

.product__meta-item--muted {
  color: var(--ink-400);
}

.product__cta {
  position: absolute;
  top: var(--sp-8);
  right: var(--sp-8);
  width: 36px;
  height: 36px;
  border-radius: var(--r-md);
  background: var(--ink-100);
  display: grid;
  place-items: center;
  color: var(--ink-500);
  transition:
    background-color var(--dur-fast) var(--ease-out),
    color var(--dur-fast) var(--ease-out);
}

.product:hover .product__cta {
  background: var(--ink-900);
  color: #fff;
}

/* ========== Features Grid（白面 + hairline，hover 只变描边与底色）========== */
.features {
  display: grid;
  grid-template-columns: repeat(2, 1fr);
  gap: var(--sp-6);
}

.feature {
  display: flex;
  gap: var(--sp-5);
  padding: var(--sp-6);
  background: var(--surface);
  border: 1px solid var(--hairline);
  border-radius: var(--r-lg);
  transition:
    border-color var(--dur-fast) var(--ease-out),
    background-color var(--dur-fast) var(--ease-out);
}

.feature:hover {
  border-color: var(--ink-300);
  background: var(--surface-sunken);
}

.feature__icon {
  flex-shrink: 0;
  width: 44px;
  height: 44px;
  border-radius: var(--r-md);
  background: var(--ink-100);
  display: grid;
  place-items: center;
  color: var(--ink-700);
}

.feature__title {
  font-size: var(--fs-xl);
  font-weight: 600;
  color: var(--ink-900);
  margin-bottom: var(--sp-2);
}

.feature__desc {
  font-size: var(--fs-md);
  color: var(--ink-500);
  line-height: 1.7;
}

/* ========== Responsive ========== */
@media (max-width: 1024px) {
  .stats__grid {
    grid-template-columns: repeat(2, 1fr);
  }

  .products {
    grid-template-columns: repeat(2, 1fr);
  }

  .features {
    grid-template-columns: 1fr;
  }
}

@media (max-width: 640px) {
  .hero__title {
    font-size: var(--fs-4xl);
  }

  .stats__grid {
    grid-template-columns: 1fr 1fr;
    gap: var(--sp-6);
  }

  .products {
    grid-template-columns: 1fr;
  }

  .section__header {
    flex-direction: column;
    align-items: flex-start;
    gap: var(--sp-4);
  }
}
</style>
