<script setup lang="ts">
// 管理后台 · 脱敏规则：内置规则开关 + 自定义规则维护 + 拦截留痕（不含明文）。
import { onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import {
  createScanRule,
  deleteScanRule,
  listScanLogs,
  listScanRules,
  updateScanRule,
} from '@/api/document'
import type { ScanLogItem, ScanRule } from '@/types/document'

const rules = ref<ScanRule[]>([])
const rulesLoading = ref(false)

/** 留痕动作 → 中文说明（后端只存英文标识） */
const ACTION_LABELS: Record<string, string> = {
  publish: '发布文档',
  upload: '上传文件',
  update: '编辑重提',
  visibility: '切换可见性',
  precheck: '发布前预检',
  admin_meta: '管理端改元数据',
}

function actionLabel(action: string) {
  return ACTION_LABELS[action] ?? action
}

async function loadRules() {
  rulesLoading.value = true
  try {
    rules.value = await listScanRules()
  } catch {
    rules.value = []
  } finally {
    rulesLoading.value = false
  }
}

const togglingCode = ref('')

async function onToggle(rule: ScanRule) {
  togglingCode.value = rule.code
  try {
    const updated = await updateScanRule(rule.code, { enabled: rule.enabled })
    const idx = rules.value.findIndex((r) => r.code === updated.code)
    if (idx >= 0) rules.value[idx] = updated
    ElMessage.success(`${rule.enabled ? '已启用' : '已关闭'}：${updated.label}`)
  } catch {
    // 失败时回滚开关，避免界面与后端不一致
    rule.enabled = !rule.enabled
  } finally {
    togglingCode.value = ''
  }
}

// ---------- 新增自定义规则 ----------
const dialogVisible = ref(false)
const saving = ref(false)
const ruleForm = reactive({ label: '', pattern: '' })

function openCreate() {
  ruleForm.label = ''
  ruleForm.pattern = ''
  dialogVisible.value = true
}

async function saveRule() {
  const label = ruleForm.label.trim()
  const pattern = ruleForm.pattern.trim()
  if (!label) {
    ElMessage.warning('请填写规则名称')
    return
  }
  if (!pattern) {
    ElMessage.warning('请填写正则表达式')
    return
  }
  saving.value = true
  try {
    await createScanRule({ label, pattern })
    dialogVisible.value = false
    ElMessage.success('规则已新增并立即生效')
    await loadRules()
  } catch {
    /* 拦截器已提示（含正则非法） */
  } finally {
    saving.value = false
  }
}

async function onDelete(rule: ScanRule) {
  try {
    await ElMessageBox.confirm(
      `删除规则「${rule.label}」？删除后不再参与扫描，历史留痕仍保留。`,
      '删除规则',
      { type: 'warning', confirmButtonText: '删除', cancelButtonText: '取消' },
    )
    await deleteScanRule(rule.code)
    ElMessage.success('规则已删除')
    await loadRules()
  } catch {
    /* 取消或拦截器已提示 */
  }
}

// ---------- 拦截留痕 ----------
const logs = ref<ScanLogItem[]>([])
const logsLoading = ref(false)
const logLimit = ref(100)

async function loadLogs() {
  logsLoading.value = true
  try {
    logs.value = await listScanLogs(logLimit.value)
  } catch {
    logs.value = []
  } finally {
    logsLoading.value = false
  }
}

onMounted(() => {
  void loadRules()
  void loadLogs()
})
</script>

<template>
  <div>
    <div class="page-head">
      <h2 class="page-head__title">脱敏规则</h2>
      <p class="page-head__sub">
        团队文档发布前按启用规则扫描；内置规则可关闭，自定义规则可新增（如工号、内网 IP、内部域名）。
      </p>
    </div>

    <div class="card filter-bar">
      <el-button type="primary" @click="openCreate">新增自定义规则</el-button>
      <el-button @click="loadRules">刷新</el-button>
      <div class="spacer" />
      <span class="hint">关闭某条规则后，扫描将不再命中该类型</span>
    </div>

    <div class="card table-card">
      <el-table v-loading="rulesLoading" :data="rules" style="width: 100%">
        <el-table-column prop="label" label="规则名称" min-width="180" />
        <el-table-column label="类型" width="100">
          <template #default="{ row }">
            <span class="badge" :class="row.builtin ? 'badge--neutral' : 'badge--custom'">
              {{ row.builtin ? '内置' : '自定义' }}
            </span>
          </template>
        </el-table-column>
        <el-table-column prop="code" label="标识" width="150" show-overflow-tooltip />
        <el-table-column label="正则" min-width="260" show-overflow-tooltip>
          <template #default="{ row }">
            <code class="pattern">{{ row.pattern }}</code>
          </template>
        </el-table-column>
        <el-table-column label="启用" width="90" align="center">
          <template #default="{ row }">
            <el-switch
              v-model="row.enabled"
              :loading="togglingCode === row.code"
              @change="onToggle(row)"
            />
          </template>
        </el-table-column>
        <el-table-column label="操作" width="100" fixed="right">
          <template #default="{ row }">
            <el-button v-if="!row.builtin" text type="danger" @click="onDelete(row)">
              删除
            </el-button>
            <span v-else class="hint">仅可关闭</span>
          </template>
        </el-table-column>
      </el-table>

      <div v-if="!rulesLoading && !rules.length" class="empty-state">暂无脱敏规则</div>
    </div>

    <div class="section-head">
      <h3 class="section-head__title">拦截留痕</h3>
      <p class="section-head__sub">
        只记录规则类型、命中数量、操作者与时间，不记录命中明文，用于证明扫描确实生效。
      </p>
    </div>

    <div class="card filter-bar">
      <el-select v-model="logLimit" class="w-120" @change="loadLogs">
        <el-option label="最近 50 条" :value="50" />
        <el-option label="最近 100 条" :value="100" />
        <el-option label="最近 200 条" :value="200" />
        <el-option label="最近 500 条" :value="500" />
      </el-select>
      <el-button @click="loadLogs">刷新</el-button>
    </div>

    <div class="card table-card">
      <el-table v-loading="logsLoading" :data="logs" style="width: 100%">
        <el-table-column label="时间" width="180">
          <template #default="{ row }">
            <span class="num">{{ row.createdAt }}</span>
          </template>
        </el-table-column>
        <el-table-column label="操作者" width="130">
          <template #default="{ row }">{{ row.username || '—' }}</template>
        </el-table-column>
        <el-table-column label="动作" width="150">
          <template #default="{ row }">{{ actionLabel(row.action) }}</template>
        </el-table-column>
        <el-table-column label="文档" min-width="220" show-overflow-tooltip>
          <template #default="{ row }">{{ row.docTitle || '—' }}</template>
        </el-table-column>
        <el-table-column label="规则类型" width="150">
          <template #default="{ row }">
            <span class="badge badge--neutral">{{ row.ruleType }}</span>
          </template>
        </el-table-column>
        <el-table-column prop="hitCount" label="命中数量" width="100" align="center" />
      </el-table>

      <div v-if="!logsLoading && !logs.length" class="empty-state">
        暂无拦截记录，说明近期没有扫描命中
      </div>
    </div>

    <el-dialog v-model="dialogVisible" title="新增自定义脱敏规则" width="520px">
      <el-form label-width="88px">
        <el-form-item label="规则名称">
          <el-input v-model="ruleForm.label" placeholder="例如：工号 / 内网 IP / 内部域名" />
        </el-form-item>
        <el-form-item label="正则表达式">
          <el-input
            v-model="ruleForm.pattern"
            type="textarea"
            :rows="3"
            placeholder="例如：EMP\d{6}"
          />
          <p class="form-hint">
            保存前会校验正则是否可编译；规则立即生效，建议先在「发布前预检」验证。
          </p>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="saving" @click="saveRule">保存</el-button>
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

.section-head {
  margin-top: var(--sp-8);
}

.section-head__title {
  margin: 0;
  font-size: var(--fs-lg);
  font-weight: 600;
  color: var(--ink-900);
}

.section-head__sub {
  margin: var(--sp-1) 0 var(--sp-3);
  font-size: var(--fs-sm);
  color: var(--ink-500);
}

.w-120 {
  width: 120px;
}

.hint {
  font-size: var(--fs-xs);
  color: var(--ink-400);
}

.pattern {
  font-size: 12px;
  color: var(--ink-500);
  word-break: break-all;
}

.badge--neutral {
  background: var(--ink-100);
  color: var(--ink-600);
}

.badge--custom {
  background: var(--brand-50);
  color: var(--brand-700);
}

.form-hint {
  margin: var(--sp-1) 0 0;
  font-size: var(--fs-xs);
  color: var(--ink-400);
  line-height: 1.6;
}

.empty-state {
  padding: var(--sp-10) 0;
  text-align: center;
  font-size: var(--fs-sm);
  color: var(--ink-400);
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
</style>
