<script setup lang="ts">
import { ref } from 'vue'
import PageHead from '@/components/layout/PageHead.vue'
// 知识库（外壳）：左体系树 + 右文档卡骨架。
// 数据依赖未就绪后端（GET /api/kb/overview|tree|docs，见 spec §待确认 C7），
// 故节点/文档卡一律空态，不请求、不伪造数据；搜索框仅纯前端占位。
const keyword = ref('')
</script>

<template>
  <section class="container kb">
    <PageHead title="知识库" subtitle="按产品线 · 版本 · 来源浏览全部已收录文档" />

    <div class="kb-layout">
      <!-- 左侧体系树骨架：产品线 ▸ 版本 ▸ 来源 三级结构，节点为空态 -->
      <aside class="kb-tree">
        <input
          v-model="keyword"
          class="kb-tree__search"
          type="search"
          placeholder="筛选文档标题..."
          aria-label="筛选文档标题"
        />
        <details open>
          <summary>产品线</summary>
          <div class="kb-tree__group">
            <details open>
              <summary>版本</summary>
              <div class="kb-tree__group">
                <div class="kb-tree__leaf">
                  来源 <span class="kb-tree__count">—</span>
                </div>
              </div>
            </details>
          </div>
        </details>
        <p class="kb-tree__empty">知识库导航 · 待后端 /api/kb 就绪</p>
      </aside>

      <!-- 右侧文档卡网格：空态 -->
      <div class="doc-grid">
        <div class="card empty doc-grid__empty">
          <p class="empty__title">暂无可浏览文档</p>
          <p class="empty__hint">文档列表 · 待后端 /api/kb 就绪</p>
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

.kb-layout {
  display: grid;
  grid-template-columns: 240px 1fr;
  gap: var(--sp-6);
  align-items: start;
  padding-bottom: var(--sp-16);
}

.kb-tree {
  background: #fff;
  border: 1px solid var(--ink-200);
  border-radius: var(--r-xl);
  padding: var(--sp-4);
  position: sticky;
  top: 84px;
}

.kb-tree__search {
  width: 100%;
  border: 1px solid var(--ink-200);
  border-radius: var(--r-md);
  padding: var(--sp-2) var(--sp-3);
  font-size: var(--fs-sm);
  color: var(--ink-700);
  background: #fff;
  outline: none;
  margin-bottom: var(--sp-3);
}

.kb-tree__search:focus {
  border-color: var(--brand-400);
}

.kb-tree details {
  font-size: var(--fs-sm);
}

.kb-tree summary {
  cursor: pointer;
  list-style: none;
  display: flex;
  align-items: center;
  gap: var(--sp-1);
  padding: 6px var(--sp-2);
  border-radius: var(--r-sm);
  color: var(--ink-700);
  font-weight: 600;
  user-select: none;
}

.kb-tree summary::-webkit-details-marker {
  display: none;
}

.kb-tree summary:hover {
  background: var(--ink-50);
}

.kb-tree summary::before {
  content: '';
  width: 0;
  height: 0;
  flex-shrink: 0;
  border-left: 5px solid var(--ink-400);
  border-top: 4px solid transparent;
  border-bottom: 4px solid transparent;
  transition: transform 0.15s var(--ease-out);
}

.kb-tree details[open] > summary::before {
  transform: rotate(90deg);
}

.kb-tree__group {
  margin-left: var(--sp-4);
}

.kb-tree__leaf {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 5px var(--sp-2) 5px var(--sp-3);
  border-radius: var(--r-sm);
  font-size: var(--fs-sm);
  color: var(--ink-500);
  border-left: 2px solid transparent;
}

.kb-tree__count {
  font-size: var(--fs-xs);
  color: var(--ink-400);
  font-weight: 400;
}

.kb-tree__empty {
  margin-top: var(--sp-3);
  padding-top: var(--sp-3);
  border-top: 1px dashed var(--ink-200);
  font-size: var(--fs-xs);
  color: var(--ink-400);
  line-height: 1.6;
}

.doc-grid {
  display: grid;
  grid-template-columns: repeat(2, 1fr);
  gap: var(--sp-4);
}

.doc-grid__empty {
  grid-column: 1 / -1;
}

.empty {
  text-align: center;
  padding: var(--sp-12);
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
  .kb-layout {
    grid-template-columns: 1fr;
  }

  .kb-tree {
    position: static;
  }

  .doc-grid {
    grid-template-columns: 1fr;
  }
}
</style>
