<script setup lang="ts">
// 管理后台 · 文档管理：由原 DocumentList 迁移，接口与行为保持不变（/api/admin/documents）。
import { computed, onMounted, reactive, ref } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Plus } from '@element-plus/icons-vue'
import { getDocumentChunks } from '@/api/document'
import { useDocumentStore } from '@/stores/document'
import type { Chunk, Document } from '@/types/document'

const router = useRouter()
const store = useDocumentStore()

// 版本下拉从已加载文档动态提取（真实库内版本为 3.x / latest / 4.2-SNAPSHOT 等，非固定 3.2/3.3）
const versionOptions = computed(() => {
  const set = new Set<string>()
  store.documents.forEach((d) => d.version && set.add(d.version))
  return [...set].sort()
})

const filterForm = reactive({
  source: '',
  version: '',
  active: '',
  keyword: '',
})

const hasFilter = computed(() =>
  Boolean(
    filterForm.source || filterForm.version || filterForm.active || filterForm.keyword.trim(),
  ),
)

function applyFilter() {
  store.filter = {
    source: filterForm.source || undefined,
    version: filterForm.version || undefined,
    active: filterForm.active || undefined,
    keyword: filterForm.keyword.trim() || undefined,
  }
  store.fetchDocuments()
}

function resetFilter() {
  filterForm.source = ''
  filterForm.version = ''
  filterForm.active = ''
  filterForm.keyword = ''
  store.filter = {}
  store.fetchDocuments()
}

onMounted(() => {
  store.fetchDocuments()
})

async function onToggleActive(doc: Document) {
  // 提示文案必须用切换后的目标值：doc.active 在请求前仍是旧值，
  // 直接用会导致「点开显示停用、点关显示启用」的反向提示
  const target = !doc.active
  try {
    await store.toggleActive(doc)
    ElMessage.success(`${target ? '已启用' : '已停用'}：${doc.title}`)
  } catch {
    /* 拦截器已提示 */
  }
}

async function onDelete(doc: Document) {
  try {
    await ElMessageBox.confirm(
      `删除「${doc.title}」及其全部 ${doc.chunkCount} 个 chunk？此操作不可恢复。`,
      '删除文档',
      { type: 'warning', confirmButtonText: '删除', cancelButtonText: '取消' },
    )
    await store.removeDocument(doc.id)
    ElMessage.success('文档已删除')
  } catch {
    /* 取消或失败 */
  }
}

const drawerVisible = ref(false)
const drawerTitle = ref('')
const chunks = ref<Chunk[]>([])
const chunksLoading = ref(false)

async function openChunks(doc: Document) {
  drawerTitle.value = doc.title
  drawerVisible.value = true
  chunksLoading.value = true
  try {
    chunks.value = await getDocumentChunks(doc.id)
  } catch {
    chunks.value = []
  } finally {
    chunksLoading.value = false
  }
}
</script>

<template>
  <div>
    <div class="page-head">
      <h2 class="page-head__title">文档管理</h2>
      <p class="page-head__sub">过滤 · 列表 · active 切换 · 删除 · chunks</p>
    </div>

    <div class="card filter-bar">
      <el-select v-model="filterForm.source" placeholder="来源" clearable class="w-140">
        <el-option label="官方文档" value="official" />
        <el-option label="社区问答" value="community" />
      </el-select>
      <el-select v-model="filterForm.version" placeholder="版本" clearable class="w-120">
        <el-option v-for="v in versionOptions" :key="v" :label="v" :value="v" />
      </el-select>
      <el-select v-model="filterForm.active" placeholder="状态" clearable class="w-120">
        <el-option label="活跃" value="true" />
        <el-option label="停用" value="false" />
      </el-select>
      <el-input
        v-model="filterForm.keyword"
        placeholder="按标题关键字搜索"
        clearable
        class="w-200"
        @keyup.enter="applyFilter"
      />
      <el-button type="primary" @click="applyFilter">查询</el-button>
      <el-button @click="resetFilter">重置</el-button>
      <div class="spacer" />
      <el-button plain :icon="Plus" @click="router.push('/admin/import')">
        导入文档
      </el-button>
    </div>

    <div class="card table-card">
      <el-table v-loading="store.loading" :data="store.documents" style="width: 100%">
        <el-table-column prop="title" label="标题" min-width="260" show-overflow-tooltip>
          <template #default="{ row }">
            <span class="doc-title">{{ row.title }}</span>
          </template>
        </el-table-column>
        <el-table-column label="来源" width="110">
          <template #default="{ row }">
            <span class="badge" :class="row.source === 'official' ? 'badge--official' : 'badge--community'">
              {{ row.source === 'official' ? '官方' : '社区' }}
            </span>
          </template>
        </el-table-column>
        <el-table-column label="版本" width="110">
          <template #default="{ row }">
            <span class="badge badge--neutral">{{ row.version }}</span>
          </template>
        </el-table-column>
        <el-table-column label="状态" width="110">
          <template #default="{ row }">
            <div class="status-cell">
              <span class="status-dot" :class="{ 'status-dot--signal': !row.active }" aria-hidden="true" />
              <el-switch :model-value="row.active" @change="onToggleActive(row)" />
            </div>
          </template>
        </el-table-column>
        <el-table-column prop="chunkCount" label="Chunk 数" width="100" align="center">
          <template #default="{ row }">
            <span class="num chunk-count">{{ row.chunkCount }}</span>
          </template>
        </el-table-column>
        <el-table-column label="更新时间" width="170">
          <template #default="{ row }">
            <span class="time-cell">{{ row.updatedAt }}</span>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="160" fixed="right">
          <template #default="{ row }">
            <el-button link type="primary" size="small" @click="openChunks(row)">
              查看 chunk
            </el-button>
            <el-button link type="danger" size="small" @click="onDelete(row)">
              删除
            </el-button>
          </template>
        </el-table-column>

        <template #empty>
          <div class="empty">
            <template v-if="hasFilter">
              <p class="empty__title">没有符合条件的文档 —— 换个条件再试试</p>
              <p class="empty__hint">当前筛选：来源 / 版本 / 状态 / 关键字。</p>
              <el-button @click="resetFilter">清空筛选</el-button>
            </template>
            <template v-else>
              <p class="empty__title">知识库还是空的 —— 先导入一份文档</p>
              <p class="empty__hint">导入后会自动切分片段，参与问答检索。</p>
              <el-button type="primary" :icon="Plus" @click="router.push('/admin/import')">
                去导入文档
              </el-button>
            </template>
          </div>
        </template>
      </el-table>
    </div>

    <el-drawer v-model="drawerVisible" :title="drawerTitle" size="46%">
      <div v-loading="chunksLoading" class="chunk-list">
        <div v-for="(c, i) in chunks" :key="c.id" class="chunk-item">
          <div class="chunk-head">
            <span class="chunk-id num">#{{ i + 1 }} · {{ c.id }}</span>
            <span v-if="c.metadata.section" class="chunk-meta">{{ c.metadata.section }}</span>
          </div>
          <p class="chunk-content">{{ c.content }}</p>
          <div class="chunk-meta-row">
            <el-tag v-if="c.metadata.source" size="small" effect="plain">
              {{ c.metadata.source }}
            </el-tag>
            <el-tag v-if="c.metadata.version" size="small" effect="plain">
              v{{ c.metadata.version }}
            </el-tag>
            <el-tag v-if="c.metadata.so_score" size="small" effect="plain">
              SO 评分 {{ c.metadata.so_score }}
            </el-tag>
          </div>
        </div>
        <div v-if="!chunksLoading && chunks.length === 0" class="empty">
          <p class="empty__title">这份文档还没有可用片段</p>
          <p class="empty__hint">导入时提供了正文，才会自动切分成可检索的片段。</p>
          <el-button @click="drawerVisible = false">关闭</el-button>
        </div>
      </div>
    </el-drawer>
  </div>
</template>

<style scoped>
.filter-bar {
  display: flex;
  align-items: center;
  gap: 10px;
  flex-wrap: wrap;
}

.spacer {
  flex: 1;
}

.table-card {
  margin-top: var(--sp-4);
}

.w-140 {
  width: 140px;
}

.w-120 {
  width: 120px;
}

.w-200 {
  width: 200px;
}

/* 表格：无竖线、无斑马纹、仅 1px 底部横向分隔线 */
.table-card :deep(.el-table) {
  --el-table-border-color: var(--hairline);
  --el-table-header-bg-color: var(--surface-sunken);
  --el-table-row-hover-bg-color: #f5f5f4;
  background: var(--surface);
}

.table-card :deep(.el-table th.el-table__cell) {
  background: var(--surface-sunken);
  font-size: var(--fs-sm);
  font-weight: 600;
  color: var(--ink-600);
}

.table-card :deep(.el-table td.el-table__cell),
.table-card :deep(.el-table th.el-table__cell) {
  border-right: none;
}

.table-card :deep(.el-table-fixed-column--right::before),
.table-card :deep(.el-table__fixed-right::before) {
  box-shadow: none;
}

.doc-title {
  font-weight: 500;
  font-size: 13.5px;
  color: var(--ink-900);
}

.badge--neutral {
  background: var(--ink-100);
  color: var(--ink-600);
}

.status-cell {
  display: inline-flex;
  align-items: center;
  gap: var(--sp-2);
}

/* 信号：停用 = 不参与召回，只用陶土小圆点，不做彩色底块 */
.status-dot {
  width: 6px;
  height: 6px;
  border-radius: 50%;
  background: var(--ink-300);
  flex: none;
}

.status-dot--signal {
  background: var(--brand-600);
}

.chunk-count {
  color: var(--ink-700);
}

.time-cell {
  font-size: 12.5px;
  color: var(--ink-500);
}

.empty {
  padding: var(--sp-12) var(--sp-6);
  text-align: center;
}

.empty__title {
  font-size: var(--fs-lg);
  font-weight: 600;
  color: var(--ink-900);
}

.empty__hint {
  margin: var(--sp-2) 0 var(--sp-5);
  font-size: var(--fs-sm);
  color: var(--ink-500);
}

/* 分页器：克制 —— 无按钮边框、当前页墨色实底、其余文字按钮 */
.table-card :deep(.el-pagination) {
  justify-content: flex-end;
  margin-top: var(--sp-4);
}

.table-card :deep(.el-pagination button),
.table-card :deep(.el-pagination .el-pager li) {
  border: none;
  background: transparent;
  box-shadow: none;
  font-weight: 400;
}

.table-card :deep(.el-pagination .el-pager li.is-active) {
  background: var(--ink-900);
  color: #fff;
  border-radius: var(--r-sm);
  font-weight: 600;
}

/* 抽屉内：hairline-strong 分隔，去掉彩色底块 */
.chunk-list {
  display: flex;
  flex-direction: column;
}

.chunk-item {
  padding: var(--sp-4) 0;
  border-bottom: 1px solid var(--hairline-strong);
}

.chunk-item:first-child {
  padding-top: 0;
}

.chunk-item:last-child {
  border-bottom: none;
}

.chunk-head {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: var(--sp-2);
}

.chunk-id {
  font-size: 12px;
  color: var(--ink-500);
}

.chunk-meta {
  font-size: 12px;
  color: var(--ink-500);
}

.chunk-content {
  margin: 0 0 var(--sp-2);
  font-size: 13px;
  line-height: 1.7;
  color: var(--ink-700);
  max-height: 120px;
  overflow-y: auto;
}

.chunk-meta-row {
  display: flex;
  gap: 6px;
  flex-wrap: wrap;
}
</style>
