<script setup lang="ts">
import { onBeforeUnmount, onMounted, reactive, ref } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage, type FormInstance, type FormRules } from 'element-plus'
import NavBar from '@/components/layout/NavBar.vue'
import { fetchSetupStatus, initSetup } from '@/api/setup'
import { sendEmailCode } from '@/api/auth'
import { useAuthStore } from '@/stores/auth'

// 首次启动向导：三步初始化（团队信息 / 管理员账号 / 完成），仅系统无用户时可用。
const router = useRouter()
const auth = useAuthStore()

const steps = ['团队信息', '管理员账号', '完成']
const step = ref(0)
const checking = ref(true)
const initialized = ref(false)
const initError = ref('')
const submitting = ref(false)
const formRef = ref<FormInstance>()

const form = reactive({
  teamName: '',
  allowAnonymous: true,
  adminUsername: '',
  adminEmail: '',
  code: '',
  adminPassword: '',
  confirmPassword: '',
})

const cooldown = ref(0)
const devCode = ref('')
let timer: number | undefined

const rules: FormRules = {
  teamName: [
    { required: true, message: '请输入团队名称', trigger: 'blur' },
    { max: 40, message: '团队名称最多 40 个字符', trigger: 'blur' },
  ],
  adminUsername: [
    { required: true, message: '请输入管理员用户名', trigger: 'blur' },
    { min: 3, max: 32, message: '用户名长度需在 3-32 个字符之间', trigger: 'blur' },
  ],
  adminEmail: [
    { required: true, message: '请输入管理员邮箱', trigger: 'blur' },
    { type: 'email', message: '邮箱格式不正确', trigger: 'blur' },
  ],
  code: [
    { required: true, message: '请输入邮箱验证码', trigger: 'blur' },
    { len: 6, message: '验证码为 6 位数字', trigger: 'blur' },
  ],
  adminPassword: [
    { required: true, message: '请设置管理员密码', trigger: 'blur' },
    { min: 6, message: '密码长度至少 6 位', trigger: 'blur' },
  ],
  confirmPassword: [
    {
      validator: (_rule, value: string, callback) => {
        if (value !== form.adminPassword) {
          callback(new Error('两次输入的密码不一致'))
        } else {
          callback()
        }
      },
      trigger: 'blur',
    },
  ],
}

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

async function checkStatus() {
  checking.value = true
  initError.value = ''
  try {
    const status = await fetchSetupStatus()
    initialized.value = status.initialized
    if (status.teamName) form.teamName = status.teamName
    form.allowAnonymous = status.allowAnonymous
    if (status.initialized && auth.isAuthenticated) {
      void router.replace('/')
    }
  } catch {
    initError.value = '无法连接后端服务，请确认服务已启动后重试'
  } finally {
    checking.value = false
  }
}

async function sendCode() {
  const valid = await formRef.value?.validateField('adminEmail').catch(() => false)
  if (!valid) return
  try {
    const result = await sendEmailCode(form.adminEmail.trim(), 'register')
    startCooldown(result.cooldown || 60)
    devCode.value = result.devCode ?? ''
    ElMessage.success(
      result.devCode ? `验证码已生成（开发环境：${result.devCode}）` : '验证码已发送，请查收邮箱',
    )
  } catch {
    /* 拦截器已提示错误 */
  }
}

async function nextFromTeam() {
  const valid = await formRef.value?.validateField('teamName').catch(() => false)
  if (valid) step.value = 1
}

async function nextFromAdmin() {
  const valid = await formRef.value
    ?.validateField(['adminUsername', 'adminEmail', 'code', 'adminPassword', 'confirmPassword'])
    .catch(() => false)
  if (valid) step.value = 2
}

async function finish() {
  submitting.value = true
  try {
    const resp = await initSetup({
      teamName: form.teamName.trim(),
      adminUsername: form.adminUsername.trim(),
      adminEmail: form.adminEmail.trim(),
      adminPassword: form.adminPassword,
      code: form.code.trim(),
      allowAnonymous: form.allowAnonymous,
    })
    auth.applyAuth(resp)
    ElMessage.success('初始化完成，已自动登录')
    void router.replace('/')
  } catch {
    /* 拦截器已提示错误 */
  } finally {
    submitting.value = false
  }
}

onMounted(checkStatus)
onBeforeUnmount(stopTimer)
</script>

<template>
  <div class="setup-page">
    <NavBar />

    <main class="setup">
      <div class="setup__stack">
        <div class="setup__ghost"></div>
        <div class="setup__ghost setup__ghost--2"></div>

        <div class="setup__card">
          <div class="setup__brand">
            <svg width="24" height="24" viewBox="0 0 24 24">
              <path d="M4 4h16v4H4V4zm0 8h10v8H4v-8zm12 0h4v8h-4v-8z" fill="#fff" />
            </svg>
          </div>

          <template v-if="checking">
            <h1 class="setup__title">正在检查初始化状态…</h1>
            <p class="setup__sub">稍等，正在连接后端服务</p>
          </template>

          <template v-else-if="initError">
            <h1 class="setup__title">暂时无法初始化</h1>
            <p class="setup__sub">{{ initError }}</p>
            <div class="setup__actions">
              <button class="btn btn--primary" type="button" @click="checkStatus">重试</button>
            </div>
          </template>

          <template v-else-if="initialized">
            <h1 class="setup__title">系统已完成初始化</h1>
            <p class="setup__sub">无需重复初始化，请使用已有账号登录</p>
            <div class="setup__actions">
              <button class="btn btn--primary" type="button" @click="router.push('/login')">
                去登录
              </button>
            </div>
          </template>

          <template v-else>
            <h1 class="setup__title">欢迎使用 SpringKB</h1>
            <p class="setup__sub">三步完成团队知识库初始化</p>

            <div class="setup__steps">
              <template v-for="(label, index) in steps" :key="label">
                <div class="setup__step" :class="{ 'setup__step--active': index === step }">
                  <span class="setup__step-dot">{{ index + 1 }}</span>
                  <span class="setup__step-label">{{ label }}</span>
                </div>
                <span v-if="index < steps.length - 1" class="setup__step-line"></span>
              </template>
            </div>

            <el-form
              ref="formRef"
              :model="form"
              :rules="rules"
              label-position="top"
              @submit.prevent
            >
              <div v-show="step === 0" class="setup__body">
                <p class="setup__lead">先给你的团队起个名字 —— 这一步定下来，后面的知识才有个家</p>
                <div class="setup__form">
                  <el-form-item label="团队名称" prop="teamName">
                    <el-input v-model="form.teamName" placeholder="例如：平台架构组" maxlength="40" />
                  </el-form-item>
                  <div class="setup__switch">
                    <div class="setup__switch-text">
                      <span class="setup__field-label">匿名访问问答</span>
                      <span class="setup__switch-desc">开启后未登录访客也能使用智能问答</span>
                    </div>
                    <el-switch v-model="form.allowAnonymous" />
                  </div>
                </div>
              </div>

              <div v-show="step === 1" class="setup__body">
                <p class="setup__lead">创建第一个管理员账号 —— 它会拥有成员管理与文档管理权限</p>
                <div class="setup__form">
                  <el-form-item label="管理员用户名" prop="adminUsername">
                    <el-input v-model="form.adminUsername" placeholder="3-32 位字符" autocomplete="username" />
                  </el-form-item>
                  <el-form-item label="管理员邮箱" prop="adminEmail">
                    <el-input v-model="form.adminEmail" placeholder="用于接收验证码与找回账号" autocomplete="email" />
                  </el-form-item>
                  <el-form-item label="邮箱验证码" prop="code">
                    <div class="code-row">
                      <el-input v-model="form.code" placeholder="6 位验证码" maxlength="6" />
                      <el-button :disabled="cooldown > 0" @click="sendCode">
                        {{ cooldown > 0 ? `${cooldown}s 后重发` : '发送验证码' }}
                      </el-button>
                    </div>
                  </el-form-item>
                  <p v-if="devCode" class="dev-code">开发环境验证码：{{ devCode }}</p>
                  <el-form-item label="管理员密码" prop="adminPassword">
                    <el-input
                      v-model="form.adminPassword"
                      type="password"
                      placeholder="至少 6 位"
                      show-password
                      autocomplete="new-password"
                    />
                  </el-form-item>
                  <el-form-item label="确认密码" prop="confirmPassword">
                    <el-input
                      v-model="form.confirmPassword"
                      type="password"
                      placeholder="再次输入密码"
                      show-password
                      autocomplete="new-password"
                    />
                  </el-form-item>
                </div>
              </div>

              <div v-show="step === 2" class="setup__body">
                <p class="setup__lead">确认信息后即可完成初始化，管理员账号会自动登录</p>
                <dl class="setup__summary">
                  <div class="setup__summary-row">
                    <dt>团队名称</dt>
                    <dd>{{ form.teamName }}</dd>
                  </div>
                  <div class="setup__summary-row">
                    <dt>管理员</dt>
                    <dd>{{ form.adminUsername }}（{{ form.adminEmail }}）</dd>
                  </div>
                  <div class="setup__summary-row">
                    <dt>匿名问答</dt>
                    <dd>{{ form.allowAnonymous ? '允许' : '不允许' }}</dd>
                  </div>
                </dl>
              </div>
            </el-form>

            <div class="setup__actions">
              <button v-if="step > 0" class="btn btn--ghost" type="button" @click="step -= 1">
                上一步
              </button>
              <button v-if="step === 0" class="btn btn--primary" type="button" @click="nextFromTeam">
                下一步
              </button>
              <button v-else-if="step === 1" class="btn btn--primary" type="button" @click="nextFromAdmin">
                下一步
              </button>
              <button
                v-else
                class="btn btn--primary"
                type="button"
                :disabled="submitting"
                @click="finish"
              >
                {{ submitting ? '初始化中…' : '完成初始化' }}
              </button>
            </div>
          </template>
        </div>
      </div>
    </main>
  </div>
</template>

<style scoped>
.setup-page {
  display: flex;
  flex-direction: column;
  min-height: 100vh;
}

.setup {
  flex: 1;
  display: grid;
  place-items: center;
  padding: var(--sp-12) var(--sp-4);
  background: var(--page-bg);
}

.setup__stack {
  position: relative;
  width: 520px;
  max-width: 100%;
}

/* 居中叠卡：白面 + hairline，无阴影 */
.setup__ghost {
  position: absolute;
  inset: 0;
  background: var(--surface);
  border: 1px solid var(--hairline);
  border-radius: var(--r-xl);
  opacity: 0.45;
  transform: scale(0.96) translateY(14px);
  pointer-events: none;
}

.setup__ghost--2 {
  opacity: 0.25;
  transform: scale(0.92) translateY(28px);
}

.setup__card {
  position: relative;
  background: var(--surface);
  border: 1px solid var(--hairline);
  border-radius: var(--r-xl);
  padding: var(--sp-10);
}

.setup__brand {
  width: 52px;
  height: 52px;
  border-radius: var(--r-lg);
  background: var(--ink-900);
  display: grid;
  place-items: center;
  margin: 0 auto var(--sp-4);
}

.setup__title {
  text-align: center;
  font-size: var(--fs-3xl);
  font-weight: 600;
  color: var(--ink-900);
}

.setup__sub {
  text-align: center;
  color: var(--ink-500);
  margin: var(--sp-2) 0 var(--sp-8);
}

.setup__steps {
  display: flex;
  align-items: center;
  justify-content: center;
  margin-bottom: var(--sp-8);
}

.setup__step {
  display: flex;
  align-items: center;
  gap: var(--sp-2);
}

/* 步骤点：墨色实心；当前步为陶土点 */
.setup__step-dot {
  width: 28px;
  height: 28px;
  border-radius: 50%;
  display: grid;
  place-items: center;
  background: var(--ink-900);
  color: #fff;
  font-size: var(--fs-sm);
  font-weight: 600;
}

.setup__step--active .setup__step-dot {
  background: var(--brand-600);
}

.setup__step-label {
  font-size: var(--fs-sm);
  color: var(--ink-500);
}

.setup__step--active .setup__step-label {
  color: var(--ink-900);
  font-weight: 600;
}

.setup__step-line {
  width: 40px;
  height: 1px;
  background: var(--hairline);
  margin: 0 var(--sp-2);
}

.setup__body {
  text-align: left;
}

.setup__lead {
  font-size: var(--fs-md);
  color: var(--ink-600);
  line-height: 1.7;
  margin-bottom: var(--sp-6);
  text-align: center;
}

.setup__form {
  display: flex;
  flex-direction: column;
  gap: var(--sp-1);
}

.setup__form :deep(.el-input__wrapper) {
  border-radius: var(--r-md);
  box-shadow: 0 0 0 1px var(--hairline) inset;
}

.setup__form :deep(.el-input__wrapper.is-focus) {
  box-shadow: var(--ring);
}

.setup__switch {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: var(--sp-4);
  padding: var(--sp-3) 0;
}

.setup__switch-text {
  display: flex;
  flex-direction: column;
  gap: 2px;
}

.setup__switch-desc {
  font-size: var(--fs-xs);
  color: var(--ink-500);
}

.setup__field-label {
  font-size: var(--fs-sm);
  font-weight: 600;
  color: var(--ink-600);
}

.code-row {
  display: flex;
  gap: var(--sp-2);
  width: 100%;
}

.code-row :deep(.el-input) {
  flex: 1;
}

.dev-code {
  margin: 0 0 var(--sp-3);
  font-size: var(--fs-xs);
  color: var(--brand-700);
  background: var(--brand-50);
  border: 1px solid var(--hairline);
  border-radius: var(--r-sm);
  padding: var(--sp-2) var(--sp-3);
}

.setup__summary {
  margin: 0;
  border: 1px solid var(--hairline);
  border-radius: var(--r-lg);
  overflow: hidden;
}

.setup__summary-row {
  display: flex;
  align-items: baseline;
  gap: var(--sp-4);
  padding: var(--sp-3) var(--sp-4);
}

.setup__summary-row + .setup__summary-row {
  border-top: 1px solid var(--hairline);
}

.setup__summary-row dt {
  width: 84px;
  flex-shrink: 0;
  font-size: var(--fs-sm);
  color: var(--ink-500);
}

.setup__summary-row dd {
  margin: 0;
  font-size: var(--fs-md);
  color: var(--ink-900);
  font-weight: 600;
  word-break: break-all;
}

.setup__actions {
  display: flex;
  justify-content: center;
  gap: var(--sp-3);
  margin-top: var(--sp-8);
}

.btn[disabled] {
  opacity: 0.55;
  cursor: not-allowed;
}

@media (max-width: 560px) {
  .setup__card {
    padding: var(--sp-6);
  }

  .setup__step-label {
    display: none;
  }

  .setup__step-line {
    width: 24px;
  }
}
</style>
