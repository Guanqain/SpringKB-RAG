<script setup lang="ts">
import { useRouter } from 'vue-router'
// 写文档（外壳）：顶栏 + 元信息 + 分栏编辑器骨架，发布通道禁用，待后端就绪。
const router = useRouter()
</script>

<template>
  <section class="container editor">
    <div class="editor__topbar">
      <button class="editor__back" type="button" @click="router.back()">返回</button>
      <span class="editor__topbar-title">写文档</span>
      <button class="btn btn--primary" type="button" disabled>发布</button>
    </div>

    <input class="editor__title-input" placeholder="输入文档标题..." disabled />

    <div class="editor__split">
      <div class="editor__pane">
        <div class="editor__pane-head">Markdown</div>
        <div class="editor__placeholder">功能开发中 / 待后端就绪</div>
      </div>
      <div class="editor__pane">
        <div class="editor__pane-head">实时预览</div>
        <div class="editor__placeholder">功能开发中 / 待后端就绪</div>
      </div>
    </div>

    <p class="editor__tip">发布与密钥扫描能力待后端就绪后开放。</p>
  </section>
</template>

<style scoped>
.editor {
  padding-top: var(--sp-4);
  padding-bottom: var(--sp-16);
}

.editor__topbar {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: var(--sp-6) 0 var(--sp-4);
}

.editor__back {
  color: var(--ink-500);
  font-size: var(--fs-md);
  cursor: pointer;
}

.editor__back:hover {
  color: var(--brand-600);
}

.editor__topbar-title {
  font-size: var(--fs-lg);
  font-weight: 700;
  color: var(--ink-900);
}

.btn[disabled] {
  opacity: 0.55;
  cursor: not-allowed;
  transform: none;
  box-shadow: none;
}

.editor__title-input {
  width: 100%;
  border: none;
  outline: none;
  background: transparent;
  font-size: var(--fs-4xl);
  font-weight: 800;
  color: var(--ink-900);
  padding: var(--sp-2) 0;
  letter-spacing: -0.01em;
  margin-bottom: var(--sp-5);
}

.editor__title-input::placeholder {
  color: var(--ink-300);
}

.editor__split {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: var(--sp-4);
  align-items: stretch;
}

.editor__pane {
  border-radius: var(--r-lg);
  overflow: hidden;
  border: 1px solid var(--ink-200);
  background: #fff;
  display: flex;
  flex-direction: column;
}

.editor__pane-head {
  padding: var(--sp-2) var(--sp-4);
  font-size: var(--fs-xs);
  font-weight: 700;
  letter-spacing: 0.05em;
  color: var(--ink-400);
  background: var(--ink-50);
  border-bottom: 1px solid var(--ink-200);
  text-transform: uppercase;
}

.editor__placeholder {
  min-height: 380px;
  display: grid;
  place-items: center;
  font-size: var(--fs-sm);
  color: var(--ink-400);
}

.editor__tip {
  margin: var(--sp-5) 0 0;
  font-size: var(--fs-sm);
  color: var(--ink-500);
}

@media (max-width: 900px) {
  .editor__split {
    grid-template-columns: 1fr;
  }
}
</style>
