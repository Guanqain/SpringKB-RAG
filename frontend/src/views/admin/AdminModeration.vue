<script setup lang="ts">
// 管理后台 · 内容审核：团队 UGC 文档须在此通过后才进入知识库与问答引用。
import { computed, onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import type { TableInstance } from 'element-plus'
import {
  approveReview,
  batchApproveReviews,
  getDocumentChunks,
  listAdminCategories,
  listReviews,
  rejectReview,
} from '@/api/document'
import type { Category } from '@/types/admin'
import type { Chunk, ReviewItem } from '@/types/document'

const tabs = [
  { label: '待审核', value: 'pending' },
  { label: '已驳回', value: 'rejected' },
  { label: '已通过', value: 'approved' },
]
const status = ref('pending')
const items = ref<ReviewItem[]>([])
const loading = ref(false)

const currentLabel = computed(() => tabs.find((t) => t.value === status.value)?.label ?? '')

// ---------- 筛选：技术分类 / 作者 / 关键词 ----------
const filterForm = reactive({ product: '', author: '', keyword: '' })
const categories = ref<Category[]>([])

async function loadCategories() {
  try {
    categories.value = await listAdminCategories()
  } catch {
    categories.value = []
  }
}

const hasFilter = computed(() =>
  Boolean(filterForm.product || filterForm.author.trim() || filterForm.keyword.trim()),
)

async function load() {
  loading.value = true
  try {
    items.value = await listReviews({
      status: status.value,
      product: filterForm.product || undefined,
      author: filterForm.author.trim() || undefined,
      keyword: filterForm.keyword.trim() || undefined,
    })
    tableRef.value?.clearSelection()
  } catch {
    items.value = []
  } finally {
    loading.value = false
  }
}

function applyFilter() {
  void load()
}

function resetFilter() {
  filterForm.product = ''
  filterForm.author = ''
  filterForm.keyword = ''
  void load()
}

function switchStatus(value: string) {
  if (status.value === value) return
  status.value = value
  void load()
}

// ---------- 批量通过（驳回仍需逐条填写理由） ----------
const tableRef = ref<TableInstance>()
const selectedIds = ref<string[]>([])

function onSelectionChange(rows: ReviewItem[]) {
  selectedIds.value = rows.map((row) => row.id)
}

async function batchApprove() {
  const ids = selectedIds.value
  if (!ids.length) {
    ElMessage.warning('请先勾选要通过的文档')
    return
  }
  try {
    await ElMessageBox.confirm(
      `批量通过 ${ids.length} 篇文档？通过后将进入知识库，可被检索与问答引用。`,
      '批量审核通过',
      { type: 'info', confirmButtonText: '全部通过', cancelButtonText: '取消' },
    )
  } catch {
    return
  }
  try {
    const result = await batchApproveReviews(ids)
    if (result.failed) {
      ElMessage.warning(
        `已通过 ${result.approved} 篇，${result.failed} 篇失败：${result.errors.join('；')}`,
      )
    } else {
      ElMessage.success(`已通过 ${result.approved} 篇文档`)
    }
    await load()
  } catch {
    /* 拦截器已提示 */
  }
}

// 预览：审核前先看分块正文（含标题结构），避免直接打开详情页绕过可见性
const drawerVisible = ref(false)
const drawerTitle = ref('')
const chunks = ref<Chunk[]>([])
const chunksLoading = ref(false)

async function openPreview(item: ReviewItem) {
  drawerTitle.value = item.title
  drawerVisible.value = true
  chunksLoading.value = true
  try {
    chunks.value = await getDocumentChunks(item.id)
  } catch {
    chunks.value = []
  } finally {
    chunksLoading.value = false
  }
}

async function approve(item: ReviewItem) {
  try {
    await ElMessageBox.confirm(
      `通过「${item.title}」？通过后将进入知识库，可被检索与问答引用。`,
      '审核通过',
      { type: 'info', confirmButtonText: '通过', cancelButtonText: '取消' },
    )
    await approveReview(item.id)
    ElMessage.success('已通过审核')
    await load()
  } catch {
    /* 取消或拦截器已提示 */
  }
}

async function reject(item: ReviewItem) {
  try {
    const { value } = await ElMessageBox.prompt(
      '请填写驳回理由（作者可见，并据此修改后重新提交）',
      '驳回文档',
      {
        type: 'warning',
        confirmButtonText: '驳回',
        cancelButtonText: '取消',
        inputPlaceholder: '例如：包含内部地址、需要补充版本说明',
        inputValidator: (v) => (v && v.trim() ? true : '驳回理由不能为空'),
      },
    )
    await rejectReview(item.id, value.trim())
    ElMessage.success('已驳回')
    await load()
  } catch {
    /* 取消或拦截器已提示 */
  }
}

onMounted(() => {
  void load()
  void loadCategories()
})
</script>

<template>
  <div>
    <div class="page-head">
      <h2 class="page-head__title">内容审核</h2>
      <p class="page-head__sub">
        团队成员发布的文档需通过审核后才进入知识库；私有文档免审。管理员发布的团队文档同样需要审核。
      </p>
    </div>

    <div class="card filter-bar">
      <el-radio-group :model-value="status" @change="switchStatus">
        <el-radio-button v-for="tab in tabs" :key="tab.value" :value="tab.value">
          {{ tab.label }}
        </el-radio-button>
      </el-radio-group>
      <el-select v-model="filterForm.product" placeholder="技术分类" clearable class="w-140">
        <el-option
          v-for="c in categories"
          :key="c.product"
          :label="c.displayName || c.product"
          :value="c.product"
        />
      </el-select>
      <el-input
        v-model="filterForm.author"
        placeholder="按作者筛选"
        clearable
        class="w-140"
        @keyup.enter="applyFilter"
      />
      <el-input
        v-model="filterForm.keyword"
        placeholder="标题 / 作者关键词"
        clearable
        class="w-180"
        @keyup.enter="applyFilter"
      />
      <el-button type="primary" @click="applyFilter">查询</el-button>
      <el-button v-if="hasFilter" @click="resetFilter">重置</el-button>
      <div class="spacer" />
      <el-button
        v-if="status === 'pending'"
        type="primary"
        plain
        :disabled="!selectedIds.length"
        @click="batchApprove"
      >
        批量通过{{ selectedIds.length ? `（${selectedIds.length}）` : '' }}
      </el-button>
      <el-button @click="load">刷新</el-button>
    </div>

    <div class="card table-card">
      <el-table
        ref="tableRef"
        v-loading="loading"
        :data="items"
        style="width: 100%"
        @selection-change="onSelectionChange"
      >
        <el-table-column v-if="status === 'pending'" type="selection" width="46" />
        <el-table-column prop="title" label="标题" min-width="240" show-overflow-tooltip />
        <el-table-column label="作者" width="120">
          <template #default="{ row }">{{ row.author || '—' }}</template>
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
        <el-table-column prop="chunkCount" label="片段" width="90" align="center" />
        <el-table-column label="提交时间" width="170">
          <template #default="{ row }">
            <span class="num">{{ row.submittedAt }}</span>
          </template>
        </el-table-column>
        <el-table-column v-if="status !== 'pending'" label="审核留痕" min-width="200">
          <template #default="{ row }">
            <span class="review-trace">
              {{ row.reviewedBy || '—' }} · {{ row.reviewedAt || '—' }}
              <template v-if="row.reviewNote">：{{ row.reviewNote }}</template>
            </span>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="220" fixed="right">
          <template #default="{ row }">
            <el-button text @click="openPreview(row)">查看正文</el-button>
            <template v-if="status === 'pending'">
              <el-button text type="success" @click="approve(row)">通过</el-button>
              <el-button text type="danger" @click="reject(row)">驳回</el-button>
            </template>
          </template>
        </el-table-column>
      </el-table>

      <div v-if="!loading && !items.length" class="empty-state">
        {{ status === 'pending' ? '没有待审核的文档' : `暂无${currentLabel}的记录` }}
      </div>
    </div>

    <el-drawer v-model="drawerVisible" :title="drawerTitle" size="560px">
      <div v-loading="chunksLoading">
        <article
          v-for="chunk in chunks"
          :key="chunk.id"
          class="chunk-card"
        >
          <p v-if="chunk.metadata?.heading_path" class="chunk-card__heading">
            {{ chunk.metadata.heading_path }}
          </p>
          <pre class="chunk-card__content">{{ chunk.content }}</pre>
        </article>
        <p v-if="!chunksLoading && !chunks.length" class="empty-state">这篇文档还没有正文分块</p>
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

.w-140 {
  width: 140px;
}

.w-180 {
  width: 180px;
}

.table-card {
  margin-top: var(--sp-4);
}

.badge--neutral {
  background: var(--ink-100);
  color: var(--ink-600);
}

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

.review-trace {
  font-size: var(--fs-xs);
  color: var(--ink-500);
  line-height: 1.6;
}

.empty-state {
  padding: var(--sp-10) 0;
  text-align: center;
  font-size: var(--fs-sm);
  color: var(--ink-400);
}

.chunk-card + .chunk-card {
  margin-top: var(--sp-4);
  padding-top: var(--sp-4);
  border-top: 1px solid var(--hairline);
}

.chunk-card__heading {
  margin: 0 0 var(--sp-2);
  font-size: var(--fs-xs);
  font-weight: 600;
  color: var(--ink-500);
  letter-spacing: 0.04em;
}

.chunk-card__content {
  margin: 0;
  white-space: pre-wrap;
  overflow-wrap: anywhere;
  font-family: inherit;
  font-size: var(--fs-sm);
  line-height: 1.7;
  color: var(--ink-700);
}
</style>
