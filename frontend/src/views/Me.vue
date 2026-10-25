<script setup lang="ts">
import { computed, onMounted, ref, watch } from 'vue'
import { useAuthStore } from '@/stores/auth'
import { fetchMyDocuments, fetchMyFavorites } from '@/api/me'
import type { KbDocCard } from '@/types/kb'
// 个人中心：资料卡 + 我的文档 / 我的收藏（均来自后端真实数据）
const auth = useAuthStore()

const tabs = ['我的文档', '我的收藏', '账号设置']
const activeTab = ref(tabs[0])

const documents = ref<KbDocCard[]>([])
const favorites = ref<KbDocCard[]>([])
const loading = ref(false)

const avatarText = computed(() => (auth.user?.username ?? '?').slice(0, 1).toUpperCase())
// 后端 role 为大写 ADMIN/MEMBER（坑 #29），统一归一化后比较
const isAdmin = computed(() => auth.user?.role?.toUpperCase() === 'ADMIN')

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

watch(activeTab, (tab) => void loadTab(tab))
onMounted(() => void loadTab(activeTab.value))
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
                <router-link class="doc-item__main" :to="`/kb/doc/${doc.id}`">
                  <span
                    class="badge"
                    :class="doc.authority === 'official' ? 'badge--official' : 'badge--community'"
                  >{{ doc.authority === 'official' ? '官方' : '社区' }}</span>
                  <span class="doc-item__title">{{ doc.title }}</span>
                  <span class="doc-item__meta num">{{ doc.chunkCount }} 片段 · {{ doc.updatedAt }}</span>
                </router-link>
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
              <dt class="settings__key">角色</dt>
              <dd class="settings__val">{{ isAdmin ? '管理员' : '成员' }}</dd>
            </div>
          </dl>
          <button class="btn btn--outline settings__action" type="button" disabled title="账号设置能力还没接上">
            修改密码
          </button>
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

.doc-item + .doc-item {
  border-top: 1px solid var(--hairline);
}

.doc-item__main {
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
  font-size: var(--fs-md);
  color: var(--ink-900);
}

.settings__action {
  margin-top: var(--sp-5);
}

.settings__action:disabled {
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
