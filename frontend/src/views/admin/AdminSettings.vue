<script setup lang="ts">
// 管理后台 · 系统设置：团队名称与匿名访问开关，保存后立即生效。
import { onMounted, reactive, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { fetchAdminSettings, updateAdminSettings } from '@/api/settings'

const loading = ref(true)
const saving = ref(false)
const form = reactive({ teamName: '', allowAnonymous: true })

function apply(data: { teamName: string; allowAnonymous: boolean }) {
  form.teamName = data.teamName
  form.allowAnonymous = data.allowAnonymous
}

async function load() {
  loading.value = true
  try {
    apply(await fetchAdminSettings())
  } catch {
    /* 拦截器已提示错误 */
  } finally {
    loading.value = false
  }
}

async function save() {
  saving.value = true
  try {
    apply(await updateAdminSettings({ teamName: form.teamName.trim(), allowAnonymous: form.allowAnonymous }))
    ElMessage.success('设置已保存，立即生效')
  } catch {
    /* 拦截器已提示错误 */
  } finally {
    saving.value = false
  }
}

onMounted(load)
</script>

<template>
  <div class="settings">
    <div class="page-head">
      <h2 class="page-head__title">系统设置</h2>
      <p class="page-head__sub">团队展示与访问策略，保存后立即生效</p>
    </div>

    <section v-loading="loading" class="panel" aria-label="系统设置">
      <div class="field">
        <label class="field__label" for="team-name">团队名称</label>
        <el-input
          id="team-name"
          v-model="form.teamName"
          maxlength="40"
          show-word-limit
          placeholder="例如：平台架构组"
        />
        <p class="field__hint">展示在首页标题上方；留空时回落为「我的团队」。</p>
      </div>

      <div class="field field--row">
        <div>
          <p class="field__label">允许匿名访问问答</p>
          <p class="field__hint">关闭后未登录用户无法访问问答与知识库接口。</p>
        </div>
        <el-switch v-model="form.allowAnonymous" />
      </div>

      <div class="actions">
        <el-button type="primary" :loading="saving" @click="save">保存设置</el-button>
      </div>
    </section>
  </div>
</template>

<style scoped>
.settings {
  display: flex;
  flex-direction: column;
  gap: var(--sp-4);
  max-width: 720px;
}

.panel {
  background: var(--surface);
  border: 1px solid var(--hairline);
  border-radius: var(--r-lg);
  padding: var(--sp-6);
}

.field + .field {
  margin-top: var(--sp-6);
  padding-top: var(--sp-6);
  border-top: 1px solid var(--hairline);
}

.field--row {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: var(--sp-6);
}

.field__label {
  display: block;
  margin-bottom: var(--sp-2);
  font-size: var(--fs-sm);
  font-weight: 600;
  color: var(--ink-700);
}

.field--row .field__label {
  margin-bottom: var(--sp-1);
}

.field__hint {
  margin: var(--sp-1) 0 0;
  font-size: var(--fs-xs);
  color: var(--ink-500);
  line-height: 1.6;
}

.actions {
  display: flex;
  justify-content: flex-end;
  margin-top: var(--sp-6);
  padding-top: var(--sp-4);
  border-top: 1px solid var(--hairline);
}
</style>
