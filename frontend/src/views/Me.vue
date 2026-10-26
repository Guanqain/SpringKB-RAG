<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, reactive, ref, watch } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { useAuthStore } from '@/stores/auth'
import {
  bindEmail,
  changePassword,
  fetchMyDocuments,
  fetchMyFavorites,
} from '@/api/me'
import { deleteUgcDocument, setUgcVisibility } from '@/api/ugc'
import { sendEmailCode } from '@/api/auth'
import type { KbDocCard, ReviewStatus } from '@/types/kb'
// 个人中心：资料卡 + 我的文档 / 我的收藏 / 账号设置（均来自后端真实数据）
const auth = useAuthStore()

const tabs = ['我的文档', '我的收藏', '账号设置']
const activeTab = ref(tabs[0])

const documents = ref<KbDocCard[]>([])
const favorites = ref<KbDocCard[]>([])
const loading = ref(false)

const avatarText = computed(() => (auth.user?.username ?? '?').slice(0, 1).toUpperCase())
// 后端 role 为大写 ADMIN/MEMBER（坑 #29），统一归一化后比较
const isAdmin = computed(() => auth.user?.role?.toUpperCase() === 'ADMIN')

// 绑定 / 换绑邮箱
const emailForm = reactive({ email: '', code: '' })
const bindCooldown = ref(0)
const devCode = ref('')
const binding = ref(false)
let timer: number | undefined

// 修改密码
const pwdForm = reactive({ oldPassword: '', newPassword: '', confirm: '' })
const savingPwd = ref(false)

const togglingId = ref<string | null>(null)

async function loadTab(tab: string) {
  loading.value = true
  try {
    if (tab === '我的文档') {
      documents.value = await fetchMyDocuments()
    } else if (tab === '我的收藏') {
      favorites.value = await fetchMyFavorites()
    }
  } catch {
    /* 401/网络错误已由 http 拦截器统一提示 */
  } finally {
    loading.value = false
  }
}

function stopTimer() {
  if (timer !== undefined) {
    window.clearInterval(timer)
    timer = undefined
  }
}

function startCooldown(seconds: number) {
  stopTimer()
  bindCooldown.value = seconds
  timer = window.setInterval(() => {
    bindCooldown.value -= 1
    if (bindCooldown.value <= 0) stopTimer()
  }, 1000)
}

async function sendBindCode() {
  const email = emailForm.email.trim()
  if (!/^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(email)) {
    ElMessage.warning('请先填写正确的邮箱地址')
    return
  }
  try {
    const result = await sendEmailCode(email, 'bind')
    startCooldown(result.cooldown || 60)
    devCode.value = result.devCode ?? ''
    ElMessage.success(
      result.devCode ? `验证码已生成（开发环境：${result.devCode}）` : '验证码已发送，请查收邮箱',
    )
  } catch {
    /* 拦截器已提示错误 */
  }
}

async function submitBindEmail() {
  const email = emailForm.email.trim()
  if (!email || !emailForm.code.trim()) {
    ElMessage.warning('请填写邮箱与验证码')
    return
  }
  binding.value = true
  try {
    await bindEmail(email, emailForm.code.trim())
    ElMessage.success('邮箱绑定成功')
    emailForm.code = ''
    devCode.value = ''
    stopTimer()
    bindCooldown.value = 0
    await auth.restoreSession()
  } catch {
    /* 拦截器已提示错误 */
  } finally {
    binding.value = false
  }
}

async function submitPassword() {
  if (!pwdForm.oldPassword || !pwdForm.newPassword) {
    ElMessage.warning('请填写旧密码与新密码')
    return
  }
  if (pwdForm.newPassword.length < 6) {
    ElMessage.warning('新密码长度至少 6 位')
    return
  }
  if (pwdForm.newPassword !== pwdForm.confirm) {
    ElMessage.warning('两次输入的新密码不一致')
    return
  }
  savingPwd.value = true
  try {
    await changePassword(pwdForm.oldPassword, pwdForm.newPassword)
    ElMessage.success('密码已更新')
    pwdForm.oldPassword = ''
    pwdForm.newPassword = ''
    pwdForm.confirm = ''
  } catch {
    /* 拦截器已提示错误 */
  } finally {
    savingPwd.value = false
  }
}

async function toggleVisibility(doc: KbDocCard) {
  const next = doc.visibility === 'private' ? 'team' : 'private'
  togglingId.value = doc.id
  try {
    const reviewStatus = (await setUgcVisibility(doc.id, next)) as ReviewStatus
    doc.visibility = next
    doc.reviewStatus = reviewStatus
    if (next === 'private') {
      ElMessage.success('已设为仅自己可见（免扫描、免审核）')
    } else if (reviewStatus === 'pending') {
      ElMessage.success('已提交管理员审核，通过后才会进入知识库')
    } else {
      ElMessage.success('已设为团队可见')
    }
  } catch {
    /* 拦截器已提示错误；422 时说明正文含敏感信息，已在提示中说明 */
  } finally {
    togglingId.value = null
  }
}

async function removeDocument(doc: KbDocCard) {
  try {
    await ElMessageBox.confirm(
      `删除「${doc.title}」及其全部片段？此操作不可恢复。`,
      '删除文档',
      { type: 'warning', confirmButtonText: '删除', cancelButtonText: '取消' },
    )
  } catch {
    return
  }
  try {
    await deleteUgcDocument(doc.id)
    documents.value = documents.value.filter((item) => item.id !== doc.id)
    ElMessage.success('文档已删除')
  } catch {
    /* 拦截器已提示错误 */
  }
}

/** 审核状态徽标文案 */
function reviewLabel(doc: KbDocCard): string {
  if (doc.reviewStatus === 'pending') return '待审核'
  if (doc.reviewStatus === 'rejected') return '已驳回'
  return ''
}

watch(activeTab, (tab) => void loadTab(tab))
onMounted(() => {
  emailForm.email = auth.user?.email ?? ''
  void loadTab(activeTab.value)
})
onBeforeUnmount(stopTimer)
</script>

<template>
  <section class="container me">
    <div class="me-layout">
      <!-- 左：资料卡（仅渲染已登录用户的真实信息） -->
      <aside class="me-card">
        <div class="me-card__avatar">{{ avatarText }}</div>
        <div class="me-card__name">{{ auth.user?.username ?? '未登录' }}</div>
        <div class="me-card__role">
          <span v-if="isAdmin" class="badge--admin">admin</span>
          <span v-else class="me-card__role-text">成员</span>
        </div>
      </aside>

      <!-- 右：tabs + 列表壳 -->
      <div class="me-main">
        <div class="tabs">
          <button
            v-for="tab in tabs"
            :key="tab"
            class="tab"
            :class="{ 'tab--active': activeTab === tab }"
            type="button"
            @click="activeTab = tab"
          >
            {{ tab }}
          </button>
        </div>

        <div v-if="activeTab === '我的文档'" class="panel">
          <template v-if="documents.length">
            <ul class="doc-list">
              <li v-for="doc in documents" :key="doc.id" class="doc-item">
                <div class="doc-item__wrap">
                  <router-link class="doc-item__main" :to="`/kb/doc/${doc.id}`">
                    <span
                      class="badge"
                      :class="doc.authority === 'official' ? 'badge--official' : 'badge--community'"
                    >{{ doc.authority === 'official' ? '官方' : '社区' }}</span>
                    <span
                      v-if="doc.visibility === 'private'"
                      class="badge badge--private"
                      title="仅自己可见，不参与问答引用"
                    >私有</span>
                    <span
                      v-if="reviewLabel(doc)"
                      class="badge"
                      :class="doc.reviewStatus === 'rejected' ? 'badge--rejected' : 'badge--pending'"
                    >{{ reviewLabel(doc) }}</span>
                    <span class="doc-item__title">{{ doc.title }}</span>
                    <span class="doc-item__meta num">{{ doc.chunkCount }} 片段 · {{ doc.updatedAt }}</span>
                  </router-link>
                  <p v-if="doc.reviewStatus === 'rejected' && doc.reviewNote" class="doc-item__note">
                    审核意见：{{ doc.reviewNote }}
                  </p>
                </div>
                <div class="doc-item__actions">
                  <button
                    class="doc-item__vis"
                    type="button"
                    :disabled="togglingId === doc.id"
                    @click="toggleVisibility(doc)"
                  >
                    {{ doc.visibility === 'private' ? '设为团队可见' : '设为私有' }}
                  </button>
                  <router-link
                    class="doc-item__vis"
                    :to="{ path: '/editor', query: { docId: doc.id } }"
                  >编辑</router-link>
                  <button class="doc-item__vis doc-item__vis--danger" type="button" @click="removeDocument(doc)">
                    删除
                  </button>
                </div>
              </li>
            </ul>
            <router-link class="btn btn--outline me__more" to="/editor">再写一篇</router-link>
          </template>
          <div v-else-if="!loading" class="panel--empty">
            <p class="empty__text">你还没有写过文档 —— 从写下第一个问题开始</p>
            <router-link class="btn btn--primary" to="/editor">去写文档</router-link>
          </div>
        </div>

        <div v-else-if="activeTab === '我的收藏'" class="panel">
          <template v-if="favorites.length">
            <ul class="doc-list">
              <li v-for="doc in favorites" :key="doc.id" class="doc-item">
                <router-link class="doc-item__main" :to="`/kb/doc/${doc.id}`">
                  <span
                    class="badge"
                    :class="doc.authority === 'official' ? 'badge--official' : 'badge--community'"
                  >{{ doc.authority === 'official' ? '官方' : '社区' }}</span>
                  <span class="doc-item__title">{{ doc.title }}</span>
                  <span class="doc-item__meta num">{{ doc.version }} · {{ doc.chunkCount }} 片段</span>
                </router-link>
              </li>
            </ul>
            <router-link class="btn btn--outline me__more" to="/kb">去知识库发现更多</router-link>
          </template>
          <div v-else-if="!loading" class="panel--empty">
            <p class="empty__text">收藏夹是空的 —— 遇到好文档，先收起来</p>
            <router-link class="btn btn--primary" to="/kb">去知识库</router-link>
          </div>
        </div>

        <div v-else class="panel">
          <dl class="settings">
            <div class="settings__row">
              <dt class="settings__key">用户名</dt>
              <dd class="settings__val">{{ auth.user?.username ?? '未登录' }}</dd>
            </div>
            <div class="settings__row">
              <dt class="settings__key">邮箱</dt>
              <dd class="settings__val">{{ auth.user?.email || '未绑定' }}</dd>
            </div>
            <div class="settings__row">
              <dt class="settings__key">角色</dt>
              <dd class="settings__val">{{ isAdmin ? '管理员' : '成员' }}</dd>
            </div>
          </dl>

          <section class="settings-block">
            <h2 class="settings-block__title">
              {{ auth.user?.email ? '换绑邮箱' : '绑定邮箱' }}
            </h2>
            <p class="settings-block__hint">
              绑定后可用邮箱接收验证码登录，注册与登录都需要通过邮箱验证码校验。
            </p>
            <div class="settings-form">
              <label class="field">
                <span class="field__label">邮箱</span>
                <input v-model="emailForm.email" class="field__input" type="email" placeholder="you@example.com" />
              </label>
              <label class="field">
                <span class="field__label">验证码</span>
                <div class="code-row">
                  <input v-model="emailForm.code" class="field__input" type="text" maxlength="6" placeholder="6 位验证码" />
                  <button class="btn btn--outline" type="button" :disabled="bindCooldown > 0" @click="sendBindCode">
                    {{ bindCooldown > 0 ? `${bindCooldown}s 后重发` : '发送验证码' }}
                  </button>
                </div>
              </label>
              <p v-if="devCode" class="dev-code">开发环境验证码：{{ devCode }}</p>
              <button class="btn btn--primary settings-block__submit" type="button" :disabled="binding" @click="submitBindEmail">
                {{ binding ? '提交中…' : auth.user?.email ? '确认换绑' : '绑定邮箱' }}
              </button>
            </div>
          </section>

          <section class="settings-block">
            <h2 class="settings-block__title">修改密码</h2>
            <div class="settings-form">
              <label class="field">
                <span class="field__label">旧密码</span>
                <input v-model="pwdForm.oldPassword" class="field__input" type="password" autocomplete="current-password" placeholder="当前密码" />
              </label>
              <label class="field">
                <span class="field__label">新密码</span>
                <input v-model="pwdForm.newPassword" class="field__input" type="password" autocomplete="new-password" placeholder="至少 6 位" />
              </label>
              <label class="field">
                <span class="field__label">确认新密码</span>
                <input v-model="pwdForm.confirm" class="field__input" type="password" autocomplete="new-password" placeholder="再次输入新密码" />
              </label>
              <button class="btn btn--primary settings-block__submit" type="button" :disabled="savingPwd" @click="submitPassword">
                {{ savingPwd ? '提交中…' : '更新密码' }}
              </button>
            </div>
          </section>
        </div>
      </div>
    </div>
  </section>
</template>

<style scoped>
.me {
  padding-top: var(--sp-12);
  padding-bottom: var(--sp-16);
}

.me-layout {
  display: grid;
  grid-template-columns: 280px 1fr;
  gap: var(--sp-6);
  align-items: start;
}

/* 左资料卡：白面 + hairline，无阴影 */
.me-card {
  background: var(--surface);
  border: 1px solid var(--hairline);
  border-radius: var(--r-lg);
  padding: var(--sp-8) var(--sp-6);
  text-align: center;
  position: sticky;
  top: 84px;
}

.me-card__avatar {
  width: 72px;
  height: 72px;
  border-radius: 50%;
  background: var(--ink-100);
  border: 1px solid var(--hairline);
  display: grid;
  place-items: center;
  margin: 0 auto var(--sp-4);
  color: var(--ink-700);
  font-size: var(--fs-3xl);
  font-weight: 600;
}

.me-card__name {
  font-size: var(--fs-2xl);
  font-weight: 600;
  color: var(--ink-900);
}

.me-card__role {
  margin-top: var(--sp-2);
}

.me-card__role-text {
  font-size: var(--fs-xs);
  color: var(--ink-500);
}

.me-main {
  min-width: 0;
}

.tabs {
  display: flex;
  gap: var(--sp-1);
  border-bottom: 1px solid var(--hairline);
  margin-bottom: var(--sp-5);
}

.tab {
  padding: var(--sp-3) var(--sp-4);
  font-size: var(--fs-md);
  font-weight: 600;
  color: var(--ink-500);
  border-bottom: 2px solid transparent;
  margin-bottom: -1px;
  transition: color var(--dur-fast) var(--ease-out);
}

.tab:hover {
  color: var(--ink-900);
}

.tab--active {
  color: var(--ink-900);
  border-bottom-color: var(--brand-600);
}

/* 列表壳：白面 + hairline */
.panel {
  background: var(--surface);
  border: 1px solid var(--hairline);
  border-radius: var(--r-lg);
  padding: var(--sp-6);
}

.panel--empty {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: var(--sp-4);
  min-height: 260px;
  text-align: center;
}

/* 空态嵌在 panel 内时不再叠加一层白面描边 */
.panel .panel--empty {
  background: none;
  border: none;
  padding: 0;
}

/* 文档列表：hairline 行分隔，hover 轻微下沉底色 */
.doc-list {
  display: flex;
  flex-direction: column;
  margin: 0;
  padding: 0;
  list-style: none;
}

.doc-item {
  display: flex;
  align-items: center;
  gap: var(--sp-2);
}

.doc-item + .doc-item {
  border-top: 1px solid var(--hairline);
}

.doc-item__main {
  flex: 1;
  min-width: 0;
  display: flex;
  align-items: center;
  gap: var(--sp-3);
  padding: var(--sp-3) var(--sp-2);
  border-radius: var(--r-md);
  color: inherit;
  text-decoration: none;
  transition: background-color var(--dur-fast) var(--ease-out);
}

.doc-item__main:hover {
  background: var(--surface-sunken);
}

.doc-item__wrap {
  flex: 1;
  min-width: 0;
}

.doc-item__note {
  margin: 0;
  padding: 0 var(--sp-2) var(--sp-3);
  font-size: var(--fs-xs);
  color: var(--ink-500);
  line-height: 1.6;
}

.doc-item__actions {
  flex-shrink: 0;
  display: flex;
  align-items: center;
  gap: var(--sp-2);
}

.doc-item__title {
  flex: 1;
  min-width: 0;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  font-size: var(--fs-md);
  font-weight: 600;
  color: var(--ink-900);
}

.doc-item__meta {
  flex-shrink: 0;
  font-size: var(--fs-xs);
  color: var(--ink-400);
}

.doc-item__vis {
  flex-shrink: 0;
  padding: 4px 10px;
  border: 1px solid var(--hairline-strong);
  border-radius: var(--r-sm);
  background: var(--surface);
  font-size: var(--fs-xs);
  color: var(--ink-600);
  text-decoration: none;
  cursor: pointer;
  transition:
    border-color var(--dur-fast) var(--ease-out),
    color var(--dur-fast) var(--ease-out);
}

.doc-item__vis:hover {
  border-color: var(--brand-600);
  color: var(--brand-700);
}

.doc-item__vis:disabled {
  opacity: 0.55;
  cursor: not-allowed;
}

.doc-item__vis--danger:hover {
  border-color: #b91c1c;
  color: #b91c1c;
}

.me__more {
  margin-top: var(--sp-4);
}

.empty__text {
  font-size: var(--fs-lg);
  font-weight: 600;
  color: var(--ink-700);
  line-height: 1.7;
  max-width: 420px;
}

.settings {
  display: flex;
  flex-direction: column;
  margin: 0;
}

.settings__row {
  display: grid;
  grid-template-columns: 120px 1fr;
  gap: var(--sp-4);
  padding: var(--sp-3) 0;
  border-bottom: 1px solid var(--hairline);
}

.settings__key {
  font-size: var(--fs-sm);
  color: var(--ink-500);
}

.settings__val {
  margin: 0;
  font-size: var(--fs-md);
  color: var(--ink-900);
}

.settings-block {
  margin-top: var(--sp-8);
  padding-top: var(--sp-6);
  border-top: 1px solid var(--hairline);
}

.settings-block__title {
  font-size: var(--fs-lg);
  font-weight: 600;
  color: var(--ink-900);
  margin: 0 0 var(--sp-2);
}

.settings-block__hint {
  font-size: var(--fs-sm);
  color: var(--ink-500);
  line-height: 1.7;
  margin: 0 0 var(--sp-4);
}

.settings-form {
  display: flex;
  flex-direction: column;
  gap: var(--sp-3);
  max-width: 420px;
}

.field {
  display: flex;
  flex-direction: column;
  gap: var(--sp-1);
}

.field__label {
  font-size: var(--fs-sm);
  font-weight: 600;
  color: var(--ink-600);
}

.field__input {
  width: 100%;
  border: 1px solid var(--hairline);
  border-radius: var(--r-md);
  padding: var(--sp-3) var(--sp-4);
  font-size: var(--fs-md);
  color: var(--ink-900);
  background: var(--surface);
  outline: none;
  transition: box-shadow var(--dur-fast) var(--ease-out);
}

.field__input::placeholder {
  color: var(--ink-400);
}

.field__input:focus {
  border-color: transparent;
  box-shadow: var(--ring);
}

.code-row {
  display: flex;
  gap: var(--sp-2);
}

.code-row .field__input {
  flex: 1;
}

.code-row .btn {
  flex-shrink: 0;
}

.dev-code {
  margin: 0;
  font-size: var(--fs-xs);
  color: var(--brand-700);
  background: var(--brand-50);
  border: 1px solid var(--hairline);
  border-radius: var(--r-sm);
  padding: var(--sp-2) var(--sp-3);
}

.settings-block__submit {
  align-self: flex-start;
  margin-top: var(--sp-1);
}

.settings-block__submit:disabled {
  opacity: 0.55;
  cursor: not-allowed;
}

@media (max-width: 900px) {
  .me-layout {
    grid-template-columns: 1fr;
  }

  .me-card {
    position: static;
  }
}
</style>
