<script setup lang="ts">
// 管理后台 · 问答统计（外壳）：待后端就绪。
</script>

<template>
  <div>
    <div class="page-head">
      <h2 class="page-head__title">问答统计</h2>
      <p class="page-head__sub">命中率 · 提问量 · 引用溯源趋势</p>
    </div>

    <div class="card empty">
      <p class="empty__title">问答统计</p>
      <p class="empty__hint">功能开发中 / 待后端就绪</p>
    </div>
  </div>
</template>

<style scoped>
.empty {
  text-align: center;
  padding: var(--sp-12);
  border-style: dashed;
  box-shadow: none;
}

.empty__title {
  font-size: var(--fs-lg);
  font-weight: 700;
  color: var(--ink-800);
}

.empty__hint {
  margin-top: var(--sp-2);
  font-size: var(--fs-md);
  color: var(--ink-500);
}
</style>
