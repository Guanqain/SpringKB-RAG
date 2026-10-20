<script setup lang="ts">
import { useRouter } from 'vue-router'
// 写文档（外壳）：顶栏 + 大标题输入 + 元信息 radio 卡 + 密钥扫描提示条 + 左右分栏。
// 发布通道未就绪（C8：/api/ugc/*）→ 发布按钮禁用 + 说明；页面不发起任何请求。
const router = useRouter()
</script>

<template>
  <section class="container editor">
    <div class="editor__topbar">
      <button class="editor__back" type="button" @click="router.back()">返回</button>
      <span class="editor__topbar-title">写文档</span>
      <div class="editor__topbar-actions">
        <span class="editor__note">发布能力还没接上 —— 这里写的内容还不会保存</span>
        <button class="btn btn--primary" type="button" disabled title="发布能力还没接上">发布</button>
      </div>
    </div>

    <input
      class="editor__title-input"
      type="text"
      placeholder="输入文档标题..."
      aria-label="文档标题"
    />

    <div class="editor__meta">
      <span class="editor__meta-label">可见性</span>
      <div class="meta-cards">
        <label class="meta-card">
          <input class="meta-card__radio" type="radio" name="visibility" value="team" />
          <span class="meta-card__title">团队可见</span>
          <span class="meta-card__desc">进入知识库，团队成员都能检索到</span>
        </label>
        <label class="meta-card">
          <input class="meta-card__radio" type="radio" name="visibility" value="private" />
          <span class="meta-card__title">仅自己可见</span>
          <span class="meta-card__desc">只有你本人能在知识库中看到它</span>
        </label>
      </div>
    </div>

    <div class="secret-bar" role="status">
      <span class="secret-bar__dot" aria-hidden="true"></span>
      <span class="secret-bar__text">
        密钥扫描：发布前会检查正文里是否混入密钥与凭据 —— 扫描能力还没接上，这里暂不拦截。
      </span>
    </div>

    <div class="editor__split">
      <div class="editor__pane">
        <div class="editor__pane-head">Markdown</div>
        <textarea
          class="editor__textarea"
          placeholder="在这里写 Markdown —— 标题、段落、代码块..."
          aria-label="Markdown 正文"
        ></textarea>
      </div>
      <div class="editor__pane">
        <div class="editor__pane-head">实时预览</div>
        <div class="editor__preview">写下的内容会在这里实时排版。</div>
      </div>
    </div>
  </section>
</template>

<style scoped>
.editor {
  padding-top: var(--sp-4);
  padding-bottom: var(--sp-16);
}

.editor__topbar {
  display: flex;
  align-items: center;
  gap: var(--sp-4);
  padding: var(--sp-6) 0 var(--sp-5);
  border-bottom: 1px solid var(--hairline);
}

.editor__back {
  color: var(--ink-500);
  font-size: var(--fs-md);
  flex-shrink: 0;
}

.editor__back:hover {
  color: var(--brand-600);
}

.editor__topbar-title {
  font-size: var(--fs-lg);
  font-weight: 600;
  color: var(--ink-900);
}

.editor__topbar-actions {
  display: flex;
  align-items: center;
  gap: var(--sp-3);
  margin-left: auto;
  min-width: 0;
}

.editor__note {
  font-size: var(--fs-sm);
  color: var(--ink-500);
}

.editor__title-input {
  width: 100%;
  border: none;
  outline: none;
  background: transparent;
  font-size: var(--fs-4xl);
  font-weight: 600;
  color: var(--ink-900);
  padding: var(--sp-5) 0 var(--sp-4);
  letter-spacing: -0.01em;
}

.editor__title-input::placeholder {
  color: var(--ink-300);
}

.editor__title-input:focus {
  border-radius: var(--r-md);
  box-shadow: var(--ring);
}

/* 元信息：radio 卡（白面 + hairline，选中仅极淡陶土底 + 陶土描边） */
.editor__meta {
  display: flex;
  align-items: flex-start;
  gap: var(--sp-4);
  margin-bottom: var(--sp-5);
}

.editor__meta-label {
  font-size: var(--fs-sm);
  font-weight: 600;
  color: var(--ink-600);
  padding-top: var(--sp-3);
  flex-shrink: 0;
}

.meta-cards {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: var(--sp-3);
  max-width: 560px;
}

.meta-card {
  position: relative;
  display: flex;
  flex-direction: column;
  gap: 2px;
  padding: var(--sp-3) var(--sp-4);
  border: 1px solid var(--hairline);
  border-radius: var(--r-lg);
  background: var(--surface);
  cursor: pointer;
  transition:
    border-color var(--dur-fast) var(--ease-out),
    background-color var(--dur-fast) var(--ease-out);
}

.meta-card:hover {
  border-color: var(--ink-400);
}

.meta-card:has(.meta-card__radio:checked) {
  border-color: var(--brand-600);
  background: var(--brand-50);
}

.meta-card:has(.meta-card__radio:focus-visible) {
  box-shadow: var(--ring);
}

.meta-card__radio {
  position: absolute;
  opacity: 0;
  pointer-events: none;
}

.meta-card__title {
  font-size: var(--fs-md);
  font-weight: 600;
  color: var(--ink-900);
}

.meta-card__desc {
  font-size: var(--fs-xs);
  color: var(--ink-500);
  line-height: 1.6;
}

/* 密钥扫描提示条：克制的 --warning 文字 + 极淡底 */
.secret-bar {
  display: flex;
  align-items: center;
  gap: var(--sp-3);
  padding: var(--sp-3) var(--sp-4);
  background: var(--brand-50);
  border: 1px solid var(--hairline);
  border-radius: var(--r-md);
  margin-bottom: var(--sp-5);
}

.secret-bar__dot {
  width: 6px;
  height: 6px;
  border-radius: 50%;
  background: var(--warning);
  flex-shrink: 0;
}

.secret-bar__text {
  font-size: var(--fs-sm);
  color: var(--warning);
  line-height: 1.7;
}

/* 左右分栏：单一容器 + 1px hairline 竖分隔（非双卡片） */
.editor__split {
  display: grid;
  grid-template-columns: 1fr 1fr;
  align-items: stretch;
  background: var(--surface);
  border: 1px solid var(--hairline);
  border-radius: var(--r-lg);
  overflow: hidden;
}

.editor__pane {
  display: flex;
  flex-direction: column;
  min-width: 0;
}

.editor__pane + .editor__pane {
  border-left: 1px solid var(--hairline);
}

.editor__pane-head {
  padding: var(--sp-2) var(--sp-4);
  font-size: var(--fs-xs);
  font-weight: 600;
  letter-spacing: 0.05em;
  color: var(--ink-500);
  background: var(--surface-sunken);
  border-bottom: 1px solid var(--hairline);
  text-transform: uppercase;
}

.editor__textarea {
  flex: 1;
  min-height: 360px;
  width: 100%;
  border: none;
  outline: none;
  resize: vertical;
  background: var(--surface);
  padding: var(--sp-4);
  font-size: var(--fs-md);
  line-height: 1.7;
  color: var(--ink-700);
}

.editor__textarea::placeholder {
  color: var(--ink-400);
}

.editor__textarea:focus {
  box-shadow: inset var(--ring);
}

.editor__preview {
  flex: 1;
  min-height: 360px;
  padding: var(--sp-4);
  font-size: var(--fs-sm);
  line-height: 1.7;
  color: var(--ink-400);
}

.btn[disabled] {
  opacity: 0.55;
  cursor: not-allowed;
}

@media (max-width: 900px) {
  .editor__split {
    grid-template-columns: 1fr;
  }

  .editor__pane + .editor__pane {
    border-left: none;
    border-top: 1px solid var(--hairline);
  }

  .editor__note {
    display: none;
  }

  .editor__meta {
    flex-direction: column;
    gap: var(--sp-2);
  }

  .editor__meta-label {
    padding-top: 0;
  }

  .meta-cards {
    grid-template-columns: 1fr;
    width: 100%;
  }
}
</style>
