<script setup lang="ts">
import { computed } from 'vue'
import { useRoute } from 'vue-router'

const route = useRoute()

const items = [
  { label: '概览', to: '/admin', exact: true },
  { label: '文档管理', to: '/admin/documents' },
  { label: '内容审核', to: '/admin/moderation' },
  { label: '脱敏规则', to: '/admin/scan-rules' },
  { label: '数据导入', to: '/admin/import' },
  { label: '成员管理', to: '/admin/members' },
  { label: '问答统计', to: '/admin/qa-stats' },
  { label: '知识盲区', to: '/admin/blindspots' },
  { label: '待复审', to: '/admin/review' },
  { label: '检索诊断', to: '/admin/rag-debug' },
]

function isActive(item: { to: string; exact?: boolean }) {
  const p = route.path
  if (item.exact) return p === item.to
  return p === item.to || p.startsWith(item.to + '/')
}

/** 主区顶部工具条显示当前页名（克制的面包屑式文字） */
const currentLabel = computed(
  () => items.find((item) => isActive(item))?.label ?? '管理后台',
)
</script>

<template>
  <div class="admin">
    <aside class="admin__sidebar">
      <div class="admin__side-title">管理后台</div>
      <router-link
        v-for="item in items"
        :key="item.to"
        class="admin__side-item"
        :class="{ 'admin__side-item--active': isActive(item) }"
        :to="item.to"
      >
        <span class="admin__side-dot"></span>
        {{ item.label }}
      </router-link>
    </aside>

    <main class="admin__main">
      <div class="admin__toolbar">
        <span class="admin__toolbar-crumb">管理后台</span>
        <span class="admin__toolbar-sep">/</span>
        <span class="admin__toolbar-title">{{ currentLabel }}</span>
      </div>
      <div class="admin__content">
        <router-view v-slot="{ Component }">
          <transition name="page-fade" mode="out-in">
            <component :is="Component" :key="route.path" />
          </transition>
        </router-view>
      </div>
    </main>
  </div>
</template>

<style scoped>
.admin {
  display: grid;
  grid-template-columns: var(--admin-sidebar-w) minmax(0, 1fr);
  /* 左右各自滚动：外层锁定视口高度，导航与内容区互不影响 */
  height: calc(100vh - 68px);
  height: calc(100dvh - 68px);
  overflow: hidden;
}

.admin__sidebar {
  background: var(--ink-900);
  color: var(--ink-300);
  padding: var(--sp-6) var(--sp-3);
  height: 100%;
  overflow-y: auto;
  overscroll-behavior: contain;
  scrollbar-width: thin;
  scrollbar-color: rgba(255, 255, 255, 0.18) transparent;
}

.admin__side-title {
  font-size: var(--fs-xs);
  font-weight: 600;
  letter-spacing: 0.08em;
  color: var(--ink-500);
  text-transform: uppercase;
  padding: 0 var(--sp-3);
  margin-bottom: var(--sp-3);
}

.admin__side-item {
  display: flex;
  align-items: center;
  gap: var(--sp-2);
  padding: var(--sp-2) var(--sp-3);
  border-radius: var(--r-md);
  font-size: var(--fs-sm);
  color: var(--ink-400);
  cursor: pointer;
  margin-bottom: 2px;
  border-left: 3px solid transparent;
  transition:
    background-color var(--dur-fast) var(--ease-out),
    color var(--dur-fast) var(--ease-out),
    border-color var(--dur-fast) var(--ease-out);
}

.admin__side-item:hover {
  color: var(--ink-100);
  background: rgba(255, 255, 255, 0.06);
}

.admin__side-item--active {
  color: #fff;
  background: rgba(180, 83, 9, 0.1);
  border-left-color: var(--brand-600);
}

.admin__side-dot {
  width: 6px;
  height: 6px;
  border-radius: 50%;
  background: currentColor;
  opacity: 0.6;
  flex-shrink: 0;
}

.admin__side-item--active .admin__side-dot {
  background: var(--brand-600);
  opacity: 1;
}

.admin__main {
  background: var(--admin-bg);
  min-width: 0;
  height: 100%;
  display: flex;
  flex-direction: column;
  overflow: hidden;
}

.admin__toolbar {
  height: 46px;
  flex-shrink: 0;
  display: flex;
  align-items: center;
  gap: var(--sp-2);
  padding: 0 var(--sp-6);
  background: var(--surface);
  border-bottom: 1px solid var(--hairline);
  font-size: var(--fs-sm);
  color: var(--ink-400);
}

.admin__toolbar-sep {
  color: var(--ink-300);
}

.admin__toolbar-title {
  color: var(--ink-700);
  font-weight: 600;
}

.admin__content {
  flex: 1;
  min-width: 0;
  min-height: 0;
  overflow: auto;
  overscroll-behavior: contain;
  padding: var(--sp-8);
}

@media (max-width: 1440px) {
  .admin__content {
    padding: var(--sp-6);
  }
}

@media (max-width: 1180px) {
  .admin__content {
    padding: var(--sp-5) var(--sp-4);
  }

  .admin__toolbar {
    padding: 0 var(--sp-4);
  }
}

/* 路由切换：仅轻微透明度变化，无位移 */
.page-fade-enter-active,
.page-fade-leave-active {
  transition: opacity var(--dur-base) var(--ease-out);
}

.page-fade-enter-from,
.page-fade-leave-to {
  opacity: 0;
}

@media (max-width: 900px) {
  .admin {
    grid-template-columns: minmax(0, 1fr);
    grid-template-rows: auto minmax(0, 1fr);
  }

  .admin__sidebar {
    height: auto;
    display: flex;
    overflow-x: auto;
    overflow-y: hidden;
    padding: var(--sp-3);
    gap: var(--sp-1);
  }

  .admin__side-title {
    display: none;
  }

  .admin__side-item {
    border-left: none;
    white-space: nowrap;
    margin-bottom: 0;
    border-bottom: 3px solid transparent;
  }

  .admin__side-item--active {
    border-bottom-color: var(--brand-600);
  }

  .admin__content {
    padding: var(--sp-5) var(--sp-4);
  }
}
</style>
