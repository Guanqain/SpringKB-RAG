<script setup lang="ts">
// 管理后台 · 概览：由原 Dashboard 迁移，数据均来自 /api/admin/documents 真实聚合。
import { computed, onMounted } from 'vue'
import StatCard from '@/components/StatCard.vue'
import { useDocumentStore } from '@/stores/document'

const store = useDocumentStore()

onMounted(() => {
  store.fetchDocuments()
})

const totalDocs = computed(() => store.documents.length)
const totalChunks = computed(() =>
  store.documents.reduce((sum, d) => sum + d.chunkCount, 0),
)
const activeCount = computed(
  () => store.documents.filter((d) => d.active).length,
)
const inactiveCount = computed(
  () => store.documents.filter((d) => !d.active).length,
)

interface DistItem {
  label: string
  count: number
  pct: number
}

const sourceDist = computed<DistItem[]>(() => {
  const total = totalDocs.value || 1
  const official = store.documents.filter((d) => d.source === 'official').length
  const community = store.documents.filter((d) => d.source === 'community').length
  return [
    { label: '官方文档 official', count: official, pct: (official / total) * 100 },
    { label: '社区问答 community', count: community, pct: (community / total) * 100 },
  ]
})

const versionDist = computed<DistItem[]>(() => {
  const total = totalDocs.value || 1
  const counts = new Map<string, number>()
  store.documents.forEach((d) => {
    const v = d.version || '未知'
    counts.set(v, (counts.get(v) || 0) + 1)
  })
  return [...counts.entries()]
    .sort((a, b) => b[1] - a[1])
    .map(([version, count]) => ({
      label: `版本 ${version}`,
      count,
      pct: (count / total) * 100,
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
  <div class="page-enter">
    <div class="page-head">
      <h2 class="page-head__title">概览</h2>
      <p class="page-head__sub">团队知识库整体概况（数据来自 admin documents 聚合）</p>
    </div>

    <el-row :gutter="16">
      <el-col :span="6">
        <StatCard label="文档总数" :value="totalDocs" hint="kb_document 行数" tone="primary" />
      </el-col>
      <el-col :span="6">
        <StatCard label="Chunk 总数" :value="totalChunks" hint="vector_store 向量条数" />
      </el-col>
      <el-col :span="6">
        <StatCard label="活跃文档" :value="activeCount" hint="参与检索召回" tone="success" />
      </el-col>
      <el-col :span="6">
        <StatCard label="停用文档" :value="inactiveCount" hint="不参与召回" tone="danger" />
      </el-col>
    </el-row>

    <el-row :gutter="16" class="mt">
      <el-col :span="8">
        <div class="card dist-card">
          <h3 class="dist-title">来源分布</h3>
          <div v-for="item in sourceDist" :key="item.label" class="dist-row">
            <div class="dist-head">
              <span>{{ item.label }}</span>
              <span class="mono">{{ item.count }} · {{ item.pct.toFixed(0) }}%</span>
            </div>
            <div class="dist-track">
              <div class="dist-fill" :style="{ width: item.pct + '%' }" />
            </div>
          </div>
        </div>
      </el-col>
      <el-col :span="8">
        <div class="card dist-card">
          <h3 class="dist-title">版本分布</h3>
          <div v-for="item in versionDist" :key="item.label" class="dist-row">
            <div class="dist-head">
              <span>{{ item.label }}</span>
              <span class="mono">{{ item.count }} · {{ item.pct.toFixed(0) }}%</span>
            </div>
            <div class="dist-track">
              <div class="dist-fill dist-fill--amber" :style="{ width: item.pct + '%' }" />
            </div>
          </div>
        </div>
      </el-col>
      <el-col :span="8">
        <div class="card dist-card">
          <h3 class="dist-title">启用状态</h3>
          <div v-for="item in activeDist" :key="item.label" class="dist-row">
            <div class="dist-head">
              <span>{{ item.label }}</span>
              <span class="mono">{{ item.count }} · {{ item.pct.toFixed(0) }}%</span>
            </div>
            <div class="dist-track">
              <div class="dist-fill dist-fill--emerald" :style="{ width: item.pct + '%' }" />
            </div>
          </div>
        </div>
      </el-col>
    </el-row>
  </div>
</template>

<style scoped>
.mt {
  margin-top: 16px;
}

.dist-title {
  font-size: var(--fs-md);
  font-weight: 700;
  color: var(--ink-900);
  margin: 0 0 16px;
}

.dist-row {
  margin-bottom: 14px;
}

.dist-row:last-child {
  margin-bottom: 0;
}

.dist-head {
  display: flex;
  justify-content: space-between;
  font-size: var(--fs-sm);
  color: var(--ink-500);
  margin-bottom: 6px;
}

.dist-track {
  height: 8px;
  border-radius: 4px;
  background: var(--ink-100);
  overflow: hidden;
}

.dist-fill {
  height: 100%;
  border-radius: 4px;
  background: var(--grad-brand);
  transition: width 0.4s ease;
}

.dist-fill--amber {
  background: linear-gradient(90deg, var(--brand-400), var(--brand-600));
}

.dist-fill--emerald {
  background: linear-gradient(90deg, var(--success), #34d399);
}
</style>
