<script setup lang="ts">
import { computed, onMounted, ref, watch } from 'vue'
import PageHead from '@/components/layout/PageHead.vue'
import { fetchKbDocs, fetchKbTree } from '@/api/kb'
import type { KbDocCard, KbTreeNode } from '@/types/kb'

const PAGE_SIZE = 12

const keyword = ref('')
const tree = ref<KbTreeNode[]>([])
const docs = ref<KbDocCard[]>([])
const loading = ref(false)
// 选中的三级过滤（product/version/source），驱动右侧文档列表
const selected = ref<{ product?: string; version?: string; source?: string }>({})
const page = ref(1)
const total = ref(0)

// 树折叠状态：产品线 / 版本两级可折叠（键为完整路径，避免同名版本相互干扰）
const expandedProducts = ref(new Set<string>())
const expandedVersions = ref(new Set<string>())

const totalPages = computed(() => Math.max(1, Math.ceil(total.value / PAGE_SIZE)))

async function loadTree() {
  tree.value = await fetchKbTree()
}

async function loadDocs() {
  loading.value = true
  try {
    const result = await fetchKbDocs({
      product: selected.value.product,
      version: selected.value.version,
      source: selected.value.source,
      keyword: keyword.value || undefined,
      page: page.value,
      size: PAGE_SIZE,
    })
    const maxPage = Math.max(1, Math.ceil(result.total / PAGE_SIZE))
    if (page.value > maxPage) {
      // 过滤后数据变少：把页码收回到最后一页再取一次，避免停在空白页
      page.value = maxPage
      await loadDocs()
      return
    }
    docs.value = result.list
    total.value = result.total
  } finally {
    loading.value = false
  }
}

function resetAndLoad() {
  page.value = 1
  void loadDocs()
}

function toggleProduct(p: KbTreeNode) {
  if (expandedProducts.value.has(p.key)) {
    expandedProducts.value.delete(p.key)
  } else {
    expandedProducts.value.add(p.key)
  }
}

function toggleVersion(p: KbTreeNode, v: KbTreeNode) {
  const key = `${p.key}::${v.key}`
  if (expandedVersions.value.has(key)) {
    expandedVersions.value.delete(key)
  } else {
    expandedVersions.value.add(key)
  }
}

function selectProduct(p: KbTreeNode) {
  // 再次点击同一产品线 → 取消筛选；否则只按产品线筛选并展开
  selected.value = selected.value.product === p.key && !selected.value.version ? {} : { product: p.key }
  if (selected.value.product) {
    expandedProducts.value.add(p.key)
  }
  resetAndLoad()
}

function selectVersion(p: KbTreeNode, v: KbTreeNode) {
  const same = selected.value.product === p.key && selected.value.version === v.key && !selected.value.source
  selected.value = same ? { product: p.key } : { product: p.key, version: v.key }
  if (!same) {
    expandedProducts.value.add(p.key)
    expandedVersions.value.add(`${p.key}::${v.key}`)
  }
  resetAndLoad()
}

function selectSource(p: KbTreeNode, v: KbTreeNode, s: KbTreeNode) {
  const same = selected.value.product === p.key
    && selected.value.version === v.key
    && selected.value.source === s.key
  selected.value = same
    ? { product: p.key, version: v.key }
    : { product: p.key, version: v.key, source: s.key }
  if (!same) {
    expandedProducts.value.add(p.key)
    expandedVersions.value.add(`${p.key}::${v.key}`)
  }
  resetAndLoad()
}

// 高亮必须比较完整路径：official/community 在多个产品线下同名，只比 source 会全部点亮
const isProductActive = (p: KbTreeNode) => selected.value.product === p.key && !selected.value.version
const isVersionActive = (p: KbTreeNode, v: KbTreeNode) =>
  selected.value.product === p.key && selected.value.version === v.key && !selected.value.source
const isSourceActive = (p: KbTreeNode, v: KbTreeNode, s: KbTreeNode) =>
  selected.value.product === p.key && selected.value.version === v.key && selected.value.source === s.key

function goPage(target: number) {
  if (target < 1 || target > totalPages.value || target === page.value) return
  page.value = target
  void loadDocs()
}

let timer: number | undefined
watch(keyword, () => {
  window.clearTimeout(timer)
  timer = window.setTimeout(resetAndLoad, 250)
})

onMounted(() => {
  void loadTree()
  void loadDocs()
})
</script>

<template>
  <section class="container kb">
    <PageHead title="知识库" subtitle="按产品线 · 版本 · 来源浏览全部已收录文档" />

    <div class="kb-layout">
      <!-- 左：体系树（产品线 ▸ 版本 ▸ 来源，可折叠） -->
      <aside class="kb-tree">
        <input
          v-model="keyword"
          class="kb-tree__search"
          type="search"
          placeholder="筛选文档标题..."
          aria-label="筛选文档标题"
        />

        <nav class="kb-tree__nav" aria-label="体系树">
          <template v-for="p in tree" :key="p.key">
            <div class="kb-tree__node kb-tree__node--top">
              <button
                class="kb-tree__chev"
                type="button"
                :aria-expanded="expandedProducts.has(p.key)"
                :aria-label="expandedProducts.has(p.key) ? '折叠' : '展开'"
                @click.stop="toggleProduct(p)"
              >
                {{ expandedProducts.has(p.key) ? '−' : '+' }}
              </button>
              <span
                class="kb-tree__label"
                :class="{ 'kb-tree__label--active': isProductActive(p) }"
                role="button"
                tabindex="0"
                @click="selectProduct(p)"
                @keydown.enter="selectProduct(p)"
              >{{ p.label }}</span>
              <span class="kb-tree__count num">{{ p.documentCount }}</span>
            </div>

            <template v-if="expandedProducts.has(p.key)">
              <template v-for="v in p.children" :key="v.key">
                <div class="kb-tree__node kb-tree__node--mid">
                  <button
                    v-if="v.children.length"
                    class="kb-tree__chev"
                    type="button"
                    :aria-expanded="expandedVersions.has(`${p.key}::${v.key}`)"
                    :aria-label="expandedVersions.has(`${p.key}::${v.key}`) ? '折叠' : '展开'"
                    @click.stop="toggleVersion(p, v)"
                  >
                    {{ expandedVersions.has(`${p.key}::${v.key}`) ? '−' : '+' }}
                  </button>
                  <span v-else class="kb-tree__chev kb-tree__chev--empty" aria-hidden="true" />
                  <span
                    class="kb-tree__label"
                    :class="{ 'kb-tree__label--active': isVersionActive(p, v) }"
                    role="button"
                    tabindex="0"
                    @click="selectVersion(p, v)"
                    @keydown.enter="selectVersion(p, v)"
                  >{{ v.label }}</span>
                </div>

                <template v-if="expandedVersions.has(`${p.key}::${v.key}`)">
                  <div
                    v-for="s in v.children"
                    :key="s.key"
                    class="kb-tree__node kb-tree__node--leaf"
                  >
                    <span class="kb-tree__chev kb-tree__chev--empty" aria-hidden="true" />
                    <span
                      class="kb-tree__label"
                      :class="{ 'kb-tree__label--active': isSourceActive(p, v, s) }"
                      role="button"
                      tabindex="0"
                      @click="selectSource(p, v, s)"
                      @keydown.enter="selectSource(p, v, s)"
                    >{{ s.label }}</span>
                    <span class="kb-tree__count num">{{ s.documentCount }}</span>
                  </div>
                </template>
              </template>
            </template>
          </template>
        </nav>

        <div v-if="!loading && tree.length === 0" class="kb-tree__empty">
          <p class="empty__text">知识还是一片空白 —— 从写第一篇文档开始</p>
          <router-link class="btn btn--primary" to="/editor">去写文档</router-link>
        </div>
      </aside>

      <!-- 右：文档卡网格（分页） -->
      <div class="kb-docs">
        <div v-if="docs.length > 0" class="doc-grid">
          <router-link
            v-for="card in docs"
            :key="card.id"
            class="doc-card"
            :to="`/kb/doc/${card.id}`"
          >
            <div class="doc-card__head">
              <span
                class="badge"
                :class="card.authority === 'official' ? 'badge--official' : 'badge--community'"
              >
                {{ card.authority === 'official' ? '官方' : '社区' }}
              </span>
              <span v-if="card.visibility === 'private'" class="badge badge--private">私有</span>
              <span v-if="card.version" class="doc-card__ver num">{{ card.version }}</span>
            </div>
            <h3 class="doc-card__title">{{ card.title }}</h3>
            <p class="doc-card__desc">{{ card.section || card.product }}</p>
            <span class="doc-card__count num">{{ card.chunkCount }} 片段</span>
          </router-link>
        </div>

        <div v-else-if="!loading" class="doc-grid">
          <div class="doc-grid__empty">
            <p class="empty__text">还没有文档 —— 你先写一篇，团队的知识就从它开始</p>
            <router-link class="btn btn--primary" to="/editor">去写文档</router-link>
          </div>
        </div>

        <div v-if="total > 0" class="kb-pager">
          <button class="btn btn--outline" type="button" :disabled="page <= 1" @click="goPage(page - 1)">
            上一页
          </button>
          <span class="kb-pager__info num">第 {{ page }} / {{ totalPages }} 页 · 共 {{ total }} 篇</span>
          <button
            class="btn btn--outline"
            type="button"
            :disabled="page >= totalPages"
            @click="goPage(page + 1)"
          >
            下一页
          </button>
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
  display: flex;
  align-items: center;
  gap: 2px;
  padding: 6px var(--sp-2);
  border-radius: var(--r-sm);
  font-size: var(--fs-sm);
  color: var(--ink-500);
  transition:
    background-color var(--dur-fast) var(--ease-out),
    color var(--dur-fast) var(--ease-out);
}

.kb-tree__node:hover {
  background: var(--surface-sunken);
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

.kb-tree__chev {
  flex-shrink: 0;
  width: 18px;
  height: 18px;
  display: grid;
  place-items: center;
  border: none;
  background: none;
  border-radius: var(--r-sm);
  color: var(--ink-400);
  font-size: var(--fs-xs);
  font-family: var(--font-num);
  cursor: pointer;
  transition:
    color var(--dur-fast) var(--ease-out),
    background-color var(--dur-fast) var(--ease-out);
}

.kb-tree__chev:hover {
  color: var(--brand-600);
  background: var(--ink-100);
}

.kb-tree__chev--empty {
  cursor: default;
  pointer-events: none;
}

.kb-tree__label {
  flex: 1;
  min-width: 0;
  padding: 1px var(--sp-1);
  border-left: 2px solid transparent;
  border-radius: var(--r-sm);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  cursor: pointer;
  user-select: none;
  transition: color var(--dur-fast) var(--ease-out);
}

.kb-tree__label:hover {
  color: var(--ink-900);
}

.kb-tree__label--active {
  border-left-color: var(--brand-600);
  color: var(--ink-900);
  font-weight: 600;
}

.kb-tree__count {
  flex-shrink: 0;
  font-size: var(--fs-xs);
  color: var(--ink-400);
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
  display: flex;
  flex-direction: column;
  gap: var(--sp-5);
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

/* 分页：hairline 按钮 + 等宽数字信息 */
.kb-pager {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: var(--sp-4);
  margin-top: auto;
  padding-top: var(--sp-2);
}

.kb-pager .btn {
  padding: var(--sp-2) var(--sp-4);
  font-size: var(--fs-sm);
}

.kb-pager .btn[disabled] {
  opacity: 0.45;
  cursor: not-allowed;
}

.kb-pager__info {
  font-size: var(--fs-sm);
  color: var(--ink-500);
}

/* 文档卡：hairline 描边，无阴影，hover 抬升为陶土描边 */
.doc-card {
  display: flex;
  flex-direction: column;
  gap: var(--sp-2);
  padding: var(--sp-4);
  border: 1px solid var(--hairline);
  border-radius: var(--r-lg);
  background: var(--surface);
  text-decoration: none;
  color: inherit;
  transition:
    border-color var(--dur-fast) var(--ease-out),
    background-color var(--dur-fast) var(--ease-out);
}

.doc-card:hover {
  border-color: var(--ink-400);
}

.doc-card__head {
  display: flex;
  align-items: center;
  gap: var(--sp-2);
}

.doc-card__ver {
  font-size: var(--fs-xs);
  color: var(--ink-500);
}

.doc-card__title {
  font-size: var(--fs-md);
  font-weight: 600;
  color: var(--ink-900);
  line-height: 1.4;
}

.doc-card__desc {
  margin: 0;
  font-size: var(--fs-sm);
  color: var(--ink-500);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.doc-card__count {
  margin-top: auto;
  font-size: var(--fs-xs);
  color: var(--ink-400);
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
