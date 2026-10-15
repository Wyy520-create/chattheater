<script setup>
/** 情绪标签：与后端 EmotionUtil 的 7 类对齐。 */
const props = defineProps({
  emotion: { type: String, default: '' }
})

const MAP = {
  joy: { label: '开心', icon: '😄' },
  angry: { label: '生气', icon: '😠' },
  sad: { label: '难过', icon: '😢' },
  shock: { label: '震惊', icon: '😲' },
  jealousy: { label: '酸了', icon: '🍋' },
  pride: { label: '得意', icon: '😎' },
  neutral: { label: '平静', icon: '🙂' }
}
</script>

<template>
  <span v-if="emotion && MAP[emotion]" class="emo" :class="`emo-${emotion}`">
    {{ MAP[emotion].icon }} {{ MAP[emotion].label }}
  </span>
</template>

<style scoped>
.emo {
  font-size: 11px;
  padding: 1px 8px;
  border-radius: 999px;
  white-space: nowrap;
  opacity: 0.95;
}

.emo-joy { color: var(--emo-joy); background: rgba(255, 209, 102, 0.12); }
.emo-angry { color: var(--emo-angry); background: rgba(255, 107, 107, 0.12); }
.emo-sad { color: var(--emo-sad); background: rgba(116, 160, 255, 0.12); }
.emo-shock { color: var(--emo-shock); background: rgba(255, 159, 67, 0.12); }
.emo-jealousy { color: var(--emo-jealousy); background: rgba(167, 139, 250, 0.12); }
.emo-pride { color: var(--emo-pride); background: rgba(78, 205, 196, 0.12); }
.emo-neutral { color: var(--emo-neutral); background: rgba(138, 147, 173, 0.12); }
</style>
