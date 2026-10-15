<script setup>
/**
 * 我的剧场：我导演的全部剧目（LIVE / ENDED），一键直达直播间或名场面。
 */
import { ref, onMounted } from 'vue'
import { theaterApi } from '../../api/modules'
import TheaterCard from '../../components/TheaterCard.vue'
import EmptyState from '../../components/EmptyState.vue'

const list = ref([])
const loading = ref(true)

onMounted(async () => {
  try {
    // mine 返回 TheaterVO，这里映射成 TheaterCard 需要的 PlazaCardVO 形状
    list.value = ((await theaterApi.mine()) || []).map((t) => ({
      ...t,
      scenarioBrief: (t.scenarioSnapshot || '').slice(0, 60),
      castSummary: (t.members || []).map((m) => m.name).join(' / ')
    }))
  } finally {
    loading.value = false
  }
})
</script>

<template>
  <div class="page">
    <div class="page-head">
      <div>
        <div class="page-title">我的剧场</div>
        <div class="page-sub">你导演过的每一场戏：直播中的随时操控，完结的留档名场面</div>
      </div>
      <router-link to="/wizard" class="btn btn-primary">＋ 再开一部新戏</router-link>
    </div>

    <div v-if="loading" class="mt-loading">清点片场中……</div>

    <template v-else>
      <div class="mt-tabs" v-if="list.length">
        <span class="mt-chip live">🔴 直播中 {{ list.filter((t) => t.status === 'LIVE').length }}</span>
        <span class="mt-chip">🎬 总场次 {{ list.length }}</span>
        <span class="mt-chip">💬 总台词 {{ list.reduce((s, t) => s + (t.messageCount || 0), 0) }}</span>
        <span class="mt-chip">❤️ 总获赞 {{ list.reduce((s, t) => s + (t.likeCount || 0), 0) }}</span>
      </div>

      <div class="grid grid-3">
        <TheaterCard v-for="t in list" :key="t.id" :theater="t" />
      </div>

      <EmptyState
        v-if="!list.length"
        icon="🎬"
        title="还没有导演过任何戏"
        desc="选个场景、拉个剧组、喊声开拍——你的第一部戏三分钟就能上映"
      >
        <router-link to="/wizard" class="btn btn-primary">开拍第一部 →</router-link>
      </EmptyState>
    </template>
  </div>
</template>

<style scoped>
.mt-loading {
  text-align: center;
  padding: 90px 0;
  color: var(--text-3);
}

.mt-tabs {
  display: flex;
  gap: 8px;
  margin-bottom: 18px;
  flex-wrap: wrap;
}

.mt-chip {
  font-size: 12.5px;
  color: var(--text-2);
  background: var(--panel);
  border: 1px solid var(--border);
  border-radius: 999px;
  padding: 5px 14px;
}

.mt-chip.live {
  color: var(--heat);
  border-color: rgba(255, 90, 95, 0.4);
  background: var(--heat-soft);
}
</style>
