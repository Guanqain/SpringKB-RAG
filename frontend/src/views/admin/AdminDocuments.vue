<script setup lang="ts">
// 管理后台 · 文档管理：过滤 / 列表 / 元数据编辑（技术分类·版本·可见性）/ active / 删除 / chunks。
import { computed, onMounted, reactive, ref } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Plus } from '@element-plus/icons-vue'
import {
  createAdminCategory,
  getDocumentChunks,
  listAdminCategories,
  updateDocumentMeta,
} from '@/api/document'
import { useDocumentStore } from '@/stores/document'
import type { Category, DocumentMetaPayload } from '@/types/admin'
import type { Chunk, Document } from '@/types/document'

const router = useRouter()
const store = useDocumentStore()

// 技术分类来自管理端注册表（含文档动态聚合出的版本）
const categories = ref<Category[]>([])

async function loadCategories() {
  try {
    categories.value = await listAdminCategories()
  } catch {
    categories.value = []
  }
}

const productOptions = computed(() => {
  const set = new Set<string>()
  categories.value.forEach((c) => set.add(c.product))
  store.documents.forEach((d) => d.product && set.add(d.product))
  return [...set].sort()
})

// 版本级联：选定分类时只展示该分类下的版本，否则展示全部分类的版本
const versionOptions = computed(() => {
  if (filterForm.product) {
    const hit = categories.value.find((c) => c.product === filterForm.product)
    if (hit?.versions.length) return [...hit.versions].sort()
  }
  const set = new Set<string>()
  categories.value.forEach((c) => c.versions.forEach((v) => set.add(v)))
  store.documents.forEach((d) => d.version && set.add(d.version))
  return [...set].sort()
})

const filterForm = reactive({
  source: '',
  product: '',
  version: '',
  active: '',
  keyword: '',
})

const hasFilter = computed(() =>
  Boolean(
    filterForm.source ||
      filterForm.product ||
      filterForm.version ||
      filterForm.active ||
      filterForm.keyword.trim(),
  ),
)

function onFilterProductChange() {
  filterForm.version = ''
}

function applyFilter() {
  store.filter = {
    source: filterForm.source || undefined,
    product: filterForm.product || undefined,
    version: filterForm.version || undefined,
    active: filterForm.active || undefined,
    keyword: filterForm.keyword.trim() || undefined,
  }
  // 筛选条件变化后从第 1 页重新取数
  void store.goToPage(1)
}

function resetFilter() {
  filterForm.source = ''
  filterForm.product = ''
  filterForm.version = ''
  filterForm.active = ''
  filterForm.keyword = ''
  store.filter = {}
  void store.goToPage(1)
}

function onPageChange(target: number) {
  // 改 size 时组件可能连带触发 current-change，页面已由 changeSize 拉到第 1 页则跳过
  if (target === store.page) return
  void store.goToPage(target)
}

function onSizeChange(target: number) {
  void store.changeSize(target)
}

onMounted(() => {
  store.fetchDocuments()
  void loadCategories()
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

// ---------- 元数据编辑（技术分类 / 版本 / 可见性） ----------
const metaVisible = ref(false)
const metaSaving = ref(false)
const metaDocId = ref('')
const metaForm = reactive<Required<DocumentMetaPayload>>({
  product: '',
  version: '',
  visibility: 'team',
})

function openMeta(doc: Document) {
  metaDocId.value = doc.id
  metaForm.product = doc.product
  metaForm.version = doc.version
  metaForm.visibility = doc.visibility
  metaVisible.value = true
}

async function saveMeta() {
  if (!metaForm.product.trim()) {
    ElMessage.warning('技术分类不能为空')
    return
  }
  metaSaving.value = true
  try {
    const updated = await updateDocumentMeta(metaDocId.value, {
      product: metaForm.product.trim(),
      version: metaForm.version.trim(),
      visibility: metaForm.visibility,
    })
    const idx = store.documents.findIndex((d) => d.id === updated.id)
    if (idx >= 0) store.documents[idx] = updated
    metaVisible.value = false
    ElMessage.success(
      updated.reviewStatus === 'pending'
        ? '已更新：团队可见变更已转入待审核，通过后才会参与检索'
        : '文档元数据已更新',
    )
    void loadCategories()
  } catch {
    /* 拦截器已提示 */
  } finally {
    metaSaving.value = false
  }
}

// ---------- 新增技术分类 ----------
const categoryVisible = ref(false)
const categorySaving = ref(false)
const categoryForm = reactive({ product: '', displayName: '' })

function openCategory() {
  categoryForm.product = ''
  categoryForm.displayName = ''
  categoryVisible.value = true
}

async function saveCategory() {
  const product = categoryForm.product.trim()
  const displayName = categoryForm.displayName.trim() || product
  if (!product) {
    ElMessage.warning('请填写技术分类标识')
    return
  }
  categorySaving.value = true
  try {
    await createAdminCategory(product, displayName)
    categoryVisible.value = false
    ElMessage.success('技术分类已新增')
    void loadCategories()
  } catch {
    /* 拦截器已提示 */
  } finally {
    categorySaving.value = false
  }
}
</script>

<template>
  <div>
    <div class="page-head">
      <h2 class="page-head__title">文档管理</h2>
      <p class="page-head__sub">按技术分类 / 版本 / 来源过滤，编辑元数据与可见性，查看 chunk</p>
    </div>

    <div class="card filter-bar">
      <el-select v-model="filterForm.source" placeholder="来源" clearable class="w-140">
        <el-option label="官方文档" value="official" />
        <el-option label="社区问答" value="community" />
      </el-select>
      <el-select
        v-model="filterForm.product"
        placeholder="技术分类"
        clearable
        class="w-140"
        @change="onFilterProductChange"
      >
        <el-option v-for="p in productOptions" :key="p" :label="p" :value="p" />
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
      <el-button plain @click="openCategory">新增技术分类</el-button>
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
        <el-table-column label="技术分类" width="130">
          <template #default="{ row }">
            <span class="badge badge--neutral">{{ row.product || '未分类' }}</span>
          </template>
        </el-table-column>
        <el-table-column label="版本" width="110">
          <template #default="{ row }">
            <span class="badge badge--neutral">{{ row.version || '未标注' }}</span>
          </template>
        </el-table-column>
        <el-table-column label="可见性" width="100">
          <template #default="{ row }">
            <span class="badge" :class="row.visibility === 'private' ? 'badge--private' : 'badge--neutral'">
              {{ row.visibility === 'private' ? '私有' : '团队' }}
            </span>
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
        <el-table-column label="操作" width="220" fixed="right">
          <template #default="{ row }">
            <el-button link type="primary" size="small" @click="openMeta(row)">
              编辑元数据
            </el-button>
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

      <el-pagination
        v-if="store.total > 0"
        :current-page="store.page"
        :page-size="store.size"
        :page-sizes="[10, 20, 50, 100]"
        :total="store.total"
        layout="total, sizes, prev, pager, next"
        @current-change="onPageChange"
        @size-change="onSizeChange"
      />
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

    <el-dialog v-model="metaVisible" title="编辑文档元数据" width="420px">
      <div class="dialog-form">
        <label class="dialog-field">
          <span class="dialog-field__label">技术分类</span>
          <el-input v-model="metaForm.product" placeholder="例如：Spring、MySQL" />
        </label>
        <label class="dialog-field">
          <span class="dialog-field__label">版本</span>
          <el-input v-model="metaForm.version" placeholder="留空表示未标注" />
        </label>
        <label class="dialog-field">
          <span class="dialog-field__label">可见性</span>
          <el-select v-model="metaForm.visibility" style="width: 100%">
            <el-option label="团队可见（参与检索）" value="team" />
            <el-option label="仅作者可见（不参与检索）" value="private" />
          </el-select>
        </label>
      </div>
      <template #footer>
        <el-button @click="metaVisible = false">取消</el-button>
        <el-button type="primary" :loading="metaSaving" @click="saveMeta">保存</el-button>
      </template>
    </el-dialog>

    <el-dialog v-model="categoryVisible" title="新增技术分类" width="400px">
      <div class="dialog-form">
        <label class="dialog-field">
          <span class="dialog-field__label">分类标识（product）</span>
          <el-input v-model="categoryForm.product" placeholder="例如：Kafka" />
        </label>
        <label class="dialog-field">
          <span class="dialog-field__label">展示名称</span>
          <el-input v-model="categoryForm.displayName" placeholder="缺省与标识一致" />
        </label>
        <p class="dialog-hint">新增的分类会立即出现在筛选器与写文档页的分类建议中。</p>
      </div>
      <template #footer>
        <el-button @click="categoryVisible = false">取消</el-button>
        <el-button type="primary" :loading="categorySaving" @click="saveCategory">新增</el-button>
      </template>
    </el-dialog>
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

.dialog-form {
  display: flex;
  flex-direction: column;
  gap: var(--sp-3);
}

.dialog-field {
  display: flex;
  flex-direction: column;
  gap: var(--sp-1);
}

.dialog-field__label {
  font-size: var(--fs-sm);
  font-weight: 600;
  color: var(--ink-600);
}

.dialog-hint {
  margin: 0;
  font-size: var(--fs-xs);
  color: var(--ink-500);
  line-height: 1.6;
}
</style>
