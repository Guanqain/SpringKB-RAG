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
