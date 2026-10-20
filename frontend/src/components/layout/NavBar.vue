<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import { useRoute } from 'vue-router'
import UserMenu from './UserMenu.vue'

const route = useRoute()
const mobileOpen = ref(false)

const links = [
  { label: '首页', to: '/' },
  { label: '问答', to: '/qa' },
  { label: '知识库', to: '/kb' },
]

const activePath = computed(() => {
  const p = route.path
  if (p === '/' || p === '/home') return '/'
  if (p.startsWith('/qa')) return '/qa'
  if (p.startsWith('/kb')) return '/kb'
  return ''
})

watch(
  () => route.fullPath,
  () => {
    mobileOpen.value = false
  },
)
</script>

<template>
  <nav class="nav">
    <div class="container nav__inner">
      <router-link class="nav__brand" to="/">
        <span class="nav__brand-icon">
          <svg width="20" height="20" viewBox="0 0 24 24">
            <path d="M4 4h16v4H4V4zm0 8h10v8H4v-8zm12 0h4v8h-4v-8z" fill="#fff" />
          </svg>
        </span>
        <span class="nav__brand-name">Spring<span>KB</span></span>
      </router-link>

      <div class="nav__links">
        <router-link
          v-for="link in links"
          :key="link.to"
          class="nav__link"
          :class="{ 'nav__link--active': activePath === link.to }"
          :to="link.to"
        >
          {{ link.label }}
        </router-link>
      </div>

      <div class="nav__actions">
        <button
          class="nav__burger btn--icon"
          type="button"
          aria-label="展开导航"
          @click="mobileOpen = !mobileOpen"
        >
          <svg
            width="20"
            height="20"
            viewBox="0 0 24 24"
            fill="none"
            stroke="currentColor"
            stroke-width="2"
            stroke-linecap="round"
          >
            <line x1="3" y1="6" x2="21" y2="6" />
            <line x1="3" y1="12" x2="21" y2="12" />
            <line x1="3" y1="18" x2="21" y2="18" />
          </svg>
        </button>
        <UserMenu />
      </div>
    </div>

    <div v-if="mobileOpen" class="nav__mobile">
      <router-link
        v-for="link in links"
        :key="link.to"
        class="nav__link"
        :class="{ 'nav__link--active': activePath === link.to }"
        :to="link.to"
      >
        {{ link.label }}
      </router-link>
    </div>
  </nav>
</template>

<style scoped>
.nav {
  position: sticky;
  top: 0;
  z-index: 50;
  background: rgba(253, 252, 250, 0.75);
  backdrop-filter: saturate(180%) blur(16px);
  -webkit-backdrop-filter: saturate(180%) blur(16px);
  border-bottom: 1px solid rgba(228, 228, 231, 0.7);
}

.nav__inner {
  height: 68px;
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: var(--sp-4);
}

.nav__brand {
  display: flex;
  align-items: center;
  gap: var(--sp-2);
  font-weight: 700;
  font-size: var(--fs-xl);
  color: var(--ink-900);
  flex-shrink: 0;
}

.nav__brand-icon {
  width: 32px;
  height: 32px;
  border-radius: var(--r-md);
  background: var(--grad-brand);
  display: grid;
  place-items: center;
  box-shadow: var(--shadow-sm);
}

.nav__brand-name span {
  color: var(--brand-500);
}

.nav__links {
  display: flex;
  align-items: center;
  gap: var(--sp-1);
}

.nav__link {
  padding: var(--sp-2) var(--sp-4);
  border-radius: var(--r-md);
  font-size: var(--fs-md);
  color: var(--ink-600);
  font-weight: 500;
  transition: all 0.15s var(--ease-out);
  position: relative;
}

.nav__link:hover {
  color: var(--ink-900);
  background: var(--ink-100);
}

.nav__link--active {
  color: var(--ink-900);
}

.nav__link--active::after {
  content: '';
  position: absolute;
  bottom: -1px;
  left: 50%;
  transform: translateX(-50%);
  width: 4px;
  height: 4px;
  border-radius: 50%;
  background: var(--brand-500);
}

.nav__actions {
  display: flex;
  align-items: center;
  gap: var(--sp-3);
}

.nav__burger {
  display: none;
  color: var(--ink-600);
}

.nav__mobile {
  display: none;
}

@media (max-width: 640px) {
  .nav__links {
    display: none;
  }

  .nav__burger {
    display: grid;
  }

  .nav__mobile {
    display: flex;
    flex-direction: column;
    padding: var(--sp-2) var(--sp-6) var(--sp-4);
    border-top: 1px solid var(--ink-100);
  }
}
</style>
