<script setup lang="ts">
import { computed, onMounted, ref, watch } from 'vue'
import { useRoute } from 'vue-router'
import { fetchKbDoc } from '@/api/kb'
import { setDocFavorite, setDocLike } from '@/api/me'
import { renderMarkdown } from '@/utils/markdown'
import type { KbDocDetail } from '@/types/kb'

const route = useRoute()
const docId = computed(() => String(route.params.id ?? ''))

const detail = ref<KbDocDetail | null>(null)
const loading = ref(false)
const notFound = ref(false)
const pending = ref(false)

async function load() {
  if (!docId.value) return
  loading.value = true
  notFound.value = false
  try {
    detail.value = await fetchKbDoc(docId.value)
  } catch {
    detail.value = null
    notFound.value = true
  } finally {
    loading.value = false
  }
}

// 点赞/收藏均为幂等接口，成功后直接用返回的最新计数与状态回填
async function toggleLike() {
  if (!detail.value || pending.value) return
  pending.value = true
  try {
    const r = await setDocLike(detail.value.id, !detail.value.liked)
    detail.value = {
      ...detail.value,
      likeCount: r.likeCount,
      favoriteCount: r.favoriteCount,
      liked: r.liked,
      favorited: r.favorited,
    }
  } finally {
    pending.value = false
  }
}

async function toggleFavorite() {
  if (!detail.value || pending.value) return
  pending.value = true
  try {
    const r = await setDocFavorite(detail.value.id, !detail.value.favorited)
    detail.value = {
      ...detail.value,
      likeCount: r.likeCount,
      favoriteCount: r.favoriteCount,
      liked: r.liked,
      favorited: r.favorited,
    }
  } finally {
    pending.value = false
  }
}

const html = computed(() => renderMarkdown(detail.value?.markdown ?? ''))

const authorityLabel = computed(() => {
  if (detail.value?.authority === 'official') return '官方'
  if (detail.value?.authority === 'ugc') return '团队'
  return '社区'
})

watch(docId, () => void load())
onMounted(() => void load())
</script>

<template>
  <section class="container doc">
    <div class="crumb">
      <router-link to="/kb">知识库</router-link>
      <span>/</span>
      <span>文档详情 #<span class="num">{{ docId }}</span></span>
    </div>

    <template v-if="detail">
      <!-- 详情头：标题 / 元信息 / 操作位 -->
      <header class="doc-hero">
        <div class="doc-hero__main">
          <h2 class="doc-hero__title">{{ detail.title }}</h2>
          <p class="doc-hero__meta">
            <span class="badge" :class="detail.authority === 'official' ? 'badge--official' : 'badge--community'">
              {{ authorityLabel }}
            </span>
            <span v-if="detail.version" class="num meta-item">{{ detail.version }}</span>
            <span v-if="detail.docType" class="meta-item">{{ detail.docType }}</span>
            <span v-if="detail.language" class="meta-item">{{ detail.language }}</span>
            <span v-if="detail.author" class="meta-item">作者 {{ detail.author }}</span>
            <span class="meta-item">{{ detail.chunkCount }} 片段</span>
            <span v-if="detail.updatedAt" class="meta-item">更新于 {{ detail.updatedAt }}</span>
          </p>
        </div>
        <div class="doc-hero__actions">
          <router-link class="btn btn--primary" :to="{ path: '/qa', query: { doc: detail.id } }">
            + 基于本文提问
          </router-link>
          <button
            class="btn btn--outline"
            :class="{ 'btn--on': detail.liked }"
            type="button"
            :disabled="pending"
            @click="toggleLike"
          >
            {{ detail.liked ? '已赞' : '点赞' }}<span v-if="detail.likeCount" class="num"> {{ detail.likeCount }}</span>
          </button>
          <button
            class="btn btn--outline"
            :class="{ 'btn--on': detail.favorited }"
            type="button"
            :disabled="pending"
            @click="toggleFavorite"
          >
            {{ detail.favorited ? '已收藏' : '收藏' }}<span v-if="detail.favoriteCount" class="num"> {{ detail.favoriteCount }}</span>
          </button>
        </div>
      </header>

      <!-- 正文区：Markdown 渲染（纸面直排，无卡片外壳） -->
      <article class="doc-body">
        <div v-if="html" class="md-body" v-html="html" />
        <div v-else-if="!loading" class="doc-body__empty">
          <p class="empty__text">这篇文档还没有正文内容。</p>
        </div>
      </article>

      <!-- 原文外链：originalUrl 就绪 → 可点；否则禁用态，不伪造 URL -->
      <p class="doc-origin">
        <a
          v-if="detail.originalUrl"
          class="doc-origin__link"
          :href="detail.originalUrl"
          target="_blank"
          rel="noopener noreferrer"
        >打开原文</a>
        <span v-else class="doc-origin__link doc-origin__link--disabled" title="本文为团队/手动文档，无原文外链">
          打开原文
        </span>
      </p>

      <!-- 相关推荐位（数据依赖 C4/C7，暂未就绪） -->
      <section class="rec">
        <h3 class="rec__label">相关推荐</h3>
        <p class="rec__note">等更多文档被收录，这里会列出与它相关的文档。</p>
      </section>
    </template>

    <template v-else-if="!loading && notFound">
      <article class="doc-body">
        <p class="empty__text">这篇文档暂时打不开 —— 它可能还没被导入，或者已经下线</p>
        <router-link class="btn btn--primary" to="/kb">回到知识库</router-link>
      </article>
    </template>
  </section>
</template>

<style scoped>
.doc {
  max-width: 900px;
  margin: 0 auto;
  padding-top: var(--sp-10);
  padding-bottom: var(--sp-16);
}

.crumb {
  margin-bottom: var(--sp-4);
  font-size: var(--fs-sm);
  color: var(--ink-500);
}

.crumb a {
  color: var(--brand-600);
  text-decoration: none;
}

.crumb a:hover {
  text-decoration: underline;
}

.doc-hero {
  display: flex;
  justify-content: space-between;
  align-items: flex-start;
  gap: var(--sp-6);
  padding-bottom: var(--sp-6);
  border-bottom: 1px solid var(--hairline);
}

.doc-hero__main {
  min-width: 0;
}

.doc-hero__title {
  font-size: var(--fs-4xl);
  color: var(--ink-900);
}

.doc-hero__meta {
  margin-top: var(--sp-2);
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: var(--sp-2);
  font-size: var(--fs-sm);
  color: var(--ink-500);
}

.meta-item {
  margin-left: var(--sp-2);
}

.doc-hero__actions {
  display: flex;
  gap: var(--sp-2);
  flex-shrink: 0;
}

.doc-hero__actions .btn:disabled {
  opacity: 0.55;
  cursor: not-allowed;
}

/* 已点赞/已收藏：陶土色描边 + 文字，状态可一眼识别 */
.doc-hero__actions .btn--on {
  color: var(--brand-600);
  border-color: var(--brand-600);
}

/* 正文区：纸面直排，不使用卡片外壳 */
.doc-body {
  padding: var(--sp-8) 0;
  line-height: 1.7;
  color: var(--ink-700);
}

.doc-body__empty {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: var(--sp-4);
  min-height: 200px;
  text-align: center;
}

.empty__text {
  font-weight: 600;
  color: var(--ink-700);
  line-height: 1.7;
  max-width: 460px;
}

.doc-origin {
  margin-top: var(--sp-4);
}

.doc-origin__link {
  display: inline-block;
  font-size: var(--fs-md);
  color: var(--brand-600);
  text-decoration: none;
}

.doc-origin__link:hover {
  text-decoration: underline;
  text-underline-offset: 2px;
}

.doc-origin__link--disabled {
  color: var(--ink-400);
  cursor: not-allowed;
}

.doc-origin__link--disabled:hover {
  text-decoration: none;
}

.rec {
  margin-top: var(--sp-10);
  padding-top: var(--sp-6);
  border-top: 1px solid var(--hairline);
}

.rec__label {
  font-size: var(--fs-sm);
  font-weight: 600;
  color: var(--ink-600);
  letter-spacing: 0.05em;
}

.rec__note {
  margin-top: var(--sp-2);
  font-size: var(--fs-sm);
  color: var(--ink-400);
  line-height: 1.7;
}

@media (max-width: 900px) {
  .doc-hero {
    flex-direction: column;
  }
}
</style>

<!-- 排版样式（非 scoped：v-html 插入的 HTML 不被 scoped 属性命中；纸面阅读配色） -->
<style>
.doc .md-body {
  color: var(--ink-700);
  font-size: var(--fs-lg);
  line-height: 1.7;
}

.doc .md-body h1,
.doc .md-body h2,
.doc .md-body h3,
.doc .md-body h4 {
  font-weight: 600;
  color: var(--ink-900);
  margin: 20px 0 8px;
}

.doc .md-body h1 {
  font-size: var(--fs-2xl);
}

.doc .md-body h2 {
  font-size: var(--fs-xl);
}

.doc .md-body h3,
.doc .md-body h4 {
  font-size: var(--fs-lg);
}

.doc .md-body p {
  margin: 10px 0;
}

.doc .md-body ul,
.doc .md-body ol {
  margin: 10px 0;
  padding-left: 22px;
}

.doc .md-body li {
  margin: 4px 0;
}

.doc .md-body ul li {
  list-style: disc;
}

.doc .md-body ol li {
  list-style: decimal;
}

.doc .md-body blockquote {
  margin: 12px 0;
  padding: 8px 14px;
  border-left: 2px solid var(--hairline-strong);
  background: var(--surface-sunken);
  border-radius: 0 var(--r-sm) var(--r-sm) 0;
  color: var(--ink-600);
}

.doc .md-body hr {
  border: none;
  height: 1px;
  background: var(--hairline);
  margin: 16px 0;
}

.doc .md-body code {
  font-family: var(--font-num);
  font-size: var(--fs-sm);
  padding: 2px 6px;
  border-radius: 4px;
  background: var(--ink-100);
  color: var(--ink-800);
}

.doc .md-body pre {
  margin: 12px 0;
  padding: 12px 14px;
  border-radius: var(--r-md);
  background: var(--ink-900);
  overflow-x: auto;
}

.doc .md-body pre code {
  padding: 0;
  background: transparent;
  color: var(--ink-100);
  font-size: var(--fs-sm);
  line-height: 1.7;
}

.doc .md-body table {
  width: 100%;
  border-collapse: collapse;
  margin: 12px 0;
  font-size: var(--fs-md);
}

.doc .md-body th,
.doc .md-body td {
  border: 1px solid var(--hairline);
  padding: 6px 10px;
  text-align: left;
}

.doc .md-body th {
  background: var(--surface-sunken);
  color: var(--ink-600);
  font-weight: 600;
}

.doc .md-body a {
  color: var(--ink-700);
  text-decoration: underline;
  text-decoration-color: var(--hairline-strong);
  text-underline-offset: 2px;
}

.doc .md-body a:hover {
  color: var(--brand-600);
  text-decoration-color: var(--brand-600);
}

.doc .md-body strong {
  color: var(--ink-900);
  font-weight: 600;
}

.doc .md-body em {
  font-style: italic;
}
</style>