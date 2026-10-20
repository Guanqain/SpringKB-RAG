<script setup lang="ts">
const emit = defineEmits<{
  refine: []
  version: []
}>()
</script>

<template>
  <div class="fb glass">
    <div class="ic">◎</div>
    <b>知识库中未找到相关答案</b>
    <p>当前版本语料未命中（相似度低于阈值，未调用大模型）</p>
    <div class="acts">
      <button type="button" class="gh" @click="emit('refine')">换种问法</button>
      <button type="button" class="gh" @click="emit('version')">切换版本重试</button>
    </div>
  </div>
</template>

<style scoped>
.fb {
  text-align: center;
  padding: 30px 18px;
}

.ic {
  width: 46px;
  height: 46px;
  border-radius: 999px;
  margin: 0 auto 12px;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 20px;
  color: var(--brand-600);
  background: var(--grad-soft);
  border: 1px solid var(--brand-200);
}

@media (prefers-reduced-motion: no-preference) {
  .ic {
    animation: breathe 3s ease-in-out infinite;
  }

  @keyframes breathe {
    0%,
    100% {
      transform: scale(1);
    }
    50% {
      transform: scale(1.06);
    }
  }
}

b {
  font-size: 16px;
  color: var(--ink-900);
}

p {
  color: var(--ink-500);
  font-size: 12px;
  margin: 6px 0 14px;
}

.acts {
  display: flex;
  justify-content: center;
  gap: 10px;
}

.gh {
  border: 1px solid var(--ink-200);
  background: #fff;
  color: var(--ink-700);
  border-radius: var(--r-md);
  padding: 7px 14px;
  font-size: 13px;
  font-family: inherit;
  cursor: pointer;
  transition: border-color 0.12s ease, color 0.12s ease, background 0.12s ease;
}

.gh:hover {
  border-color: var(--brand-400);
  color: var(--brand-700);
  background: var(--brand-50);
}
</style>
