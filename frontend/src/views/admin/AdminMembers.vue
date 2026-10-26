<script setup lang="ts">
// 管理后台 · 成员管理：真实成员列表 + 角色切换（禁止修改自己的角色）。
import { onMounted, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { fetchMembers, updateMemberRole } from '@/api/member'
import { useAuthStore } from '@/stores/auth'
import type { Member, Role } from '@/types/admin'

const auth = useAuthStore()
const members = ref<Member[]>([])
const loading = ref(false)
const savingId = ref<number | null>(null)

async function load() {
  loading.value = true
  try {
    members.value = await fetchMembers()
  } catch {
    /* 拦截器已提示 */
  } finally {
    loading.value = false
  }
}

function isSelf(member: Member) {
  return member.id === auth.user?.id
}

async function changeRole(member: Member, role: string | number | boolean | undefined) {
  const next = role as Role
  if (next === member.role) return
  savingId.value = member.id
  try {
    const updated = await updateMemberRole(member.id, next)
    const idx = members.value.findIndex((m) => m.id === member.id)
    if (idx >= 0) members.value[idx] = updated
    ElMessage.success(`已将 ${member.username} 设为${next === 'ADMIN' ? '管理员' : '成员'}`)
  } catch {
    // 失败时回滚下拉显示
    const idx = members.value.findIndex((m) => m.id === member.id)
    if (idx >= 0) members.value[idx] = { ...member }
  } finally {
    savingId.value = null
  }
}

onMounted(load)
</script>

<template>
  <div>
    <div class="page-head">
      <h2 class="page-head__title">成员管理</h2>
      <p class="page-head__sub">管理员可管理文档与成员；成员可读写团队文档与私有文档</p>
    </div>

    <div class="card table-card">
      <el-table v-loading="loading" :data="members" style="width: 100%">
        <el-table-column prop="username" label="用户名" min-width="180">
          <template #default="{ row }">
            <span class="member-name">{{ row.username }}</span>
            <span v-if="isSelf(row)" class="badge badge--neutral member-self">我</span>
          </template>
        </el-table-column>
        <el-table-column label="邮箱" min-width="220">
          <template #default="{ row }">
            <span class="member-email">{{ row.email || '未绑定' }}</span>
          </template>
        </el-table-column>
        <el-table-column label="角色" width="180">
          <template #default="{ row }">
            <el-select
              :model-value="row.role"
              size="small"
              :disabled="isSelf(row) || savingId === row.id"
              class="role-select"
              @change="(value: string | number | boolean | undefined) => changeRole(row, value)"
            >
              <el-option label="管理员 ADMIN" value="ADMIN" />
              <el-option label="成员 MEMBER" value="MEMBER" />
            </el-select>
          </template>
        </el-table-column>
        <el-table-column label="加入时间" width="180">
          <template #default="{ row }">
            <span class="time-cell">{{ row.createdAt }}</span>
          </template>
        </el-table-column>

        <template #empty>
          <div class="empty">
            <p class="empty__title">还没有其他成员</p>
            <p class="empty__hint">成员通过注册页绑定邮箱并验证后即可加入团队。</p>
          </div>
        </template>
      </el-table>
    </div>
  </div>
</template>

<style scoped>
.table-card {
  background: var(--surface);
  border: 1px solid var(--hairline);
  border-radius: var(--r-lg);
  padding: var(--sp-5);
}

.table-card :deep(.el-table) {
  --el-table-border-color: var(--hairline);
  --el-table-header-bg-color: var(--surface-sunken);
  --el-table-row-hover-bg-color: #f5f5f4;
  background: var(--surface);
}

.table-card :deep(.el-table th.el-table__cell) {
  background: var(--surface-sunken);
  font-size: var(--fs-sm);
  font-weight: 600;
  color: var(--ink-600);
}

.table-card :deep(.el-table td.el-table__cell),
.table-card :deep(.el-table th.el-table__cell) {
  border-right: none;
}

.member-name {
  font-weight: 600;
  font-size: 13.5px;
  color: var(--ink-900);
}

.member-self {
  margin-left: var(--sp-2);
}

.member-email {
  font-size: 12.5px;
  color: var(--ink-600);
}

.role-select {
  width: 150px;
}

.time-cell {
  font-size: 12.5px;
  color: var(--ink-500);
}

.empty {
  padding: var(--sp-12) var(--sp-6);
  text-align: center;
}

.empty__title {
  font-size: var(--fs-lg);
  font-weight: 600;
  color: var(--ink-900);
}

.empty__hint {
  margin: var(--sp-2) 0 0;
  font-size: var(--fs-sm);
  color: var(--ink-500);
}
</style>
