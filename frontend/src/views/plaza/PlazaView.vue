<script setup>
/**
 * 剧场广场：全站数字带 + 直播大厅 / 完结片场双 Tab。
 * 游客可逛；卡片点击直达直播间或名场面馆。
 */
import { ref, onMounted, inject } from 'vue'
import { plazaApi } from '../../api/modules'
import TheaterCard from '../../components/TheaterCard.vue'
import EmptyState from '../../components/EmptyState.vue'

const toast = inject('toast', () => {})

const tab = ref('live')
const stats = ref(null)
const cards = ref([])
const page = ref(1)
const total = ref(0)
const loading = ref(false)

onMounted(async () => {
  plazaApi.stats().then((s) => (stats.value = s)).catch(() => {})
  load(true)
})

async function load(reset = false) {
  if (reset) {
    page.value = 1
    total.value = 0
    cards.value = []
  }
  loading.value = true
  try {
    const fn = tab.value === 'live' ? plazaApi.live : plazaApi.ended
    const r = await fn({ page: page.value, size: 12 })
    cards.value = reset ? r.records : [...cards.value, ...r.records]
    total.value = r.total
  } catch (e) {
    toast(e.message, 'err')
  } finally {
    loading.value = false
  }
}

function switchTab(t) {
  if (tab.value === t) return
  tab.value = t
  load(true)
}

function loadMore() {
  page.value += 1
  load(false)
}

function fmtNum(n) {
  if (n == null) return '0'
  if (n >= 10000) return (n / 10000).toFixed(1).replace(/\.0$/, '') + '万'
  return String(n)
}
</script>

<template>
  <div class="page">
    <div class="page-head">
      <div>
        <div class="page-title">剧场广场</div>
        <div class="page-sub">全网好戏尽收眼底：围观直播、插话怼演员、考古名场面</div>
      </div>
    </div>

    <!-- 数字带 -->
    <div v-if="stats" class="pz-stats card">
      <div class="pz-stat">
        <b class="pz-live-num">{{ fmtNum(stats.liveCount) }}</b>
        <span>正在上映</span>
      </div>
      <div class="pz-stat">
        <b>{{ fmtNum(stats.endedCount) }}</b>
        <span>已完结剧目</span>
      </div>
      <div class="pz-stat">
        <b>{{ fmtNum(stats.totalMessages) }}</b>
        <span>累计台词</span>
      </div>
      <div class="pz-stat">
        <b>{{ fmtNum(stats.totalPersonas) }}</b>
        <span>在册演员</span>
      </div>
      <div class="pz-stat">
        <b>{{ fmtNum(stats.totalUsers) }}</b>
        <span>注册导演</span>
      </div>
      <div class="pz-stat">
        <b class="pz-heat-num">{{ fmtNum(stats.onlineAudience) }}</b>
        <span>此刻在线围观</span>
      </div>
    </div>

    <!-- Tab -->
    <div class="pz-tabs">
      <button class="pz-tab" :class="{ on: tab === 'live' }" @click="switchTab('live')">
        🔴 直播大厅
      </button>
      <button class="pz-tab" :class="{ on: tab === 'ended' }" @click="switchTab('ended')">
        🏆 完结片场
      </button>
    </div>

    <div class="grid grid-3">
      <TheaterCard v-for="t in cards" :key="t.id" :theater="t" />
    </div>

    <div v-if="loading" class="pz-loading">节目单加载中……</div>
    <EmptyState
      v-else-if="!cards.length"
      :icon="tab === 'live' ? '🎬' : '🏆'"
      :title="tab === 'live' ? '此刻一片寂静' : '还没有完结的剧目'"
      :desc="tab === 'live' ? '当红好戏还没开播，不如自己开一部？' : '等第一部戏杀青，高光时刻会陈列在这里'"
    >
      <router-link to="/wizard" class="btn btn-primary">我要开拍 →</router-link>
    </EmptyState>

    <div v-if="cards.length < total && !loading" class="pz-more">
      <button class="btn btn-ghost" @click="loadMore">加载更多（{{ cards.length }}/{{ total }}）</button>
    </div>
  </div>
</template>

<style scoped>
/* 数字带 */
.pz-stats {
  display: grid;
  grid-template-columns: repeat(6, 1fr);
  padding: 18px 10px;
  margin-bottom: 20px;
}

.pz-stat {
  text-align: center;
  border-right: 1px dashed var(--border-soft);
}

.pz-stat:last-child {
  border-right: none;
}

.pz-stat b {
  display: block;
  font-size: 24px;
  font-weight: 800;
  font-family: var(--font-mono);
  color: var(--gold);
  line-height: 1.2;
}

.pz-live-num {
  color: var(--heat) !important;
  animation: pz-breathe 1.8s ease-in-out infinite;
}

.pz-heat-num {
  color: var(--violet) !important;
}

@keyframes pz-breathe {
  0%, 100% { opacity: 1; }
  50% { opacity: 0.55; }
}

.pz-stat span {
  font-size: 12px;
  color: var(--text-3);
}

/* Tab */
.pz-tabs {
  display: flex;
  gap: 8px;
  margin-bottom: 18px;
  border-bottom: 1px solid var(--border-soft);
}

.pz-tab {
  background: none;
  border: none;
  border-bottom: 2px solid transparent;
  color: var(--text-3);
  font-size: 14.5px;
  font-weight: 600;
  padding: 10px 16px;
  transition: all 0.15s;
}

.pz-tab.on {
  color: var(--gold);
  border-bottom-color: var(--gold);
}

.pz-loading {
  text-align: center;
  color: var(--text-3);
  font-size: 13px;
  padding: 30px;
}

.pz-more {
  display: flex;
  justify-content: center;
  margin-top: 20px;
}

@media (max-width: 780px) {
  .pz-stats {
    grid-template-columns: repeat(3, 1fr);
    gap: 12px 0;
  }

  .pz-stat:nth-child(3) {
    border-right: none;
  }
}
</style>
