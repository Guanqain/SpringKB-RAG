<script setup lang="ts">
import { computed } from 'vue'
import { useRoute } from 'vue-router'
import { DataAnalysis, FolderOpened, Upload } from '@element-plus/icons-vue'

const route = useRoute()
const activeMenu = computed(() => route.path)
const pageTitle = computed(() => (route.meta.title as string) ?? 'SpringKB-RAG')
</script>

<template>
  <div class="app-shell">
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
        <span class="topbar-sub">版本过滤 · 权威性加权 · 无答案兜底</span>
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
