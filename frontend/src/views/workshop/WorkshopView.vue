<script setup>
/**
 * 人设工坊：我造的演员 + 全市场演员浏览。
 * 我的演员可编辑/删除/试聊；市场演员可查看档案/试聊。
 */
import { ref, computed, onMounted, inject } from 'vue'
import { useRouter } from 'vue-router'
import { personaApi } from '../../api/modules'
import { useUserStore } from '../../stores/user'
import PersonaCard from '../../components/PersonaCard.vue'
import EmptyState from '../../components/EmptyState.vue'

const router = useRouter()
const store = useUserStore()
const toast = inject('toast', () => {})

const tab = ref(store.loggedIn ? 'mine' : 'market')
const mine = ref([])
const market = ref([])
const archetypes = ref(['毒舌', '杠精', '慈祥', '卷王', '社恐', '吃瓜', '显眼包', '暴躁', '阴阳', '财迷'])

const filterArchetype = ref('')
const keyword = ref('')
const sort = ref('hot')
const page = ref(1)
const total = ref(0)
const loading = ref(false)

onMounted(async () => {
  personaApi.archetypes().then((a) => {
    if (a?.length) archetypes.value = a
  }).catch(() => {})
  if (store.loggedIn) loadMine()
  loadMarket(true)
})

async function loadMine() {
  try {
    mine.value = (await personaApi.mine()) || []
  } catch (e) {
    mine.value = []
  }
}

async function loadMarket(reset = false) {
  if (reset) {
    page.value = 1
    total.value = 0
  }
  loading.value = true
  try {
    const r = await personaApi.market({
      page: page.value,
      size: 12,
      archetype: filterArchetype.value || undefined,
      keyword: keyword.value.trim() || undefined,
      sort: sort.value
    })
    market.value = reset ? r.records : [...market.value, ...r.records]
    total.value = r.total
  } catch (e) {
    toast(e.message, 'err')
  } finally {
    loading.value = false
  }
}

function applyFilter() {
  loadMarket(true)
}

const hasMore = computed(() => market.value.length < total.value)

function loadMore() {
  page.value += 1
  loadMarket(false)
}

async function remove(p) {
  if (!confirm(`确定删除演员「${p.name}」？此操作不可撤销。`)) return
  try {
    await personaApi.remove(p.id)
    toast('演员已送别')
    loadMine()
    loadMarket(true)
  } catch (e) {
    toast(e.message, 'err')
  }
}
</script>

<template>
  <div class="page">
    <div class="page-head">
      <div>
        <div class="page-title">人设工坊</div>
        <div class="page-sub">演员经纪处：自造人设上台演出，或从市场签约现成好戏骨</div>
      </div>
      <router-link v-if="store.loggedIn" to="/workshop/new" class="btn btn-primary">＋ 造一位新演员</router-link>
    </div>

    <div class="wk-tabs">
      <button class="wk-tab" :class="{ on: tab === 'mine' }" @click="tab = 'mine'">
        🎭 我的人设 <span v-if="mine.length" class="wk-count">{{ mine.length }}</span>
      </button>
      <button class="wk-tab" :class="{ on: tab === 'market' }" @click="tab = 'market'">
        🌍 演员市场 <span class="wk-count">{{ total }}</span>
      </button>
    </div>

    <!-- 我的人设 -->
    <template v-if="tab === 'mine'">
      <div v-if="store.loggedIn" class="grid grid-4">
        <div v-for="p in mine" :key="p.id" class="wk-item">
          <PersonaCard :persona="p">
            <template #actions>
              <button class="btn btn-ghost btn-sm" @click="router.push(`/workshop/${p.id}/edit`)">✏️ 编辑</button>
              <button class="btn btn-ghost btn-sm wk-del" @click="remove(p)">🗑️ 删除</button>
            </template>
          </PersonaCard>
        </div>
      </div>
      <EmptyState
        v-if="!mine.length"
        icon="🎭"
        title="还没有自造演员"
        desc="造一位有性格、有雷点、有故事的演员，让它替你上台吵架"
      >
        <router-link to="/workshop/new" class="btn btn-primary">开造第一位 →</router-link>
      </EmptyState>
    </template>

    <!-- 市场 -->
    <template v-else>
      <div class="wk-filters">
        <input v-model="keyword" class="wk-keyword" placeholder="🔍 搜演员名 / 一句话人设" @keydown.enter="applyFilter" />
        <div class="wk-arch">
          <button class="wk-chip" :class="{ on: !filterArchetype }" @click="filterArchetype = ''; applyFilter()">全部</button>
          <button
            v-for="a in archetypes"
            :key="a"
            class="wk-chip"
            :class="{ on: filterArchetype === a }"
            @click="filterArchetype = filterArchetype === a ? '' : a; applyFilter()"
          >
            {{ a }}
          </button>
        </div>
        <div class="wk-sort">
          <button class="wk-chip" :class="{ on: sort === 'hot' }" @click="sort = 'hot'; applyFilter()">最热</button>
          <button class="wk-chip" :class="{ on: sort === 'new' }" @click="sort = 'new'; applyFilter()">最新</button>
        </div>
      </div>

      <div class="grid grid-4">
        <PersonaCard v-for="p in market" :key="`${p.id}-${p.isOfficial}`" :persona="p">
          <template #actions>
            <router-link :to="`/workshop/${p.id}`" class="btn btn-ghost btn-sm">💬 试聊</router-link>
          </template>
        </PersonaCard>
      </div>

      <div v-if="loading" class="wk-loading">翻演员名册中……</div>
      <EmptyState v-else-if="!market.length" icon="🔍" title="没有匹配的演员" desc="换个筛选条件试试" />

      <div v-if="hasMore && !loading" class="wk-more">
        <button class="btn btn-ghost" @click="loadMore">加载更多（{{ market.length }}/{{ total }}）</button>
      </div>
    </template>
  </div>
</template>

<style scoped>
.wk-tabs {
  display: flex;
  gap: 8px;
  margin-bottom: 18px;
  border-bottom: 1px solid var(--border-soft);
  padding-bottom: 0;
}

.wk-tab {
  background: none;
  border: none;
  border-bottom: 2px solid transparent;
  color: var(--text-3);
  font-size: 14.5px;
  font-weight: 600;
  padding: 10px 16px;
  transition: all 0.15s;
}

.wk-tab.on {
  color: var(--gold);
  border-bottom-color: var(--gold);
}

.wk-count {
  font-size: 11px;
  background: var(--bg-soft);
  border-radius: 999px;
  padding: 1px 8px;
  margin-left: 6px;
  font-family: var(--font-mono);
}

.wk-item {
  display: flex;
  flex-direction: column;
}

.wk-del:hover {
  color: var(--heat);
  border-color: rgba(255, 90, 95, 0.4);
}

.wk-filters {
  display: flex;
  gap: 12px;
  align-items: center;
  flex-wrap: wrap;
  margin-bottom: 18px;
}

.wk-keyword {
  width: 220px;
}

.wk-arch,
.wk-sort {
  display: flex;
  gap: 6px;
  flex-wrap: wrap;
}

.wk-chip {
  background: var(--panel);
  border: 1px solid var(--border);
  color: var(--text-2);
  border-radius: 999px;
  padding: 4px 13px;
  font-size: 12.5px;
  transition: all 0.15s;
}

.wk-chip.on {
  color: var(--gold);
  border-color: var(--gold-line);
  background: var(--gold-soft);
}

.wk-loading {
  text-align: center;
  color: var(--text-3);
  font-size: 13px;
  padding: 24px;
}

.wk-more {
  display: flex;
  justify-content: center;
  margin-top: 20px;
}
</style>
