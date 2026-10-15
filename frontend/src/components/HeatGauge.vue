<script setup>
/** 热度仪表：0~100，用色带表现"吵架激烈程度"。 */
defineProps({
  heat: { type: Number, default: 0 }
})

function heatWord(h) {
  if (h >= 85) return '战况惨烈'
  if (h >= 60) return '火药味十足'
  if (h >= 35) return '逐渐上头'
  if (h > 0) return '小试牛刀'
  return '风平浪静'
}
</script>

<template>
  <div class="heat">
    <div class="heat-head">
      <span class="heat-flame">🔥</span>
      <span class="heat-num">{{ heat }}</span>
      <span class="heat-word">{{ heatWord(heat) }}</span>
    </div>
    <div class="heat-bar">
      <i :style="{ width: Math.min(100, heat) + '%' }" />
    </div>
  </div>
</template>

<style scoped>
.heat {
  width: 100%;
}

.heat-head {
  display: flex;
  align-items: baseline;
  gap: 6px;
  margin-bottom: 6px;
}

.heat-flame {
  font-size: 15px;
}

.heat-num {
  font-size: 22px;
  font-weight: 800;
  color: var(--heat);
  font-family: var(--font-mono);
  line-height: 1;
}

.heat-word {
  font-size: 12px;
  color: var(--text-3);
}

.heat-bar {
  height: 7px;
  border-radius: 999px;
  background: var(--bg-soft);
  overflow: hidden;
}

.heat-bar i {
  display: block;
  height: 100%;
  border-radius: 999px;
  background: linear-gradient(90deg, #ffb02e, #ff5a5f);
  transition: width 0.6s ease;
}
</style>
