<script setup lang="ts">
import { computed } from 'vue'
import { useRoute } from 'vue-router'

const route = useRoute()

const items = [
  { label: '概览', to: '/admin', exact: true },
  { label: '文档管理', to: '/admin/documents' },
  { label: '数据导入', to: '/admin/import' },
  { label: '成员管理', to: '/admin/members' },
  { label: '问答统计', to: '/admin/qa-stats' },
  { label: '知识盲区', to: '/admin/blindspots' },
  { label: '待复审', to: '/admin/review' },
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
  grid-template-columns: var(--admin-sidebar-w) 1fr;
  min-height: calc(100vh - 68px);
}

.admin__sidebar {
  background: var(--ink-900);
  color: var(--ink-300);
  padding: var(--sp-6) var(--sp-3);
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
  display: flex;
  flex-direction: column;
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
  padding: var(--sp-8);
  min-width: 0;
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
    grid-template-columns: 1fr;
  }

  .admin__sidebar {
    display: flex;
    overflow-x: auto;
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
