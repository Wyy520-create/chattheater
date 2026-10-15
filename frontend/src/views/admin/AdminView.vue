<script setup>
/**
 * 运营看板（仅 ADMIN）：总览数字带 / LLM 成本 / 敏感词管理 / 审核日志 / 人设巡查。
 */
import { ref, computed, onMounted, inject } from 'vue'
import { adminApi } from '../../api/modules'
import EmptyState from '../../components/EmptyState.vue'

const toast = inject('toast', () => {})

const tab = ref('board')

// 看板
const overview = ref(null)
const cost = ref(null)

// 敏感词
const words = ref([])
const wordsPage = ref(1)
const wordsTotal = ref(0)
const newWord = ref('')
const newCategory = ref('通用')
const wordBusy = ref(false)

// 审核日志
const logs = ref([])
const logsPage = ref(1)
const logsTotal = ref(0)

// 人设管理
const personas = ref([])
const personasPage = ref(1)
const personasTotal = ref(0)
const personaKeyword = ref('')
const personaStatus = ref('')

const PAGE_SIZE = 10

onMounted(async () => {
  loadBoard()
  loadWords()
  loadLogs()
  loadPersonas()
})

async function loadBoard() {
  try {
    const [o, c] = await Promise.all([adminApi.overview(), adminApi.cost()])
    overview.value = o
    cost.value = c
  } catch (e) {
    toast(e.message, 'err')
  }
}

async function loadWords(reset = false) {
  if (reset) wordsPage.value = 1
  try {
    const r = await adminApi.words({ page: wordsPage.value, size: PAGE_SIZE })
    words.value = r.records || []
    wordsTotal.value = r.total
  } catch (e) {
    toast(e.message, 'err')
  }
}

async function addWord() {
  const w = newWord.value.trim()
  if (!w) return toast('敏感词不能为空', 'err')
  wordBusy.value = true
  try {
    await adminApi.addWord({ word: w, category: newCategory.value || '通用' })
    toast(`敏感词「${w}」已入库，立即生效`)
    newWord.value = ''
    loadWords()
  } catch (e) {
    toast(e.message, 'err')
  } finally {
    wordBusy.value = false
  }
}

async function delWord(w) {
  if (!confirm(`删除敏感词「${w.word}」？删除后立即失效。`)) return
  try {
    await adminApi.deleteWord(w.id)
    toast('已删除')
    loadWords()
  } catch (e) {
    toast(e.message, 'err')
  }
}

async function loadLogs(reset = false) {
  if (reset) logsPage.value = 1
  try {
    const r = await adminApi.logs({ page: logsPage.value, size: PAGE_SIZE })
    logs.value = r.records || []
    logsTotal.value = r.total
  } catch (e) {
    toast(e.message, 'err')
  }
}

async function loadPersonas(reset = false) {
  if (reset) personasPage.value = 1
  try {
    const r = await adminApi.personas({
      page: personasPage.value,
      size: PAGE_SIZE,
      keyword: personaKeyword.value.trim() || undefined,
      status: personaStatus.value === '' ? undefined : Number(personaStatus.value)
    })
    personas.value = r.records || []
    personasTotal.value = r.total
  } catch (e) {
    toast(e.message, 'err')
  }
}

async function togglePersonaStatus(p) {
  const next = p.status === 1 ? 0 : 1
  const verb = next === 1 ? '上架' : '下架'
  if (!confirm(`确认${verb}人设「${p.name}」？${next === 0 ? '下架后将不出现在市场与选角中' : '上架后恢复市场可见'}。`)) return
  try {
    const r = await adminApi.personaStatus(p.id, next)
    p.status = r.status
    toast(`「${p.name}」已${verb}`)
  } catch (e) {
    toast(e.message, 'err')
  }
}

// 成本柱状图数据
const chart = computed(() => {
  if (!cost.value?.daily?.length) return []
  const max = Math.max(...cost.value.daily.map((d) => d.calls), 1)
  return cost.value.daily.map((d) => ({
    ...d,
    h: Math.max(4, Math.round((d.calls / max) * 100)),
    mockH: Math.max(0, Math.round((d.mockCalls / max) * 100))
  }))
})

function pages(total, size = PAGE_SIZE) {
  return Math.max(1, Math.ceil(total / size))
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
    <div class="page-head">
      <div>
        <div class="page-title">运营看板</div>
        <div class="page-sub">平台全量数据、内容安全与成本监测</div>
      </div>
    </div>

    <div class="ad-tabs">
      <button class="ad-tab" :class="{ on: tab === 'board' }" @click="tab = 'board'">📊 数据看板</button>
      <button class="ad-tab" :class="{ on: tab === 'words' }" @click="tab = 'words'">🛡️ 敏感词</button>
      <button class="ad-tab" :class="{ on: tab === 'logs' }" @click="tab = 'logs'">📋 审核日志</button>
      <button class="ad-tab" :class="{ on: tab === 'personas' }" @click="tab = 'personas'">🎭 人设巡查</button>
    </div>

    <!-- ============ 看板 ============ -->
    <template v-if="tab === 'board'">
      <div v-if="overview" class="ad-stats">
        <div class="card ad-stat">
          <b>{{ overview.users }}</b><span>注册用户</span>
          <i class="ad-sub">今日 +{{ overview.todayNewUsers }}</i>
        </div>
        <div class="card ad-stat">
          <b>{{ overview.liveTheaters }}</b><span>上映中剧场</span>
        </div>
        <div class="card ad-stat">
          <b>{{ overview.endedTheaters }}</b><span>已完结剧场</span>
        </div>
        <div class="card ad-stat">
          <b>{{ overview.personas }}</b><span>在册人设</span>
        </div>
        <div class="card ad-stat">
          <b>{{ overview.messages }}</b><span>累计消息</span>
          <i class="ad-sub">今日 +{{ overview.todayNewMessages }}</i>
        </div>
        <div class="card ad-stat">
          <b>{{ overview.blockedCount }}</b><span>审核拦截</span>
        </div>
      </div>

      <div v-if="cost" class="ad-cost-grid">
        <div class="card ad-cost">
          <div class="panel-title">📡 LLM 调用总览</div>
          <div class="ac-nums">
            <div class="ac-num">
              <b>{{ cost.totalCalls }}</b><span>总调用</span>
            </div>
            <div class="ac-num">
              <b class="ok">{{ cost.okCalls }}</b><span>成功</span>
            </div>
            <div class="ac-num">
              <b class="mock">{{ cost.mockCalls }}</b><span>Mock 兜底</span>
            </div>
            <div class="ac-num">
              <b>{{ cost.realCalls }}</b><span>真实调用</span>
            </div>
          </div>
          <div class="ac-ratio">
            <div class="ac-ratio-label">
              <span>Mock 占比</span>
              <b>{{ cost.mockRatio.toFixed(1) }}%</b>
            </div>
            <div class="ac-ratio-bar">
              <i :style="{ width: Math.min(100, cost.mockRatio) + '%' }"></i>
            </div>
            <p class="ac-tip">无真实 Key 时全量 Mock 兜底，用户无感知；配 Key 后该值应显著下降</p>
          </div>
        </div>

        <div class="card ad-cost">
          <div class="panel-title">💰 Token 与延迟</div>
          <div class="ac-nums">
            <div class="ac-num"><b>{{ cost.promptTokens }}</b><span>Prompt Tokens</span></div>
            <div class="ac-num"><b>{{ cost.completionTokens }}</b><span>Completion Tokens</span></div>
            <div class="ac-num"><b>{{ cost.avgLatencyMs }}ms</b><span>平均延迟</span></div>
          </div>
        </div>

        <div class="card ad-chart-card">
          <div class="panel-title">📈 近 7 日调用量</div>
          <div v-if="chart.length" class="ad-chart">
            <div v-for="d in chart" :key="d.day" class="ad-col" :title="`${d.day}：总 ${d.calls} 次（Mock ${d.mockCalls}）`">
              <div class="ad-col-bars">
                <div class="ad-bar real" :style="{ height: d.h + '%' }"></div>
                <div class="ad-bar mock" :style="{ height: d.mockH + '%' }"></div>
              </div>
              <span class="ad-day">{{ d.day.slice(5) }}</span>
            </div>
          </div>
          <EmptyState v-else icon="📈" title="暂无调用数据" desc="等演员们开口，这里会亮起来" />
          <div class="ad-legend">
            <span><i class="dot real"></i>总调用</span>
            <span><i class="dot mock"></i>Mock</span>
          </div>
        </div>
      </div>
    </template>

    <!-- ============ 敏感词 ============ -->
    <template v-else-if="tab === 'words'">
      <div class="card ad-panel">
        <div class="ad-add-row">
          <input v-model="newWord" class="ad-add-input" placeholder="新敏感词" maxlength="40" @keydown.enter="addWord" />
          <input v-model="newCategory" class="ad-add-cat" placeholder="分类" maxlength="16" />
          <button class="btn btn-primary" :disabled="wordBusy || !newWord.trim()" @click="addWord">＋ 入库</button>
        </div>
        <p class="ad-add-tip">入库即热生效（内存词库即时刷新），命中将替换为"该台词未通过内容审查"并记一条 BLOCK 日志</p>

        <table class="ad-table">
          <thead>
            <tr><th>敏感词</th><th>分类</th><th>命中次数</th><th>入库时间</th><th>操作</th></tr>
          </thead>
          <tbody>
            <tr v-for="w in words" :key="w.id">
              <td class="ad-word">{{ w.word }}</td>
              <td><span class="tag">{{ w.category }}</span></td>
              <td class="ad-mono">{{ w.hitCount }}</td>
              <td class="ad-mute">{{ fmtTime(w.createdAt) }}</td>
              <td><button class="ad-del" @click="delWord(w)">删除</button></td>
            </tr>
            <tr v-if="!words.length"><td colspan="5" class="ad-empty-cell">词库空空如也</td></tr>
          </tbody>
        </table>

        <div class="ad-pager" v-if="pages(wordsTotal) > 1">
          <button :disabled="wordsPage <= 1" @click="wordsPage--; loadWords()">← 上一页</button>
          <span>{{ wordsPage }} / {{ pages(wordsTotal) }}</span>
          <button :disabled="wordsPage >= pages(wordsTotal)" @click="wordsPage++; loadWords()">下一页 →</button>
        </div>
      </div>
    </template>

    <!-- ============ 审核日志 ============ -->
    <template v-else-if="tab === 'logs'">
      <div class="card ad-panel">
        <table class="ad-table">
          <thead>
            <tr><th>时间</th><th>目标类型</th><th>目标 ID</th><th>命中词</th><th>动作</th><th>详情</th></tr>
          </thead>
          <tbody>
            <tr v-for="l in logs" :key="l.id">
              <td class="ad-mute">{{ fmtTime(l.createdAt) }}</td>
              <td><span class="tag">{{ l.targetType }}</span></td>
              <td class="ad-mono">{{ l.targetId ?? '-' }}</td>
              <td class="ad-word">{{ l.word || '-' }}</td>
              <td><span class="tag" :class="l.action === 'BLOCK' ? 'tag-block' : ''">{{ l.action }}</span></td>
              <td class="ad-mute ad-detail">{{ l.detail || '-' }}</td>
            </tr>
            <tr v-if="!logs.length"><td colspan="6" class="ad-empty-cell">平台一片清明，暂无拦截记录</td></tr>
          </tbody>
        </table>
        <div class="ad-pager" v-if="pages(logsTotal) > 1">
          <button :disabled="logsPage <= 1" @click="logsPage--; loadLogs()">← 上一页</button>
          <span>{{ logsPage }} / {{ pages(logsTotal) }}</span>
          <button :disabled="logsPage >= pages(logsTotal)" @click="logsPage++; loadLogs()">下一页 →</button>
        </div>
      </div>
    </template>

    <!-- ============ 人设巡查 ============ -->
    <template v-else>
      <div class="card ad-panel">
        <div class="ad-filter-row">
          <input v-model="personaKeyword" placeholder="🔍 按名字搜索" @keydown.enter="loadPersonas(true)" />
          <select v-model="personaStatus" @change="loadPersonas(true)">
            <option value="">全部状态</option>
            <option value="1">已上架</option>
            <option value="0">已下架</option>
          </select>
          <button class="btn btn-ghost" @click="loadPersonas(true)">查询</button>
        </div>

        <table class="ad-table">
          <thead>
            <tr><th>演员</th><th>原型</th><th>一句话人设</th><th>出品</th><th>出演</th><th>获赞</th><th>状态</th><th>操作</th></tr>
          </thead>
          <tbody>
            <tr v-for="p in personas" :key="p.id">
              <td class="ad-persona-name">{{ p.avatar }} {{ p.name }}</td>
              <td><span class="tag tag-gold">{{ p.archetype }}</span></td>
              <td class="ad-mute ad-detail">{{ p.tagline }}</td>
              <td>{{ p.isOfficial ? '官方' : 'UP主' }}</td>
              <td class="ad-mono">{{ p.useCount }}</td>
              <td class="ad-mono">{{ p.likeCount }}</td>
              <td>
                <span class="tag" :class="p.status === 1 ? 'tag-ok' : 'tag-block'">
                  {{ p.status === 1 ? '上架' : '下架' }}
                </span>
              </td>
              <td>
                <button class="ad-toggle" @click="togglePersonaStatus(p)">
                  {{ p.status === 1 ? '下架' : '上架' }}
                </button>
              </td>
            </tr>
            <tr v-if="!personas.length"><td colspan="8" class="ad-empty-cell">没有匹配的人设</td></tr>
          </tbody>
        </table>

        <div class="ad-pager" v-if="pages(personasTotal) > 1">
          <button :disabled="personasPage <= 1" @click="personasPage--; loadPersonas()">← 上一页</button>
          <span>{{ personasPage }} / {{ pages(personasTotal) }}</span>
          <button :disabled="personasPage >= pages(personasTotal)" @click="personasPage++; loadPersonas()">下一页 →</button>
        </div>
      </div>
    </template>
  </div>
</template>

<style scoped>
.ad-tabs {
  display: flex;
  gap: 6px;
  border-bottom: 1px solid var(--border-soft);
  margin-bottom: 20px;
  flex-wrap: wrap;
}

.ad-tab {
  background: none;
  border: none;
  border-bottom: 2px solid transparent;
  color: var(--text-3);
  font-size: 14.5px;
  font-weight: 600;
  padding: 10px 16px;
  transition: all 0.15s;
}

.ad-tab.on {
  color: var(--gold);
  border-bottom-color: var(--gold);
}

/* 数字带 */
.ad-stats {
  display: grid;
  grid-template-columns: repeat(6, 1fr);
  gap: 12px;
  margin-bottom: 16px;
}

.ad-stat {
  padding: 16px 12px;
  text-align: center;
  position: relative;
}

.ad-stat b {
  display: block;
  font-size: 24px;
  font-weight: 800;
  font-family: var(--font-mono);
  color: var(--gold);
}

.ad-stat span {
  font-size: 12px;
  color: var(--text-3);
}

.ad-sub {
  position: absolute;
  top: 8px;
  right: 10px;
  font-size: 10px;
  color: var(--heat);
  font-style: normal;
  font-family: var(--font-mono);
}

/* 成本 */
.ad-cost-grid {
  display: grid;
  grid-template-columns: 1.4fr 1fr;
  gap: 14px;
}

.ad-cost,
.ad-chart-card {
  padding: 18px 20px;
}

.ac-nums {
  display: flex;
  gap: 8px;
  margin: 14px 0;
}

.ac-num {
  flex: 1;
  text-align: center;
  background: var(--bg-soft);
  border-radius: var(--radius-s);
  padding: 12px 6px;
}

.ac-num b {
  display: block;
  font-size: 18px;
  font-family: var(--font-mono);
  color: var(--text-1);
}

.ac-num b.ok { color: #2ecc71; }
.ac-num b.mock { color: var(--violet); }

.ac-num span {
  font-size: 11px;
  color: var(--text-3);
}

.ac-ratio-label {
  display: flex;
  justify-content: space-between;
  font-size: 12.5px;
  color: var(--text-2);
  margin-bottom: 7px;
}

.ac-ratio-label b {
  font-family: var(--font-mono);
  color: var(--violet);
}

.ac-ratio-bar {
  height: 8px;
  border-radius: 999px;
  background: var(--bg-soft);
  overflow: hidden;
}

.ac-ratio-bar i {
  display: block;
  height: 100%;
  background: linear-gradient(90deg, var(--violet), #6c5ce7);
  transition: width 0.4s;
}

.ac-tip {
  margin-top: 10px;
  font-size: 11.5px;
  color: var(--text-3);
  line-height: 1.6;
}

/* 柱状图 */
.ad-chart {
  display: flex;
  align-items: flex-end;
  justify-content: space-around;
  height: 150px;
  margin-top: 16px;
  gap: 6px;
}

.ad-col {
  flex: 1;
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 6px;
  height: 100%;
  justify-content: flex-end;
}

.ad-col-bars {
  display: flex;
  gap: 3px;
  align-items: flex-end;
  height: 118px;
}

.ad-bar {
  width: 12px;
  border-radius: 3px 3px 0 0;
  transition: height 0.5s ease;
  min-height: 3px;
}

.ad-bar.real {
  background: linear-gradient(180deg, var(--gold), rgba(245, 197, 24, 0.25));
}

.ad-bar.mock {
  background: rgba(139, 123, 255, 0.55);
}

.ad-day {
  font-size: 10.5px;
  color: var(--text-3);
  font-family: var(--font-mono);
}

.ad-legend {
  display: flex;
  gap: 16px;
  justify-content: center;
  margin-top: 12px;
  font-size: 11.5px;
  color: var(--text-3);
}

.ad-legend .dot {
  display: inline-block;
  width: 9px;
  height: 9px;
  border-radius: 3px;
  margin-right: 5px;
  vertical-align: -1px;
}

.ad-legend .dot.real { background: var(--gold); }
.ad-legend .dot.mock { background: rgba(139, 123, 255, 0.55); }

/* 面板 */
.ad-panel {
  padding: 18px 20px;
}

.ad-add-row {
  display: flex;
  gap: 10px;
  margin-bottom: 8px;
}

.ad-add-input {
  flex: 1;
}

.ad-add-cat {
  width: 120px;
}

.ad-add-tip {
  font-size: 11.5px;
  color: var(--text-3);
  margin-bottom: 16px;
}

.ad-filter-row {
  display: flex;
  gap: 10px;
  margin-bottom: 16px;
}

.ad-filter-row input {
  width: 220px;
}

.ad-filter-row select {
  background: var(--panel-2);
  border: 1px solid var(--border);
  color: var(--text-2);
  border-radius: var(--radius-s);
  padding: 8px 12px;
}

/* 表格 */
.ad-table {
  width: 100%;
  border-collapse: collapse;
  font-size: 13px;
}

.ad-table th {
  text-align: left;
  font-size: 12px;
  color: var(--text-3);
  font-weight: 600;
  padding: 8px 10px;
  border-bottom: 1px solid var(--border);
}

.ad-table td {
  padding: 10px;
  border-bottom: 1px solid var(--border-soft);
  color: var(--text-2);
  vertical-align: middle;
}

.ad-word {
  font-weight: 700;
  color: var(--text-1);
}

.ad-mono {
  font-family: var(--font-mono);
}

.ad-mute {
  color: var(--text-3);
}

.ad-detail {
  max-width: 280px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.ad-persona-name {
  font-weight: 700;
  white-space: nowrap;
}

.ad-empty-cell {
  text-align: center;
  color: var(--text-3);
  padding: 26px 0 !important;
}

.ad-del,
.ad-toggle {
  font-size: 12px;
  background: transparent;
  border: 1px solid var(--border);
  color: var(--text-2);
  border-radius: 999px;
  padding: 3px 12px;
  transition: all 0.15s;
}

.ad-del:hover {
  color: var(--heat);
  border-color: rgba(255, 90, 95, 0.45);
}

.ad-toggle:hover {
  color: var(--gold);
  border-color: var(--gold-line);
}

.tag-block {
  color: var(--heat) !important;
  border-color: rgba(255, 90, 95, 0.45) !important;
}

.tag-ok {
  color: #2ecc71 !important;
  border-color: rgba(46, 204, 113, 0.4) !important;
}

/* 分页 */
.ad-pager {
  display: flex;
  justify-content: center;
  align-items: center;
  gap: 14px;
  margin-top: 16px;
  font-size: 13px;
  color: var(--text-3);
}

.ad-pager button {
  background: var(--panel-2);
  border: 1px solid var(--border);
  color: var(--text-2);
  border-radius: var(--radius-s);
  padding: 5px 14px;
  font-size: 12.5px;
}

.ad-pager button:hover:not(:disabled) {
  border-color: var(--gold-line);
  color: var(--gold);
}

.ad-pager button:disabled {
  opacity: 0.4;
  cursor: not-allowed;
}

@media (max-width: 900px) {
  .ad-stats {
    grid-template-columns: repeat(3, 1fr);
  }

  .ad-cost-grid {
    grid-template-columns: 1fr;
  }

  .ad-detail {
    max-width: 160px;
  }
}
</style>
