<script setup>
import { ref, computed, onMounted, inject } from 'vue'
import { useRouter } from 'vue-router'
import { scenarioApi, personaApi, theaterApi } from '../../api/modules'
import { useUserStore } from '../../stores/user'
import PersonaCard from '../../components/PersonaCard.vue'
import EmptyState from '../../components/EmptyState.vue'

const router = useRouter()
const store = useUserStore()
const toast = inject('toast', () => {})

const step = ref(1)
const scenarios = ref([])
const personas = ref([])
const myPersonas = ref([])
const archetypes = ref([])

const pickedScenario = ref(null)
const cast = ref([]) // 选中的人设（按选择顺序）
const form = ref({ title: '', visibility: 'PUBLIC', directorNote: '' })
const filterArchetype = ref('')
const filterSort = ref('hot')
const keyword = ref('')
const busy = ref(false)

onMounted(async () => {
  const [s, m, arch, mine] = await Promise.all([
    scenarioApi.list(),
    personaApi.market({ size: 30, sort: 'hot' }),
    personaApi.archetypes().catch(() => []),
    store.loggedIn ? personaApi.mine().catch(() => []) : Promise.resolve([])
  ])
  scenarios.value = s || []
  personas.value = m?.records || []
  archetypes.value = arch || []
  myPersonas.value = mine || []
})

function pickScenario(s) {
  pickedScenario.value = s
  step.value = 2
}

const candidates = computed(() => {
  let list = [...personas.value, ...myPersonas.value.filter((p) => !personas.value.some((x) => x.id === p.id))]
  if (filterArchetype.value) {
    list = list.filter((p) => p.archetype === filterArchetype.value)
  }
  if (keyword.value.trim()) {
    const k = keyword.value.trim()
    list = list.filter((p) => p.name.includes(k) || (p.tagline || '').includes(k))
  }
  if (filterSort.value === 'new') {
    list.sort((a, b) => b.id - a.id)
  } else {
    list.sort((a, b) => (b.likeCount || 0) - (a.likeCount || 0))
  }
  return list
})

function toggleCast(p) {
  const i = cast.value.findIndex((x) => x.id === p.id)
  if (i >= 0) {
    cast.value.splice(i, 1)
    return
  }
  if (cast.value.length >= 6) {
    toast('剧组最多 6 位演员', 'err')
    return
  }
  cast.value.push(p)
}

function inCast(p) {
  return cast.value.some((x) => x.id === p.id)
}

function nextStep() {
  if (cast.value.length < 2) {
    toast('至少选 2 位演员才能开吵', 'err')
    return
  }
  step.value = 3
}

async function ignite() {
  if (busy.value) return
  busy.value = true
  try {
    const created = await theaterApi.create({
      title: form.value.title.trim() || pickedScenario.value.name,
      scenarioId: pickedScenario.value.id,
      personaIds: cast.value.map((p) => p.id),
      visibility: form.value.visibility,
      directorNote: form.value.directorNote.trim() || null
    })
    toast('开拍成功！')
    router.push(`/theaters/${created.id}`)
  } catch (e) {
    toast(e.message, 'err')
  } finally {
    busy.value = false
  }
}
</script>

<template>
  <div class="page">
    <div class="page-head">
      <div>
        <div class="page-title">创作向导</div>
        <div class="page-sub">三步开演：选场景 → 拉剧组 → 喊开拍</div>
      </div>
    </div>

    <!-- 步骤条 -->
    <div class="steps-bar">
      <div v-for="(s, i) in ['选场景', '拉剧组', '开拍']" :key="s" class="steps-item" :class="{ on: step >= i + 1 }">
        <span class="steps-dot">{{ i + 1 }}</span>
        <span class="steps-label">{{ s }}</span>
      </div>
    </div>

    <!-- Step 1 场景 -->
    <template v-if="step === 1">
      <div class="grid grid-3">
        <div v-for="s in scenarios" :key="s.id" class="card scene-pick" @click="pickScenario(s)">
          <div class="sp-head">
            <span class="sp-name">{{ s.name }}</span>
            <span class="tag">建议 {{ s.suggestedCast }} 人</span>
          </div>
          <div class="sp-desc">{{ s.description }}</div>
          <div class="sp-backdrop">{{ s.backdrop }}</div>
          <div class="sp-cta">选择这个场景 →</div>
        </div>
      </div>
      <EmptyState v-if="!scenarios.length" icon="🎬" title="场景加载中或为空" />
    </template>

    <!-- Step 2 剧组 -->
    <template v-if="step === 2">
      <div class="cast-bar card">
        <div class="cb-left">
          <span class="cb-title">📋 剧组名单（{{ cast.length }}/6）</span>
          <div class="cb-chips">
            <span v-for="p in cast" :key="p.id" class="cb-chip" @click="toggleCast(p)">
              {{ p.avatar }} {{ p.name }} ✕
            </span>
            <span v-if="!cast.length" class="cb-hint">从下面挑 2~6 位演员</span>
          </div>
        </div>
        <div class="cb-acts">
          <button class="btn btn-ghost" @click="step = 1">← 换场景</button>
          <button class="btn btn-primary" :disabled="cast.length < 2" @click="nextStep">下一步 →</button>
        </div>
      </div>

      <div class="filters">
        <input v-model="keyword" class="f-keyword" placeholder="🔍 搜演员名 / 简介" />
        <div class="f-arch">
          <button class="f-chip" :class="{ on: !filterArchetype }" @click="filterArchetype = ''">全部</button>
          <button
            v-for="a in archetypes"
            :key="a"
            class="f-chip"
            :class="{ on: filterArchetype === a }"
            @click="filterArchetype = a"
          >
            {{ a }}
          </button>
        </div>
        <div class="f-sort">
          <button class="f-chip" :class="{ on: filterSort === 'hot' }" @click="filterSort = 'hot'">最热</button>
          <button class="f-chip" :class="{ on: filterSort === 'new' }" @click="filterSort = 'new'">最新</button>
        </div>
      </div>

      <div class="grid grid-4">
        <div v-for="p in candidates" :key="`${p.id}-${p.isOfficial}`" class="pick-wrap" :class="{ picked: inCast(p) }">
          <PersonaCard :persona="p" />
          <button class="pick-btn" :class="{ on: inCast(p) }" @click="toggleCast(p)">
            {{ inCast(p) ? '✓ 已进组' : '+ 拉进组' }}
          </button>
        </div>
      </div>
      <EmptyState v-if="!candidates.length" icon="🎭" title="没有匹配的演员" desc="换个筛选条件，或去工坊自造一位" />
    </template>

    <!-- Step 3 开拍 -->
    <template v-if="step === 3">
      <div class="final-grid">
        <div class="card final-form">
          <div class="panel-title">🎬 开拍确认</div>

          <label class="field">
            <span>剧场标题</span>
            <input v-model="form.title" :placeholder="pickedScenario.name" maxlength="60" />
          </label>

          <label class="field">
            <span>可见性</span>
            <div class="vis-row">
              <label class="vis" :class="{ on: form.visibility === 'PUBLIC' }">
                <input v-model="form.visibility" type="radio" value="PUBLIC" hidden />
                🌍 公开剧场——上广场，人人可围观可插话
              </label>
              <label class="vis" :class="{ on: form.visibility === 'PRIVATE' }">
                <input v-model="form.visibility" type="radio" value="PRIVATE" hidden />
                🔒 私密剧场——只有你能看
              </label>
            </div>
          </label>

          <label class="field">
            <span>导演手记（注入所有演员的戏路指引，可留空）</span>
            <textarea v-model="form.directorNote" maxlength="200"
              placeholder="例：今晚重点吵期末考试，谁先认输谁请奶茶"></textarea>
          </label>

          <div class="final-acts">
            <button class="btn btn-ghost" @click="step = 2">← 回剧组</button>
            <button class="btn btn-primary btn-lg" :disabled="busy" @click="ignite">
              {{ busy ? '开拍中…' : '🎥 喊 开 拍 ！' }}
            </button>
          </div>
        </div>

        <div class="card final-review">
          <div class="panel-title">📖 剧目单</div>
          <div class="rv-row"><span>场景</span><b>{{ pickedScenario?.name }}</b></div>
          <div class="rv-row"><span>舞台</span><b class="rv-scroll">{{ pickedScenario?.backdrop }}</b></div>
          <div class="rv-cast">
            <div v-for="p in cast" :key="p.id" class="rv-actor">
              <span class="rv-avatar">{{ p.avatar }}</span>
              <div>
                <b>{{ p.name }}</b>
                <i>{{ p.archetype }} · {{ p.tagline }}</i>
              </div>
            </div>
          </div>
          <div class="rv-tip">💡 开拍后：扔事件卡、下指令、调活跃度、随时喊卡杀青</div>
        </div>
      </div>
    </template>
  </div>
</template>

<style scoped>
/* 步骤条 */
.steps-bar {
  display: flex;
  gap: 8px;
  margin-bottom: 26px;
}

.steps-item {
  flex: 1;
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 12px 16px;
  border-radius: var(--radius);
  border: 1px solid var(--border-soft);
  background: var(--panel);
  color: var(--text-3);
}

.steps-item.on {
  border-color: var(--gold-line);
  color: var(--gold);
  background: var(--gold-soft);
}

.steps-dot {
  width: 24px;
  height: 24px;
  border-radius: 50%;
  border: 1.5px solid currentColor;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 12px;
  font-weight: 700;
}

.steps-label {
  font-size: 14px;
  font-weight: 600;
}

/* Step1 场景 */
.scene-pick {
  cursor: pointer;
  transition: border-color 0.15s, transform 0.15s;
}

.scene-pick:hover {
  border-color: var(--gold);
  transform: translateY(-3px);
}

.sp-head {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 10px;
}

.sp-name {
  font-size: 17px;
  font-weight: 700;
}

.sp-desc {
  font-size: 13px;
  color: var(--text-2);
  margin-bottom: 10px;
}

.sp-backdrop {
  font-size: 12px;
  color: var(--text-3);
  background: var(--bg-soft);
  border-radius: var(--radius-s);
  padding: 10px;
  display: -webkit-box;
  -webkit-line-clamp: 3;
  -webkit-box-orient: vertical;
  overflow: hidden;
}

.sp-cta {
  margin-top: 12px;
  font-size: 13px;
  color: var(--gold);
  text-align: right;
}

/* Step2 剧组 */
.cast-bar {
  position: sticky;
  top: calc(var(--nav-h) + 10px);
  z-index: 50;
  display: flex;
  justify-content: space-between;
  align-items: center;
  gap: 16px;
  margin-bottom: 18px;
  padding: 14px 18px;
}

.cb-left {
  flex: 1;
  min-width: 0;
}

.cb-title {
  font-size: 13px;
  color: var(--text-2);
  font-weight: 600;
}

.cb-chips {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
  margin-top: 8px;
}

.cb-chip {
  font-size: 12px;
  padding: 4px 10px;
  border-radius: 999px;
  background: var(--gold-soft);
  color: var(--gold);
  border: 1px solid var(--gold-line);
  cursor: pointer;
}

.cb-chip:hover {
  background: var(--heat-soft);
  color: var(--heat);
  border-color: rgba(255, 90, 95, 0.4);
}

.cb-hint {
  font-size: 12px;
  color: var(--text-3);
}

.cb-acts {
  display: flex;
  gap: 10px;
  flex-shrink: 0;
}

.filters {
  display: flex;
  gap: 12px;
  align-items: center;
  margin-bottom: 18px;
  flex-wrap: wrap;
}

.f-keyword {
  width: 220px;
}

.f-arch,
.f-sort {
  display: flex;
  gap: 6px;
  flex-wrap: wrap;
}

.f-chip {
  background: var(--panel);
  border: 1px solid var(--border);
  color: var(--text-2);
  border-radius: 999px;
  padding: 4px 13px;
  font-size: 12.5px;
  transition: all 0.15s;
}

.f-chip.on {
  color: var(--gold);
  border-color: var(--gold-line);
  background: var(--gold-soft);
}

.pick-wrap {
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.pick-wrap.picked :deep(.pcard) {
  border-color: var(--gold);
  box-shadow: 0 0 0 1px var(--gold-line);
}

.pick-btn {
  border: 1px dashed var(--border);
  background: transparent;
  color: var(--text-2);
  border-radius: var(--radius-s);
  padding: 8px;
  font-size: 13px;
  font-weight: 600;
  transition: all 0.15s;
}

.pick-btn.on {
  border-style: solid;
  color: var(--gold);
  border-color: var(--gold);
  background: var(--gold-soft);
}

/* Step3 确认 */
.final-grid {
  display: grid;
  grid-template-columns: 1.2fr 1fr;
  gap: 18px;
}

.field {
  display: block;
  margin-bottom: 16px;
}

.field span {
  display: block;
  font-size: 13px;
  color: var(--text-2);
  margin-bottom: 7px;
}

.field input,
.field textarea {
  width: 100%;
}

.vis-row {
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.vis {
  border: 1px solid var(--border);
  border-radius: var(--radius-s);
  padding: 11px 14px;
  font-size: 13.5px;
  color: var(--text-2);
  cursor: pointer;
  transition: all 0.15s;
}

.vis.on {
  border-color: var(--gold);
  color: var(--gold);
  background: var(--gold-soft);
}

.final-acts {
  display: flex;
  justify-content: space-between;
  margin-top: 22px;
}

.rv-row {
  display: flex;
  gap: 12px;
  font-size: 13px;
  padding: 8px 0;
  border-bottom: 1px dashed var(--border-soft);
}

.rv-row span {
  color: var(--text-3);
  flex-shrink: 0;
  width: 44px;
}

.rv-scroll {
  max-height: 84px;
  overflow-y: auto;
  color: var(--text-2);
}

.rv-cast {
  margin-top: 12px;
  display: flex;
  flex-direction: column;
  gap: 10px;
}

.rv-actor {
  display: flex;
  gap: 10px;
  align-items: center;
}

.rv-avatar {
  font-size: 26px;
}

.rv-actor b {
  font-size: 14px;
}

.rv-actor i {
  display: block;
  font-style: normal;
  font-size: 12px;
  color: var(--text-3);
}

.rv-tip {
  margin-top: 16px;
  font-size: 12.5px;
  color: var(--gold);
  background: var(--gold-soft);
  border-radius: var(--radius-s);
  padding: 10px 12px;
}

@media (max-width: 900px) {
  .final-grid { grid-template-columns: 1fr; }
  .cast-bar { position: static; flex-direction: column; align-items: stretch; }
}
</style>
