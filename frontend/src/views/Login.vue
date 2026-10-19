<script setup lang="ts">
import { reactive, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage, type FormInstance, type FormRules } from 'element-plus'
import { Lock, User } from '@element-plus/icons-vue'
import { useAuthStore } from '@/stores/auth'

const route = useRoute()
const router = useRouter()
const auth = useAuthStore()

const activeTab = ref<'login' | 'register'>('login')
const formRef = ref<FormInstance>()
const form = reactive({
  username: '',
  password: '',
  confirmPassword: '',
})
const loading = ref(false)

const rules: FormRules = {
  username: [
    { required: true, message: '请输入用户名', trigger: 'blur' },
    { min: 3, max: 32, message: '用户名长度需在 3-32 个字符之间', trigger: 'blur' },
  ],
  password: [
    { required: true, message: '请输入密码', trigger: 'blur' },
    { min: 6, message: '密码长度至少 6 位', trigger: 'blur' },
  ],
  confirmPassword: [
    {
      validator: (_rule, value: string, callback) => {
        if (activeTab.value === 'register' && value !== form.password) {
          callback(new Error('两次输入的密码不一致'))
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

  loading.value = true
  try {
    if (activeTab.value === 'login') {
      await auth.login(form.username, form.password)
      ElMessage.success('登录成功')
    } else {
      await auth.register(form.username, form.password)
      ElMessage.success('注册成功，已自动登录')
    }
    const redirect = (route.query.redirect as string) || '/'
    router.push(redirect)
  } catch {
    /* 拦截器已提示错误 */
  } finally {
    loading.value = false
  }
}

function switchTab(tab: 'login' | 'register') {
  activeTab.value = tab
  formRef.value?.clearValidate()
}
</script>

<template>
  <div class="login-page">
    <div class="login-card">
      <div class="login-brand">
        <div class="brand-mark">KB</div>
        <div>
          <h1>SpringKB-RAG</h1>
          <p>知识库管理后台 · 登录</p>
        </div>
      </div>

      <el-tabs v-model="activeTab" class="login-tabs" @tab-change="switchTab">
        <el-tab-pane label="登录" name="login" />
        <el-tab-pane label="注册" name="register" />
      </el-tabs>

      <el-form
        ref="formRef"
        :model="form"
        :rules="rules"
        size="large"
        label-position="top"
        @submit.prevent="submit"
      >
        <el-form-item prop="username">
          <el-input
            v-model="form.username"
            placeholder="用户名"
            :prefix-icon="User"
            autocomplete="username"
          />
        </el-form-item>
        <el-form-item prop="password">
          <el-input
            v-model="form.password"
            type="password"
            placeholder="密码"
            :prefix-icon="Lock"
            show-password
            autocomplete="current-password"
          />
        </el-form-item>
        <el-form-item v-if="activeTab === 'register'" prop="confirmPassword">
          <el-input
            v-model="form.confirmPassword"
            type="password"
            placeholder="确认密码"
            :prefix-icon="Lock"
            show-password
            autocomplete="new-password"
          />
        </el-form-item>
        <el-button
          type="primary"
          size="large"
          class="login-btn"
          :loading="loading"
          native-type="submit"
        >
          {{ activeTab === 'login' ? '登 录' : '注册并登录' }}
        </el-button>
      </el-form>

      <p class="login-footnote">密码经 BCrypt 加密存储 · JWT 登录态 24 小时有效</p>
    </div>
  </div>
</template>

<style scoped>
.login-page {
  flex: 1;
  height: 100vh;
  display: flex;
  align-items: center;
  justify-content: center;
  background:
    radial-gradient(900px 480px at 15% 20%, rgba(20, 184, 166, 0.18), transparent 60%),
    radial-gradient(800px 420px at 85% 80%, rgba(13, 148, 136, 0.14), transparent 55%),
    #12202b;
}

.login-card {
  width: 380px;
  background: var(--kb-surface);
  border-radius: 14px;
  padding: 32px 34px 24px;
  box-shadow: 0 24px 60px rgba(0, 0, 0, 0.35);
  animation: login-in 0.35s ease;
}

@keyframes login-in {
  from {
    opacity: 0;
    transform: translateY(10px) scale(0.98);
  }
  to {
    opacity: 1;
    transform: none;
  }
}

.login-brand {
  display: flex;
  align-items: center;
  gap: 12px;
  margin-bottom: 8px;
}

.brand-mark {
  width: 42px;
  height: 42px;
  border-radius: 11px;
  background: linear-gradient(135deg, #0f766e, #14b8a6);
  display: flex;
  align-items: center;
  justify-content: center;
  font-weight: 700;
  font-size: 14px;
  color: #fff;
  flex-shrink: 0;
}

.login-brand h1 {
  font-size: 19px;
  margin: 0;
  line-height: 1.2;
}

.login-brand p {
  margin: 2px 0 0;
  font-size: 12px;
  color: var(--kb-text-sub);
}

.login-tabs {
  margin-bottom: 6px;
}

.login-btn {
  width: 100%;
  margin-top: 4px;
  font-weight: 600;
  letter-spacing: 2px;
}

.login-footnote {
  margin: 16px 0 0;
  text-align: center;
  font-size: 11.5px;
  color: var(--kb-text-sub);
}
</style>
