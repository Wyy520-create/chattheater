<script setup>
/**
 * 聊天室 —— 产品灵魂页。
 * 三栏剧场布局：剧组侧栏 | 舞台消息流 | 导演台（仅导演可见）。
 * SSE 全协议处理：hello/typing/chunk/message/system/heat/ended/ping。
 * 消息双通道（HTTP 响应 + SSE 回流）按 id 去重。
 */
import { ref, reactive, computed, nextTick, onMounted, onBeforeUnmount, inject } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { theaterApi, pollApi, streamUrl } from '../../api/modules'
import { useUserStore } from '../../stores/user'
import EmotionTag from '../../components/EmotionTag.vue'
import HeatGauge from '../../components/HeatGauge.vue'

const route = useRoute()
const router = useRouter()
const store = useUserStore()
const toast = inject('toast', () => {})

const theaterId = Number(route.params.id)

const theater = ref(null)
const messages = ref([])
const cards = ref([])
const loadErr = ref('')

// 流式临时气泡：key = personaId（chunk 到达前显示"正在输入"）
const streaming = reactive({})
// 已渲染消息 id 集合（双通道去重）
const seen = new Set()

const draft = ref('')
const directive = ref('')
const sending = ref(false)
const paused = ref(false)
const endedReason = ref('')
const endConfirm = ref(false)
const endReasonDraft = ref('')
const loadingEarlier = ref(false)
const hasEarlier = ref(true)

// ==================== 投票改剧本 ====================
const poll = ref(null) // 当前剧场最新投票（PollVO）
const nowTs = ref(Date.now())
let pollTicker = null

// 导演发起投票的表单
const pollForm = reactive({ open: false, topic: '', options: ['', ''], durationSec: 30 })
const pollCreating = ref(false)

const pollRemainSec = computed(() => {
  if (!poll.value || poll.value.status !== 'OPEN' || !poll.value.endsAt) return 0
  return Math.max(0, Math.ceil((poll.value.endsAt - nowTs.value) / 1000))
})
const pollMaxCount = computed(() => Math.max(1, ...(poll.value?.counts || [1])))

const feedEl = ref(null)
const nearBottom = ref(true)
const unreadCount = ref(0)

let es = null
let reconnectTimer = null

const isDirector = computed(() => !!theater.value && store.user?.id === theater.value.ownerId)
const isLive = computed(() => theater.value?.status === 'LIVE')
const canSpeak = computed(() => store.loggedIn && isLive.value)

// ==================== 数据加载 ====================

onMounted(async () => {
  try {
    const [t, msgs] = await Promise.all([
      theaterApi.detail(theaterId),
      theaterApi.messages(theaterId, { limit: 50 }).catch(() => [])
    ])
    theater.value = t
    msgs.forEach(ingest)
    hasEarlier.value = msgs.length >= 50
    connectStream()
    pollApi.active(theaterId).then((p) => (poll.value = p)).catch(() => {})
    pollTicker = setInterval(() => (nowTs.value = Date.now()), 1000)
    if (isDirector.value) {
      theaterApi.eventCards().then((c) => (cards.value = c || [])).catch(() => {})
    }
  } catch (e) {
    loadErr.value = e.message || '剧场不存在或不可见'
  }
})

onBeforeUnmount(() => {
  closeStream()
  clearTimeout(reconnectTimer)
  clearInterval(pollTicker)
})

// ==================== SSE ====================

function connectStream() {
  closeStream()
  es = new EventSource(streamUrl(theaterId, store.token))
  es.addEventListener('hello', () => {})
  es.addEventListener('typing', (ev) => {
    const d = JSON.parse(ev.data)
    if (!streaming[d.pid]) {
      streaming[d.pid] = { pid: d.pid, name: d.name, avatar: d.avatar, text: '' }
    }
  })
  es.addEventListener('chunk', (ev) => {
    const d = JSON.parse(ev.data)
    if (!streaming[d.pid]) {
      streaming[d.pid] = { pid: d.pid, name: '', avatar: '', text: '' }
    }
    streaming[d.pid].text += d.delta
    scrollIfNearBottom(true)
  })
  es.addEventListener('message', (ev) => {
    const m = JSON.parse(ev.data)
    delete streaming[m.senderId] // 定稿替换临时气泡
    ingest(m)
    bumpLocal(m)
  })
  es.addEventListener('system', (ev) => {
    const d = JSON.parse(ev.data)
    // SYSTEM 气泡无 id，无法去重——直接渲染临时系统条
    messages.value.push({ id: `sys-${Date.now()}`, senderType: 'SYSTEM', content: d.text, likeCount: 0 })
    scrollIfNearBottom(true)
  })
  es.addEventListener('heat', (ev) => {
    if (theater.value) theater.value.heat = JSON.parse(ev.data).heat
  })
  es.addEventListener('poll', (ev) => {
    poll.value = JSON.parse(ev.data)
  })
  es.addEventListener('ended', () => {
    if (theater.value) {
      theater.value.status = 'ENDED'
      endedReason.value = endedReason.value || '本场演出已杀青'
    }
    Object.keys(streaming).forEach((k) => delete streaming[k])
    closeStream()
  })
  es.addEventListener('ping', () => {})
  es.onerror = () => {
    // 浏览器自动重连中（readyState=CONNECTING）；彻底断开才手动重建
    if (es && es.readyState === 2) {
      closeStream()
      if (isLive.value) {
        reconnectTimer = setTimeout(connectStream, 3000)
      }
    }
  }
}

function closeStream() {
  if (es) {
    es.close()
    es = null
  }
}

// ==================== 消息处理 ====================

function ingest(m) {
  if (m.id == null || seen.has(m.id)) return
  seen.add(m.id)
  messages.value.push(m)
  scrollIfNearBottom(false)
}

/** SSE 定稿消息同步本地统计（消息数 / 热度）。 */
function bumpLocal(m) {
  if (!theater.value) return
  if (m.senderType !== 'SYSTEM') theater.value.messageCount = (theater.value.messageCount || 0) + 1
}

function fmtTime(t) {
  if (!t) return ''
  return new Date(t).toLocaleTimeString('zh-CN', { hour: '2-digit', minute: '2-digit' })
}

function isMine(m) {
  return m.senderType === 'USER' && m.senderId === store.user?.id
}

// ==================== 滚动 ====================

function onScroll() {
  const el = feedEl.value
  if (!el) return
  nearBottom.value = el.scrollHeight - el.scrollTop - el.clientHeight < 120
  if (nearBottom.value) unreadCount.value = 0
}

function scrollIfNearBottom(force) {
  nextTick(() => {
    const el = feedEl.value
    if (!el) return
    if (force || nearBottom.value) {
      el.scrollTop = el.scrollHeight
      unreadCount.value = 0
    } else {
      unreadCount.value += 1
    }
  })
}

function scrollToBottom() {
  const el = feedEl.value
  if (el) el.scrollTop = el.scrollHeight
  nearBottom.value = true
  unreadCount.value = 0
}

async function loadEarlier() {
  if (loadingEarlier.value || !messages.value.length) return
  const firstId = messages.value.find((m) => typeof m.id === 'number')?.id
  if (!firstId) return
  loadingEarlier.value = true
  try {
    const older = await theaterApi.messages(theaterId, { before: firstId, limit: 50 })
    if (!older.length) {
      hasEarlier.value = false
      toast('没有更早的台词了', 'warn')
    } else {
      const fresh = older.filter((m) => !seen.has(m.id))
      fresh.forEach((m) => seen.add(m.id))
      const el = feedEl.value
      const prevHeight = el ? el.scrollHeight : 0
      messages.value = [...fresh, ...messages.value]
      nextTick(() => {
        if (el) el.scrollTop = el.scrollHeight - prevHeight // 保持视口位置
      })
    }
  } catch (e) {
    toast(e.message, 'err')
  } finally {
    loadingEarlier.value = false
  }
}

// ==================== 发言 ====================

async function send() {
  const text = draft.value.trim()
  if (!text || sending.value || !canSpeak.value) return
  if (text.length > 200) {
    toast('台词不能超过 200 字', 'err')
    return
  }
  sending.value = true
  try {
    const m = await theaterApi.postMessage(theaterId, text)
    ingest(m) // SSE 也会回流同一条，seen 去重
    draft.value = ''
  } catch (e) {
    toast(e.message, 'err')
  } finally {
    sending.value = false
  }
}

// ==================== 消息点赞 ====================

async function likeMsg(m) {
  if (!store.loggedIn) {
    toast('登录后才能为名场面喝彩', 'warn')
    return
  }
  try {
    const r = await theaterApi.likeMessage(m.id)
    m.liked = r.liked
    if (typeof r.likeCount === 'number') m.likeCount = r.likeCount
    else m.likeCount = (m.likeCount || 0) + (r.liked ? 1 : -1)
  } catch (e) {
    toast(e.message, 'err')
  }
}

// ==================== 剧场点赞 / 收藏 ====================

async function toggleTheaterLike() {
  if (!store.loggedIn) return toast('登录后才能点赞', 'warn')
  try {
    const r = await theaterApi.toggleLike(theaterId)
    theater.value.liked = r.liked
    theater.value.likeCount = (theater.value.likeCount || 0) + (r.liked ? 1 : -1)
  } catch (e) {
    toast(e.message, 'err')
  }
}

async function toggleFav() {
  if (!store.loggedIn) return toast('登录后才能收藏', 'warn')
  try {
    const r = await theaterApi.toggleFavorite(theaterId)
    theater.value.favorited = r.favorited
    toast(r.favorited ? '已收入个人中心' : '已取消收藏')
  } catch (e) {
    toast(e.message, 'err')
  }
}

// ==================== 导演台 ====================

async function direct(type, payload = {}) {
  if (!isDirector.value || !isLive.value) return
  try {
    return await theaterApi.director(theaterId, { type, ...payload })
  } catch (e) {
    toast(e.message, 'err')
    return null
  }
}

async function sendDirective() {
  const text = directive.value.trim()
  if (!text) return
  const r = await direct('DIRECTIVE', { content: text })
  if (r) {
    directive.value = ''
    if (r.message) ingest(r.message)
  }
}

async function playCard(card) {
  const r = await direct('EVENT_CARD', { eventKey: card.key })
  if (r?.message) ingest(r.message)
}

async function togglePause() {
  const r = paused.value ? await direct('RESUME') : await direct('PAUSE')
  if (r) paused.value = !!r.paused
}

async function toggleMute(member) {
  const r = await direct(member.muted ? 'UNMUTE' : 'MUTE', { personaId: member.personaId })
  if (r) member.muted = r.muted
}

let activityTimer = null
function onActivity(member) {
  clearTimeout(activityTimer)
  activityTimer = setTimeout(() => {
    direct('ACTIVITY', { personaId: member.personaId, activity: member.activity })
  }, 400)
}

async function doEnd() {
  const r = await theaterApi.end(theaterId, endReasonDraft.value.trim() || null).catch((e) => {
    toast(e.message, 'err')
    return null
  })
  if (r) {
    theater.value.status = 'ENDED'
    endedReason.value = endReasonDraft.value.trim() || '导演杀青'
    endConfirm.value = false
    closeStream()
    toast('本剧杀青！高光时刻已收录名场面')
  }
}

function memberPillClass(m) {
  return { muted: m.muted }
}

// ==================== 投票改剧本（交互） ====================

async function votePoll(idx) {
  if (!store.loggedIn) return toast('登录后才能投票', 'warn')
  if (!poll.value || poll.value.status !== 'OPEN' || poll.value.myVoted) return
  try {
    poll.value = await pollApi.vote(poll.value.id, idx)
  } catch (e) {
    toast(e.message, 'err')
  }
}

function addPollOption() {
  if (pollForm.options.length < 4) pollForm.options.push('')
}

function removePollOption(i) {
  if (pollForm.options.length > 2) pollForm.options.splice(i, 1)
}

async function createPoll() {
  const topic = pollForm.topic.trim()
  const options = pollForm.options.map((o) => o.trim()).filter(Boolean)
  if (!topic) return toast('投票主题不能为空', 'err')
  if (options.length < 2) return toast('至少需要 2 个剧情走向', 'err')
  if (pollCreating.value) return
  pollCreating.value = true
  try {
    poll.value = await pollApi.create(theaterId, {
      topic,
      options,
      durationSec: pollForm.durationSec
    })
    pollForm.open = false
    pollForm.topic = ''
    pollForm.options = ['', '']
    toast('🗳 投票已发起，观众正在改你的剧本')
  } catch (e) {
    toast(e.message, 'err')
  } finally {
    pollCreating.value = false
  }
}
</script>

<template>
  <div class="room-wrap">
    <!-- 加载失败 -->
    <div v-if="loadErr" class="room-err card">
      <div class="re-icon">🎬</div>
      <h3>{{ loadErr }}</h3>
      <p>剧目可能已下映，或这是一场私密首映。</p>
      <div class="re-acts">
        <router-link to="/plaza" class="btn btn-primary">去广场找戏看</router-link>
        <router-link to="/" class="btn btn-ghost">回首页</router-link>
      </div>
    </div>

    <div v-else-if="!theater" class="room-loading">
      <span class="spinner"></span> 舞台布置中……
    </div>

    <div v-else class="room-grid" :class="{ 'with-director': isDirector }">
      <!-- ============ 左栏：剧组 ============ -->
      <aside class="side card">
        <div class="side-head">
          <div class="side-title-row">
            <span class="live-badge" :class="isLive ? 'on' : 'off'">
              {{ isLive ? '● LIVE' : '■ ENDED' }}
            </span>
            <span class="vis-tag">{{ theater.visibility === 'PUBLIC' ? '🌍 公开' : '🔒 私密' }}</span>
          </div>
          <h2 class="side-title">{{ theater.title }}</h2>
          <div class="side-owner">导演 @{{ theater.ownerName }}</div>
        </div>

        <div class="side-heat">
          <HeatGauge :heat="theater.heat || 0" />
        </div>

        <div class="side-stats">
          <div class="ss-item"><b>{{ theater.audience || 0 }}</b><span>在线观众</span></div>
          <div class="ss-item"><b>{{ theater.messageCount || 0 }}</b><span>台词总数</span></div>
          <div class="ss-item"><b>{{ theater.likeCount || 0 }}</b><span>点赞</span></div>
        </div>

        <div class="side-block">
          <div class="side-label">🎬 剧组（{{ theater.members?.length || 0 }} 位演员）</div>
          <div v-for="m in theater.members" :key="m.personaId" class="member" :class="memberPillClass(m)">
            <div class="m-avatar">{{ m.avatar }}</div>
            <div class="m-info">
              <div class="m-name">
                {{ m.name }}
                <span v-if="m.muted" class="m-mute">🔇</span>
              </div>
              <div class="m-tag">{{ m.archetype }}</div>
              <template v-if="isDirector && isLive">
                <div class="m-slider">
                  <span>活跃</span>
                  <input v-model.number="m.activity" type="range" min="0" max="100" @input="onActivity(m)" />
                  <b>{{ m.activity }}</b>
                </div>
                <button class="m-mute-btn" @click="toggleMute(m)">
                  {{ m.muted ? '🔊 解禁言' : '🔇 禁言' }}
                </button>
              </template>
            </div>
          </div>
        </div>

        <div v-if="theater.scenarioSnapshot" class="side-block">
          <div class="side-label">📖 舞台设定</div>
          <div class="side-scroll">{{ theater.scenarioSnapshot }}</div>
        </div>

        <div v-if="theater.directorNote" class="side-block">
          <div class="side-label">🎬 导演手记</div>
          <div class="side-scroll note">{{ theater.directorNote }}</div>
        </div>

        <div class="side-acts">
          <button class="btn btn-ghost like-btn" :class="{ liked: theater.liked }" @click="toggleTheaterLike">
            {{ theater.liked ? '❤️' : '🤍' }} {{ theater.likeCount || 0 }}
          </button>
          <button class="btn btn-ghost" :class="{ faved: theater.favorited }" @click="toggleFav">
            {{ theater.favorited ? '⭐ 已收藏' : '☆ 收藏' }}
          </button>
        </div>
      </aside>

      <!-- ============ 中栏：舞台 ============ -->
      <section class="stage card">
        <!-- 完结谢幕横幅 -->
        <div v-if="!isLive" class="ended-banner">
          🎭 本场演出已杀青 —— {{ theater.endedReason || '精彩谢幕' }}
          <router-link :to="`/plaza/${theaterId}/hall`" class="ended-link">去名场面回放 →</router-link>
        </div>

        <div ref="feedEl" class="feed" @scroll="onScroll">
          <div v-if="hasEarlier && isLive" class="earlier">
            <button class="earlier-btn" :disabled="loadingEarlier" @click="loadEarlier">
              {{ loadingEarlier ? '翻剧本中…' : '↑ 加载更早的台词' }}
            </button>
          </div>

          <!-- 投票改剧本卡片：SSE poll 事件实时刷新 -->
          <div v-if="poll" class="poll-card" :class="{ closed: poll.status !== 'OPEN' }">
            <div class="poll-head">
              <span class="poll-title">🗳 {{ poll.topic }}</span>
              <span v-if="poll.status === 'OPEN'" class="poll-countdown">⏳ {{ pollRemainSec }}s</span>
              <span v-else class="poll-done-tag">
                {{ poll.winnerIdx != null ? '已改剧本' : '已截止' }}
              </span>
            </div>
            <div class="poll-total">{{ poll.totalVotes }} 位观众投出走向</div>
            <div class="poll-opts">
              <button
                v-for="(opt, i) in poll.options"
                :key="i"
                class="poll-opt"
                :class="{
                  mine: poll.myOption === i,
                  winner: poll.status !== 'OPEN' && poll.winnerIdx === i
                }"
                :disabled="poll.status !== 'OPEN' || poll.myVoted || !store.loggedIn"
                @click="votePoll(i)"
              >
                <span class="poll-bar" :style="{ width: ((poll.counts[i] || 0) / pollMaxCount) * 100 + '%' }"></span>
                <span class="poll-opt-text">{{ opt }}</span>
                <span class="poll-votes">{{ poll.counts[i] || 0 }} 票</span>
              </button>
            </div>
            <div class="poll-foot">
              <template v-if="poll.status === 'OPEN'">
                {{ poll.myVoted ? '已投票，等待剧情改写' : '点一个走向，演员照你改的演' }}
              </template>
              <template v-else-if="poll.winnerIdx != null">
                🎭 演员们将围绕「{{ poll.options[poll.winnerIdx] }}」继续演出
              </template>
              <template v-else>势均力敌，演员们自由发挥</template>
            </div>
          </div>

          <div v-for="m in messages" :key="m.id" class="msg" :class="`t-${m.senderType}`">
            <!-- 系统条 -->
            <div v-if="m.senderType === 'SYSTEM'" class="sys-line">{{ m.content }}</div>
            <!-- 导演指令 -->
            <div v-else-if="m.senderType === 'DIRECTOR'" class="dir-line">
              <span class="dir-badge">🎬 导演</span>
              <span>{{ m.content }}</span>
            </div>
            <!-- 用户 / 演员气泡 -->
            <template v-else>
              <div class="msg-avatar">{{ m.senderAvatar || '🎭' }}</div>
              <div class="msg-body" :class="{ mine: isMine(m) }">
                <div class="msg-meta">
                  <span class="msg-name">{{ m.senderName }}</span>
                  <span v-if="m.senderType === 'PERSONA'" class="msg-role">演员</span>
                  <span v-else class="msg-role user">观众</span>
                  <span class="msg-time">{{ fmtTime(m.createdAt) }}</span>
                </div>
                <div class="bubble" :class="{ hl: m.isHighlight === 1 }">
                  <span class="bubble-text">{{ m.content }}</span>
                  <span v-if="m.isHighlight === 1" class="hl-star" title="名场面">⭐</span>
                </div>
                <div class="msg-foot">
                  <EmotionTag v-if="m.emotion" :emotion="m.emotion" />
                  <button class="like-msg" :class="{ liked: m.liked }" @click="likeMsg(m)">
                    {{ m.liked ? '❤️' : '🤍' }} {{ m.likeCount || 0 }}
                  </button>
                </div>
              </div>
            </template>
          </div>

          <!-- 流式临时气泡 -->
          <div v-for="s in streaming" :key="s.pid" class="msg t-PERSONA streaming">
            <div class="msg-avatar">{{ s.avatar || '🎭' }}</div>
            <div class="msg-body">
              <div class="msg-meta">
                <span class="msg-name">{{ s.name || '演员' }}</span>
                <span class="typing-hint" v-if="!s.text">正在输入<span class="dots"><i></i><i></i><i></i></span></span>
              </div>
              <div class="bubble streaming-bubble">
                <span class="bubble-text">{{ s.text }}<i class="caret"></i></span>
              </div>
            </div>
          </div>

          <div v-if="!messages.length && !Object.keys(streaming).length" class="feed-empty">
            大幕将启，导演正在酝酿第一句话……
          </div>
        </div>

        <!-- 新消息悬浮提示 -->
        <transition name="fade">
          <button v-if="unreadCount > 0" class="new-msg-btn" @click="scrollToBottom">
            ↓ {{ unreadCount }} 条新台词
          </button>
        </transition>

        <!-- 输入区 -->
        <div class="composer">
          <template v-if="!store.loggedIn">
            <div class="composer-guest">
              围观中 · 想插话怼演员？
              <router-link to="/login" class="btn btn-primary btn-sm">登录开麦</router-link>
            </div>
          </template>
          <template v-else-if="!isLive">
            <div class="composer-ended">本剧已杀青，回放区见 🎭</div>
          </template>
          <template v-else>
            <input
              v-model="draft"
              class="composer-input"
              placeholder="以观众身份插一句（Enter 发送）…"
              maxlength="200"
              @keydown.enter.prevent="send"
            />
            <span class="composer-count">{{ draft.length }}/200</span>
            <button class="btn btn-primary composer-send" :disabled="sending || !draft.trim()" @click="send">
              {{ sending ? '…' : '发送' }}
            </button>
          </template>
        </div>
      </section>

      <!-- ============ 右栏：导演台 ============ -->
      <aside v-if="isDirector" class="console card">
        <div class="panel-title">🎛️ 导演台</div>

        <template v-if="isLive">
          <div class="cs-block">
            <div class="cs-label">现场指令（注入所有演员戏路）</div>
            <textarea
              v-model="directive"
              class="cs-input"
              rows="2"
              maxlength="200"
              placeholder="例：都别吵了，聊聊期末考试"
              @keydown.enter.exact.prevent="sendDirective"
            ></textarea>
            <button class="btn btn-primary btn-sm cs-send" :disabled="!directive.trim()" @click="sendDirective">
              📣 下达指令
            </button>
          </div>

          <div class="cs-block">
            <div class="cs-label">事件卡（一键引爆剧情）</div>
            <div class="card-grid">
              <button v-for="c in cards" :key="c.key" class="ecard" @click="playCard(c)">
                <span class="ec-icon">{{ c.icon }}</span>
                <span class="ec-title">{{ c.title }}</span>
                <span class="ec-text">{{ c.text }}</span>
              </button>
            </div>
          </div>

          <div class="cs-block">
            <div class="cs-label">节奏控制</div>
            <div class="cs-row">
              <button class="btn btn-ghost btn-sm" @click="togglePause">
                {{ paused ? '▶️ 恢复演出' : '⏸️ 中场喊卡' }}
              </button>
            </div>
          </div>

          <div class="cs-block">
            <div class="cs-label">🗳 投票改剧本（观众决定走向）</div>
            <button v-if="!pollForm.open" class="btn btn-ghost btn-sm" @click="pollForm.open = true">
              ＋ 发起剧情走向投票
            </button>
            <template v-else>
              <input
                v-model="pollForm.topic"
                class="cs-poll-topic"
                maxlength="60"
                placeholder="投票主题，例：接下来谁先道歉？"
              />
              <div v-for="(opt, i) in pollForm.options" :key="i" class="cs-poll-row">
                <input
                  v-model="pollForm.options[i]"
                  class="cs-poll-opt"
                  maxlength="20"
                  :placeholder="`走向 ${i + 1}（1~20 字）`"
                />
                <button
                  v-if="pollForm.options.length > 2"
                  class="cs-poll-del"
                  @click="removePollOption(i)"
                >
                  ✕
                </button>
              </div>
              <div class="cs-poll-tools">
                <button v-if="pollForm.options.length < 4" class="btn btn-ghost btn-sm" @click="addPollOption">
                  ＋ 加选项
                </button>
                <select v-model.number="pollForm.durationSec" class="cs-poll-dur">
                  <option :value="15">15 秒</option>
                  <option :value="30">30 秒</option>
                  <option :value="60">60 秒</option>
                  <option :value="120">120 秒</option>
                </select>
              </div>
              <div class="cs-poll-acts">
                <button class="btn btn-primary btn-sm" :disabled="pollCreating" @click="createPoll">
                  {{ pollCreating ? '发起中…' : '🗳 发起投票' }}
                </button>
                <button class="btn btn-ghost btn-sm" @click="pollForm.open = false">收起</button>
              </div>
            </template>
          </div>

          <div class="cs-block">
            <div class="cs-label">收工</div>
            <button class="btn btn-danger cs-end" @click="endConfirm = true">🎬 喊杀青</button>
          </div>

          <!-- 杀青确认弹层 -->
          <div v-if="endConfirm" class="end-mask" @click.self="endConfirm = false">
            <div class="end-pop card">
              <h3>确定杀青？</h3>
              <p>本剧将立即结束，点赞最高的台词将进入名场面。</p>
              <textarea v-model="endReasonDraft" rows="2" maxlength="60" placeholder="杀青理由（可留空）"></textarea>
              <div class="end-pop-acts">
                <button class="btn btn-ghost" @click="endConfirm = false">再演一会儿</button>
                <button class="btn btn-danger" @click="doEnd">正式杀青</button>
              </div>
            </div>
          </div>
        </template>

        <template v-else>
          <div class="console-ended">
            <div class="ce-icon">🏆</div>
            <div class="ce-title">本剧已杀青</div>
            <p>高光台词已收录名场面馆。</p>
            <router-link :to="`/plaza/${theaterId}/hall`" class="btn btn-primary">去名场面回放 →</router-link>
            <router-link to="/wizard" class="btn btn-ghost">再开一部新戏 →</router-link>
          </div>
        </template>
      </aside>
    </div>
  </div>
</template>

<style scoped>
.room-wrap {
  max-width: var(--max-w);
  margin: 0 auto;
  padding: 16px 18px calc(24px + var(--nav-h));
}

/* ============ 布局 ============ */
.room-grid {
  display: grid;
  grid-template-columns: 272px 1fr;
  gap: 14px;
  align-items: start;
}

.room-grid.with-director {
  grid-template-columns: 272px 1fr 300px;
}

/* ============ 左栏 ============ */
.side {
  position: sticky;
  top: calc(var(--nav-h) + 12px);
  padding: 16px;
  display: flex;
  flex-direction: column;
  gap: 14px;
  max-height: calc(100vh - var(--nav-h) - 36px);
  overflow-y: auto;
}

.side::-webkit-scrollbar {
  width: 4px;
}

.side-title-row {
  display: flex;
  gap: 8px;
  align-items: center;
}

.live-badge {
  font-size: 11px;
  font-weight: 800;
  letter-spacing: 1px;
  padding: 2px 9px;
  border-radius: 999px;
  font-family: var(--font-mono);
}

.live-badge.on {
  color: #ff5a5f;
  background: rgba(255, 90, 95, 0.14);
  border: 1px solid rgba(255, 90, 95, 0.4);
  animation: breathe 1.6s ease-in-out infinite;
}

.live-badge.off {
  color: var(--text-3);
  background: var(--bg-soft);
  border: 1px solid var(--border);
}

@keyframes breathe {
  0%, 100% { opacity: 1; }
  50% { opacity: 0.45; }
}

.vis-tag {
  font-size: 11px;
  color: var(--text-3);
}

.side-title {
  font-size: 18px;
  font-weight: 800;
  margin-top: 8px;
  line-height: 1.35;
}

.side-owner {
  font-size: 12px;
  color: var(--text-3);
  margin-top: 4px;
}

.side-stats {
  display: grid;
  grid-template-columns: repeat(3, 1fr);
  gap: 6px;
}

.ss-item {
  text-align: center;
  background: var(--bg-soft);
  border-radius: var(--radius-s);
  padding: 8px 4px;
}

.ss-item b {
  display: block;
  font-size: 16px;
  font-family: var(--font-mono);
  color: var(--gold);
}

.ss-item span {
  font-size: 11px;
  color: var(--text-3);
}

.side-label {
  font-size: 12.5px;
  font-weight: 700;
  color: var(--text-2);
  margin-bottom: 9px;
}

.side-scroll {
  font-size: 12.5px;
  color: var(--text-3);
  line-height: 1.65;
  max-height: 120px;
  overflow-y: auto;
  background: var(--bg-soft);
  border-radius: var(--radius-s);
  padding: 10px;
}

.side-scroll.note {
  color: var(--gold);
  background: var(--gold-soft);
}

/* 成员 */
.member {
  display: flex;
  gap: 10px;
  padding: 9px 8px;
  border-radius: var(--radius-s);
  transition: background 0.15s;
}

.member:hover {
  background: var(--bg-soft);
}

.member.muted .m-avatar {
  filter: grayscale(1);
  opacity: 0.55;
}

.m-avatar {
  font-size: 26px;
  width: 36px;
  height: 36px;
  display: flex;
  align-items: center;
  justify-content: center;
  background: var(--panel-2);
  border-radius: 10px;
  flex-shrink: 0;
}

.m-info {
  flex: 1;
  min-width: 0;
}

.m-name {
  font-size: 13.5px;
  font-weight: 700;
}

.m-tag {
  font-size: 11px;
  color: var(--violet);
  margin-top: 2px;
}

.m-slider {
  display: flex;
  align-items: center;
  gap: 6px;
  margin-top: 7px;
  font-size: 11px;
  color: var(--text-3);
}

.m-slider input {
  flex: 1;
  accent-color: var(--gold);
  height: 3px;
}

.m-slider b {
  font-family: var(--font-mono);
  color: var(--gold);
  width: 24px;
  text-align: right;
}

.m-mute-btn {
  margin-top: 6px;
  font-size: 11px;
  padding: 3px 10px;
  border-radius: 999px;
  border: 1px solid var(--border);
  background: transparent;
  color: var(--text-2);
  transition: all 0.15s;
}

.m-mute-btn:hover {
  border-color: var(--heat);
  color: var(--heat);
}

.side-acts {
  display: flex;
  gap: 8px;
}

.side-acts .btn {
  flex: 1;
  font-size: 13px;
}

.like-btn.liked {
  color: var(--heat);
  border-color: rgba(255, 90, 95, 0.4);
}

.side-acts .faved {
  color: var(--gold);
  border-color: var(--gold-line);
}

/* ============ 中栏舞台 ============ */
.stage {
  display: flex;
  flex-direction: column;
  height: calc(100vh - var(--nav-h) - 44px);
  min-height: 480px;
  overflow: hidden;
}

.ended-banner {
  flex-shrink: 0;
  text-align: center;
  font-size: 13px;
  padding: 9px 12px;
  background: linear-gradient(90deg, rgba(245, 197, 24, 0.16), rgba(139, 123, 255, 0.14));
  border-bottom: 1px solid var(--border-soft);
  color: var(--gold);
  display: flex;
  justify-content: center;
  align-items: center;
  gap: 10px;
  flex-wrap: wrap;
}

.ended-link {
  color: var(--violet);
  font-weight: 700;
  text-decoration: underline;
}

.feed {
  flex: 1;
  overflow-y: auto;
  padding: 18px 16px;
  display: flex;
  flex-direction: column;
  gap: 14px;
  scroll-behavior: smooth;
}

.feed::-webkit-scrollbar {
  width: 5px;
}

.feed::-webkit-scrollbar-thumb {
  background: var(--border);
  border-radius: 999px;
}

.earlier {
  text-align: center;
  flex-shrink: 0;
}

.earlier-btn {
  font-size: 12px;
  color: var(--text-3);
  background: var(--bg-soft);
  border: 1px solid var(--border-soft);
  border-radius: 999px;
  padding: 4px 16px;
  transition: all 0.15s;
}

.earlier-btn:hover:not(:disabled) {
  color: var(--gold);
  border-color: var(--gold-line);
}

/* 投票改剧本卡片 */
.poll-card {
  border: 1px solid var(--gold-line);
  background: var(--gold-soft);
  border-radius: 12px;
  padding: 12px 14px;
  margin-bottom: 12px;
}

.poll-card.closed {
  border-color: var(--border);
  background: var(--bg-soft);
}

.poll-head {
  display: flex;
  justify-content: space-between;
  align-items: center;
  gap: 10px;
}

.poll-title {
  font-weight: 700;
  font-size: 13.5px;
  color: var(--text-1);
}

.poll-countdown {
  font-size: 12px;
  color: var(--heat);
  font-family: var(--font-mono);
  flex-shrink: 0;
}

.poll-done-tag {
  font-size: 12px;
  color: var(--text-3);
  flex-shrink: 0;
}

.poll-total {
  font-size: 11.5px;
  color: var(--text-3);
  margin: 4px 0 10px;
}

.poll-opts {
  display: flex;
  flex-direction: column;
  gap: 6px;
}

.poll-opt {
  position: relative;
  display: flex;
  justify-content: space-between;
  align-items: center;
  gap: 8px;
  border: 1px solid var(--border);
  background: var(--panel);
  border-radius: 8px;
  padding: 8px 12px;
  font-size: 12.5px;
  color: var(--text-1);
  overflow: hidden;
  cursor: pointer;
  transition: border-color 0.15s;
}

.poll-opt:disabled {
  cursor: default;
}

.poll-opt:not(:disabled):hover {
  border-color: var(--gold-line);
}

.poll-opt.mine {
  border-color: var(--gold);
}

.poll-opt.winner {
  border-color: var(--gold);
  background: var(--gold-soft);
}

.poll-bar {
  position: absolute;
  left: 0;
  top: 0;
  bottom: 0;
  background: rgba(255, 199, 95, 0.16);
  transition: width 0.4s ease;
  pointer-events: none;
}

.poll-opt-text {
  position: relative;
}

.poll-votes {
  position: relative;
  font-family: var(--font-mono);
  font-size: 11.5px;
  color: var(--text-3);
  flex-shrink: 0;
}

.poll-foot {
  margin-top: 10px;
  font-size: 11.5px;
  color: var(--text-3);
}

/* 导演台：投票表单 */
.cs-poll-topic {
  width: 100%;
  margin-bottom: 6px;
}

.cs-poll-row {
  display: flex;
  gap: 6px;
  align-items: center;
  margin-bottom: 6px;
}

.cs-poll-opt {
  flex: 1;
}

.cs-poll-del {
  background: none;
  border: none;
  color: var(--text-3);
  cursor: pointer;
  padding: 0 4px;
}

.cs-poll-del:hover {
  color: var(--heat);
}

.cs-poll-tools {
  display: flex;
  gap: 8px;
  align-items: center;
  margin-bottom: 8px;
}

.cs-poll-dur {
  background: var(--panel);
  border: 1px solid var(--border);
  color: var(--text-2);
  border-radius: 8px;
  padding: 4px 8px;
  font-size: 12px;
}

.cs-poll-acts {
  display: flex;
  gap: 8px;
}

/* 消息 */
.msg {
  display: flex;
  gap: 10px;
  animation: rise 0.25s ease;
}

@keyframes rise {
  from { opacity: 0; transform: translateY(8px); }
  to { opacity: 1; transform: none; }
}

.msg.t-SYSTEM,
.msg.t-DIRECTOR {
  justify-content: center;
}

.sys-line {
  font-size: 12px;
  color: var(--text-3);
  background: var(--bg-soft);
  border-radius: 999px;
  padding: 5px 16px;
  max-width: 88%;
  text-align: center;
}

.dir-line {
  font-size: 12.5px;
  color: var(--gold);
  background: var(--gold-soft);
  border: 1px dashed var(--gold-line);
  border-radius: var(--radius-s);
  padding: 6px 14px;
  max-width: 88%;
  display: flex;
  gap: 8px;
  align-items: center;
}

.dir-badge {
  font-weight: 800;
  flex-shrink: 0;
}

.msg-avatar {
  font-size: 24px;
  width: 34px;
  height: 34px;
  display: flex;
  align-items: center;
  justify-content: center;
  background: var(--panel-2);
  border-radius: 10px;
  flex-shrink: 0;
  margin-top: 18px;
}

.msg-body {
  max-width: 76%;
  min-width: 0;
}

.msg-body.mine {
  margin-left: auto;
}

.msg-meta {
  display: flex;
  align-items: baseline;
  gap: 6px;
  font-size: 11.5px;
  margin-bottom: 4px;
}

.msg-body.mine .msg-meta {
  flex-direction: row-reverse;
}

.msg-name {
  font-weight: 700;
  color: var(--text-2);
  font-size: 12.5px;
}

.msg-role {
  color: var(--violet);
  font-size: 10.5px;
  border: 1px solid rgba(139, 123, 255, 0.35);
  border-radius: 999px;
  padding: 0 7px;
}

.msg-role.user {
  color: var(--text-3);
  border-color: var(--border);
}

.msg-time {
  color: var(--text-3);
  font-family: var(--font-mono);
  font-size: 10.5px;
}

.bubble {
  position: relative;
  background: var(--panel-2);
  border: 1px solid var(--border-soft);
  border-radius: 4px 14px 14px 14px;
  padding: 9px 13px;
  font-size: 14.5px;
  line-height: 1.65;
  word-break: break-word;
}

.msg-body.mine .bubble {
  background: rgba(139, 123, 255, 0.16);
  border-color: rgba(139, 123, 255, 0.35);
  border-radius: 14px 4px 14px 14px;
}

.bubble.hl {
  border-color: var(--gold);
  background: var(--gold-soft);
  box-shadow: 0 0 0 1px var(--gold-line);
}

.hl-star {
  margin-left: 6px;
}

.bubble.streaming-bubble {
  border-style: dashed;
  color: var(--text-2);
  min-height: 38px;
}

.caret {
  display: inline-block;
  width: 2px;
  height: 15px;
  background: var(--gold);
  margin-left: 2px;
  vertical-align: -2px;
  animation: blink 0.8s step-end infinite;
}

@keyframes blink {
  50% { opacity: 0; }
}

.typing-hint {
  font-size: 11.5px;
  color: var(--text-3);
}

.dots {
  display: inline-flex;
  gap: 3px;
  margin-left: 4px;
}

.dots i {
  width: 4px;
  height: 4px;
  border-radius: 50%;
  background: var(--text-3);
  animation: jump 1.2s ease-in-out infinite;
}

.dots i:nth-child(2) { animation-delay: 0.15s; }
.dots i:nth-child(3) { animation-delay: 0.3s; }

@keyframes jump {
  0%, 60%, 100% { transform: none; opacity: 0.5; }
  30% { transform: translateY(-4px); opacity: 1; }
}

.msg-foot {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-top: 5px;
}

.msg-body.mine .msg-foot {
  flex-direction: row-reverse;
}

.like-msg {
  font-size: 11.5px;
  color: var(--text-3);
  background: transparent;
  border: none;
  padding: 1px 4px;
  transition: transform 0.15s;
}

.like-msg:hover {
  transform: scale(1.12);
}

.like-msg.liked {
  color: var(--heat);
}

.feed-empty {
  margin: auto;
  color: var(--text-3);
  font-size: 13.5px;
}

/* 新消息悬浮 */
.new-msg-btn {
  position: absolute;
  bottom: 96px;
  left: 50%;
  transform: translateX(-50%);
  font-size: 12.5px;
  color: var(--gold);
  background: var(--gold-soft);
  border: 1px solid var(--gold-line);
  border-radius: 999px;
  padding: 6px 18px;
  z-index: 20;
  box-shadow: 0 6px 18px rgba(0, 0, 0, 0.4);
}

.fade-enter-active,
.fade-leave-active {
  transition: opacity 0.2s;
}

.fade-enter-from,
.fade-leave-to {
  opacity: 0;
}

/* 输入区 */
.composer {
  position: relative;
  flex-shrink: 0;
  display: flex;
  gap: 10px;
  align-items: center;
  padding: 12px 14px;
  border-top: 1px solid var(--border-soft);
  background: var(--panel);
}

.composer-guest,
.composer-ended {
  flex: 1;
  text-align: center;
  font-size: 13px;
  color: var(--text-3);
  display: flex;
  gap: 12px;
  justify-content: center;
  align-items: center;
}

.composer-input {
  flex: 1;
}

.composer-count {
  font-size: 11px;
  color: var(--text-3);
  font-family: var(--font-mono);
  flex-shrink: 0;
}

.composer-send {
  flex-shrink: 0;
}

/* ============ 右栏导演台 ============ */
.console {
  position: sticky;
  top: calc(var(--nav-h) + 12px);
  padding: 16px;
  max-height: calc(100vh - var(--nav-h) - 36px);
  overflow-y: auto;
  display: flex;
  flex-direction: column;
  gap: 16px;
}

.console::-webkit-scrollbar {
  width: 4px;
}

.cs-label {
  font-size: 12px;
  font-weight: 700;
  color: var(--text-2);
  margin-bottom: 8px;
}

.cs-input {
  width: 100%;
  resize: none;
}

.cs-send {
  width: 100%;
  margin-top: 8px;
}

.card-grid {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 8px;
}

.ecard {
  display: flex;
  flex-direction: column;
  gap: 3px;
  text-align: left;
  background: var(--panel-2);
  border: 1px solid var(--border-soft);
  border-radius: var(--radius-s);
  padding: 10px;
  transition: all 0.15s;
}

.ecard:hover {
  border-color: var(--gold);
  background: var(--gold-soft);
  transform: translateY(-2px);
}

.ec-icon {
  font-size: 18px;
}

.ec-title {
  font-size: 12.5px;
  font-weight: 700;
  color: var(--gold);
}

.ec-text {
  font-size: 10.5px;
  color: var(--text-3);
  line-height: 1.5;
  display: -webkit-box;
  -webkit-line-clamp: 2;
  -webkit-box-orient: vertical;
  overflow: hidden;
}

.cs-row {
  display: flex;
  gap: 8px;
}

.cs-row .btn {
  flex: 1;
}

.btn-danger {
  background: rgba(255, 90, 95, 0.16);
  border: 1px solid rgba(255, 90, 95, 0.45);
  color: var(--heat);
}

.btn-danger:hover {
  background: rgba(255, 90, 95, 0.28);
}

.cs-end {
  width: 100%;
}

/* 杀青弹层 */
.end-mask {
  position: fixed;
  inset: 0;
  background: rgba(0, 0, 0, 0.6);
  backdrop-filter: blur(3px);
  z-index: 300;
  display: flex;
  align-items: center;
  justify-content: center;
  padding: 20px;
}

.end-pop {
  width: 360px;
  padding: 22px;
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.end-pop h3 {
  font-size: 17px;
}

.end-pop p {
  font-size: 13px;
  color: var(--text-2);
}

.end-pop textarea {
  resize: none;
}

.end-pop-acts {
  display: flex;
  gap: 10px;
  justify-content: flex-end;
}

/* 完结态导演台 */
.console-ended {
  text-align: center;
  padding: 28px 8px;
  display: flex;
  flex-direction: column;
  gap: 12px;
  align-items: center;
}

.ce-icon {
  font-size: 40px;
}

.ce-title {
  font-size: 16px;
  font-weight: 800;
  color: var(--gold);
}

.console-ended p {
  font-size: 12.5px;
  color: var(--text-3);
}

/* ============ 加载/错误态 ============ */
.room-loading {
  text-align: center;
  padding: 120px 0;
  color: var(--text-3);
}

.spinner {
  display: inline-block;
  width: 22px;
  height: 22px;
  border: 2.5px solid var(--border);
  border-top-color: var(--gold);
  border-radius: 50%;
  animation: spin 0.8s linear infinite;
  vertical-align: -5px;
  margin-right: 8px;
}

@keyframes spin {
  to { transform: rotate(360deg); }
}

.room-err {
  max-width: 420px;
  margin: 90px auto 0;
  padding: 36px 30px;
  text-align: center;
  display: flex;
  flex-direction: column;
  gap: 10px;
  align-items: center;
}

.re-icon {
  font-size: 44px;
}

.room-err h3 {
  font-size: 17px;
}

.room-err p {
  font-size: 13px;
  color: var(--text-3);
}

.re-acts {
  display: flex;
  gap: 10px;
  margin-top: 8px;
}

/* ============ 响应式 ============ */
@media (max-width: 1080px) {
  .room-grid.with-director {
    grid-template-columns: 240px 1fr;
  }

  .console {
    grid-column: 1 / -1;
    position: static;
    max-height: none;
  }
}

@media (max-width: 780px) {
  .room-grid,
  .room-grid.with-director {
    grid-template-columns: 1fr;
  }

  .side {
    position: static;
    max-height: none;
  }

  .stage {
    height: 72vh;
    min-height: 420px;
  }

  .msg-body {
    max-width: 86%;
  }
}
</style>
