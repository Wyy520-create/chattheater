<script setup>
/**
 * 名场面馆：完结剧目的高光回放。
 * 顶部剧目信息 + 高光台词卡片（点赞数排序，后端已定），支持进直播间看完整台本。
 */
import { ref, computed, onMounted, inject } from 'vue'
import { useRoute } from 'vue-router'
import { plazaApi, theaterApi } from '../../api/modules'
import { useUserStore } from '../../stores/user'
import EmotionTag from '../../components/EmotionTag.vue'
import EmptyState from '../../components/EmptyState.vue'

const route = useRoute()
const store = useUserStore()
const toast = inject('toast', () => {})

const theaterId = Number(route.params.id)
const hall = ref(null)
const allMessages = ref([])
const err = ref('')
const showAll = ref(false)
const loadingScript = ref(false)

onMounted(async () => {
  try {
    hall.value = await plazaApi.highlights(theaterId)
  } catch (e) {
    err.value = e.message || '这场戏看不到了'
  }
})

const theater = computed(() => hall.value?.theater)
const highlights = computed(() => hall.value?.highlights || [])
const isEnded = computed(() => theater.value?.status === 'ENDED')

async function loadFullScript() {
  if (loadingScript.value) return
  loadingScript.value = true
  try {
    allMessages.value = await theaterApi.messages(theaterId, { limit: 100 })
    showAll.value = true
  } catch (e) {
    toast(e.message, 'err')
  } finally {
    loadingScript.value = false
  }
}

async function like(m) {
  if (!store.loggedIn) return toast('登录后才能为名场面喝彩', 'warn')
  try {
    const r = await theaterApi.likeMessage(m.id)
    m.liked = r.liked
    if (typeof r.likeCount === 'number') m.likeCount = r.likeCount
    else m.likeCount = (m.likeCount || 0) + (r.liked ? 1 : -1)
  } catch (e) {
    toast(e.message, 'err')
  }
}

function rank(i) {
  return ['🥇', '🥈', '🥉'][i] || `#${i + 1}`
}

function fmtTime(t) {
  if (!t) return ''
  return new Date(t).toLocaleString('zh-CN', {
    month: 'numeric', day: 'numeric', hour: '2-digit', minute: '2-digit'
  })
}
</script>

<template>
  <div class="page">
    <div v-if="err" style="padding-top: 60px">
      <EmptyState icon="🏆" :title="err" desc="剧目可能已被导演删除">
        <router-link to="/plaza" class="btn btn-primary">回广场 →</router-link>
      </EmptyState>
    </div>

    <div v-else-if="!hall" class="hall-loading">拉开帷幕……</div>

    <template v-else>
      <!-- 剧目头 -->
      <div class="card hall-head">
        <div class="hall-head-main">
          <div class="hall-title-row">
            <h2>{{ theater.title }}</h2>
            <span class="tag">{{ theater.status === 'ENDED' ? '已完结' : '仍在直播' }}</span>
          </div>
          <div class="hall-meta">
            导演 @{{ theater.ownerName }}
            <span v-if="theater.endedAt"> · 杀青于 {{ fmtTime(theater.endedAt) }}</span>
            <span v-if="theater.endedReason"> · {{ theater.endedReason }}</span>
          </div>
          <div v-if="theater.castSummary" class="hall-cast">🎭 {{ theater.castSummary }}</div>
        </div>
        <div class="hall-nums">
          <div class="hall-num"><b>🔥{{ theater.heat }}</b><span>热度峰值</span></div>
          <div class="hall-num"><b>💬{{ theater.messageCount }}</b><span>台词总数</span></div>
          <div class="hall-num"><b>❤️{{ theater.likeCount }}</b><span>获赞</span></div>
        </div>
      </div>

      <!-- 高光区 -->
      <div class="hall-sec-title">🏆 名场面 · 全场高光 TOP{{ highlights.length || 3 }}</div>

      <div v-if="highlights.length" class="hall-highlights">
        <div v-for="(m, i) in highlights" :key="m.id" class="card hall-hl" :class="{ top: i === 0 }">
          <div class="hl-rank">{{ rank(i) }}</div>
          <div class="hl-avatar">{{ m.senderAvatar || '🎭' }}</div>
          <div class="hl-main">
            <div class="hl-meta">
              <b>{{ m.senderName }}</b>
              <span v-if="m.senderType === 'PERSONA'" class="hl-role">演员</span>
              <span v-else-if="m.senderType === 'USER'" class="hl-role user">观众</span>
              <span v-else-if="m.senderType === 'DIRECTOR'" class="hl-role dir">导演</span>
              <EmotionTag v-if="m.emotion" :emotion="m.emotion" />
              <span class="hl-time">{{ fmtTime(m.createdAt) }}</span>
            </div>
            <div class="hl-text">「{{ m.content }}」</div>
            <button class="hl-like" :class="{ liked: m.liked }" @click="like(m)">
              {{ m.liked ? '❤️' : '🤍' }} {{ m.likeCount }} 人喝彩
            </button>
          </div>
        </div>
      </div>
      <EmptyState
        v-else
        icon="🥁"
        title="本场没有高光台词"
        desc="没有台词获得点赞——下次记得为精彩发言点个赞，好戏才留得下来"
      />

      <!-- 完整台本 -->
      <template v-if="!showAll">
        <div class="hall-script-cta">
          <button class="btn btn-ghost" :disabled="loadingScript" @click="loadFullScript">
            {{ loadingScript ? '翻台本中…' : '📖 查看完整台本（最近 100 条）' }}
          </button>
        </div>
      </template>
      <template v-else>
        <div class="hall-sec-title">📖 完整台本</div>
        <div class="card hall-script">
          <div v-for="m in allMessages" :key="m.id" class="hs-row" :class="`t-${m.senderType}`">
            <template v-if="m.senderType === 'SYSTEM'">
              <span class="hs-sys">{{ m.content }}</span>
            </template>
            <template v-else>
              <span class="hs-avatar">{{ m.senderAvatar || '·' }}</span>
              <span class="hs-name">{{ m.senderName }}</span>
              <span class="hs-time">{{ fmtTime(m.createdAt) }}</span>
              <span class="hs-content" :class="{ hl: m.isHighlight === 1 }">
                {{ m.content }}<i v-if="m.isHighlight === 1"> ⭐</i>
              </span>
            </template>
          </div>
          <div v-if="!allMessages.length" class="hs-empty">台本是空的……</div>
        </div>
      </template>

      <div class="hall-acts">
        <router-link v-if="isEnded" :to="`/theaters/${theaterId}`" class="btn btn-primary">进回放间看弹幕版 →</router-link>
        <router-link to="/plaza" class="btn btn-ghost">回广场</router-link>
      </div>
    </template>
  </div>
</template>

<style scoped>
.hall-loading {
  text-align: center;
  padding: 100px 0;
  color: var(--text-3);
}

/* 头部 */
.hall-head {
  display: flex;
  justify-content: space-between;
  gap: 20px;
  padding: 22px;
  margin-bottom: 22px;
  flex-wrap: wrap;
}

.hall-title-row {
  display: flex;
  align-items: center;
  gap: 12px;
  flex-wrap: wrap;
}

.hall-title-row h2 {
  font-size: 22px;
  font-weight: 800;
}

.hall-meta {
  margin-top: 8px;
  font-size: 13px;
  color: var(--text-3);
}

.hall-cast {
  margin-top: 8px;
  font-size: 12.5px;
  color: var(--text-2);
}

.hall-nums {
  display: flex;
  gap: 8px;
  align-items: center;
}

.hall-num {
  text-align: center;
  background: var(--bg-soft);
  border-radius: var(--radius-s);
  padding: 10px 16px;
}

.hall-num b {
  display: block;
  font-size: 17px;
  font-family: var(--font-mono);
  color: var(--gold);
}

.hall-num span {
  font-size: 11px;
  color: var(--text-3);
}

.hall-sec-title {
  font-size: 15px;
  font-weight: 800;
  margin: 22px 0 14px;
  color: var(--gold);
}

/* 高光卡 */
.hall-highlights {
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.hall-hl {
  display: flex;
  gap: 14px;
  padding: 18px 20px;
  align-items: flex-start;
}

.hall-hl.top {
  border-color: var(--gold);
  box-shadow: 0 0 24px rgba(245, 197, 24, 0.08);
  background: linear-gradient(135deg, var(--panel), rgba(245, 197, 24, 0.04));
}

.hl-rank {
  font-size: 26px;
  flex-shrink: 0;
  margin-top: 2px;
}

.hl-avatar {
  font-size: 30px;
  flex-shrink: 0;
}

.hl-main {
  flex: 1;
  min-width: 0;
}

.hl-meta {
  display: flex;
  align-items: center;
  gap: 8px;
  flex-wrap: wrap;
  font-size: 12.5px;
}

.hl-meta b {
  font-size: 14px;
}

.hl-role {
  font-size: 10.5px;
  color: var(--violet);
  border: 1px solid rgba(139, 123, 255, 0.4);
  border-radius: 999px;
  padding: 0 7px;
}

.hl-role.user {
  color: var(--text-3);
  border-color: var(--border);
}

.hl-role.dir {
  color: var(--gold);
  border-color: var(--gold-line);
}

.hl-time {
  color: var(--text-3);
  font-family: var(--font-mono);
  font-size: 11px;
}

.hl-text {
  margin-top: 8px;
  font-size: 16px;
  line-height: 1.75;
  color: var(--text-1);
}

.hall-hl.top .hl-text {
  font-size: 17.5px;
  font-weight: 600;
}

.hl-like {
  margin-top: 10px;
  background: var(--bg-soft);
  border: 1px solid var(--border);
  border-radius: 999px;
  color: var(--text-2);
  font-size: 12.5px;
  padding: 4px 14px;
  transition: all 0.15s;
}

.hl-like:hover {
  border-color: var(--heat);
  color: var(--heat);
}

.hl-like.liked {
  color: var(--heat);
  border-color: rgba(255, 90, 95, 0.45);
  background: var(--heat-soft);
}

/* 台本 */
.hall-script-cta {
  text-align: center;
  margin-top: 24px;
}

.hall-script {
  padding: 18px 22px;
  display: flex;
  flex-direction: column;
  gap: 10px;
  max-height: 520px;
  overflow-y: auto;
}

.hall-script::-webkit-scrollbar {
  width: 4px;
}

.hs-row {
  display: flex;
  align-items: baseline;
  gap: 8px;
  font-size: 13.5px;
  line-height: 1.7;
}

.hs-row.t-SYSTEM {
  justify-content: center;
}

.hs-sys {
  font-size: 11.5px;
  color: var(--text-3);
  background: var(--bg-soft);
  border-radius: 999px;
  padding: 2px 14px;
}

.hs-avatar {
  font-size: 15px;
}

.hs-name {
  font-weight: 700;
  color: var(--text-2);
  flex-shrink: 0;
}

.hs-time {
  font-size: 10.5px;
  color: var(--text-3);
  font-family: var(--font-mono);
  flex-shrink: 0;
}

.hs-content {
  color: var(--text-2);
}

.hs-content.hl {
  color: var(--gold);
}

.hs-empty {
  text-align: center;
  color: var(--text-3);
  padding: 20px;
}

.hall-acts {
  display: flex;
  gap: 10px;
  justify-content: center;
  margin-top: 26px;
}

@media (max-width: 780px) {
  .hall-head {
    flex-direction: column;
  }

  .hall-nums {
    justify-content: flex-start;
  }
}
</style>
