<script setup lang="ts">
// 管理后台 · 待复审（外壳）：仅面板骨架 + 空态，复审能力尚未接入。
import { useRouter } from 'vue-router'

const router = useRouter()
</script>

<template>
  <div>
    <div class="page-head">
      <h2 class="page-head__title">待复审</h2>
      <p class="page-head__sub">超期文档与复审提醒</p>
    </div>

    <section class="panel">
      <div class="empty">
        <p class="empty__title">没有待复审的内容 —— 看起来很干净</p>
        <p class="empty__hint">文档超期与复审提醒会在有复审数据后出现在这里。</p>
        <el-button type="primary" @click="router.push('/admin/documents')">去文档管理</el-button>
      </div>
    </section>
  </div>
</template>

<style scoped>
.panel {
  background: var(--surface);
  border: 1px solid var(--hairline);
  border-radius: var(--r-lg);
  padding: var(--sp-16) var(--sp-6);
}

.empty {
  text-align: center;
}

.empty__title {
  font-size: var(--fs-lg);
  font-weight: 600;
  color: var(--ink-900);
}

.empty__hint {
  margin: var(--sp-2) 0 var(--sp-6);
  font-size: var(--fs-sm);
  color: var(--ink-500);
}
</style>
