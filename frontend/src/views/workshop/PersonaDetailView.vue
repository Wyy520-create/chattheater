<script setup>
/**
 * 人设档案页：完整六维档案 + 在线试聊（单轮，不落库）。
 * owner 可编辑/删除；试聊回复标注是否 Mock 演出。
 */
import { ref, computed, onMounted, inject } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { personaApi } from '../../api/modules'
import { useUserStore } from '../../stores/user'
import EmptyState from '../../components/EmptyState.vue'

const route = useRoute()
const router = useRouter()
const store = useUserStore()
const toast = inject('toast', () => {})

const personaId = Number(route.params.id)
const persona = ref(null)
const err = ref('')

const liked = ref(false)
const likeCount = ref(0)
const likeBusy = ref(false)

// 试聊
const chat = ref([])
const draft = ref('')
const asking = ref(false)
const chatEl = ref(null)

onMounted(async () => {
  try {
    persona.value = await personaApi.detail(personaId)
    liked.value = !!persona.value.liked
    likeCount.value = persona.value.likeCount || 0
  } catch (e) {
    err.value = e.message || '演员档案不存在'
  }
})

const isOwner = computed(() => store.user?.id === persona.value?.ownerId)

const sections = computed(() => [
  { icon: '🧠', title: '性格描述', text: persona.value?.traits },
  { icon: '💬', title: '说话风格', text: persona.value?.speakingStyle },
  { icon: '📖', title: '背景故事', text: persona.value?.background },
  { icon: '💣', title: '雷点', text: persona.value?.taboo }
].filter((s) => s.text))

async function toggleLike() {
  if (!store.loggedIn) return toast('登录后才能点赞', 'warn')
  if (likeBusy.value) return
  likeBusy.value = true
  try {
    const r = await personaApi.toggleLike(personaId)
    liked.value = r
    likeCount.value += r ? 1 : -1
  } catch (e) {
    toast(e.message, 'err')
  } finally {
    likeBusy.value = false
  }
}

async function ask() {
  const text = draft.value.trim()
  if (!text || asking.value) return
  if (text.length > 200) return toast('单条消息最长 200 字', 'err')
  chat.value.push({ role: 'me', text })
  draft.value = ''
  asking.value = true
  chat.value.push({ role: 'waiting' })
  try {
    const r = await personaApi.tryChat(personaId, text)
    chat.value.pop()
    chat.value.push({ role: 'ai', text: r.reply, mock: r.mock })
  } catch (e) {
    chat.value.pop()
    toast(e.message, 'err')
  } finally {
    asking.value = false
    nextTickScroll()
  }
  nextTickScroll()
}

function nextTickScroll() {
  requestAnimationFrame(() => {
    if (chatEl.value) chatEl.value.scrollTop = chatEl.value.scrollHeight
  })
}

async function remove() {
  if (!confirm(`确定送别演员「${persona.value.name}」？此操作不可撤销。`)) return
  try {
    await personaApi.remove(personaId)
    toast('演员已送别')
    router.push('/workshop')
  } catch (e) {
    toast(e.message, 'err')
  }
}

function hire() {
  router.push('/wizard')
}
</script>

<template>
  <div class="page">
    <div v-if="err" class="pd-err">
      <EmptyState icon="🎭" :title="err" desc="这位演员可能已离开剧组">
        <router-link to="/workshop" class="btn btn-primary">回工坊 →</router-link>
      </EmptyState>
    </div>

    <div v-else-if="!persona" class="pd-loading">调档中……</div>

    <div v-else class="pd-grid">
      <!-- 左：档案 -->
      <div class="pd-main">
        <div class="card pd-profile">
          <div class="pd-head">
            <span class="pd-avatar">{{ persona.avatar }}</span>
            <div class="pd-tt">
              <div class="pd-name-row">
                <h2>{{ persona.name }}</h2>
                <span class="tag tag-gold">{{ persona.archetype }}</span>
                <span v-if="persona.isOfficial" class="pd-official">官方出品</span>
                <span v-if="persona.status === 0" class="pd-banned">已下架</span>
              </div>
              <div class="pd-tagline">{{ persona.tagline }}</div>
              <div class="pd-nums">
                <span>🎬 出演 {{ persona.useCount }} 次</span>
                <span>❤️ 获赞 {{ likeCount }}</span>
                <span v-if="isOwner" class="pd-mine">我的演员</span>
              </div>
            </div>
            <div class="pd-head-acts">
              <button class="btn btn-ghost" :class="{ liked }" :disabled="likeBusy" @click="toggleLike">
                {{ liked ? '❤️ 已赞' : '🤍 点赞' }}
              </button>
              <button class="btn btn-primary" @click="hire">拉进剧组 →</button>
            </div>
          </div>
        </div>

        <div v-for="s in sections" :key="s.title" class="card pd-section">
          <div class="pd-sec-title">{{ s.icon }} {{ s.title }}</div>
          <p class="pd-sec-text">{{ s.text }}</p>
        </div>

        <EmptyState v-if="!sections.length" icon="📄" title="档案还是白纸" desc="这位演员的六维档案尚未填写完整" />
      </div>

      <!-- 右：试聊 -->
      <div class="pd-side">
        <div class="card pd-chat">
          <div class="panel-title">💬 试聊小黑屋</div>
          <p class="pd-chat-sub">单轮对话，不占群聊次数——先试试它接不接得住你的话</p>

          <div ref="chatEl" class="pd-chat-feed">
            <div v-for="(c, i) in chat" :key="i" class="pd-bubble-row" :class="c.role">
              <template v-if="c.role === 'me'">
                <div class="pd-bubble mine">{{ c.text }}</div>
              </template>
              <template v-else-if="c.role === 'waiting'">
                <div class="pd-bubble ai waiting">
                  {{ persona.name }} 正在酝酿<span class="pd-dots"><i></i><i></i><i></i></span>
                </div>
              </template>
              <template v-else>
                <div class="pd-bubble ai">
                  {{ c.text }}
                  <span v-if="c.mock" class="pd-mock-tag" title="未配置真实 LLM 时的模拟演出">模拟演出</span>
                </div>
              </template>
            </div>
            <div v-if="!chat.length" class="pd-chat-empty">
              试着说句狠话，看它怎么接<br />
              <em>例："就你这水平也敢出来混？"</em>
            </div>
          </div>

          <div class="pd-chat-input">
            <template v-if="store.loggedIn">
              <input
                v-model="draft"
                placeholder="对它说句话…（Enter 发送）"
                maxlength="200"
                :disabled="persona.status === 0"
                @keydown.enter.prevent="ask"
              />
              <button class="btn btn-primary" :disabled="asking || !draft.trim()" @click="ask">
                {{ asking ? '…' : '发送' }}
              </button>
            </template>
            <div v-else class="pd-chat-login">
              <router-link to="/login" class="btn btn-primary btn-sm">登录后试聊</router-link>
            </div>
          </div>
        </div>

        <div v-if="isOwner" class="card pd-owner-acts">
          <div class="panel-title">🛠️ 档案管理</div>
          <div class="pd-owner-btns">
            <router-link :to="`/workshop/${persona.id}/edit`" class="btn btn-ghost">✏️ 编辑档案</router-link>
            <button class="btn btn-ghost pd-del" @click="remove">🗑️ 删除演员</button>
          </div>
        </div>
      </div>
    </div>
  </div>
</template>

<style scoped>
.pd-err {
  padding-top: 60px;
}

.pd-loading {
  text-align: center;
  padding: 100px 0;
  color: var(--text-3);
}

.pd-grid {
  display: grid;
  grid-template-columns: 1.55fr 1fr;
  gap: 16px;
  align-items: start;
}

.pd-main {
  display: flex;
  flex-direction: column;
  gap: 14px;
}

.pd-profile {
  padding: 22px;
}

.pd-head {
  display: flex;
  gap: 16px;
  align-items: flex-start;
}

.pd-avatar {
  font-size: 56px;
  line-height: 1;
  filter: drop-shadow(0 0 14px rgba(245, 197, 24, 0.3));
}

.pd-tt {
  flex: 1;
  min-width: 0;
}

.pd-name-row {
  display: flex;
  align-items: center;
  gap: 10px;
  flex-wrap: wrap;
}

.pd-name-row h2 {
  font-size: 22px;
  font-weight: 800;
}

.pd-official {
  font-size: 10.5px;
  color: var(--violet);
  border: 1px solid rgba(139, 123, 255, 0.45);
  background: var(--violet-soft);
  border-radius: 999px;
  padding: 1px 8px;
}

.pd-banned {
  font-size: 10.5px;
  color: var(--heat);
  border: 1px solid rgba(255, 90, 95, 0.45);
  border-radius: 999px;
  padding: 1px 8px;
}

.pd-tagline {
  margin-top: 8px;
  font-size: 14.5px;
  color: var(--gold);
  border-left: 2px solid var(--gold-line);
  padding-left: 10px;
  line-height: 1.6;
}

.pd-nums {
  display: flex;
  gap: 16px;
  margin-top: 10px;
  font-size: 12.5px;
  color: var(--text-3);
  font-family: var(--font-mono);
}

.pd-mine {
  color: var(--violet);
}

.pd-head-acts {
  display: flex;
  flex-direction: column;
  gap: 8px;
  flex-shrink: 0;
}

.pd-head-acts .liked {
  color: var(--heat);
  border-color: rgba(255, 90, 95, 0.4);
}

.pd-section {
  padding: 18px 20px;
}

.pd-sec-title {
  font-size: 13.5px;
  font-weight: 700;
  color: var(--text-2);
  margin-bottom: 10px;
}

.pd-sec-text {
  font-size: 14px;
  line-height: 1.85;
  color: var(--text-2);
  white-space: pre-wrap;
}

/* 试聊 */
.pd-side {
  display: flex;
  flex-direction: column;
  gap: 14px;
  position: sticky;
  top: calc(var(--nav-h) + 12px);
}

.pd-chat {
  padding: 18px;
  display: flex;
  flex-direction: column;
  gap: 10px;
}

.pd-chat-sub {
  font-size: 12px;
  color: var(--text-3);
}

.pd-chat-feed {
  height: 340px;
  overflow-y: auto;
  background: var(--bg-soft);
  border-radius: var(--radius-s);
  padding: 14px;
  display: flex;
  flex-direction: column;
  gap: 10px;
}

.pd-chat-feed::-webkit-scrollbar {
  width: 4px;
}

.pd-bubble-row {
  display: flex;
}

.pd-bubble-row.me {
  justify-content: flex-end;
}

.pd-bubble-row.ai {
  justify-content: flex-start;
}

.pd-bubble {
  max-width: 86%;
  font-size: 13.5px;
  line-height: 1.65;
  padding: 8px 12px;
  border-radius: 12px;
  word-break: break-word;
}

.pd-bubble.mine {
  background: rgba(139, 123, 255, 0.18);
  border: 1px solid rgba(139, 123, 255, 0.35);
  border-bottom-right-radius: 4px;
}

.pd-bubble.ai {
  background: var(--panel-2);
  border: 1px solid var(--border-soft);
  border-bottom-left-radius: 4px;
}

.pd-bubble.waiting {
  color: var(--text-3);
}

.pd-mock-tag {
  display: inline-block;
  font-size: 10px;
  color: var(--violet);
  border: 1px solid rgba(139, 123, 255, 0.4);
  border-radius: 999px;
  padding: 0 7px;
  margin-left: 8px;
  vertical-align: 1px;
}

.pd-dots {
  display: inline-flex;
  gap: 3px;
  margin-left: 4px;
}

.pd-dots i {
  width: 4px;
  height: 4px;
  border-radius: 50%;
  background: var(--text-3);
  animation: pd-jump 1.2s ease-in-out infinite;
}

.pd-dots i:nth-child(2) { animation-delay: 0.15s; }
.pd-dots i:nth-child(3) { animation-delay: 0.3s; }

@keyframes pd-jump {
  0%, 60%, 100% { transform: none; opacity: 0.5; }
  30% { transform: translateY(-4px); opacity: 1; }
}

.pd-chat-empty {
  margin: auto;
  text-align: center;
  color: var(--text-3);
  font-size: 13px;
  line-height: 2;
}

.pd-chat-empty em {
  font-size: 12px;
  color: var(--text-3);
  opacity: 0.7;
}

.pd-chat-input {
  display: flex;
  gap: 8px;
}

.pd-chat-input input {
  flex: 1;
}

.pd-chat-login {
  flex: 1;
  text-align: center;
}

/* owner 管理卡 */
.pd-owner-acts {
  padding: 18px;
}

.pd-owner-btns {
  display: flex;
  gap: 10px;
}

.pd-owner-btns .btn {
  flex: 1;
}

.pd-del:hover {
  color: var(--heat);
  border-color: rgba(255, 90, 95, 0.4);
}

@media (max-width: 900px) {
  .pd-grid {
    grid-template-columns: 1fr;
  }

  .pd-side {
    position: static;
  }

  .pd-head {
    flex-wrap: wrap;
  }
}
</style>
