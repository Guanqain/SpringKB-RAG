<script setup lang="ts">
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
  try {
    await store.toggleActive(doc)
    ElMessage.success(`${doc.active ? '已启用' : '已停用'}：${doc.title}`)
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
  <div class="page-enter">
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
      <el-button type="primary" plain :icon="Plus" @click="router.push('/documents/import')">
        导入文档
      </el-button>
    </div>

    <div class="card mt">
      <el-table
        v-loading="store.loading"
        :data="store.documents"
        stripe
        style="width: 100%"
        empty-text="暂无文档，可点击「导入文档」添加知识"
      >
        <el-table-column prop="title" label="标题" min-width="260" show-overflow-tooltip>
          <template #default="{ row }">
            <span class="doc-title">{{ row.title }}</span>
          </template>
        </el-table-column>
        <el-table-column label="来源" width="110">
          <template #default="{ row }">
            <el-tag
              :type="row.source === 'official' ? 'success' : 'info'"
              effect="light"
              size="small"
            >
              {{ row.source === 'official' ? '官方' : '社区' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="版本" width="100">
          <template #default="{ row }">
            <el-tag type="warning" effect="light" size="small">
              {{ row.version }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="状态" width="90">
          <template #default="{ row }">
            <el-switch
              :model-value="row.active"
              @change="onToggleActive(row)"
            />
          </template>
        </el-table-column>
        <el-table-column prop="chunkCount" label="Chunk 数" width="100" align="center">
          <template #default="{ row }">
            <span class="mono">{{ row.chunkCount }}</span>
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
      </el-table>
    </div>

    <el-drawer v-model="drawerVisible" :title="drawerTitle" size="46%">
      <div v-loading="chunksLoading" class="chunk-list">
        <div v-for="(c, i) in chunks" :key="c.id" class="chunk-item">
          <div class="chunk-head">
            <span class="chunk-id mono">#{{ i + 1 }} · {{ c.id }}</span>
            <span v-if="c.metadata.section" class="chunk-meta">{{ c.metadata.section }}</span>
          </div>
          <p class="chunk-content">{{ c.content }}</p>
          <div class="chunk-meta-row">
            <el-tag
              v-if="c.metadata.source"
              :type="c.metadata.source === 'official' ? 'success' : 'info'"
              size="small"
              effect="plain"
            >
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
        <el-empty v-if="!chunksLoading && chunks.length === 0" description="该文档暂无 chunk" />
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

.mt {
  margin-top: 16px;
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

.doc-title {
  font-weight: 500;
  font-size: 13.5px;
}

.time-cell {
  font-size: 12.5px;
  color: var(--kb-text-sub);
}

.chunk-list {
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.chunk-item {
  border: 1px solid var(--kb-border);
  border-radius: 8px;
  padding: 12px 14px;
  background: #fbfcfc;
}

.chunk-head {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 6px;
}

.chunk-id {
  font-size: 12px;
  color: var(--kb-text-sub);
}

.chunk-meta {
  font-size: 12px;
  color: var(--kb-primary);
}

.chunk-content {
  margin: 0 0 8px;
  font-size: 13px;
  line-height: 1.65;
  color: var(--kb-text);
  max-height: 120px;
  overflow-y: auto;
}

.chunk-meta-row {
  display: flex;
  gap: 6px;
  flex-wrap: wrap;
}
</style>
