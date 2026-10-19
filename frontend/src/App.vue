<script setup lang="ts">
import { computed } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { DataAnalysis, FolderOpened, SwitchButton, Upload, User } from '@element-plus/icons-vue'
import { useAuthStore } from '@/stores/auth'

const route = useRoute()
const router = useRouter()
const auth = useAuthStore()

const isPublic = computed(() => Boolean(route.meta.public))
const activeMenu = computed(() => route.path)
const pageTitle = computed(() => (route.meta.title as string) ?? 'SpringKB-RAG')

function onLogout() {
  auth.logout()
  ElMessage.success('已退出登录')
  router.push('/login')
}
</script>

<template>
  <!-- 公开页面（登录页）独立渲染，不套后台布局 -->
  <router-view v-if="isPublic" />

  <div v-else class="app-shell">
    <aside class="sidebar">
      <div class="brand">
        <div class="brand-mark">KB</div>
        <div class="brand-text">
          <strong>SpringKB-RAG</strong>
          <span>知识库管理后台</span>
        </div>
      </div>
      <el-menu :default-active="activeMenu" router class="nav-menu">
        <el-menu-item index="/">
          <el-icon><DataAnalysis /></el-icon>
          <span>仪表盘</span>
        </el-menu-item>
        <el-menu-item index="/documents">
          <el-icon><FolderOpened /></el-icon>
          <span>文档列表</span>
        </el-menu-item>
        <el-menu-item index="/documents/import">
          <el-icon><Upload /></el-icon>
          <span>文档导入</span>
        </el-menu-item>
      </el-menu>
    </aside>
    <main class="content">
      <header class="topbar">
        <h1>{{ pageTitle }}</h1>
        <div class="topbar-right">
          <span class="topbar-sub">版本过滤 · 权威性加权 · 无答案兜底</span>
          <el-divider direction="vertical" />
          <span class="user-chip">
            <el-icon><User /></el-icon>
            {{ auth.user?.username ?? '已登录' }}
          </span>
          <el-button link type="danger" :icon="SwitchButton" @click="onLogout">
            退出
          </el-button>
        </div>
      </header>
      <section class="page">
        <router-view v-slot="{ Component }">
          <transition name="page-fade" mode="out-in">
            <component :is="Component" />
          </transition>
        </router-view>
      </section>
    </main>
  </div>
</template>

<style scoped>
.topbar-right {
  display: flex;
  align-items: center;
  gap: 6px;
}

.user-chip {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  font-size: 13px;
  color: var(--kb-text);
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
