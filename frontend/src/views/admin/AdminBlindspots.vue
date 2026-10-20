<script setup lang="ts">
// 管理后台 · 知识盲区（外壳）：仅面板骨架 + 空态，盲区统计能力尚未接入。
import { useRouter } from 'vue-router'

const router = useRouter()
</script>

<template>
  <div>
    <div class="page-head">
      <h2 class="page-head__title">知识盲区</h2>
      <p class="page-head__sub">高频兜底问题与知识库薄弱环节</p>
    </div>

    <section class="panel">
      <div class="empty">
        <p class="empty__title">暂时没有知识盲区 —— 这其实是个好消息</p>
        <p class="empty__hint">等问答量上来，命中不到的提问会在这里暴露出来。</p>
        <el-button @click="router.push('/qa')">去问答</el-button>
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
