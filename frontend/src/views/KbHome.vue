<script setup lang="ts">
import { ref } from 'vue'
import PageHead from '@/components/layout/PageHead.vue'
// 知识库（外壳）：左体系树 + 右文档卡网格骨架。
// 数据依赖未就绪后端（GET /api/kb/overview|tree|docs，见 spec §待确认 C4/C7），
// 故节点/文档卡一律空态，不请求、不伪造数据；搜索框仅纯前端占位。
const keyword = ref('')
</script>

<template>
  <section class="container kb">
    <PageHead title="知识库" subtitle="按产品线 · 版本 · 来源浏览全部已收录文档" />

    <div class="kb-layout">
      <!-- 左：体系树骨架（产品线 ▸ 版本 ▸ 来源），节点为空态 -->
      <aside class="kb-tree">
        <input
          v-model="keyword"
          class="kb-tree__search"
          type="search"
          placeholder="筛选文档标题..."
          aria-label="筛选文档标题"
        />

        <nav class="kb-tree__nav" aria-label="体系树">
          <div class="kb-tree__node kb-tree__node--top">产品线</div>
          <div class="kb-tree__node kb-tree__node--mid">版本</div>
          <div class="kb-tree__node kb-tree__node--leaf">来源</div>
        </nav>

        <div class="kb-tree__empty">
          <p class="empty__text">知识还是一片空白 —— 从导入一批文档开始</p>
          <router-link class="btn btn--primary" to="/admin/import">去导入</router-link>
        </div>
      </aside>

      <!-- 右：文档卡网格骨架，空态 -->
      <div class="kb-docs">
        <div class="doc-grid">
          <div class="doc-grid__empty">
            <p class="empty__text">还没有文档 —— 你先写一篇，团队的知识就从它开始</p>
            <router-link class="btn btn--primary" to="/editor">去写文档</router-link>
          </div>
        </div>
      </div>
    </div>
  </section>
</template>

<style scoped>
.kb {
  padding-top: var(--sp-8);
  padding-bottom: var(--sp-16);
}

/* 白面面板 + 1px hairline；左右两栏由 hairline 竖分隔 */
.kb-layout {
  display: grid;
  grid-template-columns: 260px 1fr;
  align-items: stretch;
  min-height: 420px;
  background: var(--surface);
  border: 1px solid var(--hairline);
  border-radius: var(--r-lg);
  overflow: hidden;
  margin-bottom: var(--sp-16);
}

.kb-tree {
  display: flex;
  flex-direction: column;
  gap: var(--sp-3);
  padding: var(--sp-5);
  border-right: 1px solid var(--hairline);
}

.kb-tree__search {
  width: 100%;
  border: 1px solid var(--hairline);
  border-radius: var(--r-md);
  padding: var(--sp-2) var(--sp-3);
  font-size: var(--fs-sm);
  color: var(--ink-700);
  background: var(--surface);
  outline: none;
  transition: box-shadow var(--dur-fast) var(--ease-out);
}

.kb-tree__search::placeholder {
  color: var(--ink-400);
}

.kb-tree__search:focus {
  box-shadow: var(--ring);
  border-color: transparent;
}

.kb-tree__nav {
  display: flex;
  flex-direction: column;
  gap: 2px;
}

.kb-tree__node {
  padding: 6px var(--sp-3);
  border-left: 2px solid transparent;
  border-radius: var(--r-sm);
  font-size: var(--fs-sm);
  color: var(--ink-500);
  transition:
    background-color var(--dur-fast) var(--ease-out),
    color var(--dur-fast) var(--ease-out);
}

.kb-tree__node:hover {
  background: var(--surface-sunken);
  color: var(--ink-700);
}

.kb-tree__node--top {
  font-weight: 600;
  color: var(--ink-700);
}

.kb-tree__node--mid {
  margin-left: var(--sp-4);
}

.kb-tree__node--leaf {
  margin-left: var(--sp-8);
}

.kb-tree__node--active {
  border-left-color: var(--brand-600);
  background: var(--surface-sunken);
  color: var(--ink-900);
  font-weight: 600;
}

.kb-tree__empty {
  margin-top: auto;
  padding-top: var(--sp-4);
  border-top: 1px solid var(--hairline);
  display: flex;
  flex-direction: column;
  align-items: flex-start;
  gap: var(--sp-3);
}

.kb-tree__empty .empty__text {
  font-size: var(--fs-sm);
}

.kb-docs {
  padding: var(--sp-5);
}

.doc-grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(240px, 1fr));
  gap: var(--sp-4);
}

.doc-grid__empty {
  grid-column: 1 / -1;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: var(--sp-4);
  min-height: 340px;
  text-align: center;
}

/* 空态：有态度的一句话 + 一个动作按钮 */
.empty__text {
  font-size: var(--fs-lg);
  font-weight: 600;
  color: var(--ink-700);
  line-height: 1.7;
  max-width: 420px;
}

@media (max-width: 900px) {
  .kb-layout {
    grid-template-columns: 1fr;
  }

  .kb-tree {
    border-right: none;
    border-bottom: 1px solid var(--hairline);
  }

  .kb-tree__empty {
    margin-top: var(--sp-4);
  }
}
</style>
