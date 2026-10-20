<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import { useDocumentStore } from '@/stores/document'

// 首页（外壳）：Hero + 统计条 + 产品线卡 + 功能区。
// 数据口径：统计条复用现有 GET /api/admin/documents 聚合（总文档数 / chunk 总数），
// 不可真实获得的指标（引用命中率）一律占位「—」，产品线文档数不得伪造 → 结构展示 + 占位。
// 产品线覆盖数为结构性常量（首页结构固定覆盖 Spring Boot / Spring AI / Vue 三条产品线）。

const router = useRouter()
const store = useDocumentStore()

// 注入 onMounted 的聚合状态：loading 中 / 失败 / 成功
const loaded = ref(false)
const failed = ref(false)

onMounted(async () => {
  try {
    await store.fetchDocuments()
    loaded.value = true
  } catch {
    // 请求失败 → 统计条显示占位空态，不崩溃
    failed.value = true
  }
})

const totalDocs = computed(() => store.documents.length)
const totalChunks = computed(() =>
  store.documents.reduce((sum, d) => sum + d.chunkCount, 0),
)

/** 真实数值仅在聚合成功后渲染，其余一律占位「—」 */
function realValue(fn: () => number): string {
  return loaded.value ? String(fn()) : '—'
}

interface StatItem {
  value: string
  label: string
  hint: string
  live: boolean
}

const stats = computed<StatItem[]>(() => {
  const aggHint = failed.value
    ? '聚合接口不可用'
    : loaded.value
      ? '来自知识库聚合'
      : '加载中…'
  return [
    { value: realValue(() => totalDocs.value), label: '文档总数', hint: aggHint, live: loaded.value },
    { value: realValue(() => totalChunks.value), label: '知识片段', hint: aggHint, live: loaded.value },
    { value: '3', label: '覆盖产品线', hint: 'Spring / Vue 全家桶', live: true },
    { value: '—', label: '引用命中率', hint: '待统计', live: false },
  ]
})

const badgeText = computed(() => {
  if (failed.value) return '知识库聚合暂不可用'
  if (!loaded.value) return '知识库数据加载中…'
  return `${totalDocs.value} 篇文档已收录`
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
          让技术文档<br /><em>开口说话</em>
        </h1>
        <p class="hero__desc">
          整合 Spring Boot、Spring AI、Vue 等主流框架官方文档，<br />
          自然语言问答 · 精准引用溯源 · 中英文双语匹配
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
            :placeholder="EXAMPLES[0]"
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
            <h2 class="section__title">覆盖主流技术栈</h2>
            <p class="section__subtitle">精选官方案例，持续更新</p>
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
            <div class="product__icon product__icon--orange">
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
            <h3 class="product__title">Spring Boot</h3>
            <p class="product__desc">
              从自动配置到 Actuator，从数据源到安全框架，覆盖生产环境常见场景。
            </p>
            <div class="product__meta">
              <span class="product__meta-item product__meta-item--muted">
                文档数 · 待 /api/kb/overview 就绪
              </span>
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
            <div class="product__icon product__icon--blue">
              <svg
                width="28"
                height="28"
                viewBox="0 0 24 24"
                fill="none"
                stroke="currentColor"
                stroke-width="1.8"
              >
                <path d="M12 2a10 10 0 1 0 10 10" />
                <circle cx="12" cy="12" r="3" />
                <path d="M12 2v4" />
              </svg>
            </div>
            <h3 class="product__title">Spring AI</h3>
            <p class="product__desc">
              ChatClient、Embedding、RAG 全链路封装，快速构建企业级 AI 应用。
            </p>
            <div class="product__meta">
              <span class="product__meta-item product__meta-item--muted">
                文档数 · 待 /api/kb/overview 就绪
              </span>
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
            <div class="product__icon product__icon--green">
              <svg
                width="28"
                height="28"
                viewBox="0 0 24 24"
                fill="none"
                stroke="currentColor"
                stroke-width="1.8"
              >
                <path d="M4 4h16v4H4V4zm0 8h16v8H4v-8z" />
                <path d="M8 12v8" />
                <path d="M16 12v8" />
              </svg>
            </div>
            <h3 class="product__title">Vue 3</h3>
            <p class="product__desc">
              Composition API、响应式原理、生态工具链，前端开发一站式参考。
            </p>
            <div class="product__meta">
              <span class="product__meta-item product__meta-item--muted">
                文档数 · 待 /api/kb/overview 就绪
              </span>
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
            <h2 class="section__title">企业级 RAG 能力</h2>
            <p class="section__subtitle">不止是搜索，更要可解释、可追溯、可迭代</p>
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
                <path d="M21 15a2 2 0 0 1-2 2H7l-4 4V5a2 2 0 0 1 2-2h14a2 2 0 0 1 2 2z" />
              </svg>
            </div>
            <div>
              <h3 class="feature__title">自然语言问答</h3>
              <p class="feature__desc">
                用日常说话的方式提问，系统自动理解意图、检索最相关的知识片段，生成可直接使用的答案。
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
              <h3 class="feature__title">引用可溯源</h3>
              <p class="feature__desc">
                每段答案都标注来源文档、章节、原文片段，点击即可跳转，不再担心 AI 幻觉。
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
                <path d="M2 12h20" />
                <path
                  d="M12 2a15.3 15.3 0 0 1 4 10 15.3 15.3 0 0 1-4 10 15.3 15.3 0 0 1-4-10 15.3 15.3 0 0 1 4-10z"
                />
              </svg>
            </div>
            <div>
              <h3 class="feature__title">多语言支持</h3>
              <p class="feature__desc">
                中英双语提问都能准确检索。中文问法匹配英文文档，借助多语言向量模型实现跨语言语义理解。
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
              <h3 class="feature__title">知识盲区发现</h3>
              <p class="feature__desc">
                自动统计高频兜底问题，帮助团队识别知识库薄弱环节——"用户问了但我们答不出来"就是下一个该补的方向。
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
              <h3 class="feature__title">私有化部署与权限</h3>
              <p class="feature__desc">
                单实例单团队，隔离在部署边界（同 GitLab / Gitea）。admin / member 两角色 + 文档团队 / 私有两级可见；发布前密钥扫描防泄露。
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
                <path
                  d="M9.663 17h4.673M12 3v1m6.364 1.636-.707.707M21 12h-1M4 12H3m3.343-5.657-.707-.707m2.828 9.9a5 5 0 1 1 7.072 0l-.548.547A3.374 3.374 0 0 0 14 18.469V19a2 2 0 1 1-4 0v-.531c0-.895-.356-1.754-.988-2.386z"
                />
              </svg>
            </div>
            <div>
              <h3 class="feature__title">智能推荐</h3>
              <p class="feature__desc">
                基于向量相似度的关联文档推荐——读完一篇自动推荐同领域相关内容；问答后推荐追问方向，让知识探索不断链。
              </p>
            </div>
          </div>
        </div>
      </div>
    </section>
  </div>
</template>

<style scoped>
/* ========== Hero ========== */
.hero {
  position: relative;
  padding: 96px 0 88px;
  background:
    radial-gradient(ellipse 55% 40% at 18% 8%, rgba(251, 146, 60, 0.14) 0%, transparent 60%),
    radial-gradient(ellipse 50% 40% at 85% 15%, rgba(249, 115, 22, 0.12) 0%, transparent 60%),
    #fdfcfa;
  overflow: hidden;
}

.hero::before {
  content: '';
  position: absolute;
  inset: 0;
  background: var(--grad-hero);
  pointer-events: none;
}

.hero::after {
  content: '';
  position: absolute;
  inset: 0;
  background-image:
    linear-gradient(rgba(249, 115, 22, 0.05) 1px, transparent 1px),
    linear-gradient(90deg, rgba(249, 115, 22, 0.05) 1px, transparent 1px);
  background-size: 44px 44px;
  mask-image: radial-gradient(ellipse 70% 60% at 50% 0%, #000 30%, transparent 75%);
  -webkit-mask-image: radial-gradient(ellipse 70% 60% at 50% 0%, #000 30%, transparent 75%);
  pointer-events: none;
}

.hero__inner {
  position: relative;
  text-align: center;
  max-width: 760px;
  margin: 0 auto;
  z-index: 1;
}

.hero__badge {
  display: inline-flex;
  align-items: center;
  gap: var(--sp-2);
  padding: 6px 14px;
  border-radius: 100px;
  background: rgba(255, 255, 255, 0.7);
  border: 1px solid var(--brand-200);
  box-shadow: var(--shadow-xs);
  font-size: var(--fs-sm);
  color: var(--brand-700);
  font-weight: 500;
  margin-bottom: var(--sp-6);
}

.hero__badge-dot {
  width: 7px;
  height: 7px;
  border-radius: 50%;
  background: var(--success);
  box-shadow: 0 0 0 3px rgba(34, 197, 94, 0.18);
}

.hero__title {
  font-size: 56px;
  font-weight: 800;
  color: var(--ink-900);
  line-height: 1.12;
  letter-spacing: -0.03em;
  margin-bottom: var(--sp-5);
}

.hero__title em {
  font-style: normal;
  background: var(--grad-brand);
  -webkit-background-clip: text;
  background-clip: text;
  -webkit-text-fill-color: transparent;
  position: relative;
}

.hero__desc {
  font-size: var(--fs-xl);
  color: var(--ink-500);
  margin-bottom: 40px;
  line-height: 1.75;
}

.hero__search {
  background: #fff;
  border: 1px solid var(--ink-200);
  border-radius: 100px;
  box-shadow: var(--shadow-lg);
  padding: 8px 8px 8px 24px;
  display: flex;
  align-items: center;
  gap: var(--sp-3);
  transition: box-shadow 0.25s var(--ease-out), border-color 0.25s, transform 0.25s var(--ease-out);
  max-width: 660px;
  margin: 0 auto var(--sp-5);
}

.hero__search:focus-within {
  border-color: var(--brand-400);
  box-shadow: var(--glow-brand);
  transform: translateY(-2px);
}

.hero__search-icon {
  color: var(--brand-500);
  flex-shrink: 0;
}

.hero__search-input {
  flex: 1;
  min-width: 0;
  border: none;
  outline: none;
  font-size: var(--fs-lg);
  background: transparent;
  color: var(--ink-800);
  padding: var(--sp-3) 0;
}

.hero__search-input::placeholder {
  color: var(--ink-400);
}

.hero__search-btn {
  padding: 14px 32px;
  border-radius: 100px;
  background: var(--grad-brand);
  color: #fff;
  font-weight: 600;
  font-size: var(--fs-md);
  box-shadow: 0 4px 14px rgba(249, 115, 22, 0.35);
  transition: transform 0.18s var(--ease-out), box-shadow 0.18s;
}

.hero__search-btn:hover {
  transform: translateY(-1px) scale(1.02);
  box-shadow: 0 8px 20px rgba(249, 115, 22, 0.45);
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
  background: rgba(255, 255, 255, 0.8);
  border: 1px solid var(--ink-200);
  font-size: var(--fs-sm);
  color: var(--ink-600);
  cursor: pointer;
  transition: all 0.18s var(--ease-out);
}

.chip:hover {
  border-color: var(--brand-400);
  color: var(--brand-700);
  background: var(--brand-50);
  transform: translateY(-1px);
  box-shadow: var(--shadow-sm);
}

/* ========== Stats Bar ========== */
.stats {
  padding: var(--sp-10) 0;
  background: #fff;
  border-top: 1px solid var(--ink-100);
  border-bottom: 1px solid var(--ink-100);
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
  border-left: 1px solid var(--ink-100);
}

.stat__value {
  font-size: 40px;
  font-weight: 800;
  background: var(--grad-brand);
  -webkit-background-clip: text;
  background-clip: text;
  -webkit-text-fill-color: transparent;
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
  color: var(--success);
  margin-top: var(--sp-1);
  font-weight: 600;
}

.stat__delta--muted {
  color: var(--ink-400);
}

/* ========== Sections ========== */
.section {
  padding: var(--sp-16) 0;
}

.section--alt {
  background: #fff;
}

.section__header {
  display: flex;
  justify-content: space-between;
  align-items: flex-end;
  margin-bottom: var(--sp-10);
}

.section__title {
  font-size: var(--fs-4xl);
  font-weight: 800;
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
  transition: gap 0.15s var(--ease-out);
}

.section__link:hover {
  gap: var(--sp-2);
  color: var(--brand-700);
}

.section__link svg {
  transition: transform 0.15s var(--ease-out);
}

.section__link:hover svg {
  transform: translateX(2px);
}

/* ========== Product Cards ========== */
.products {
  display: grid;
  grid-template-columns: repeat(3, 1fr);
  gap: var(--sp-6);
}

.product {
  display: block;
  color: inherit;
  background: #fff;
  border: 1px solid var(--ink-200);
  border-radius: var(--r-xl);
  padding: var(--sp-8);
  transition: all 0.25s var(--ease-out);
  position: relative;
  overflow: hidden;
}

.product::before {
  content: '';
  position: absolute;
  top: 0;
  left: 0;
  right: 0;
  height: 3px;
  background: var(--grad-brand);
  opacity: 0;
  transition: opacity 0.25s var(--ease-out);
}

.product::after {
  content: '';
  position: absolute;
  top: -40%;
  right: -25%;
  width: 220px;
  height: 220px;
  border-radius: 50%;
  background: radial-gradient(circle, rgba(251, 146, 60, 0.12) 0%, transparent 70%);
  opacity: 0;
  transition: opacity 0.3s var(--ease-out);
  pointer-events: none;
}

.product:hover {
  transform: translateY(-5px);
  box-shadow: var(--shadow-xl);
  border-color: var(--brand-200);
}

.product:hover::before,
.product:hover::after {
  opacity: 1;
}

.product__icon {
  width: 56px;
  height: 56px;
  border-radius: var(--r-lg);
  display: grid;
  place-items: center;
  margin-bottom: var(--sp-5);
  transition: transform 0.25s var(--ease-out);
}

.product:hover .product__icon {
  transform: scale(1.08) rotate(-4deg);
}

.product__icon--orange {
  background: var(--brand-50);
  color: var(--brand-600);
  border: 1px solid var(--brand-100);
}

.product__icon--blue {
  background: #eff6ff;
  color: #2563eb;
  border: 1px solid #dbeafe;
}

.product__icon--green {
  background: #f0fdf4;
  color: #16a34a;
  border: 1px solid #dcfce7;
}

.product__title {
  font-size: var(--fs-2xl);
  font-weight: 700;
  color: var(--ink-900);
  margin-bottom: var(--sp-2);
}

.product__desc {
  font-size: var(--fs-md);
  color: var(--ink-500);
  margin-bottom: var(--sp-6);
  line-height: 1.7;
}

.product__meta {
  display: flex;
  gap: var(--sp-4);
  padding-top: var(--sp-5);
  border-top: 1px dashed var(--ink-200);
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
  transition: all 0.2s var(--ease-out);
}

.product:hover .product__cta {
  background: var(--grad-brand);
  color: #fff;
}

/* ========== Features Grid ========== */
.features {
  display: grid;
  grid-template-columns: repeat(2, 1fr);
  gap: var(--sp-6);
}

.feature {
  display: flex;
  gap: var(--sp-5);
  padding: var(--sp-6);
  background: #fdfcfa;
  border: 1px solid var(--ink-200);
  border-radius: var(--r-xl);
  transition: all 0.22s var(--ease-out);
}

.feature:hover {
  background: #fff;
  box-shadow: var(--shadow-md);
  border-color: var(--brand-200);
  transform: translateY(-2px);
}

.feature__icon {
  flex-shrink: 0;
  width: 44px;
  height: 44px;
  border-radius: var(--r-md);
  background: var(--grad-brand);
  display: grid;
  place-items: center;
  color: #fff;
  box-shadow: 0 4px 10px rgba(249, 115, 22, 0.28);
}

.feature__title {
  font-size: var(--fs-xl);
  font-weight: 700;
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
