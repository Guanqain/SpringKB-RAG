<script setup lang="ts">
import { ref } from 'vue'
import NavBar from '@/components/layout/NavBar.vue'
// 首次启动向导（外壳）：独立布局（meta.standalone），仅套用 NavBar，不渲染 Footer（Footer 只在首页）；
// 三步流程与团队表单为骨架，提交通道禁用（C1：无 /api/setup/*），不做任何请求或提交。
const steps = ['团队信息', '知识库配置', '完成']
const activeStep = ref(0)
</script>

<template>
  <div class="setup-page">
    <NavBar />

    <main class="setup">
      <div class="setup__stack">
        <div class="setup__ghost"></div>
        <div class="setup__ghost setup__ghost--2"></div>

        <div class="setup__card">
          <div class="setup__brand">
            <svg width="24" height="24" viewBox="0 0 24 24">
              <path d="M4 4h16v4H4V4zm0 8h10v8H4v-8zm12 0h4v8h-4v-8z" fill="#fff" />
            </svg>
          </div>
          <h1 class="setup__title">欢迎使用 SpringKB</h1>
          <p class="setup__sub">三步完成团队知识库初始化</p>

          <div class="setup__steps">
            <template v-for="(step, index) in steps" :key="step">
              <div class="setup__step" :class="{ 'setup__step--active': index === activeStep }">
                <span class="setup__step-dot">{{ index + 1 }}</span>
                <span class="setup__step-label">{{ step }}</span>
              </div>
              <span v-if="index < steps.length - 1" class="setup__step-line"></span>
            </template>
          </div>

          <div class="setup__body">
            <p class="setup__lead">先给你的团队起个名字 —— 这一步定下来，后面的知识才有个家</p>
            <div class="setup__form">
              <label class="setup__field">
                <span class="setup__field-label">团队名称</span>
                <input class="setup__input" type="text" placeholder="例如：平台架构组" />
              </label>
              <label class="setup__field">
                <span class="setup__field-label">知识库说明</span>
                <input class="setup__input" type="text" placeholder="一句话说明这里收录什么" />
              </label>
            </div>
          </div>

          <div class="setup__actions">
            <button class="btn btn--primary" type="button" disabled title="初始化能力还没接上">
              完成初始化
            </button>
          </div>
        </div>
      </div>
    </main>
  </div>
</template>

<style scoped>
.setup-page {
  display: flex;
  flex-direction: column;
  min-height: 100vh;
}

.setup {
  flex: 1;
  display: grid;
  place-items: center;
  padding: var(--sp-12) var(--sp-4);
  background: var(--page-bg);
}

.setup__stack {
  position: relative;
  width: 520px;
  max-width: 100%;
}

/* 居中叠卡：白面 + hairline，无阴影 */
.setup__ghost {
  position: absolute;
  inset: 0;
  background: var(--surface);
  border: 1px solid var(--hairline);
  border-radius: var(--r-xl);
  opacity: 0.45;
  transform: scale(0.96) translateY(14px);
  pointer-events: none;
}

.setup__ghost--2 {
  opacity: 0.25;
  transform: scale(0.92) translateY(28px);
}

.setup__card {
  position: relative;
  background: var(--surface);
  border: 1px solid var(--hairline);
  border-radius: var(--r-xl);
  padding: var(--sp-10);
}

.setup__brand {
  width: 52px;
  height: 52px;
  border-radius: var(--r-lg);
  background: var(--ink-900);
  display: grid;
  place-items: center;
  margin: 0 auto var(--sp-4);
}

.setup__title {
  text-align: center;
  font-size: var(--fs-3xl);
  font-weight: 600;
  color: var(--ink-900);
}

.setup__sub {
  text-align: center;
  color: var(--ink-500);
  margin: var(--sp-2) 0 var(--sp-8);
}

.setup__steps {
  display: flex;
  align-items: center;
  justify-content: center;
  margin-bottom: var(--sp-8);
}

.setup__step {
  display: flex;
  align-items: center;
  gap: var(--sp-2);
}

/* 步骤点：墨色实心；当前步为陶土点 */
.setup__step-dot {
  width: 28px;
  height: 28px;
  border-radius: 50%;
  display: grid;
  place-items: center;
  background: var(--ink-900);
  color: #fff;
  font-size: var(--fs-sm);
  font-weight: 600;
}

.setup__step--active .setup__step-dot {
  background: var(--brand-600);
}

.setup__step-label {
  font-size: var(--fs-sm);
  color: var(--ink-500);
}

.setup__step--active .setup__step-label {
  color: var(--ink-900);
  font-weight: 600;
}

.setup__step-line {
  width: 40px;
  height: 1px;
  background: var(--hairline);
  margin: 0 var(--sp-2);
}

.setup__body {
  text-align: center;
}

.setup__lead {
  font-size: var(--fs-md);
  color: var(--ink-600);
  line-height: 1.7;
  margin-bottom: var(--sp-6);
}

/* 团队信息表单外壳（无请求、无提交） */
.setup__form {
  display: flex;
  flex-direction: column;
  gap: var(--sp-4);
  text-align: left;
}

.setup__field {
  display: flex;
  flex-direction: column;
  gap: var(--sp-2);
}

.setup__field-label {
  font-size: var(--fs-sm);
  font-weight: 600;
  color: var(--ink-600);
}

.setup__input {
  border: 1px solid var(--hairline);
  border-radius: var(--r-md);
  padding: var(--sp-3) var(--sp-4);
  font-size: var(--fs-md);
  color: var(--ink-900);
  background: var(--surface);
  outline: none;
  transition: box-shadow var(--dur-fast) var(--ease-out);
}

.setup__input::placeholder {
  color: var(--ink-400);
}

.setup__input:focus {
  box-shadow: var(--ring);
  border-color: transparent;
}

.setup__actions {
  display: flex;
  justify-content: center;
  margin-top: var(--sp-8);
}

.btn[disabled] {
  opacity: 0.55;
  cursor: not-allowed;
}
</style>
