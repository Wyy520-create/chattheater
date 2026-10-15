<script setup>
import { ref } from 'vue'
import { useUserStore } from '../stores/user'
import { theaterApi } from '../api/modules'

const props = defineProps({
  theater: { type: Object, required: true }
})

const store = useUserStore()
const liked = ref(!!props.theater.liked)
const likeCount = ref(props.theater.likeCount || 0)
const busy = ref(false)

function fmtTime(t) {
  if (!t) return ''
  return new Date(t).toLocaleString('zh-CN', { month: 'numeric', day: 'numeric', hour: '2-digit', minute: '2-digit' })
}

async function toggleLike() {
  if (!store.loggedIn || busy.value) return
  busy.value = true
  try {
    const r = await theaterApi.toggleLike(props.theater.id)
    liked.value = r.liked
    likeCount.value += r.liked ? 1 : -1
  } catch {
    /* 忽略 */
  } finally {
    busy.value = false
  }
}
</script>

<template>
  <div class="tcard card">
    <router-link :to="`/theaters/${theater.id}`" class="tcard-main">
      <div class="tcard-title-row">
        <span class="tcard-title">{{ theater.title }}</span>
        <span v-if="theater.status === 'LIVE'" class="tag tag-live">直播中</span>
        <span v-else class="tag">已完结</span>
      </div>
      <div class="tcard-brief">{{ theater.scenarioBrief }}</div>
      <div v-if="theater.castSummary" class="tcard-cast">🎭 {{ theater.castSummary }}</div>
    </router-link>

    <div class="tcard-foot">
      <div class="tcard-nums">
        <span title="热度">🔥{{ theater.heat }}</span>
        <span title="消息数">💬{{ theater.messageCount }}</span>
        <span v-if="theater.status === 'LIVE'" title="在线围观">👀{{ theater.audience }}</span>
        <span class="tcard-time">{{ theater.status === 'LIVE' ? '开演 ' + fmtTime(theater.createdAt) : '杀青 ' + fmtTime(theater.endedAt) }}</span>
      </div>
      <div class="tcard-acts">
        <router-link
          v-if="theater.status === 'ENDED'"
          :to="`/plaza/${theater.id}/hall`"
          class="btn btn-ghost btn-sm"
        >
          名场面
        </router-link>
        <button
          class="tcard-like"
          :class="{ on: liked }"
          :disabled="!store.loggedIn"
          @click="toggleLike"
        >
          {{ liked ? '❤️' : '🤍' }} {{ likeCount }}
        </button>
      </div>
    </div>
  </div>
</template>

<style scoped>
.tcard {
  display: flex;
  flex-direction: column;
  gap: 12px;
  transition: border-color 0.15s, transform 0.15s;
}

.tcard:hover {
  border-color: var(--gold-line);
  transform: translateY(-2px);
}

.tcard-main {
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.tcard-title-row {
  display: flex;
  align-items: center;
  gap: 10px;
}

.tcard-title {
  font-size: 16px;
  font-weight: 700;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.tcard-brief {
  font-size: 13px;
  color: var(--text-3);
  display: -webkit-box;
  -webkit-line-clamp: 2;
  -webkit-box-orient: vertical;
  overflow: hidden;
  min-height: 40px;
}

.tcard-cast {
  font-size: 12px;
  color: var(--text-2);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.tcard-foot {
  display: flex;
  justify-content: space-between;
  align-items: center;
  border-top: 1px solid var(--border-soft);
  padding-top: 10px;
  margin-top: auto;
}

.tcard-nums {
  display: flex;
  gap: 12px;
  font-size: 12px;
  color: var(--text-2);
  font-family: var(--font-mono);
}

.tcard-time {
  color: var(--text-3);
}

.tcard-acts {
  display: flex;
  align-items: center;
  gap: 10px;
}

.tcard-like {
  background: none;
  border: none;
  color: var(--text-2);
  font-size: 13px;
}

.tcard-like.on {
  color: var(--heat);
}
</style>
