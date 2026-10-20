<script setup lang="ts">
import { ref } from 'vue'
import NavBar from '@/components/layout/NavBar.vue'
import AppFooter from '@/components/layout/AppFooter.vue'
// 首次启动向导（外壳）：独立布局（meta.standalone），自行套用 NavBar + Footer；
// 三步流程与团队表单为骨架，提交通道禁用，待后端就绪。
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
          <div class="setup__logo">
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
            <p class="setup__empty-title">初始化向导</p>
            <p class="setup__empty-hint">功能开发中 / 待后端就绪，提交通道暂未开放</p>
          </div>

          <div class="setup__actions">
            <button class="btn btn--primary" type="button" disabled>完成初始化</button>
          </div>
        </div>
      </div>
    </main>

    <AppFooter />
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
  background:
    radial-gradient(ellipse 55% 40% at 18% 8%, rgba(251, 146, 60, 0.12) 0%, transparent 60%),
    radial-gradient(ellipse 50% 40% at 85% 15%, rgba(249, 115, 22, 0.1) 0%, transparent 60%),
    var(--page-bg);
}

.setup__stack {
  position: relative;
  width: 520px;
  max-width: 100%;
}

.setup__ghost {
  position: absolute;
  inset: 0;
  background: #fff;
  border: 1px solid var(--ink-200);
  border-radius: var(--r-2xl);
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
  background: #fff;
  border: 1px solid var(--ink-200);
  border-radius: var(--r-2xl);
  padding: var(--sp-10);
  box-shadow: var(--shadow-xl);
}

.setup__logo {
  width: 52px;
  height: 52px;
  border-radius: var(--r-lg);
  background: var(--grad-brand);
  display: grid;
  place-items: center;
  margin: 0 auto var(--sp-4);
  box-shadow: var(--shadow-md);
}

.setup__title {
  text-align: center;
  font-size: var(--fs-3xl);
  font-weight: 800;
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

.setup__step-dot {
  width: 28px;
  height: 28px;
  border-radius: 50%;
  display: grid;
  place-items: center;
  background: var(--ink-100);
  color: var(--ink-400);
  font-size: var(--fs-sm);
  font-weight: 700;
}

.setup__step--active .setup__step-dot {
  background: var(--grad-brand);
  color: #fff;
  box-shadow: var(--glow-brand);
}

.setup__step-label {
  font-size: var(--fs-sm);
  color: var(--ink-400);
  font-weight: 500;
}

.setup__step--active .setup__step-label {
  color: var(--ink-900);
  font-weight: 700;
}

.setup__step-line {
  width: 40px;
  height: 2px;
  background: var(--ink-200);
  margin: 0 var(--sp-2);
}

.setup__body {
  text-align: center;
  padding: var(--sp-10);
  border: 1px dashed var(--ink-200);
  border-radius: var(--r-lg);
}

.setup__empty-title {
  font-size: var(--fs-lg);
  font-weight: 700;
  color: var(--ink-800);
}

.setup__empty-hint {
  margin-top: var(--sp-2);
  font-size: var(--fs-md);
  color: var(--ink-500);
}

.setup__actions {
  display: flex;
  justify-content: center;
  margin-top: var(--sp-8);
}

.btn[disabled] {
  opacity: 0.55;
  cursor: not-allowed;
  transform: none;
  box-shadow: none;
}
</style>
