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
        <div class="brand-mark">
          <svg width="28" height="28" viewBox="0 0 24 24">
            <path d="M4 4h16v4H4V4zm0 8h10v8H4v-8zm12 0h4v8h-4v-8z" fill="#fff" />
          </svg>
        </div>
        <h1>Spring<span>KB</span></h1>
        <p>团队知识库 · 登录以继续</p>
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
  min-height: 100vh;
  display: grid;
  place-items: center;
  padding: var(--sp-8) var(--sp-4);
  background:
    radial-gradient(ellipse 55% 40% at 18% 8%, rgba(251, 146, 60, 0.14) 0%, transparent 60%),
    radial-gradient(ellipse 50% 45% at 85% 15%, rgba(249, 115, 22, 0.12) 0%, transparent 60%),
    var(--page-bg);
}

.login-card {
  width: 400px;
  max-width: 100%;
  background: #fff;
  border: 1px solid var(--ink-200);
  border-radius: var(--r-2xl);
  padding: var(--sp-10);
  box-shadow: var(--shadow-xl);
  animation: login-in 0.35s var(--ease-out);
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
  flex-direction: column;
  align-items: center;
  text-align: center;
  margin-bottom: var(--sp-6);
}

.brand-mark {
  width: 52px;
  height: 52px;
  border-radius: var(--r-lg);
  background: var(--grad-brand);
  display: grid;
  place-items: center;
  box-shadow: var(--shadow-md);
  margin-bottom: var(--sp-4);
}

.login-brand h1 {
  font-size: var(--fs-3xl);
  font-weight: 800;
  color: var(--ink-900);
  margin: 0;
}

.login-brand h1 span {
  color: var(--brand-500);
}

.login-brand p {
  margin: var(--sp-1) 0 0;
  font-size: var(--fs-md);
  color: var(--ink-500);
}

.login-tabs {
  margin-bottom: var(--sp-4);
}

.login-tabs :deep(.el-tabs__nav-wrap)::after {
  background: var(--ink-100);
}

.login-tabs :deep(.el-tabs__nav) {
  width: 100%;
  display: flex;
}

.login-tabs :deep(.el-tabs__item) {
  flex: 1;
  justify-content: center;
  font-size: var(--fs-md);
  font-weight: 600;
  color: var(--ink-500);
}

.login-tabs :deep(.el-tabs__item.is-active) {
  color: var(--brand-600);
}

.login-tabs :deep(.el-tabs__active-bar) {
  height: 3px;
  border-radius: 3px;
  background: var(--grad-brand);
}

.login-card :deep(.el-input__wrapper) {
  border-radius: var(--r-md);
  box-shadow: 0 0 0 1px var(--ink-200) inset;
}

.login-card :deep(.el-input__wrapper.is-focus) {
  box-shadow: 0 0 0 1px var(--brand-400) inset, 0 0 0 3px rgba(249, 115, 22, 0.12);
}

.el-button.login-btn {
  width: 100%;
  height: 44px;
  margin-top: var(--sp-2);
  border: none;
  background: var(--grad-brand);
  font-weight: 600;
  letter-spacing: 2px;
  box-shadow: 0 2px 8px rgba(249, 115, 22, 0.25);
}

.el-button.login-btn:hover,
.el-button.login-btn:focus {
  background: var(--grad-brand);
  transform: translateY(-1px);
  box-shadow: 0 4px 12px rgba(249, 115, 22, 0.35);
}

.login-footnote {
  margin: var(--sp-5) 0 0;
  text-align: center;
  font-size: var(--fs-xs);
  color: var(--ink-400);
}
</style>
