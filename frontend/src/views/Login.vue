<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, reactive, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage, type FormInstance, type FormRules } from 'element-plus'
import { Lock, Message, User } from '@element-plus/icons-vue'
import { useAuthStore } from '@/stores/auth'
import { requestPasswordCode, resetPassword, sendEmailCode } from '@/api/auth'
import { fetchSetupStatus } from '@/api/setup'
import type { EmailCodePurpose } from '@/types/auth'

const route = useRoute()
const router = useRouter()
const auth = useAuthStore()

type Tab = 'login' | 'register'
type LoginMode = 'password' | 'code'

// 登录 / 注册是两个入口；「密码登录 / 验证码登录」只是登录内的方式切换，不拆成独立分类
const activeTab = ref<Tab>('login')
const loginMode = ref<LoginMode>('password')
// 验证码登录发现邮箱未注册：进入「补充信息建号」状态（验证码已校验通过，保留在 form.code）
const needsProfile = ref(false)
const pendingEmail = ref('')

const formRef = ref<FormInstance>()
const loading = ref(false)
// 初始化状态：仅在明确返回 initialized=false 时展示引导，请求失败保持静默
const setupNeeded = ref(false)

const form = reactive({
  identifier: '',
  username: '',
  email: '',
  code: '',
  password: '',
  confirmPassword: '',
})

// 验证码冷却：注册 / 验证码登录 / 绑定邮箱共用，切换 tab 时重置
const cooldown = ref(0)
const devCode = ref('')
let timer: number | undefined

const codePurpose = computed<EmailCodePurpose>(() =>
  activeTab.value === 'register' ? 'register' : 'login',
)
const codeButtonText = computed(() =>
  cooldown.value > 0 ? `${cooldown.value}s 后重发` : '发送验证码',
)
// 验证码登录（已进入补全资料状态时不显示邮箱/验证码行，验证码已校验过）
const codeLogin = computed(
  () => activeTab.value === 'login' && loginMode.value === 'code' && !needsProfile.value,
)
// 密码行：密码登录 / 注册 / 补全资料建号都需要
const passwordVisible = computed(
  () =>
    needsProfile.value ||
    activeTab.value === 'register' ||
    (activeTab.value === 'login' && loginMode.value === 'password'),
)
const submitText = computed(() => {
  if (needsProfile.value) return '创建账户并登录'
  if (activeTab.value === 'register') return '注册并登录'
  return loginMode.value === 'code' ? '验证码登录' : '登 录'
})
// 提交时只校验当前可见字段，避免隐藏字段的旧校验状态阻塞提交
const submitFields = computed(() => {
  if (needsProfile.value) return ['username', 'password']
  if (activeTab.value === 'register') {
    return ['username', 'email', 'code', 'password', 'confirmPassword']
  }
  return loginMode.value === 'code' ? ['email', 'code'] : ['identifier', 'password']
})

const rules = computed<FormRules>(() => ({
  identifier:
    activeTab.value === 'login' && loginMode.value === 'password' && !needsProfile.value
      ? [{ required: true, message: '请输入用户名或邮箱', trigger: 'blur' }]
      : [],
  username:
    activeTab.value === 'register' || needsProfile.value
      ? [
          { required: true, message: '请输入用户名', trigger: 'blur' },
          {
            pattern: /^[A-Za-z0-9_-]{3,32}$/,
            message: '用户名只能包含字母、数字、下划线和连字符，长度 3-32 位',
            trigger: 'blur',
          },
        ]
      : [],
  email:
    activeTab.value === 'register' || codeLogin.value
      ? [
          { required: true, message: '请输入邮箱', trigger: 'blur' },
          { type: 'email', message: '邮箱格式不正确', trigger: 'blur' },
        ]
      : [],
  code:
    activeTab.value === 'register' || codeLogin.value
      ? [
          { required: true, message: '请输入邮箱验证码', trigger: 'blur' },
          { len: 6, message: '验证码为 6 位数字', trigger: 'blur' },
        ]
      : [],
  password: passwordVisible.value
    ? [
        { required: true, message: '请输入密码', trigger: 'blur' },
        { min: 6, message: '密码长度至少 6 位', trigger: 'blur' },
      ]
    : [],
  confirmPassword:
    activeTab.value === 'register'
      ? [
          {
            validator: (_rule, value: string, callback) => {
              if (value !== form.password) {
                callback(new Error('两次输入的密码不一致'))
              } else {
                callback()
              }
            },
            trigger: 'blur',
          },
        ]
      : [],
}))

function stopTimer() {
  if (timer !== undefined) {
    window.clearInterval(timer)
    timer = undefined
  }
}

function startCooldown(seconds: number) {
  stopTimer()
  cooldown.value = seconds
  timer = window.setInterval(() => {
    cooldown.value -= 1
    if (cooldown.value <= 0) stopTimer()
  }, 1000)
}

async function sendCode() {
  const valid = await formRef.value?.validateField('email').catch(() => false)
  if (!valid) return
  try {
    const result = await sendEmailCode(form.email.trim(), codePurpose.value)
    startCooldown(result.cooldown || 60)
    devCode.value = result.devCode ?? ''
    ElMessage.success(
      result.devCode ? `验证码已生成（开发环境：${result.devCode}）` : '验证码已发送，请查收邮箱',
    )
  } catch {
    /* 拦截器已提示错误 */
  }
}

async function submit() {
  const valid = await formRef.value?.validateField(submitFields.value).catch(() => false)
  if (!valid) return

  loading.value = true
  try {
    if (needsProfile.value) {
      // 验证码已校验通过，这里补全用户名 + 密码直接建号并登录
      await auth.registerByEmail(
        pendingEmail.value,
        form.code.trim(),
        form.username.trim(),
        form.password,
      )
      ElMessage.success('账户已创建，已自动登录')
    } else if (activeTab.value === 'register') {
      await auth.register(
        form.username.trim(),
        form.email.trim(),
        form.password,
        form.code.trim(),
      )
      ElMessage.success('注册成功，已自动登录')
    } else if (loginMode.value === 'password') {
      await auth.login(form.identifier.trim(), form.password)
      ElMessage.success('登录成功')
    } else {
      const result = await auth.loginByCode(form.email.trim(), form.code.trim())
      if (result.action === 'needs_profile') {
        // 邮箱验证通过但还没注册：不报错，引导补全资料建号（验证码保留，提交时才会消费）
        pendingEmail.value = result.email
        needsProfile.value = true
        form.email = result.email
        form.code = form.code.trim()
        ElMessage.info('该邮箱还没有账户，补充用户名和密码即可创建并登录')
        return
      }
      ElMessage.success('登录成功')
    }
    const redirect = (route.query.redirect as string) || '/'
    router.push(redirect)
  } catch {
    /* 拦截器已提示错误 */
  } finally {
    loading.value = false
  }
}

function switchTab(tab: string | number) {
  const next = tab as Tab
  if (next === activeTab.value) return
  activeTab.value = next
  needsProfile.value = false
  pendingEmail.value = ''
  devCode.value = ''
  stopTimer()
  cooldown.value = 0
  formRef.value?.clearValidate()
}

/** 密码登录 ↔ 验证码登录：刷新冷却与校验状态 */
function switchLoginMode(mode: string | number | boolean | undefined) {
  const next = mode as LoginMode
  if (next === loginMode.value) return
  loginMode.value = next
  devCode.value = ''
  stopTimer()
  cooldown.value = 0
  formRef.value?.clearValidate()
}

/** 从「补全资料建号」退回验证码登录（保留邮箱与验证码） */
function backToCodeLogin() {
  needsProfile.value = false
  pendingEmail.value = ''
  form.username = ''
  form.password = ''
  formRef.value?.clearValidate()
}

// ---------- 忘记密码：邮箱验证码找回后设置新密码 ----------
const forgotVisible = ref(false)
const forgotLoading = ref(false)
const forgotSubmitting = ref(false)
const forgotCooldown = ref(0)
const forgotDevCode = ref('')
let forgotTimer: number | undefined
const forgotForm = reactive({ email: '', code: '', newPassword: '', confirm: '' })

function stopForgotTimer() {
  if (forgotTimer !== undefined) {
    window.clearInterval(forgotTimer)
    forgotTimer = undefined
  }
}

function startForgotCooldown(seconds: number) {
  stopForgotTimer()
  forgotCooldown.value = seconds
  forgotTimer = window.setInterval(() => {
    forgotCooldown.value -= 1
    if (forgotCooldown.value <= 0) stopForgotTimer()
  }, 1000)
}

function openForgot() {
  forgotForm.email = form.identifier.includes('@') ? form.identifier.trim() : ''
  forgotForm.code = ''
  forgotForm.newPassword = ''
  forgotForm.confirm = ''
  forgotDevCode.value = ''
  forgotCooldown.value = 0
  stopForgotTimer()
  forgotVisible.value = true
}

async function sendForgotCode() {
  const email = forgotForm.email.trim()
  if (!/^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(email)) {
    ElMessage.warning('请填写正确的邮箱地址')
    return
  }
  forgotLoading.value = true
  try {
    const result = await requestPasswordCode(email)
    startForgotCooldown(result.cooldown || 60)
    forgotDevCode.value = result.devCode ?? ''
    ElMessage.success(
      result.devCode ? `验证码已生成（开发环境：${result.devCode}）` : '验证码已发送，请查收邮箱',
    )
  } catch {
    /* 拦截器已提示错误 */
  } finally {
    forgotLoading.value = false
  }
}

async function submitForgot() {
  const email = forgotForm.email.trim()
  if (!email || !forgotForm.code.trim()) {
    ElMessage.warning('请填写邮箱与验证码')
    return
  }
  if (forgotForm.newPassword.length < 6) {
    ElMessage.warning('新密码长度至少 6 位')
    return
  }
  if (forgotForm.newPassword !== forgotForm.confirm) {
    ElMessage.warning('两次输入的新密码不一致')
    return
  }
  forgotSubmitting.value = true
  try {
    await resetPassword(email, forgotForm.code.trim(), forgotForm.newPassword)
    forgotVisible.value = false
    stopForgotTimer()
    activeTab.value = 'login'
    loginMode.value = 'password'
    form.identifier = email
    form.password = ''
    devCode.value = ''
    ElMessage.success('密码已重置，请用新密码登录')
  } catch {
    /* 拦截器已提示错误 */
  } finally {
    forgotSubmitting.value = false
  }
}

onBeforeUnmount(stopTimer)
onBeforeUnmount(stopForgotTimer)

onMounted(async () => {
  try {
    const status = await fetchSetupStatus()
    setupNeeded.value = status.initialized === false
  } catch {
    /* 初始化状态未知：静默，不阻断登录 */
  }
})
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

      <div v-if="setupNeeded" class="setup-hint">
        <p class="setup-hint__text">系统尚未初始化，请先创建管理员账号</p>
        <el-button size="small" @click="router.push('/setup')">去初始化</el-button>
      </div>

      <el-tabs v-model="activeTab" class="login-tabs" @tab-change="switchTab">
        <el-tab-pane label="登录" name="login" />
        <el-tab-pane label="注册" name="register" />
      </el-tabs>

      <!-- 登录方式切换：密码登录 / 验证码登录同属「登录」，不再作为独立分类 -->
      <div v-if="activeTab === 'login' && !needsProfile" class="mode-switch">
        <el-radio-group v-model="loginMode" size="large" @change="switchLoginMode">
          <el-radio-button value="password">密码登录</el-radio-button>
          <el-radio-button value="code">验证码登录</el-radio-button>
        </el-radio-group>
      </div>

      <!-- 验证码通过但邮箱未注册：引导补全资料直接建号，而不是报「未绑定账户」 -->
      <div v-if="needsProfile" class="profile-hint">
        <p class="profile-hint__title">该邮箱还没有账户</p>
        <p class="profile-hint__desc">
          <strong>{{ pendingEmail }}</strong>
          已通过验证码校验，补充用户名和密码即可创建账户并直接登录。
        </p>
      </div>

      <el-form
        ref="formRef"
        :model="form"
        :rules="rules"
        size="large"
        label-position="top"
        @submit.prevent="submit"
      >
        <el-form-item
          v-if="activeTab === 'login' && loginMode === 'password' && !needsProfile"
          prop="identifier"
        >
          <el-input
            v-model="form.identifier"
            placeholder="用户名或邮箱"
            :prefix-icon="User"
            autocomplete="username"
          />
        </el-form-item>

        <template v-if="activeTab === 'register' || needsProfile">
          <el-form-item prop="username">
            <el-input
              v-model="form.username"
              placeholder="用户名（3-32 位字母/数字/_/-）"
              :prefix-icon="User"
              autocomplete="username"
            />
          </el-form-item>
        </template>

        <el-form-item v-if="activeTab === 'register' || codeLogin" prop="email">
          <el-input
            v-model="form.email"
            placeholder="邮箱"
            :prefix-icon="Message"
            autocomplete="email"
          />
        </el-form-item>

        <el-form-item v-if="activeTab === 'register' || codeLogin" prop="code">
          <div class="code-row">
            <el-input
              v-model="form.code"
              placeholder="6 位邮箱验证码"
              maxlength="6"
              autocomplete="one-time-code"
            />
            <el-button
              class="code-btn"
              :disabled="cooldown > 0"
              @click="sendCode"
            >
              {{ codeButtonText }}
            </el-button>
          </div>
        </el-form-item>

        <el-form-item v-if="passwordVisible" prop="password">
          <el-input
            v-model="form.password"
            type="password"
            :placeholder="needsProfile ? '设置密码（至少 6 位）' : '密码'"
            :prefix-icon="Lock"
            show-password
            :autocomplete="
              activeTab === 'login' && loginMode === 'password' && !needsProfile
                ? 'current-password'
                : 'new-password'
            "
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

        <p v-if="devCode" class="dev-code">开发环境验证码：{{ devCode }}</p>

        <el-button
          type="primary"
          size="large"
          class="login-btn"
          :loading="loading"
          native-type="submit"
        >
          {{ submitText }}
        </el-button>

        <el-button v-if="needsProfile" text class="back-btn" @click="backToCodeLogin">
          返回验证码登录
        </el-button>

        <div v-if="activeTab === 'login' && loginMode === 'password' && !needsProfile" class="forgot-link">
          <el-button text @click="openForgot">忘记密码？</el-button>
        </div>
      </el-form>

      <p class="login-footnote">密码经 BCrypt 加密存储 · JWT 登录态 24 小时有效</p>
    </div>

    <!-- 忘记密码：邮箱验证码校验后设置新密码（未注册邮箱也返回一致响应） -->
    <el-dialog v-model="forgotVisible" title="找回密码" width="420px" append-to-body>
      <p class="forgot-hint">输入绑定邮箱获取验证码，验证通过后即可设置新密码。</p>
      <el-form label-position="top" size="large" @submit.prevent="submitForgot">
        <el-form-item label="邮箱">
          <el-input
            v-model="forgotForm.email"
            placeholder="you@example.com"
            :prefix-icon="Message"
            autocomplete="email"
          />
        </el-form-item>
        <el-form-item label="验证码">
          <div class="code-row">
            <el-input
              v-model="forgotForm.code"
              placeholder="6 位验证码"
              maxlength="6"
              autocomplete="one-time-code"
            />
            <el-button
              class="code-btn"
              :loading="forgotLoading"
              :disabled="forgotCooldown > 0"
              @click="sendForgotCode"
            >
              {{ forgotCooldown > 0 ? `${forgotCooldown}s 后重发` : '发送验证码' }}
            </el-button>
          </div>
        </el-form-item>
        <p v-if="forgotDevCode" class="dev-code">开发环境验证码：{{ forgotDevCode }}</p>
        <el-form-item label="新密码">
          <el-input
            v-model="forgotForm.newPassword"
            type="password"
            placeholder="至少 6 位"
            :prefix-icon="Lock"
            show-password
            autocomplete="new-password"
          />
        </el-form-item>
        <el-form-item label="确认新密码">
          <el-input
            v-model="forgotForm.confirm"
            type="password"
            placeholder="再次输入新密码"
            :prefix-icon="Lock"
            show-password
            autocomplete="new-password"
          />
        </el-form-item>
        <el-button
          type="primary"
          class="login-btn"
          :loading="forgotSubmitting"
          native-type="submit"
        >
          重置密码
        </el-button>
      </el-form>
    </el-dialog>
  </div>
</template>

<style scoped>
.login-page {
  min-height: 100vh;
  display: grid;
  place-items: center;
  padding: var(--sp-8) var(--sp-4);
  background: var(--page-bg);
}

.login-card {
  width: 400px;
  max-width: 100%;
  background: var(--surface);
  border: 1px solid var(--hairline);
  border-radius: var(--r-xl);
  padding: var(--sp-10);
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
  background: var(--ink-900);
  display: grid;
  place-items: center;
  margin-bottom: var(--sp-4);
}

.login-brand h1 {
  font-size: var(--fs-3xl);
  font-weight: 600;
  color: var(--ink-900);
  margin: 0;
}

.login-brand h1 span {
  color: var(--brand-600);
}

.login-brand p {
  margin: var(--sp-1) 0 0;
  font-size: var(--fs-md);
  color: var(--ink-500);
}

.login-tabs {
  margin-bottom: var(--sp-4);
}

.setup-hint {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: var(--sp-3);
  margin-bottom: var(--sp-4);
  padding: var(--sp-2) var(--sp-3);
  border: 1px solid var(--hairline);
  border-radius: var(--r-md);
  background: var(--brand-50);
}

.setup-hint__text {
  margin: 0;
  font-size: var(--fs-sm);
  color: var(--ink-600);
  line-height: 1.5;
}

.login-tabs :deep(.el-tabs__nav-wrap)::after {
  background: var(--hairline);
}

.login-tabs :deep(.el-tabs__nav) {
  width: 100%;
  display: flex;
}

.login-tabs :deep(.el-tabs__item) {
  flex: 1;
  justify-content: center;
  padding: 0 var(--sp-2);
  font-size: var(--fs-md);
  font-weight: 600;
  color: var(--ink-500);
}

.login-tabs :deep(.el-tabs__item.is-active) {
  color: var(--brand-600);
}

.login-tabs :deep(.el-tabs__active-bar) {
  height: 2px;
  border-radius: 2px;
  background: var(--brand-600);
}

.login-card :deep(.el-input__wrapper) {
  border-radius: var(--r-md);
  box-shadow: 0 0 0 1px var(--hairline) inset;
}

.login-card :deep(.el-input__wrapper.is-focus) {
  box-shadow: var(--ring);
}

.login-card :deep(.el-input__inner) {
  color: var(--ink-900);
}

.login-card :deep(.el-input__inner::placeholder) {
  color: var(--ink-400);
}

.code-row {
  display: flex;
  gap: var(--sp-2);
  width: 100%;
}

.code-row :deep(.el-input) {
  flex: 1;
}

.el-button.code-btn {
  flex-shrink: 0;
  height: 40px;
  border-color: var(--hairline-strong);
  color: var(--ink-700);
  font-weight: 600;
}

.el-button.code-btn[disabled] {
  color: var(--ink-400);
}

.dev-code {
  margin: 0 0 var(--sp-2);
  font-size: var(--fs-xs);
  color: var(--brand-700);
  background: var(--brand-50);
  border: 1px solid var(--hairline);
  border-radius: var(--r-sm);
  padding: var(--sp-2) var(--sp-3);
}

.mode-switch {
  margin-bottom: var(--sp-4);
}

.mode-switch :deep(.el-radio-group) {
  width: 100%;
  display: flex;
}

.mode-switch :deep(.el-radio-button) {
  flex: 1;
}

.mode-switch :deep(.el-radio-button__inner) {
  width: 100%;
  height: 38px;
  display: flex;
  align-items: center;
  justify-content: center;
  font-weight: 600;
  color: var(--ink-600);
  background: var(--surface);
  border-color: var(--hairline-strong);
  box-shadow: none;
}

.mode-switch :deep(.el-radio-button__original-radio:checked + .el-radio-button__inner) {
  color: var(--ink-900);
  background: var(--brand-50);
  border-color: var(--brand-600);
  box-shadow: -1px 0 0 0 var(--brand-600);
}

.profile-hint {
  margin-bottom: var(--sp-4);
  padding: var(--sp-3) var(--sp-4);
  border: 1px solid var(--hairline);
  border-radius: var(--r-md);
  background: var(--brand-50);
}

.profile-hint__title {
  margin: 0 0 var(--sp-1);
  font-size: var(--fs-md);
  font-weight: 600;
  color: var(--ink-900);
}

.profile-hint__desc {
  margin: 0;
  font-size: var(--fs-sm);
  line-height: 1.6;
  color: var(--ink-600);
}

.el-button.back-btn {
  width: 100%;
  margin: var(--sp-2) 0 0;
  color: var(--ink-500);
}

.forgot-link {
  display: flex;
  justify-content: flex-end;
  margin-top: var(--sp-1);
}

.forgot-link :deep(.el-button) {
  color: var(--ink-500);
  font-size: var(--fs-sm);
}

.forgot-hint {
  margin: 0 0 var(--sp-4);
  font-size: var(--fs-sm);
  color: var(--ink-500);
  line-height: 1.6;
}

.el-button.login-btn {
  width: 100%;
  height: 44px;
  margin-top: var(--sp-2);
  border: none;
  background: var(--ink-900);
  font-weight: 600;
  letter-spacing: 2px;
  transition: background-color var(--dur-fast) var(--ease-out);
}

.el-button.login-btn:hover,
.el-button.login-btn:focus {
  background: var(--ink-800);
}

.login-footnote {
  margin: var(--sp-5) 0 0;
  text-align: center;
  font-size: var(--fs-xs);
  color: var(--ink-400);
}
</style>
