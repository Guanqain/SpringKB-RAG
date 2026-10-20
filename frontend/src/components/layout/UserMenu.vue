<script setup lang="ts">
import { ref } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { useAuthStore } from '@/stores/auth'

const auth = useAuthStore()
const router = useRouter()
const open = ref(false)

const menuItems = [
  { label: '个人中心', to: '/me' },
  { label: '写文档', to: '/editor' },
  { label: '我的收藏', to: '/me' },
]

function go(path: string) {
  open.value = false
  router.push(path)
}

function onLogin() {
  router.push('/login')
}

function onLogout() {
  auth.logout()
  open.value = false
  ElMessage.success('已退出登录')
  router.push('/login')
}
</script>

<template>
  <div v-if="!auth.isAuthenticated" class="login-entry">
    <button class="btn btn--ghost" @click="onLogin">登录</button>
  </div>

  <div
    v-else
    class="user-menu"
    @mouseenter="open = true"
    @mouseleave="open = false"
  >
    <button
      class="user-menu__avatar"
      type="button"
      title="账户"
      @click="open = !open"
    >
      <svg
        width="18"
        height="18"
        viewBox="0 0 24 24"
        fill="none"
        stroke="currentColor"
        stroke-width="2"
        stroke-linecap="round"
        stroke-linejoin="round"
      >
        <path d="M20 21v-2a4 4 0 0 0-4-4H8a4 4 0 0 0-4 4v2" />
        <circle cx="12" cy="7" r="4" />
      </svg>
    </button>

    <div class="user-menu__dropdown" :class="{ 'is-open': open }">
      <div class="user-menu__header">
        <div class="user-menu__name">
          {{ auth.user?.username ?? '已登录' }}
          <span v-if="auth.user?.role === 'admin'" class="badge--admin">admin</span>
        </div>
        <div class="user-menu__ws">SpringKB 团队</div>
      </div>

      <a
        v-for="item in menuItems"
        :key="item.label"
        class="user-menu__item"
        @click.prevent="go(item.to)"
      >
        {{ item.label }}
      </a>

      <div class="user-menu__divider"></div>

      <a class="user-menu__item" @click.prevent="go('/admin')">
        管理后台
        <span class="user-menu__tag user-menu__tag--admin">admin</span>
      </a>

      <a class="user-menu__item" @click.prevent="onLogout">退出登录</a>
    </div>
  </div>
</template>

<style scoped>
.login-entry {
  display: flex;
  align-items: center;
}

.user-menu {
  position: relative;
}

.user-menu__avatar {
  width: 38px;
  height: 38px;
  border-radius: 50%;
  display: grid;
  place-items: center;
  background: var(--ink-100);
  border: 1px solid var(--ink-200);
  color: var(--ink-500);
  cursor: pointer;
  transition: all 0.18s var(--ease-out);
}

.user-menu__avatar:hover {
  background: var(--brand-50);
  color: var(--brand-600);
  border-color: var(--brand-300);
}

.user-menu__dropdown {
  position: absolute;
  top: calc(100% + 10px);
  right: 0;
  width: 220px;
  background: #fff;
  border: 1px solid var(--ink-200);
  border-radius: var(--r-lg);
  box-shadow: var(--shadow-xl);
  padding: var(--sp-2);
  opacity: 0;
  visibility: hidden;
  transform: translateY(-6px);
  transition: all 0.18s var(--ease-out);
  z-index: 60;
}

.user-menu__dropdown.is-open {
  opacity: 1;
  visibility: visible;
  transform: translateY(0);
}

.user-menu__header {
  padding: var(--sp-2) var(--sp-3) var(--sp-3);
  border-bottom: 1px solid var(--ink-100);
  margin-bottom: var(--sp-2);
}

.user-menu__name {
  display: flex;
  align-items: center;
  gap: var(--sp-1);
  font-size: var(--fs-sm);
  font-weight: 700;
  color: var(--ink-900);
}

.user-menu__ws {
  font-size: var(--fs-xs);
  color: var(--brand-600);
  font-weight: 500;
  margin-top: 2px;
}

.user-menu__item {
  display: flex;
  align-items: center;
  gap: var(--sp-2);
  padding: 8px var(--sp-3);
  border-radius: var(--r-md);
  font-size: var(--fs-sm);
  color: var(--ink-700);
  cursor: pointer;
  transition: all 0.15s var(--ease-out);
}

.user-menu__item:hover {
  background: var(--brand-50);
  color: var(--brand-700);
}

.user-menu__tag {
  margin-left: auto;
  font-size: 10px;
  font-weight: 700;
  padding: 2px 6px;
  border-radius: 4px;
  background: var(--ink-100);
  color: var(--ink-400);
  letter-spacing: 0.03em;
}

.user-menu__tag--admin {
  background: var(--brand-100);
  color: var(--brand-700);
}

.user-menu__divider {
  height: 1px;
  background: var(--ink-100);
  margin: var(--sp-2) 0;
}
</style>
