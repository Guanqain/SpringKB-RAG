<script setup lang="ts">
import { computed } from 'vue'
import { useRoute } from 'vue-router'
// 文档详情（外壳）：面包屑 + 详情头 + Markdown 正文容器 + 相关推荐。
// 数据依赖未就绪后端（GET /api/kb/doc/:id，见 spec §待确认 C6/C7），
// 正文/推荐/元信息一律空态；:id 仅用于展示与面包屑，不请求、不伪造数据。
const route = useRoute()
const docId = computed(() => String(route.params.id ?? ''))
</script>

<template>
  <section class="container doc">
    <div class="crumb">
      <router-link to="/kb">知识库</router-link>
      <span>/</span>
      <span>文档详情 #{{ docId }}</span>
    </div>

    <!-- 详情头：标题 / 元信息 / 操作位 -->
    <div class="doc-hero">
      <div class="doc-hero__main">
        <h2 class="doc-hero__title">文档详情</h2>
        <div class="doc-hero__meta">
          <span class="badge badge--placeholder">版本 · 待补充</span>
          <span class="badge badge--placeholder">来源 · 待补充</span>
          <span class="badge badge--placeholder">已验证 · 待补充</span>
          <span class="doc-hero__id">文档 ID：{{ docId }}</span>
        </div>
      </div>
      <div class="doc-hero__actions">
        <router-link class="btn btn--primary" to="/qa">
          <svg
            width="16"
            height="16"
            viewBox="0 0 24 24"
            fill="none"
            stroke="currentColor"
            stroke-width="2"
            stroke-linecap="round"
            stroke-linejoin="round"
          >
            <path d="M21 15a2 2 0 0 1-2 2H7l-4 4V5a2 2 0 0 1 2-2h14a2 2 0 0 1 2 2z" />
          </svg>
          基于本文提问
        </router-link>
        <button class="btn btn--outline" type="button" disabled title="待后端就绪">
          <svg
            width="15"
            height="15"
            viewBox="0 0 24 24"
            fill="none"
            stroke="currentColor"
            stroke-width="2"
            stroke-linecap="round"
            stroke-linejoin="round"
          >
            <path d="M7 10v12" />
            <path
              d="M15 5.88 14 10h5.83a2 2 0 0 1 1.92 2.56l-2.33 8A2 2 0 0 1 17.5 22H4a2 2 0 0 1-2-2v-8a2 2 0 0 1 2-2h2.76a2 2 0 0 0 1.79-1.11L12 2a3.13 3.13 0 0 1 3 3.88Z"
            />
          </svg>
          点赞
        </button>
        <button class="btn btn--outline" type="button" disabled title="待后端就绪">
          <svg
            width="15"
            height="15"
            viewBox="0 0 24 24"
            fill="none"
            stroke="currentColor"
            stroke-width="2"
            stroke-linecap="round"
            stroke-linejoin="round"
          >
            <path d="m19 21-7-5-7 5V5a2 2 0 0 1 2-2h10a2 2 0 0 1 2 2z" />
          </svg>
          收藏
        </button>
      </div>
    </div>

    <!-- 正文区：Markdown 渲染容器（内容待后端就绪，当前空态） -->
    <div class="doc-body">
      <div class="md-body empty">
        <p class="empty__title">文档正文</p>
        <p class="empty__hint">Markdown 内容 · 待后端 /api/kb/doc 就绪</p>
      </div>
    </div>

    <!-- 相关推荐 -->
    <div class="rec">
      <div class="rec__label">相关推荐</div>
      <div class="rec-grid">
        <div class="card empty rec-grid__empty">
          <p class="empty__title">暂无相关推荐</p>
          <p class="empty__hint">相似文档 · 待后端就绪</p>
        </div>
      </div>
    </div>
  </section>
</template>

<style scoped>
.doc {
  max-width: 900px;
  margin: 0 auto;
  padding-top: var(--sp-10);
  padding-bottom: var(--sp-16);
}

.doc-hero {
  display: flex;
  justify-content: space-between;
  align-items: flex-start;
  gap: var(--sp-6);
  margin-bottom: var(--sp-6);
}

.doc-hero__main {
  min-width: 0;
}

.doc-hero__title {
  font-size: var(--fs-4xl);
  font-weight: 800;
  color: var(--ink-900);
}

.doc-hero__meta {
  display: flex;
  gap: var(--sp-3);
  align-items: center;
  flex-wrap: wrap;
  font-size: var(--fs-sm);
  color: var(--ink-500);
  margin-top: var(--sp-3);
}

.doc-hero__id {
  color: var(--ink-400);
}

.badge--placeholder {
  background: transparent;
  border: 1px dashed var(--ink-300);
  color: var(--ink-400);
  font-weight: 500;
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

.doc-body {
  background: #fff;
  border: 1px solid var(--ink-200);
  border-radius: var(--r-xl);
  padding: var(--sp-8);
  line-height: 1.85;
  color: var(--ink-700);
  margin-bottom: var(--sp-6);
}

.md-body {
  min-height: 180px;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
}

.rec__label {
  font-size: var(--fs-sm);
  font-weight: 600;
  color: var(--ink-500);
  margin-bottom: var(--sp-3);
  text-transform: uppercase;
  letter-spacing: 0.05em;
}

.rec-grid {
  display: grid;
  grid-template-columns: repeat(3, 1fr);
  gap: var(--sp-3);
}

.rec-grid__empty {
  grid-column: 1 / -1;
}

.empty {
  text-align: center;
  padding: var(--sp-10);
  border: 1px dashed var(--ink-200);
  box-shadow: none;
}

.empty__title {
  font-size: var(--fs-lg);
  font-weight: 700;
  color: var(--ink-800);
}

.empty__hint {
  margin-top: var(--sp-2);
  font-size: var(--fs-md);
  color: var(--ink-500);
}

@media (max-width: 900px) {
  .doc-hero {
    flex-direction: column;
  }

  .rec-grid {
    grid-template-columns: 1fr;
  }
}
</style>
