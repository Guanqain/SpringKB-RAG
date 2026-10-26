<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import {
  publishUgcDocument,
  scanUgcDocument,
  updateUgcDocument,
  uploadUgcDocument,
} from '@/api/ugc'
import { fetchKbCategories } from '@/api/category'
import { fetchKbDoc } from '@/api/kb'
import type { Category } from '@/types/admin'
import type { ScanResult } from '@/types/kb'
import { renderMarkdown } from '@/utils/markdown'

const route = useRoute()
const router = useRouter()

// 编辑模式：/editor?docId=xxx（编辑后重新提交审核；团队文档重新进入待审）
const editId = computed(() => {
  const raw = route.query.docId
  return typeof raw === 'string' ? raw : ''
})
const isEdit = computed(() => Boolean(editId.value))

const title = ref('')
const markdown = ref('')
const product = ref('')
const version = ref('')
const visibility = ref<'team' | 'private'>('team')
const publishing = ref(false)

// 技术分类 / 版本建议：来自知识库现有分类，输入框仍可自由填写以扩充分类
const categories = ref<Category[]>([])
const productSuggestions = computed(() => categories.value.map((item) => item.product))
const versionSuggestions = computed(() => {
  const hit = categories.value.find((item) => item.product === product.value.trim())
  return hit?.versions ?? []
})

// 文本文件导入：.txt / .md，≤10MB，上传后直接走服务端转换为文档
const MAX_FILE_BYTES = 10 * 1024 * 1024
const importOpen = ref(false)
const dragging = ref(false)
const pickedFile = ref<File | null>(null)
const importTitle = ref('')
const uploading = ref(false)

const previewHtml = computed(() => renderMarkdown(markdown.value))

// 脱敏扫描状态：团队文档发布前预检，命中则展示明细并拦截
const scanning = ref(false)
const scanResult = ref<ScanResult | null>(null)

const scanState = computed(() => {
  if (visibility.value === 'private') return 'private'
  if (scanning.value) return 'scanning'
  if (scanResult.value?.blocked) return 'blocked'
  if (scanResult.value) return 'clean'
  return 'idle'
})

onMounted(async () => {
  try {
    categories.value = await fetchKbCategories()
  } catch {
    /* 分类建议拉取失败不影响写作 */
  }
  if (isEdit.value) {
    try {
      const doc = await fetchKbDoc(editId.value)
      title.value = doc.title
      markdown.value = doc.markdown
      product.value = doc.product
      version.value = doc.version === '未标注' ? '' : doc.version
      visibility.value = doc.visibility
      if (doc.reviewNote) {
        ElMessage.warning(`上次审核意见：${doc.reviewNote}`)
      }
    } catch {
      /* 打不开时拦截器已提示；不阻塞进入写作页 */
    }
  }
})

function buildPayload() {
  return {
    title: title.value.trim(),
    markdown: markdown.value,
    product: product.value.trim() || undefined,
    version: version.value.trim() || undefined,
    visibility: visibility.value,
  }
}

/** 发布前预检：私有文档跳过；返回 true 表示可以提交 */
async function precheck(): Promise<boolean> {
  if (visibility.value === 'private') {
    scanResult.value = null
    return true
  }
  scanning.value = true
  try {
    const result = await scanUgcDocument(buildPayload())
    scanResult.value = result
    if (result.blocked) {
      ElMessage.warning('检测到敏感信息，请修改后再发布')
      return false
    }
    return true
  } catch {
    return false
  } finally {
    scanning.value = false
  }
}

/** 发布接口可能返回 422（携带命中项），统一回填到扫描面板 */
function captureScanError(error: unknown) {
  const err = error as { code?: number; data?: ScanResult }
  if (err.code === 422 && err.data && 'findings' in err.data) {
    scanResult.value = err.data
  }
}

async function publish() {
  if (!title.value.trim()) {
    ElMessage.warning('请先填写文档标题')
    return
  }
  if (!markdown.value.trim()) {
    ElMessage.warning('请先填写正文')
    return
  }
  if (!(await precheck())) return
  publishing.value = true
  try {
    const payload = buildPayload()
    const result = isEdit.value
      ? await updateUgcDocument(editId.value, payload)
      : await publishUgcDocument(payload)
    ElMessage.success(result.message || `发布成功，共 ${result.chunkCount} 个片段`)
    if (result.reviewStatus === 'pending') {
      void router.push('/me')
    } else {
      void router.push(`/kb/doc/${result.docId}`)
    }
  } catch (error) {
    captureScanError(error)
  } finally {
    publishing.value = false
  }
}

function formatSize(bytes: number) {
  if (bytes < 1024) return `${bytes} B`
  if (bytes < 1024 * 1024) return `${(bytes / 1024).toFixed(1)} KB`
  return `${(bytes / 1024 / 1024).toFixed(1)} MB`
}

function pickFile(file: File | null | undefined) {
  if (!file) return
  const lower = file.name.toLowerCase()
  if (!lower.endsWith('.txt') && !lower.endsWith('.md')) {
    ElMessage.warning('仅支持 .txt / .md 文本文件')
    return
  }
  if (file.size > MAX_FILE_BYTES) {
    ElMessage.warning('文件大小不能超过 10MB')
    return
  }
  pickedFile.value = file
  importTitle.value = file.name.replace(/\.[^.]+$/, '')
}

function onFileChange(event: Event) {
  const input = event.target as HTMLInputElement
  pickFile(input.files?.[0])
  input.value = ''
}

function onDrop(event: DragEvent) {
  dragging.value = false
  pickFile(event.dataTransfer?.files?.[0])
}

async function uploadFile() {
  if (!pickedFile.value) {
    ElMessage.warning('请先选择要上传的文件')
    return
  }
  uploading.value = true
  try {
    const result = await uploadUgcDocument(pickedFile.value, {
      title: importTitle.value.trim() || undefined,
      product: product.value.trim() || undefined,
      version: version.value.trim() || undefined,
      visibility: visibility.value,
    })
    ElMessage.success(result.message || `上传成功，共 ${result.chunkCount} 个片段`)
    if (result.reviewStatus === 'pending') {
      void router.push('/me')
    } else {
      void router.push(`/kb/doc/${result.docId}`)
    }
  } catch (error) {
    captureScanError(error)
  } finally {
    uploading.value = false
  }
}
</script>

<template>
  <section class="container editor">
    <div class="editor__topbar">
      <button class="editor__back" type="button" @click="router.back()">返回</button>
      <span class="editor__topbar-title">{{ isEdit ? '编辑文档' : '写文档' }}</span>
      <div class="editor__topbar-actions">
        <button
          v-if="!isEdit"
          class="btn btn--outline"
          type="button"
          @click="importOpen = !importOpen"
        >
          导入文件
        </button>
        <button class="btn btn--primary" type="button" :disabled="publishing || scanning" @click="publish">
          {{ publishing ? '提交中...' : isEdit ? '保存并重新提交' : '发布' }}
        </button>
      </div>
    </div>

    <input
      v-model="title"
      class="editor__title-input"
      type="text"
      placeholder="输入文档标题..."
      aria-label="文档标题"
    />

    <div class="editor__meta">
      <span class="editor__meta-label">可见性</span>
      <div class="meta-cards">
        <label class="meta-card">
          <input v-model="visibility" class="meta-card__radio" type="radio" name="visibility" value="team" />
          <span class="meta-card__title">团队可见</span>
          <span class="meta-card__desc">需通过脱敏扫描与管理员审核后才进入知识库</span>
        </label>
        <label class="meta-card">
          <input v-model="visibility" class="meta-card__radio" type="radio" name="visibility" value="private" />
          <span class="meta-card__title">仅自己可见</span>
          <span class="meta-card__desc">免扫描、免审核，不参与问答引用，只有你本人可见</span>
        </label>
      </div>
    </div>

    <div class="editor__meta">
      <span class="editor__meta-label">技术分类</span>
      <div class="meta-inputs">
        <label class="meta-input">
          <span class="meta-input__label">分类（可自定义）</span>
          <input
            v-model="product"
            class="meta-input__field"
            type="text"
            list="editor-product-options"
            placeholder="例如：Spring、MySQL"
          />
          <datalist id="editor-product-options">
            <option v-for="item in productSuggestions" :key="item" :value="item" />
          </datalist>
        </label>
        <label class="meta-input">
          <span class="meta-input__label">版本（可选）</span>
          <input
            v-model="version"
            class="meta-input__field"
            type="text"
            list="editor-version-options"
            placeholder="例如：3.2、8.0"
          />
          <datalist id="editor-version-options">
            <option v-for="item in versionSuggestions" :key="item" :value="item" />
          </datalist>
        </label>
      </div>
    </div>

    <div v-if="importOpen && !isEdit" class="import-panel">
      <div class="import-panel__head">
        <span class="import-panel__title">导入文本文件生成文档</span>
        <span class="import-panel__hint">支持 .txt / .md，单文件不超过 10MB，将按当前分类 / 版本 / 可见性入库</span>
      </div>
      <label
        class="dropzone"
        :class="{ 'dropzone--active': dragging }"
        @dragover.prevent="dragging = true"
        @dragleave.prevent="dragging = false"
        @drop.prevent="onDrop"
      >
        <input
          type="file"
          accept=".txt,.md,text/plain,text/markdown"
          class="dropzone__input"
          @change="onFileChange"
        />
        <span class="dropzone__text">拖拽文件到此处，或点击选择</span>
        <span class="dropzone__sub">.txt 按纯文本入库，.md 保留 Markdown 结构</span>
      </label>

      <div v-if="pickedFile" class="import-panel__file">
        <span class="import-panel__file-name">{{ pickedFile.name }}</span>
        <span class="import-panel__file-size num">{{ formatSize(pickedFile.size) }}</span>
        <button class="import-panel__remove" type="button" @click="pickedFile = null">移除</button>
      </div>

      <div class="import-panel__row">
        <label class="meta-input meta-input--grow">
          <span class="meta-input__label">文档标题</span>
          <input v-model="importTitle" class="meta-input__field" type="text" placeholder="缺省使用文件名" />
        </label>
        <button
          class="btn btn--primary"
          type="button"
          :disabled="uploading || !pickedFile"
          @click="uploadFile"
        >
          {{ uploading ? '上传中...' : '上传并生成文档' }}
        </button>
      </div>
    </div>

    <div class="secret-bar" :class="{ 'secret-bar--blocked': scanState === 'blocked' }" role="status">
      <span class="secret-bar__dot" aria-hidden="true"></span>
      <span v-if="scanState === 'private'" class="secret-bar__text">
        私有文档：跳过脱敏扫描与管理员审核，发布后仅你自己可见。
      </span>
      <span v-else-if="scanState === 'scanning'" class="secret-bar__text">
        正在扫描敏感信息，请稍候…
      </span>
      <span v-else-if="scanState === 'blocked'" class="secret-bar__text">
        检测到敏感信息，已拦截发布。请按下方清单修改正文后重新提交。
      </span>
      <span v-else-if="scanState === 'clean'" class="secret-bar__text">
        脱敏扫描通过，未发现密钥、凭据或证件号等敏感信息。
      </span>
      <span v-else class="secret-bar__text">
        密钥扫描：团队文档发布前会检查密钥、凭据、证件号等敏感信息，命中将拦截发布。
      </span>
    </div>

    <div v-if="scanResult?.blocked" class="scan-findings">
      <p class="scan-findings__title">
        命中 {{ scanResult.findings.length }} 类敏感信息（样例已打码）
      </p>
      <ul class="scan-findings__list">
        <li v-for="finding in scanResult.findings" :key="finding.type" class="scan-findings__item">
          <span class="scan-findings__label">{{ finding.label }}</span>
          <span class="scan-findings__count num">{{ finding.count }} 处</span>
          <span class="scan-findings__samples">{{ finding.samples.join('、') }}</span>
        </li>
      </ul>
    </div>

    <div class="editor__split">
      <div class="editor__pane">
        <div class="editor__pane-head">Markdown</div>
        <textarea
          v-model="markdown"
          class="editor__textarea"
          placeholder="在这里写 Markdown —— 标题、段落、代码块..."
          aria-label="Markdown 正文"
        ></textarea>
      </div>
      <div class="editor__pane">
        <div class="editor__pane-head">实时预览</div>
        <div v-if="previewHtml" class="editor__preview md-body" v-html="previewHtml" />
        <div v-else class="editor__preview">写下的内容会在这里实时排版。</div>
      </div>
    </div>
  </section>
</template>

<style scoped>
.editor {
  padding-top: var(--sp-4);
  padding-bottom: var(--sp-16);
}

.editor__topbar {
  display: flex;
  align-items: center;
  gap: var(--sp-4);
  padding: var(--sp-6) 0 var(--sp-5);
  border-bottom: 1px solid var(--hairline);
}

.editor__back {
  color: var(--ink-500);
  font-size: var(--fs-md);
  flex-shrink: 0;
  background: none;
  border: none;
  cursor: pointer;
  padding: 0;
}

.editor__back:hover {
  color: var(--brand-600);
}

.editor__topbar-title {
  font-size: var(--fs-lg);
  font-weight: 600;
  color: var(--ink-900);
}

.editor__topbar-actions {
  display: flex;
  align-items: center;
  gap: var(--sp-3);
  margin-left: auto;
  min-width: 0;
}

.editor__note {
  font-size: var(--fs-sm);
  color: var(--ink-500);
}

.editor__title-input {
  width: 100%;
  border: none;
  outline: none;
  background: transparent;
  font-size: var(--fs-4xl);
  font-weight: 600;
  color: var(--ink-900);
  padding: var(--sp-5) 0 var(--sp-4);
  letter-spacing: -0.01em;
}

.editor__title-input::placeholder {
  color: var(--ink-300);
}

.editor__title-input:focus {
  border-radius: var(--r-md);
  box-shadow: var(--ring);
}

/* 元信息：radio 卡（白面 + hairline，选中仅极淡陶土底 + 陶土描边） */
.editor__meta {
  display: flex;
  align-items: flex-start;
  gap: var(--sp-4);
  margin-bottom: var(--sp-5);
}

.editor__meta-label {
  font-size: var(--fs-sm);
  font-weight: 600;
  color: var(--ink-600);
  padding-top: var(--sp-3);
  flex-shrink: 0;
}

.meta-cards {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: var(--sp-3);
  max-width: 560px;
}

.meta-card {
  position: relative;
  display: flex;
  flex-direction: column;
  gap: 2px;
  padding: var(--sp-3) var(--sp-4);
  border: 1px solid var(--hairline);
  border-radius: var(--r-lg);
  background: var(--surface);
  cursor: pointer;
  transition:
    border-color var(--dur-fast) var(--ease-out),
    background-color var(--dur-fast) var(--ease-out);
}

.meta-card:hover {
  border-color: var(--ink-400);
}

.meta-card:has(.meta-card__radio:checked) {
  border-color: var(--brand-600);
  background: var(--brand-50);
}

.meta-card:has(.meta-card__radio:focus-visible) {
  box-shadow: var(--ring);
}

.meta-card__radio {
  position: absolute;
  opacity: 0;
  pointer-events: none;
}

.meta-card__title {
  font-size: var(--fs-md);
  font-weight: 600;
  color: var(--ink-900);
}

.meta-card__desc {
  font-size: var(--fs-xs);
  color: var(--ink-500);
  line-height: 1.6;
}

/* 技术分类 / 版本：可自由填写的输入框 + 建议列表 */
.meta-inputs {
  display: flex;
  gap: var(--sp-3);
  width: 100%;
  max-width: 560px;
}

.meta-input {
  display: flex;
  flex-direction: column;
  gap: var(--sp-1);
  flex: 1;
  min-width: 0;
}

.meta-input--grow {
  flex: 1;
}

.meta-input__label {
  font-size: var(--fs-xs);
  color: var(--ink-500);
}

.meta-input__field {
  width: 100%;
  border: 1px solid var(--hairline);
  border-radius: var(--r-md);
  padding: var(--sp-2) var(--sp-3);
  font-size: var(--fs-md);
  color: var(--ink-900);
  background: var(--surface);
  outline: none;
  transition: box-shadow var(--dur-fast) var(--ease-out);
}

.meta-input__field::placeholder {
  color: var(--ink-400);
}

.meta-input__field:focus {
  border-color: transparent;
  box-shadow: var(--ring);
}

/* 文件导入面板：白面 + hairline，虚线拖拽区 */
.import-panel {
  border: 1px solid var(--hairline);
  border-radius: var(--r-lg);
  background: var(--surface);
  padding: var(--sp-4);
  margin-bottom: var(--sp-5);
}

.import-panel__head {
  display: flex;
  flex-direction: column;
  gap: 2px;
  margin-bottom: var(--sp-3);
}

.import-panel__title {
  font-size: var(--fs-md);
  font-weight: 600;
  color: var(--ink-900);
}

.import-panel__hint {
  font-size: var(--fs-xs);
  color: var(--ink-500);
  line-height: 1.6;
}

.dropzone {
  position: relative;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: var(--sp-1);
  border: 1px dashed var(--hairline-strong);
  border-radius: var(--r-md);
  padding: var(--sp-6) var(--sp-4);
  cursor: pointer;
  text-align: center;
  transition:
    border-color var(--dur-fast) var(--ease-out),
    background-color var(--dur-fast) var(--ease-out);
}

.dropzone:hover,
.dropzone--active {
  border-color: var(--brand-600);
  background: var(--brand-50);
}

.dropzone__input {
  position: absolute;
  inset: 0;
  opacity: 0;
  cursor: pointer;
}

.dropzone__text {
  font-size: var(--fs-md);
  font-weight: 600;
  color: var(--ink-700);
}

.dropzone__sub {
  font-size: var(--fs-xs);
  color: var(--ink-500);
}

.import-panel__file {
  display: flex;
  align-items: center;
  gap: var(--sp-3);
  margin-top: var(--sp-3);
  padding: var(--sp-2) var(--sp-3);
  background: var(--surface-sunken);
  border-radius: var(--r-md);
  font-size: var(--fs-sm);
}

.import-panel__file-name {
  flex: 1;
  min-width: 0;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  color: var(--ink-900);
  font-weight: 600;
}

.import-panel__file-size {
  flex-shrink: 0;
  color: var(--ink-500);
}

.import-panel__remove {
  flex-shrink: 0;
  background: none;
  border: none;
  padding: 0;
  font-size: var(--fs-xs);
  color: var(--ink-500);
  cursor: pointer;
}

.import-panel__remove:hover {
  color: var(--brand-700);
}

.import-panel__row {
  display: flex;
  align-items: flex-end;
  gap: var(--sp-3);
  margin-top: var(--sp-3);
}

/* 密钥扫描提示条：克制的 --warning 文字 + 极淡底 */
.secret-bar {
  display: flex;
  align-items: center;
  gap: var(--sp-3);
  padding: var(--sp-3) var(--sp-4);
  background: var(--brand-50);
  border: 1px solid var(--hairline);
  border-radius: var(--r-md);
  margin-bottom: var(--sp-5);
}

.secret-bar__dot {
  width: 6px;
  height: 6px;
  border-radius: 50%;
  background: var(--warning);
  flex-shrink: 0;
}

.secret-bar__text {
  font-size: var(--fs-sm);
  color: var(--warning);
  line-height: 1.7;
}

.secret-bar--blocked {
  border-color: var(--warning);
}

/* 命中明细：白面 + hairline，样例打码后以等宽字体展示 */
.scan-findings {
  margin: calc(-1 * var(--sp-3)) 0 var(--sp-5);
  padding: var(--sp-3) var(--sp-4);
  background: var(--surface);
  border: 1px solid var(--warning);
  border-radius: var(--r-md);
}

.scan-findings__title {
  margin: 0 0 var(--sp-2);
  font-size: var(--fs-sm);
  font-weight: 600;
  color: var(--ink-900);
}

.scan-findings__list {
  margin: 0;
  padding: 0;
  list-style: none;
  display: flex;
  flex-direction: column;
  gap: var(--sp-1);
}

.scan-findings__item {
  display: flex;
  align-items: baseline;
  gap: var(--sp-3);
  font-size: var(--fs-sm);
}

.scan-findings__label {
  flex-shrink: 0;
  font-weight: 600;
  color: var(--ink-700);
}

.scan-findings__count {
  flex-shrink: 0;
  color: var(--warning);
}

.scan-findings__samples {
  min-width: 0;
  color: var(--ink-500);
  font-family: var(--font-num);
  overflow-wrap: anywhere;
}

/* 左右分栏：单一容器 + 1px hairline 竖分隔（非双卡片） */
.editor__split {
  display: grid;
  grid-template-columns: 1fr 1fr;
  align-items: stretch;
  background: var(--surface);
  border: 1px solid var(--hairline);
  border-radius: var(--r-lg);
  overflow: hidden;
}

.editor__pane {
  display: flex;
  flex-direction: column;
  min-width: 0;
}

.editor__pane + .editor__pane {
  border-left: 1px solid var(--hairline);
}

.editor__pane-head {
  padding: var(--sp-2) var(--sp-4);
  font-size: var(--fs-xs);
  font-weight: 600;
  letter-spacing: 0.05em;
  color: var(--ink-500);
  background: var(--surface-sunken);
  border-bottom: 1px solid var(--hairline);
  text-transform: uppercase;
}

.editor__textarea {
  flex: 1;
  min-height: 360px;
  width: 100%;
  border: none;
  outline: none;
  resize: vertical;
  background: var(--surface);
  padding: var(--sp-4);
  font-size: var(--fs-md);
  line-height: 1.7;
  color: var(--ink-700);
}

.editor__textarea::placeholder {
  color: var(--ink-400);
}

.editor__textarea:focus {
  box-shadow: inset var(--ring);
}

.editor__preview {
  flex: 1;
  min-height: 360px;
  padding: var(--sp-4);
  font-size: var(--fs-sm);
  line-height: 1.7;
  color: var(--ink-400);
  overflow-y: auto;
}

.editor__preview.md-body {
  color: var(--ink-700);
  font-size: var(--fs-md);
}

.btn[disabled] {
  opacity: 0.55;
  cursor: not-allowed;
}

@media (max-width: 900px) {
  .editor__split {
    grid-template-columns: 1fr;
  }

  .editor__pane + .editor__pane {
    border-left: none;
    border-top: 1px solid var(--hairline);
  }

  .editor__note {
    display: none;
  }

  .editor__meta {
    flex-direction: column;
    gap: var(--sp-2);
  }

  .editor__meta-label {
    padding-top: 0;
  }

  .meta-cards {
    grid-template-columns: 1fr;
    width: 100%;
  }

  .meta-inputs {
    flex-direction: column;
  }

  .import-panel__row {
    flex-direction: column;
    align-items: stretch;
  }
}
</style>

<!-- 预览区 Markdown 排版（非 scoped：v-html 插入的 HTML 不被 scoped 命中） -->
<style>
.editor .md-body h1,
.editor .md-body h2,
.editor .md-body h3,
.editor .md-body h4 {
  font-weight: 600;
  color: var(--ink-900);
  margin: 16px 0 8px;
}

.editor .md-body h1 {
  font-size: var(--fs-2xl);
}

.editor .md-body h2 {
  font-size: var(--fs-xl);
}

.editor .md-body h3,
.editor .md-body h4 {
  font-size: var(--fs-lg);
}

.editor .md-body p {
  margin: 8px 0;
}

.editor .md-body ul,
.editor .md-body ol {
  margin: 8px 0;
  padding-left: 20px;
}

.editor .md-body li {
  margin: 4px 0;
}

.editor .md-body code {
  font-family: var(--font-num);
  font-size: var(--fs-sm);
  padding: 2px 6px;
  border-radius: 4px;
  background: var(--ink-100);
  color: var(--ink-800);
}

.editor .md-body pre {
  margin: 10px 0;
  padding: 12px 14px;
  border-radius: var(--r-md);
  background: var(--ink-900);
  overflow-x: auto;
}

.editor .md-body pre code {
  padding: 0;
  background: transparent;
  color: var(--ink-100);
}

.editor .md-body blockquote {
  margin: 10px 0;
  padding: 8px 14px;
  border-left: 2px solid var(--hairline-strong);
  background: var(--surface-sunken);
  border-radius: 0 var(--r-sm) var(--r-sm) 0;
  color: var(--ink-600);
}

.editor .md-body a {
  color: var(--brand-600);
  text-decoration: underline;
  text-underline-offset: 2px;
}
</style>