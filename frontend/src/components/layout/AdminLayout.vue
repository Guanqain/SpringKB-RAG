<script setup lang="ts">
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
      <router-view v-slot="{ Component }">
        <transition name="page-fade" mode="out-in">
          <component :is="Component" :key="route.path" />
        </transition>
      </router-view>
    </main>
  </div>
</template>

<style scoped>
.admin {
  display: grid;
  grid-template-columns: 220px 1fr;
  min-height: calc(100vh - 68px);
}

.admin__sidebar {
  background: var(--ink-900);
  color: var(--ink-300);
  padding: var(--sp-6) var(--sp-3);
}

.admin__side-title {
  font-size: var(--fs-xs);
  font-weight: 700;
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
  transition: all 0.15s var(--ease-out);
}

.admin__side-item:hover {
  color: var(--ink-100);
  background: rgba(255, 255, 255, 0.05);
}

.admin__side-item--active {
  color: #fff;
  background: rgba(249, 115, 22, 0.14);
  border-left-color: var(--brand-500);
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
  background: var(--brand-500);
  opacity: 1;
}

.admin__main {
  padding: var(--sp-8);
  background: var(--page-bg);
  min-width: 0;
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
    border-bottom-color: var(--brand-500);
  }
}
</style>
