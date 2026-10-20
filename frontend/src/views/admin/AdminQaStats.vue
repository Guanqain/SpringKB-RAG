<script setup lang="ts">
// 管理后台 · 问答统计（外壳）：仅面板骨架 + 空态，统计能力尚未接入。
import { useRouter } from 'vue-router'

const router = useRouter()
</script>

<template>
  <div>
    <div class="page-head">
      <h2 class="page-head__title">问答统计</h2>
      <p class="page-head__sub">命中率 · 提问量 · 引用溯源趋势</p>
    </div>

    <section class="panel">
      <div class="empty">
        <p class="empty__title">还没有足够的问题可以统计 —— 第一个问题往往最有价值</p>
        <p class="empty__hint">问答积累起来后，命中率与引用趋势会在这里汇总。</p>
        <el-button type="primary" @click="router.push('/qa')">去问答</el-button>
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
