<script setup>
defineProps({
  icon: { type: String, default: '🎭' },
  title: { type: String, default: '这里空空如也' },
  desc: { type: String, default: '' }
})
</script>

<template>
  <div class="empty">
    <div class="empty-icon">{{ icon }}</div>
    <div class="empty-title">{{ title }}</div>
    <div v-if="desc" class="empty-desc">{{ desc }}</div>
    <div class="empty-act"><slot /></div>
  </div>
</template>

<style scoped>
.empty-title {
  font-size: 15px;
  font-weight: 600;
  color: var(--text-2);
}

.empty-desc {
  margin-top: 6px;
  font-size: 13px;
  color: var(--text-3);
}

.empty-act {
  margin-top: 16px;
}
</style>
