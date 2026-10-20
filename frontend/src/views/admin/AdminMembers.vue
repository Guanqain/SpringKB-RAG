<script setup lang="ts">
// 管理后台 · 成员管理（外壳）：仅面板骨架 + 空态，成员能力尚未接入。
</script>

<template>
  <div>
    <div class="page-head">
      <h2 class="page-head__title">成员管理</h2>
      <p class="page-head__sub">团队成员的邀请与角色管理</p>
    </div>

    <section class="panel">
      <div class="empty">
        <p class="empty__title">团队里只有你一个人 —— 先把同事拉进来</p>
        <p class="empty__hint">成员邀请与角色分配能力尚未接入，接入后这里会列出全部成员。</p>
        <el-button type="primary" disabled title="成员邀请能力尚未接入，暂时无法发送邀请">
          邀请成员
        </el-button>
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
