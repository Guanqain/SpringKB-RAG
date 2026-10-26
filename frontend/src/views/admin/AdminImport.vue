<script setup lang="ts">
// 管理后台 · 数据导入：由原 ImportDocument 迁移，接口与校验逻辑保持不变。
import { reactive, ref } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage, type FormInstance, type FormRules } from 'element-plus'
import { importDocument } from '@/api/document'
import type { ImportRequest } from '@/types/document'

const router = useRouter()
const formRef = ref<FormInstance>()

const form = reactive<ImportRequest>({
  source: 'official',
  product: '',
  version: '3.3',
  title: '',
  docKey: '',
  text: '',
  url: '',
  soQuestionId: '',
  soScore: '',
})

const submitting = ref(false)

const rules: FormRules = {
  title: [{ required: true, message: '请输入文档标题', trigger: 'blur' }],
  docKey: [
    {
      validator: (_rule, value: string, callback) => {
        if (!value || !value.trim()) {
          callback(new Error('请填写业务唯一键（官方=章节 URL，社区=SO 问题 ID）'))
        } else {
          callback()
        }
      },
      trigger: 'blur',
    },
  ],
  text: [
    {
      validator: (_rule, value: string, callback) => {
        const hasUrl = Boolean(form.url && form.url.trim())
        const hasText = Boolean(value && value.trim())
        if (form.source === 'official' && hasUrl) {
          callback()
          return
        }
        if (hasText) {
          callback()
          return
        }
        callback(new Error('请输入文档正文（official 也可通过 URL 抓取）'))
      },
      trigger: 'blur',
    },
  ],
  url: [
    {
      validator: (_rule, value: string, callback) => {
        if (value && value.trim() && !/^https?:\/\//.test(value.trim())) {
          callback(new Error('URL 需以 http:// 或 https:// 开头'))
        } else {
          callback()
        }
      },
      trigger: 'blur',
    },
  ],
}

async function submit() {
  const valid = await formRef.value?.validate().catch(() => false)
  if (!valid) return

  submitting.value = true
  try {
    const result = await importDocument(form)
    if (result.skipped) {
      ElMessage.info(`文档已存在（幂等跳过）：${result.docId}`)
    } else {
      ElMessage.success(`导入成功：${result.docId}（${result.imported ? '新增' : '已存在'}）`)
    }
    router.push('/admin/documents')
  } catch {
    /* 拦截器已提示 */
  } finally {
    submitting.value = false
  }
}

function resetForm() {
  formRef.value?.resetFields()
}
</script>

<template>
  <div>
    <div class="page-head">
      <h2 class="page-head__title">数据导入</h2>
      <p class="page-head__sub">导入官方文档与知识片段</p>
    </div>

    <div class="import-wrap">
      <div class="card import-card">
        <el-form
          ref="formRef"
          :model="form"
          :rules="rules"
          label-width="110px"
          label-position="left"
        >
          <h3 class="section-title">基础信息</h3>
          <el-form-item label="来源" prop="source">
            <el-radio-group v-model="form.source">
              <el-radio-button value="official">官方文档</el-radio-button>
              <el-radio-button value="community">社区问答</el-radio-button>
            </el-radio-group>
          </el-form-item>
          <el-form-item label="技术分类" prop="product">
            <el-input
              v-model="form.product"
              placeholder="可自定义，如 Spring / Vue / MySQL；留空归入未分类"
            />
          </el-form-item>
          <el-form-item label="版本" prop="version">
            <el-input
              v-model="form.version"
              placeholder="如 3.3 / 3.x / 4.2-SNAPSHOT / latest"
            />
          </el-form-item>
          <el-form-item label="标题" prop="title">
            <el-input v-model="form.title" placeholder="文档标题" />
          </el-form-item>
          <el-form-item label="业务键" prop="docKey">
            <el-input
              v-model="form.docKey"
              :placeholder="
                form.source === 'official'
                  ? '章节 URL，如 https://docs.spring.io/.../web.html'
                  : 'SO 问题 ID，如 123456'
              "
            />
          </el-form-item>

          <h3 class="section-title">内容来源</h3>
          <template v-if="form.source === 'official'">
            <el-form-item label="抓取 URL" prop="url">
              <el-input v-model="form.url" placeholder="可选：官方文档章节 URL（由后端 Jsoup 抓取）" />
            </el-form-item>
            <el-form-item label="正文" prop="text">
              <el-input
                v-model="form.text"
                type="textarea"
                :rows="8"
                placeholder="直接粘贴纯文本正文（未填 URL 时必填）"
              />
            </el-form-item>
          </template>
          <template v-else>
            <el-form-item label="SO 问题 ID" prop="soQuestionId">
              <el-input v-model="form.soQuestionId" placeholder="Stack Overflow 问题 ID" />
            </el-form-item>
            <el-form-item label="SO 评分" prop="soScore">
              <el-input v-model="form.soScore" placeholder="答案评分（≥5）" />
            </el-form-item>
            <el-form-item label="正文" prop="text">
              <el-input
                v-model="form.text"
                type="textarea"
                :rows="8"
                placeholder="粘贴清洗后的问答正文"
              />
            </el-form-item>
          </template>

          <div class="form-actions">
            <el-button type="primary" :loading="submitting" @click="submit">
              提交导入
            </el-button>
            <el-button @click="resetForm">重置</el-button>
            <el-button @click="router.push('/admin/documents')">返回列表</el-button>
            <span class="form-actions__note">导入成功后自动跳转文档管理列表</span>
          </div>
        </el-form>
      </div>
    </div>
  </div>
</template>

<style scoped>
.import-wrap {
  display: flex;
}

.import-card {
  width: 640px;
  max-width: 100%;
}

.section-title {
  font-size: var(--fs-sm);
  font-weight: 600;
  letter-spacing: 0.02em;
  color: var(--ink-900);
  margin: var(--sp-2) 0 var(--sp-4);
  padding-bottom: var(--sp-2);
  border-bottom: 1px solid var(--hairline);
}

.form-actions {
  display: flex;
  align-items: center;
  gap: var(--sp-2);
  flex-wrap: wrap;
}

.form-actions__note {
  margin-left: var(--sp-2);
  font-size: var(--fs-xs);
  color: var(--ink-500);
}

/* 输入类控件 focus：1px 陶土内描边（与 theme.css 一致，就近声明避免被局部样式覆盖） */
.import-card :deep(.el-input__wrapper.is-focus),
.import-card :deep(.el-textarea__inner:focus) {
  box-shadow: var(--ring);
}

/* 分组单选按钮：选中即深墨实底，无彩色强调块 */
.import-card :deep(.el-radio-button__inner) {
  border-color: var(--hairline);
  box-shadow: none;
}
</style>
