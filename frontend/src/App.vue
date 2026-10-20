<script setup lang="ts">
import { computed } from 'vue'
import { useRoute } from 'vue-router'
import NavBar from '@/components/layout/NavBar.vue'
import AppFooter from '@/components/layout/AppFooter.vue'

const route = useRoute()

/** 独立页（登录/Setup 等）：仅渲染 router-view，不套导航/页脚 */
const standalone = computed(() => Boolean(route.meta.standalone))

/** 管理端：路由组件即 AdminLayout（内含深色侧边栏 + 子 router-view），不渲染站点 Footer */
const isAdmin = computed(
  () => route.path === '/admin' || route.path.startsWith('/admin/'),
)
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
    <AppFooter v-if="!isAdmin" />
  </template>
</template>

<style scoped>
.app-body {
  flex: 1;
  display: flex;
  flex-direction: column;
  min-width: 0;
}

.page-fade-enter-active,
.page-fade-leave-active {
  transition: opacity 0.18s ease, transform 0.18s ease;
}

.page-fade-enter-from {
  opacity: 0;
  transform: translateY(6px);
}

.page-fade-leave-to {
  opacity: 0;
}
</style>
