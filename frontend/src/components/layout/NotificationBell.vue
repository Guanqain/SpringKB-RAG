<script setup lang="ts">
// 顶部通知铃铛：审核结果站内通知（未读 badge + 下拉列表 + 单条/全部已读）。
import { onMounted, onUnmounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { useAuthStore } from '@/stores/auth'
import {
  fetchNotifications,
  markAllNotificationsRead,
  markNotificationRead,
} from '@/api/me'
import type { NotificationItem } from '@/types/me'

const auth = useAuthStore()
const router = useRouter()

const open = ref(false)
const loading = ref(false)
const items = ref<NotificationItem[]>([])
const unread = ref(0)

async function refresh() {
  if (!auth.isAuthenticated) return
  loading.value = true
  try {
    const data = await fetchNotifications(20)
    items.value = data.items
    unread.value = data.unread
  } catch {
    /* 拦截器已提示（未登录会跳转） */
  } finally {
    loading.value = false
  }
}

function toggle() {
  open.value = !open.value
  if (open.value) void refresh()
}

async function onItemClick(item: NotificationItem) {
  open.value = false
  if (!item.read) {
    try {
      await markNotificationRead(item.id)
      item.read = true
      unread.value = Math.max(0, unread.value - 1)
    } catch {
      /* 拦截器已提示 */
    }
  }
  // 审核状态在「个人中心 · 我的文档」中查看（驳回文档不进公共知识库）
  router.push('/me')
}

async function onReadAll() {
  if (!unread.value) return
  try {
    const updated = await markAllNotificationsRead()
    items.value = items.value.map((item) => ({ ...item, read: true }))
    unread.value = 0
    ElMessage.success(updated ? `已标记 ${updated} 条为已读` : '没有未读通知')
  } catch {
    /* 拦截器已提示 */
  }
}

let timer: ReturnType<typeof setInterval> | undefined

onMounted(() => {
  void refresh()
  // 轮询保持未读数新鲜；页面隐藏时浏览器会自然降频
  timer = setInterval(() => void refresh(), 60_000)
})

onUnmounted(() => {
  if (timer) clearInterval(timer)
})
</script>

<template>
  <div
    v-if="auth.isAuthenticated"
    class="bell"
    @mouseenter="open = true"
    @mouseleave="open = false"
  >
    <button class="bell__btn" type="button" title="通知" @click="toggle">
      <svg
        width="18"
        height="18"
        viewBox="0 0 24 24"
        fill="none"
        stroke="currentColor"
        stroke-width="2"
        stroke-linecap="round"
        stroke-linejoin="round"
      >
        <path d="M18 8a6 6 0 0 0-12 0c0 7-3 9-3 9h18s-3-2-3-9" />
        <path d="M13.73 21a2 2 0 0 1-3.46 0" />
      </svg>
      <span v-if="unread > 0" class="bell__badge">{{ unread > 99 ? '99+' : unread }}</span>
    </button>

    <div class="bell__dropdown" :class="{ 'is-open': open }">
      <div class="bell__head">
        <span class="bell__title">通知</span>
        <button
          class="bell__read-all"
          type="button"
          :disabled="!unread"
          @click="onReadAll"
        >
          全部已读
        </button>
      </div>

      <div v-loading="loading" class="bell__list">
        <button
          v-for="item in items"
          :key="item.id"
          class="bell__item"
          :class="{ 'bell__item--unread': !item.read }"
          type="button"
          @click="onItemClick(item)"
        >
          <span class="bell__dot" :class="{ 'is-unread': !item.read }"></span>
          <span class="bell__body">
            <span class="bell__item-title">{{ item.title }}</span>
            <span v-if="item.body" class="bell__item-body">{{ item.body }}</span>
            <span class="bell__time">{{ item.createdAt }}</span>
          </span>
        </button>
        <p v-if="!loading && !items.length" class="bell__empty">暂无通知</p>
      </div>
    </div>
  </div>
</template>

<style scoped>
.bell {
  position: relative;
}

.bell__btn {
  position: relative;
  width: 38px;
  height: 38px;
  border-radius: 50%;
  display: grid;
  place-items: center;
  background: var(--surface);
  border: 1px solid var(--hairline);
  color: var(--ink-600);
  cursor: pointer;
  transition:
    background-color var(--dur-fast) var(--ease-out),
    border-color var(--dur-fast) var(--ease-out),
    color var(--dur-fast) var(--ease-out);
}

.bell__btn:hover {
  background: var(--ink-100);
  color: var(--ink-900);
  border-color: var(--hairline-strong);
}

.bell__badge {
  position: absolute;
  top: -2px;
  right: -2px;
  min-width: 16px;
  height: 16px;
  padding: 0 4px;
  border-radius: 8px;
  background: var(--brand-600);
  color: #fff;
  font-size: 10px;
  font-weight: 600;
  line-height: 16px;
  text-align: center;
}

.bell__dropdown {
  position: absolute;
  top: calc(100% + 10px);
  right: 0;
  width: 320px;
  max-height: 420px;
  display: flex;
  flex-direction: column;
  background: var(--surface);
  border: 1px solid var(--hairline);
  border-radius: var(--r-lg);
  box-shadow: var(--shadow-overlay-sm);
  padding: var(--sp-2);
  opacity: 0;
  visibility: hidden;
  transform: translateY(-6px);
  transition:
    opacity var(--dur-fast) var(--ease-out),
    transform var(--dur-fast) var(--ease-out),
    visibility var(--dur-fast) var(--ease-out);
  z-index: 60;
}

.bell__dropdown.is-open {
  opacity: 1;
  visibility: visible;
  transform: translateY(0);
}

.bell__head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: var(--sp-2) var(--sp-3) var(--sp-3);
  border-bottom: 1px solid var(--hairline);
  margin-bottom: var(--sp-2);
}

.bell__title {
  font-size: var(--fs-sm);
  font-weight: 600;
  color: var(--ink-900);
}

.bell__read-all {
  border: none;
  background: transparent;
  font-size: var(--fs-xs);
  color: var(--brand-700);
  cursor: pointer;
}

.bell__read-all:disabled {
  color: var(--ink-300);
  cursor: default;
}

.bell__list {
  overflow-y: auto;
  min-height: 60px;
}

.bell__item {
  display: flex;
  gap: var(--sp-2);
  width: 100%;
  text-align: left;
  padding: 8px var(--sp-3);
  border: none;
  background: transparent;
  border-radius: var(--r-md);
  cursor: pointer;
}

.bell__item:hover {
  background: var(--surface-sunken);
}

.bell__dot {
  width: 6px;
  height: 6px;
  border-radius: 50%;
  margin-top: 6px;
  background: transparent;
  flex-shrink: 0;
}

.bell__dot.is-unread {
  background: var(--brand-600);
}

.bell__body {
  display: flex;
  flex-direction: column;
  gap: 2px;
  min-width: 0;
}

.bell__item-title {
  font-size: var(--fs-sm);
  color: var(--ink-800);
  font-weight: 500;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.bell__item--unread .bell__item-title {
  color: var(--ink-900);
  font-weight: 600;
}

.bell__item-body {
  font-size: var(--fs-xs);
  color: var(--ink-500);
  line-height: 1.5;
  display: -webkit-box;
  -webkit-line-clamp: 2;
  -webkit-box-orient: vertical;
  overflow: hidden;
}

.bell__time {
  font-size: 11px;
  color: var(--ink-400);
}

.bell__empty {
  padding: var(--sp-6) 0;
  text-align: center;
  font-size: var(--fs-sm);
  color: var(--ink-400);
}
</style>
