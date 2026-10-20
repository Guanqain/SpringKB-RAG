<script setup lang="ts">
defineProps<{
  title: string
  subtitle?: string
}>()
</script>

<template>
  <header class="page-head">
    <div v-if="$slots.breadcrumb" class="crumb">
      <slot name="breadcrumb" />
    </div>
    <h2 class="page-head__title">{{ title }}</h2>
    <p v-if="subtitle" class="page-head__sub">{{ subtitle }}</p>
  </header>
</template>

<style scoped>
/* 页头底部 hairline 分隔（标题/副标题/面包屑沿用 theme.css 全局定义，不重复声明） */
.page-head {
  padding-bottom: var(--sp-6);
  border-bottom: 1px solid var(--hairline);
}
</style>
