<script setup>
/**
 * 个人中心（导演主页）：等级成长条 + 六维数据 + 我的演员 + 收藏夹。
 */
import { ref, onMounted } from 'vue'
import { meApi, personaApi } from '../../api/modules'
import { useUserStore } from '../../stores/user'
import PersonaCard from '../../components/PersonaCard.vue'
import TheaterCard from '../../components/TheaterCard.vue'
import EmptyState from '../../components/EmptyState.vue'

const store = useUserStore()
const overview = ref(null)
const favorites = ref([])
const myPersonas = ref([])
const loading = ref(true)

onMounted(async () => {
  try {
    const [o, fav, mine] = await Promise.all([
      meApi.overview(),
      meApi.favorites().catch(() => []),
      personaApi.mine().catch(() => [])
    ])
    overview.value = o
    favorites.value = fav || []
    myPersonas.value = mine || []
  } finally {
    loading.value = false
  }
})

function fmtXp(n) {
  if (n == null) return '0'
  if (n >= 10000) return (n / 10000).toFixed(1).replace(/\.0$/, '') + 'w'
  return String(n)
}
</script>

<template>
  <div class="page">
    <div class="page-head">
      <div>
        <div class="page-title">个人中心</div>
        <div class="page-sub">你的导演生涯档案：等级、数据、演员与收藏</div>
      </div>
    </div>

    <div v-if="loading" class="me-loading">整理档案中……</div>

    <template v-else-if="overview">
      <!-- 成长卡 -->
      <div class="card me-hero">
        <div class="me-hero-left">
          <span class="me-avatar">{{ overview.avatar || '🎬' }}</span>
          <div>
            <div class="me-nick">
              {{ overview.nickname }}
              <span class="me-level">{{ overview.levelName }}</span>
            </div>
            <div class="me-xp-row">
              <div class="me-xp-bar">
                <i :style="{ width: Math.min(100, overview.levelProgress) + '%' }"></i>
              </div>
              <span class="me-xp-text">
                {{ fmtXp(overview.xp) }} XP<template v-if="overview.nextLevelXp > 0"> / {{ fmtXp(overview.nextLevelXp) }}</template>
              </span>
            </div>
            <div class="me-xp-hint">
              <template v-if="overview.nextLevelXp > 0">
                再获 {{ fmtXp(overview.nextLevelXp - overview.xp) }} XP 晋级下一档（开拍+10 / 发言+1 / 造人设+5 / 被赞+5）
              </template>
              <template v-else>已抵达导演链顶点：金牌导演</template>
            </div>
          </div>
        </div>
        <div class="me-hero-right">
          <router-link to="/my-theaters" class="me-quick">🎬<br />我的剧场</router-link>
          <router-link to="/workshop" class="me-quick">🎭<br />人设工坊</router-link>
          <router-link to="/wizard" class="me-quick">🎥<br />再开新戏</router-link>
        </div>
      </div>

      <!-- 六维数据 -->
      <div class="me-stats">
        <div class="card me-stat">
          <b>{{ overview.liveTheaters }}</b>
          <span>上映中剧场</span>
        </div>
        <div class="card me-stat">
          <b>{{ overview.endedTheaters }}</b>
          <span>已完结剧场</span>
        </div>
        <div class="card me-stat">
          <b>{{ overview.personaCount }}</b>
          <span>自造人设</span>
        </div>
        <div class="card me-stat">
          <b>{{ overview.myMessageCount }}</b>
          <span>我的发言数</span>
        </div>
        <div class="card me-stat">
          <b>{{ overview.receivedLikes }}</b>
          <span>累计获赞</span>
        </div>
        <div class="card me-stat">
          <b>{{ overview.favoriteCount }}</b>
          <span>收藏剧目</span>
        </div>
      </div>

      <!-- 我的演员 -->
      <div class="me-sec-title">🎭 我的演员（{{ myPersonas.length }}）</div>
      <div v-if="myPersonas.length" class="grid grid-4">
        <PersonaCard v-for="p in myPersonas" :key="p.id" :persona="p">
          <template #actions>
            <router-link :to="`/workshop/${p.id}`" class="btn btn-ghost btn-sm">查看档案</router-link>
          </template>
        </PersonaCard>
      </div>
      <EmptyState v-else icon="🎭" title="还没有自造演员" desc="造一位演员，让它替你上台">
        <router-link to="/workshop/new" class="btn btn-primary">去造一位 →</router-link>
      </EmptyState>

      <!-- 收藏 -->
      <div class="me-sec-title">⭐ 我的收藏（{{ favorites.length }}）</div>
      <div v-if="favorites.length" class="grid grid-3">
        <TheaterCard v-for="t in favorites" :key="t.id" :theater="t" />
      </div>
      <EmptyState v-else icon="⭐" title="收藏夹是空的" desc="在直播间点收藏，好戏随时回看">
        <router-link to="/plaza" class="btn btn-primary">去广场逛逛 →</router-link>
      </EmptyState>
    </template>
  </div>
</template>

<style scoped>
.me-loading {
  text-align: center;
  padding: 100px 0;
  color: var(--text-3);
}

/* 成长卡 */
.me-hero {
  display: flex;
  justify-content: space-between;
  gap: 20px;
  padding: 22px;
  margin-bottom: 16px;
  flex-wrap: wrap;
  background: linear-gradient(135deg, var(--panel), rgba(245, 197, 24, 0.05));
}

.me-hero-left {
  display: flex;
  gap: 16px;
  align-items: center;
  min-width: 260px;
  flex: 1;
}

.me-avatar {
  font-size: 52px;
  filter: drop-shadow(0 0 12px rgba(245, 197, 24, 0.3));
}

.me-nick {
  font-size: 20px;
  font-weight: 800;
  display: flex;
  align-items: center;
  gap: 10px;
  flex-wrap: wrap;
}

.me-level {
  font-size: 11.5px;
  color: var(--gold);
  border: 1px solid var(--gold-line);
  background: var(--gold-soft);
  border-radius: 999px;
  padding: 2px 11px;
  font-weight: 700;
}

.me-xp-row {
  display: flex;
  align-items: center;
  gap: 10px;
  margin-top: 10px;
}

.me-xp-bar {
  width: 200px;
  height: 8px;
  border-radius: 999px;
  background: var(--bg-soft);
  overflow: hidden;
}

.me-xp-bar i {
  display: block;
  height: 100%;
  background: linear-gradient(90deg, var(--gold), var(--violet));
  border-radius: 999px;
  transition: width 0.5s ease;
}

.me-xp-text {
  font-size: 12px;
  color: var(--text-2);
  font-family: var(--font-mono);
}

.me-xp-hint {
  margin-top: 8px;
  font-size: 11.5px;
  color: var(--text-3);
}

.me-hero-right {
  display: flex;
  gap: 10px;
  align-items: center;
}

.me-quick {
  width: 84px;
  padding: 12px 8px;
  text-align: center;
  font-size: 12px;
  color: var(--text-2);
  background: var(--bg-soft);
  border: 1px solid var(--border);
  border-radius: var(--radius-s);
  line-height: 1.7;
  transition: all 0.15s;
}

.me-quick:hover {
  border-color: var(--gold-line);
  color: var(--gold);
  transform: translateY(-2px);
}

/* 六维 */
.me-stats {
  display: grid;
  grid-template-columns: repeat(6, 1fr);
  gap: 12px;
  margin-bottom: 24px;
}

.me-stat {
  padding: 16px 10px;
  text-align: center;
}

.me-stat b {
  display: block;
  font-size: 22px;
  font-weight: 800;
  font-family: var(--font-mono);
  color: var(--gold);
}

.me-stat span {
  font-size: 12px;
  color: var(--text-3);
}

.me-sec-title {
  font-size: 15px;
  font-weight: 800;
  margin: 24px 0 14px;
}

@media (max-width: 900px) {
  .me-stats {
    grid-template-columns: repeat(3, 1fr);
  }

  .me-hero-right {
    width: 100%;
    justify-content: flex-start;
  }
}
</style>
