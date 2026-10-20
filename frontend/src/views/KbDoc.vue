<script setup lang="ts">
import { computed } from 'vue'
import { useRoute } from 'vue-router'
// 文档详情（外壳）：面包屑 + 详情头 + Markdown 正文区 + 原文外链 + 相关推荐。
// 数据依赖未就绪后端（GET /api/kb/doc/:id，见 spec §待确认 C4/C6/C7），
// 正文/元信息/推荐一律空态；:id 仅用于展示与面包屑，不请求、不伪造数据。
const route = useRoute()
const docId = computed(() => String(route.params.id ?? ''))
</script>

<template>
  <section class="container doc">
    <div class="crumb">
      <router-link to="/kb">知识库</router-link>
      <span>/</span>
      <span>文档详情 #<span class="num">{{ docId }}</span></span>
    </div>

    <!-- 详情头：标题 / 元信息 / 操作位（徽标与元信息依赖 C7，暂不伪造） -->
    <header class="doc-hero">
      <div class="doc-hero__main">
        <h2 class="doc-hero__title">文档详情</h2>
        <p class="doc-hero__meta">
          文档 ID：<span class="num">{{ docId }}</span>
        </p>
      </div>
      <div class="doc-hero__actions">
        <router-link class="btn btn--primary" to="/qa">+ 基于本文提问</router-link>
        <button class="btn btn--outline" type="button" disabled title="点赞能力还没接上">
          点赞
        </button>
        <button class="btn btn--outline" type="button" disabled title="收藏能力还没接上">
          收藏
        </button>
      </div>
    </header>

    <!-- 正文区：无卡片外壳，直接排在纸面上（Markdown 渲染依赖 C6/C7） -->
    <article class="doc-body">
      <p class="empty__text">这篇文档暂时打不开 —— 它可能还没被导入，或者已经下线</p>
      <router-link class="btn btn--primary" to="/kb">回到知识库</router-link>
    </article>

    <!-- 原文外链：SourceRef 无 originalUrl（契约 §8 / C6），未就绪 → 禁用态 + 提示，不伪造 URL -->
    <p class="doc-origin">
      <span class="doc-origin__link" role="link" aria-disabled="true" title="原文链接待后端就绪">
        打开原文
      </span>
    </p>

    <!-- 相关推荐位（数据依赖 C4/C7） -->
    <section class="rec">
      <h3 class="rec__label">相关推荐</h3>
      <p class="rec__note">等这篇文档能被打开，这里会列出与它相关的文档。</p>
    </section>
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
  font-size: var(--fs-sm);
  color: var(--ink-500);
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

/* 正文区：纸面直排，不使用卡片外壳 */
.doc-body {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: var(--sp-4);
  min-height: 260px;
  padding: var(--sp-12) 0;
  text-align: center;
  font-size: var(--fs-lg);
  line-height: 1.7;
  color: var(--ink-700);
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
  opacity: 0.55;
  cursor: not-allowed;
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
