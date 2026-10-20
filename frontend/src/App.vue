<script setup lang="ts">
import { computed } from 'vue'
import { useRoute } from 'vue-router'
import NavBar from '@/components/layout/NavBar.vue'
import AppFooter from '@/components/layout/AppFooter.vue'

const route = useRoute()

/** 独立页（登录/Setup 等）：仅渲染 router-view，不套导航/页脚 */
const standalone = computed(() => Boolean(route.meta.standalone))

/** 站点 Footer 只出现在首页：功能页与管理端不渲染，避免过重的页面底部介绍 */
const showFooter = computed(() => route.name === 'home')
</script>

<template>
  <router-view v-if="standalone" />

  <template v-else>
    <NavBar />
    <div class="app-body">
      <router-view v-slot="{ Component }">
        <transition name="page-fade" mode="out-in">
          <component :is="Component" :key="route.path" />
        </transition>
      </router-view>
    </div>
    <AppFooter v-if="showFooter" />
  </template>
</template>

<style scoped>
.app-body {
  flex: 1;
  display: flex;
  flex-direction: column;
  min-width: 0;
  background: var(--page-bg);
}

.page-fade-enter-active,
.page-fade-leave-active {
  transition: opacity var(--dur-base) var(--ease-out);
}

.page-fade-enter-from,
.page-fade-leave-to {
  opacity: 0;
}
</style>
