<script setup lang="ts">
const emit = defineEmits<{
  refine: []
  version: []
}>()
</script>

<template>
  <div class="fb">
    <b>知识库中未找到相关答案</b>
    <p>当前版本语料未命中（相似度低于阈值，未调用大模型）</p>
    <div class="acts">
      <button type="button" class="txt-btn" @click="emit('refine')">换种问法</button>
      <button type="button" class="txt-btn" @click="emit('version')">切换版本重试</button>
    </div>
  </div>
</template>

<style scoped>
/* 兜底态：纸面文字 + 文字按钮（无卡片外壳、无彩色底） */
.fb {
  padding: var(--sp-5) 0;
  color: var(--ink-500);
}

.fb b {
  display: block;
  font-size: var(--fs-lg);
  font-weight: 600;
  color: var(--ink-700);
}

.fb p {
  margin: var(--sp-2) 0 var(--sp-4);
  font-size: var(--fs-sm);
  color: var(--ink-500);
}

.acts {
  display: flex;
  gap: var(--sp-5);
}

.txt-btn {
  padding: 0;
  font-size: var(--fs-md);
  color: var(--ink-500);
  cursor: pointer;
  text-decoration: underline;
  text-decoration-color: var(--hairline-strong);
  text-underline-offset: 3px;
  transition: color var(--dur-fast) var(--ease-out);
}

.txt-btn:hover {
  color: var(--brand-600);
  text-decoration-color: var(--brand-600);
}
</style>
