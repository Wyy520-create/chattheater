<script setup>
/**
 * 剧组 PK 擂台：两部直播剧场同场打擂。
 * 得分 = 擂台期热度增量 + 观众助威票数×3，平局双剧同辉。
 * 跨剧场 SSE 不方便，本页 5 秒轮询 list 保持实时。
 */
import { ref, computed, onMounted, onBeforeUnmount, inject } from 'vue'
import { pkApi, plazaApi } from '../../api/modules'
import { useUserStore } from '../../stores/user'
import EmptyState from '../../components/EmptyState.vue'

const store = useUserStore()
const toast = inject('toast', () => {})

const list = ref([])
const loading = ref(true)
const nowTs = ref(Date.now())
let ticker = null

// 发起擂台
const liveTheaters = ref([])
const form = ref({ open: false, title: '', a: null, b: null, durationSec: 900 })
const creating = ref(false)

const canVote = computed(() => store.loggedIn)

onMounted(async () => {
  await refresh()
  ticker = setInterval(() => {
    nowTs.value = Date.now()
    refresh() // 5 秒轮询：比分/倒计时
  }, 5000)
})

onBeforeUnmount(() => clearInterval(ticker))

async function refresh() {
  try {
    list.value = (await pkApi.list()) || []
  } catch (e) {
    if (!list.value.length) toast(e.message, 'err')
  } finally {
    loading.value = false
  }
}

async function openForm() {
  form.value.open = !form.value.open
  if (form.value.open && !liveTheaters.value.length) {
    try {
      const r = await plazaApi.live({ page: 1, size: 12 })
      liveTheaters.value = (r?.records || []).filter((t) => t.status === 'LIVE')
    } catch (e) {
      toast(e.message, 'err')
    }
  }
}

function togglePick(t) {
  if (form.value.a?.id === t.id) {
    form.value.a = null
    return
  }
  if (form.value.b?.id === t.id) {
    form.value.b = null
    return
  }
  if (!form.value.a) {
    form.value.a = t
    return
  }
  if (!form.value.b) {
    form.value.b = t
    return
  }
  toast('A、B 两侧都选满了，先点掉一个再换', 'warn')
}

async function createPk() {
  if (!form.value.a || !form.value.b) return toast('请选出打擂的 A、B 两部剧场', 'err')
  if (creating.value) return
  creating.value = true
  try {
    await pkApi.create({
      title: form.value.title.trim() || null,
      theaterAId: form.value.a.id,
      theaterBId: form.value.b.id,
      durationSec: form.value.durationSec
    })
    form.value = { open: false, title: '', a: null, b: null, durationSec: 900 }
    toast('⚔️ 擂台已开，观众可以给剧组助威了')
    await refresh()
  } catch (e) {
    toast(e.message, 'err')
  } finally {
    creating.value = false
  }
}

async function vote(pk, side) {
  if (!store.loggedIn) return toast('登录后才能助威', 'warn')
  if (pk.status !== 'OPEN' || pk.myVoted) return
  try {
    const fresh = await pkApi.vote(pk.id, side)
    const i = list.value.findIndex((x) => x.id === pk.id)
    if (i >= 0) list.value[i] = fresh
  } catch (e) {
    toast(e.message, 'err')
  }
}

function remainSec(pk) {
  if (pk.status !== 'OPEN' || !pk.endsAt) return 0
  return Math.max(0, Math.ceil((pk.endsAt - nowTs.value) / 1000))
}

function fmtRemain(sec) {
  if (sec <= 0) return '结算中…'
  const m = Math.floor(sec / 60)
  const s = sec % 60
  return m > 0 ? `${m}分${s.toString().padStart(2, '0')}秒` : `${s}秒`
}

function winnerText(pk) {
  if (pk.status !== 'CLOSED') return ''
  if (pk.winner === 'T') return '🤝 势均力敌，双剧同辉'
  const t = pk.winner === 'A' ? pk.theaterA : pk.theaterB
  return `🏆 《${t?.title || '神秘剧组'}》胜出`
}
</script>

<template>
  <div class="page">
    <div class="page-head">
      <div>
        <div class="page-title">⚔️ 剧组 PK 擂台</div>
        <div class="page-sub">两部直播剧场同场打擂：观众助威一票 ×3 分，擂台期热度增量一并计入</div>
      </div>
      <button v-if="store.loggedIn" class="btn btn-primary" @click="openForm">
        {{ form.open ? '收起' : '＋ 摆下擂台' }}
      </button>
    </div>

    <!-- 发起擂台 -->
    <div v-if="form.open" class="card pk-form">
      <div class="pkf-label">第一步：从正在直播的公开剧目中选出两部（点卡片选 A / B）</div>
      <div v-if="!liveTheaters.length" class="pkf-empty">暂时没有正在直播的公开剧目……</div>
      <div v-else class="pkf-grid">
        <button
          v-for="t in liveTheaters"
          :key="t.id"
          class="pkf-theater"
          :class="{ a: form.a?.id === t.id, b: form.b?.id === t.id }"
          @click="togglePick(t)"
        >
          <span class="pkf-pick">{{ form.a?.id === t.id ? 'A 方' : form.b?.id === t.id ? 'B 方' : '点选' }}</span>
          <b>{{ t.title }}</b>
          <span class="pkf-cast">{{ t.castSummary }}</span>
          <span class="pkf-heat">🔥 {{ t.heat }}</span>
        </button>
      </div>
      <div class="pkf-label">第二步：起个擂台名（可留空自动生成）</div>
      <input v-model="form.title" class="pkf-title" maxlength="60" placeholder="例：年度撕逼剧王争霸赛" />
      <div class="pkf-label">第三步：擂台时长</div>
      <select v-model.number="form.durationSec" class="pkf-dur">
        <option :value="300">5 分钟</option>
        <option :value="600">10 分钟</option>
        <option :value="900">15 分钟</option>
        <option :value="1800">30 分钟</option>
        <option :value="3600">60 分钟</option>
      </select>
      <div class="pkf-acts">
        <button class="btn btn-primary" :disabled="creating || !form.a || !form.b" @click="createPk">
          {{ creating ? '摆擂中…' : `⚔️ 开打（${form.a?.title || 'A'} VS ${form.b?.title || 'B'}）` }}
        </button>
      </div>
    </div>

    <!-- 擂台列表 -->
    <div v-if="loading" class="pk-loading">敲锣打鼓准备中……</div>
    <EmptyState v-else-if="!list.length" icon="⚔️" title="还没有擂台" desc="点右上角「摆下擂台」，让两部剧当场分个高下" />

    <div v-else class="pk-list">
      <div v-for="pk in list" :key="pk.id" class="card pk-item" :class="{ closed: pk.status !== 'OPEN' }">
        <div class="pki-head">
          <span class="pki-title">{{ pk.title }}</span>
          <span v-if="pk.status === 'OPEN'" class="pki-countdown">⏳ {{ fmtRemain(remainSec(pk)) }}</span>
          <span v-else class="pki-result">{{ winnerText(pk) }}</span>
        </div>

        <div class="pki-versus">
          <!-- A 剧 -->
          <div class="pki-side" :class="{ win: pk.status === 'CLOSED' && pk.winner === 'A', tie: pk.status === 'CLOSED' && pk.winner === 'T' }">
            <router-link :to="`/theaters/${pk.theaterA?.id}`" class="pki-theater">
              <b>{{ pk.theaterA?.title || '已散场' }}</b>
              <span>🔥 热度 {{ pk.theaterA?.heat ?? 0 }}</span>
            </router-link>
            <div class="pki-score">{{ pk.scoreA }}</div>
            <div class="pki-votes">{{ pk.votesA }} 助威</div>
            <button
              v-if="pk.status === 'OPEN'"
              class="pki-btn a"
              :disabled="!canVote || pk.myVoted"
              @click="vote(pk, 'A')"
            >
              {{ pk.mySide === 'A' ? '✓ 已助威' : pk.myVoted ? '已助威' : '📣 给 A 助威' }}
            </button>
          </div>

          <div class="pki-vs">VS</div>

          <!-- B 剧 -->
          <div class="pki-side" :class="{ win: pk.status === 'CLOSED' && pk.winner === 'B', tie: pk.status === 'CLOSED' && pk.winner === 'T' }">
            <router-link :to="`/theaters/${pk.theaterB?.id}`" class="pki-theater">
              <b>{{ pk.theaterB?.title || '已散场' }}</b>
              <span>🔥 热度 {{ pk.theaterB?.heat ?? 0 }}</span>
            </router-link>
            <div class="pki-score">{{ pk.scoreB }}</div>
            <div class="pki-votes">{{ pk.votesB }} 助威</div>
            <button
              v-if="pk.status === 'OPEN'"
              class="pki-btn b"
              :disabled="!canVote || pk.myVoted"
              @click="vote(pk, 'B')"
            >
              {{ pk.mySide === 'B' ? '✓ 已助威' : pk.myVoted ? '已助威' : '📣 给 B 助威' }}
            </button>
          </div>
        </div>
      </div>
    </div>
  </div>
</template>

<style scoped>
.pk-loading {
  text-align: center;
  color: var(--text-3);
  padding: 40px;
}

/* 发起表单 */
.pk-form {
  padding: 16px;
  margin-bottom: 18px;
}

.pkf-label {
  font-size: 12.5px;
  color: var(--text-3);
  margin: 10px 0 8px;
}

.pkf-label:first-child {
  margin-top: 0;
}

.pkf-empty {
  font-size: 13px;
  color: var(--text-3);
  padding: 12px 0;
}

.pkf-grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(200px, 1fr));
  gap: 8px;
}

.pkf-theater {
  display: flex;
  flex-direction: column;
  gap: 4px;
  text-align: left;
  border: 1px solid var(--border);
  background: var(--panel);
  border-radius: 10px;
  padding: 10px 12px;
  cursor: pointer;
  transition: all 0.15s;
  color: var(--text-1);
  font-size: 12.5px;
}

.pkf-theater:hover {
  border-color: var(--gold-line);
}

.pkf-theater.a {
  border-color: var(--heat);
  background: rgba(255, 110, 120, 0.08);
}

.pkf-theater.b {
  border-color: var(--info, #6aa9ff);
  background: rgba(110, 160, 255, 0.08);
}

.pkf-pick {
  font-size: 11px;
  color: var(--text-3);
  font-family: var(--font-mono);
}

.pkf-theater.a .pkf-pick {
  color: var(--heat);
}

.pkf-theater.b .pkf-pick {
  color: var(--info, #6aa9ff);
}

.pkf-cast {
  font-size: 11.5px;
  color: var(--text-3);
}

.pkf-heat {
  font-size: 11.5px;
  color: var(--heat);
}

.pkf-title {
  width: 100%;
  max-width: 420px;
}

.pkf-dur {
  background: var(--panel);
  border: 1px solid var(--border);
  color: var(--text-2);
  border-radius: 8px;
  padding: 6px 10px;
  font-size: 13px;
}

.pkf-acts {
  margin-top: 14px;
}

/* 擂台卡片 */
.pk-list {
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.pk-item {
  padding: 14px 16px;
}

.pk-item.closed {
  opacity: 0.88;
}

.pki-head {
  display: flex;
  justify-content: space-between;
  align-items: center;
  gap: 10px;
  margin-bottom: 12px;
}

.pki-title {
  font-weight: 700;
  font-size: 14.5px;
}

.pki-countdown {
  font-size: 12px;
  color: var(--heat);
  font-family: var(--font-mono);
  flex-shrink: 0;
}

.pki-result {
  font-size: 12.5px;
  color: var(--gold);
  flex-shrink: 0;
}

.pki-versus {
  display: grid;
  grid-template-columns: 1fr 44px 1fr;
  gap: 10px;
  align-items: center;
}

.pki-side {
  display: flex;
  flex-direction: column;
  gap: 6px;
  border: 1px solid var(--border);
  border-radius: 10px;
  padding: 10px 12px;
  background: var(--bg-soft);
}

.pki-side.win {
  border-color: var(--gold);
  background: var(--gold-soft);
}

.pki-side.tie {
  border-color: var(--gold-line);
}

.pki-theater {
  display: flex;
  flex-direction: column;
  gap: 2px;
  font-size: 12.5px;
  color: var(--text-2);
}

.pki-theater b {
  color: var(--text-1);
  font-size: 13px;
}

.pki-theater:hover b {
  color: var(--gold);
}

.pki-score {
  font-size: 26px;
  font-weight: 800;
  font-family: var(--font-mono);
  color: var(--text-1);
}

.pki-votes {
  font-size: 11.5px;
  color: var(--text-3);
}

.pki-btn {
  border-radius: 8px;
  padding: 6px 0;
  font-size: 12.5px;
  border: 1px solid var(--border);
  background: var(--panel);
  color: var(--text-1);
  cursor: pointer;
  transition: all 0.15s;
}

.pki-btn.a:hover:not(:disabled) {
  border-color: var(--heat);
  color: var(--heat);
}

.pki-btn.b:hover:not(:disabled) {
  border-color: var(--info, #6aa9ff);
  color: var(--info, #6aa9ff);
}

.pki-btn:disabled {
  cursor: default;
  opacity: 0.65;
}

.pki-vs {
  text-align: center;
  font-weight: 900;
  font-size: 16px;
  color: var(--gold);
  font-family: var(--font-mono);
}
</style>
