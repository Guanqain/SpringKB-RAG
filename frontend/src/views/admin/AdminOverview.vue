<script setup lang="ts">
// 管理后台 · 概览：数据来自 /api/admin/stats 的服务端聚合，不再拉取全量文档行。
import { computed, onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import StatCard from '@/components/StatCard.vue'
import { fetchAdminStats, listAdminCategories } from '@/api/document'
import type { Category } from '@/types/admin'
import type { AdminStats } from '@/types/document'

const router = useRouter()

const stats = ref<AdminStats>({
  total: 0,
  active: 0,
  pending: 0,
  chunks: 0,
  official: 0,
  community: 0,
  products: [],
})
const loading = ref(false)
// product → 展示名：技术分类注册表可自定义展示名，未配置时回落到 product 本身
const displayNames = ref<Record<string, string>>({})

onMounted(async () => {
  loading.value = true
  try {
    const [categories, aggregated] = await Promise.all([
      listAdminCategories().catch(() => [] as Category[]),
      fetchAdminStats(),
    ])
    displayNames.value = Object.fromEntries(
      categories.map((c) => [c.product, c.displayName || c.product]),
    )
    stats.value = aggregated
  } catch {
    stats.value = {
      total: 0,
      active: 0,
      pending: 0,
      chunks: 0,
      official: 0,
      community: 0,
      products: [],
    }
  } finally {
    loading.value = false
  }
})

const totalDocs = computed(() => stats.value.total)
const totalChunks = computed(() => stats.value.chunks)
const activeCount = computed(() => stats.value.active)
const inactiveCount = computed(() => stats.value.total - stats.value.active)
const hasData = computed(() => stats.value.total > 0)

interface DistItem {
  label: string
  count: number
  pct: number
}

const sourceDist = computed<DistItem[]>(() => {
  const total = totalDocs.value || 1
  return [
    { label: '官方文档 official', count: stats.value.official, pct: (stats.value.official / total) * 100 },
    { label: '社区问答 community', count: stats.value.community, pct: (stats.value.community / total) * 100 },
  ]
})

const productDist = computed<DistItem[]>(() => {
  const total = totalDocs.value || 1
  return stats.value.products.map((p) => ({
    label: displayNames.value[p.product] ?? p.product,
    count: p.count,
    pct: (p.count / total) * 100,
  }))
})

const activeDist = computed<DistItem[]>(() => {
  const total = totalDocs.value || 1
  return [
    { label: '活跃 active', count: activeCount.value, pct: (activeCount.value / total) * 100 },
    { label: '停用 inactive', count: inactiveCount.value, pct: (inactiveCount.value / total) * 100 },
  ]
})
</script>

<template>
  <div v-loading="loading">
    <div class="page-head">
      <h2 class="page-head__title">概览</h2>
      <p class="page-head__sub">团队知识库整体概况（数据来自 admin stats 聚合）</p>
    </div>

    <div class="stat-grid">
      <StatCard label="文档总数" :value="totalDocs" hint="kb_document 行数" tone="primary" />
      <StatCard label="Chunk 总数" :value="totalChunks" hint="vector_store 向量条数" />
      <StatCard label="活跃文档" :value="activeCount" hint="参与检索召回" tone="success" />
      <StatCard label="停用文档" :value="inactiveCount" hint="不参与召回" tone="danger" />
    </div>

    <template v-if="hasData">
      <div class="panel-row">
        <section class="panel">
          <h3 class="panel-title">来源分布</h3>
          <div v-for="item in sourceDist" :key="item.label" class="dist-row">
            <div class="dist-head">
              <span>{{ item.label }}</span>
              <span class="num dist-count">{{ item.count }} · {{ item.pct.toFixed(0) }}%</span>
            </div>
            <div class="dist-track">
              <div class="dist-fill" :style="{ width: item.pct + '%' }" />
            </div>
          </div>
        </section>

        <section class="panel">
          <h3 class="panel-title">技术分布</h3>
          <div v-for="item in productDist" :key="item.label" class="dist-row">
            <div class="dist-head">
              <span>{{ item.label }}</span>
              <span class="num dist-count">{{ item.count }} · {{ item.pct.toFixed(0) }}%</span>
            </div>
            <div class="dist-track">
              <div class="dist-fill" :style="{ width: item.pct + '%' }" />
            </div>
          </div>
        </section>

        <section class="panel">
          <h3 class="panel-title">启用状态</h3>
          <div v-for="item in activeDist" :key="item.label" class="dist-row">
            <div class="dist-head">
              <span>{{ item.label }}</span>
              <span class="num dist-count">{{ item.count }} · {{ item.pct.toFixed(0) }}%</span>
            </div>
            <div class="dist-track">
              <div class="dist-fill" :style="{ width: item.pct + '%' }" />
            </div>
          </div>
        </section>
      </div>
    </template>

    <section v-else class="panel empty">
      <p class="empty__title">知识库还是空的 —— 先导入一篇，概览才有东西可算</p>
      <p class="empty__hint">文档一进来，来源、版本与启用状态就会在这里自动汇总。</p>
      <el-button type="primary" @click="router.push('/admin/import')">去导入文档</el-button>
    </section>
  </div>
</template>

<style scoped>
.stat-grid {
  display: grid;
  grid-template-columns: repeat(4, minmax(0, 1fr));
  background: var(--surface);
  border: 1px solid var(--hairline);
  border-radius: var(--r-lg);
  overflow: hidden;
}

.stat-grid :deep(.stat-card) {
  border: none;
  border-radius: 0;
}

.stat-grid :deep(.stat-card + .stat-card) {
  border-left: 1px solid var(--hairline);
}

.panel-row {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: var(--sp-4);
  margin-top: var(--sp-4);
}

.panel {
  background: var(--surface);
  border: 1px solid var(--hairline);
  border-radius: var(--r-lg);
  padding: var(--sp-5);
}

.panel-title {
  font-size: var(--fs-md);
  font-weight: 600;
  color: var(--ink-900);
  margin: 0 0 var(--sp-4);
  padding-bottom: var(--sp-3);
  border-bottom: 1px solid var(--hairline);
}

.dist-row {
  padding: var(--sp-3) 0;
  border-bottom: 1px solid var(--hairline);
}

.dist-row:last-child {
  border-bottom: none;
  padding-bottom: 0;
}

.dist-head {
  display: flex;
  justify-content: space-between;
  gap: var(--sp-3);
  font-size: var(--fs-sm);
  color: var(--ink-600);
  margin-bottom: var(--sp-2);
}

.dist-count {
  color: var(--ink-500);
  white-space: nowrap;
}

.dist-track {
  height: 4px;
  border-radius: 2px;
  background: var(--ink-100);
  overflow: hidden;
}

.dist-fill {
  height: 100%;
  border-radius: 2px;
  background: var(--ink-400);
  transition: width var(--dur-slow) var(--ease-out);
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
  margin: var(--sp-2) 0 var(--sp-6);
  font-size: var(--fs-sm);
  color: var(--ink-500);
}

@media (max-width: 1100px) {
  .stat-grid {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }

  .stat-grid :deep(.stat-card:nth-child(3)) {
    border-left: none;
  }

  .stat-grid :deep(.stat-card:nth-child(n + 3)) {
    border-top: 1px solid var(--hairline);
  }

  .panel-row {
    grid-template-columns: 1fr;
  }
}

@media (max-width: 640px) {
  .stat-grid {
    grid-template-columns: 1fr;
  }

  .stat-grid :deep(.stat-card + .stat-card) {
    border-left: none;
    border-top: 1px solid var(--hairline);
  }
}
</style>
