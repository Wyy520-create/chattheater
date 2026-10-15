<script setup>
import { ref } from 'vue'
import { useUserStore } from '../stores/user'
import { personaApi } from '../api/modules'

const props = defineProps({
  persona: { type: Object, required: true }
})

const store = useUserStore()
const liked = ref(!!props.persona.liked)
const likeCount = ref(props.persona.likeCount || 0)
const busy = ref(false)

async function toggleLike() {
  if (!store.loggedIn) return
  if (busy.value) return
  busy.value = true
  try {
    const r = await personaApi.toggleLike(props.persona.id)
    liked.value = r.liked
    likeCount.value += r.liked ? 1 : -1
  } catch {
    /* 401 等由 http 层处理 */
  } finally {
    busy.value = false
  }
}
</script>

<template>
  <div class="pcard card">
    <div class="pcard-head">
      <span class="pcard-avatar">{{ persona.avatar }}</span>
      <div class="pcard-tt">
        <div class="pcard-name">
          {{ persona.name }}
          <span class="tag tag-gold">{{ persona.archetype }}</span>
          <span v-if="persona.isOfficial" class="pcard-official">官方</span>
        </div>
        <div class="pcard-tagline">{{ persona.tagline }}</div>
      </div>
    </div>

    <div class="pcard-meta">
      <span class="pcard-stat">🎬 出演 {{ persona.useCount }} 次</span>
      <button
        class="pcard-like"
        :class="{ on: liked }"
        :disabled="!store.loggedIn"
        :title="store.loggedIn ? '' : '登录后可点赞'"
        @click="toggleLike"
      >
        {{ liked ? '❤️' : '🤍' }} {{ likeCount }}
      </button>
    </div>

    <div class="pcard-act">
      <router-link :to="`/workshop/${persona.id}`" class="btn btn-ghost btn-sm">查看档案</router-link>
      <slot name="actions" />
    </div>
  </div>
</template>

<style scoped>
.pcard {
  display: flex;
  flex-direction: column;
  gap: 12px;
  transition: border-color 0.15s, transform 0.15s;
}

.pcard:hover {
  border-color: var(--gold-line);
  transform: translateY(-2px);
}

.pcard-head {
  display: flex;
  gap: 12px;
  align-items: flex-start;
}

.pcard-avatar {
  font-size: 38px;
  line-height: 1;
  filter: drop-shadow(0 0 10px rgba(245, 197, 24, 0.25));
}

.pcard-tt {
  flex: 1;
  min-width: 0;
}

.pcard-name {
  font-size: 16px;
  font-weight: 700;
  display: flex;
  align-items: center;
  gap: 8px;
  flex-wrap: wrap;
}

.pcard-official {
  font-size: 10px;
  color: var(--violet);
  border: 1px solid rgba(139, 123, 255, 0.45);
  background: var(--violet-soft);
  border-radius: 999px;
  padding: 0 7px;
}

.pcard-tagline {
  margin-top: 5px;
  font-size: 13px;
  color: var(--text-3);
  overflow: hidden;
  text-overflow: ellipsis;
  display: -webkit-box;
  -webkit-line-clamp: 2;
  -webkit-box-orient: vertical;
}

.pcard-meta {
  display: flex;
  justify-content: space-between;
  align-items: center;
}

.pcard-stat {
  font-size: 12px;
  color: var(--text-3);
}

.pcard-like {
  background: none;
  border: none;
  color: var(--text-2);
  font-size: 13px;
  transition: transform 0.12s;
}

.pcard-like.on {
  color: var(--heat);
}

.pcard-like:active {
  transform: scale(1.2);
}

.pcard-act {
  display: flex;
  gap: 8px;
  margin-top: auto;
}
</style>
