<script setup lang="ts">
import { computed, ref } from 'vue'
import { useAuthStore } from '@/stores/auth'
// 个人中心（外壳）：资料卡 + tabs 骨架，文档/收藏/统计待后端就绪 → 空态。
const auth = useAuthStore()

const tabs = ['我的文档', '我的收藏', '账号设置']
const activeTab = ref(tabs[0])

const avatarText = computed(() => (auth.user?.username ?? '?').slice(0, 1).toUpperCase())
</script>

<template>
  <section class="container me">
    <div class="me-layout">
      <aside class="me-card">
        <div class="me-card__avatar">{{ avatarText }}</div>
        <div class="me-card__name">{{ auth.user?.username ?? '未登录' }}</div>
        <div class="me-card__join">
          <span v-if="auth.user?.role === 'admin'" class="badge--admin">admin</span>
        </div>
      </aside>

      <div>
        <div class="tabs">
          <button
            v-for="tab in tabs"
            :key="tab"
            class="tab"
            :class="{ 'tab--active': activeTab === tab }"
            type="button"
            @click="activeTab = tab"
          >
            {{ tab }}
          </button>
        </div>

        <div class="card empty">
          <p class="empty__title">{{ activeTab }}</p>
          <p class="empty__hint">功能开发中 / 待后端就绪</p>
        </div>
      </div>
    </div>
  </section>
</template>

<style scoped>
.me {
  padding-top: var(--sp-12);
  padding-bottom: var(--sp-16);
}

.me-layout {
  display: grid;
  grid-template-columns: 280px 1fr;
  gap: var(--sp-6);
  align-items: start;
}

.me-card {
  background: #fff;
  border: 1px solid var(--ink-200);
  border-radius: var(--r-xl);
  padding: var(--sp-8) var(--sp-6);
  text-align: center;
  position: sticky;
  top: 84px;
}

.me-card__avatar {
  width: 88px;
  height: 88px;
  border-radius: 50%;
  background: var(--grad-brand);
  display: grid;
  place-items: center;
  margin: 0 auto var(--sp-4);
  color: #fff;
  font-size: var(--fs-3xl);
  font-weight: 800;
  box-shadow: var(--shadow-md);
}

.me-card__name {
  font-size: var(--fs-2xl);
  font-weight: 800;
  color: var(--ink-900);
}

.me-card__join {
  margin-top: var(--sp-2);
}

.tabs {
  display: flex;
  gap: var(--sp-1);
  border-bottom: 1px solid var(--ink-200);
  margin-bottom: var(--sp-6);
}

.tab {
  padding: var(--sp-3) var(--sp-4);
  font-size: var(--fs-md);
  font-weight: 600;
  color: var(--ink-500);
  cursor: pointer;
  border-bottom: 2px solid transparent;
  margin-bottom: -1px;
}

.tab--active {
  color: var(--brand-600);
  border-bottom-color: var(--brand-500);
}

.empty {
  text-align: center;
  padding: var(--sp-12);
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

@media (max-width: 900px) {
  .me-layout {
    grid-template-columns: 1fr;
  }

  .me-card {
    position: static;
  }
}
</style>
