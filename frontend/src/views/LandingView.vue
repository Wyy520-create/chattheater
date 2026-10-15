<script setup>
import { ref, onMounted } from 'vue'
import { plazaApi, personaApi, scenarioApi } from '../api/modules'
import { useUserStore } from '../stores/user'

const store = useUserStore()
const stats = ref(null)
const personas = ref([])
const scenarios = ref([])

onMounted(async () => {
  plazaApi.stats().then((d) => (stats.value = d)).catch(() => {})
  personaApi.market({ size: 6, sort: 'hot' }).then((d) => (personas.value = d.records || [])).catch(() => {})
  scenarioApi.list().then((d) => (scenarios.value = d || [])).catch(() => {})
})

const steps = [
  { icon: '🎬', title: '选场景', desc: '宿舍风云、豆腐脑甜咸之争…… 六大官方剧本一键开台' },
  { icon: '🎭', title: '拉剧组', desc: '毒舌 Lisa、杠精老王、中二龙傲天…… 三十位招牌演员，或自造人设' },
  { icon: '🧧', title: '扔事件卡', desc: '红包来袭、班主任查岗——随时搅局，看他们吵翻天' }
]
</script>

<template>
  <div class="landing">
    <!-- Hero -->
    <section class="hero">
      <div class="hero-glow"></div>
      <div class="hero-inner">
        <div class="hero-badge">AI 群聊扮演 × 导演操控 娱乐创作平台</div>
        <h1 class="hero-title">
          拉一群 AI 进群<br />
          <span class="hero-gold">看他们吵翻天</span>
        </h1>
        <p class="hero-sub">
          你是导演。选好场景、拉起剧组、喊下开拍——<br class="br-md" />
          AI 演员们自动飙戏互怼，你随时插话、下指令、扔事件卡搅局。
        </p>
        <div class="hero-cta">
          <router-link to="/wizard" class="btn btn-primary btn-lg">🎬 立即开拍</router-link>
          <router-link to="/plaza" class="btn btn-ghost btn-lg">去广场围观</router-link>
        </div>

        <!-- 实时数字带 -->
        <div v-if="stats" class="hero-stats">
          <div class="hstat"><b>{{ stats.onlineAudience }}</b><span>在线观众</span></div>
          <div class="hstat"><b>{{ stats.liveCount }}</b><span>正在直播</span></div>
          <div class="hstat"><b>{{ stats.totalMessages }}</b><span>已诞生的台词</span></div>
          <div class="hstat"><b>{{ stats.totalPersonas }}</b><span>在册演员</span></div>
        </div>
      </div>
    </section>

    <!-- 三步开演 -->
    <section class="sec">
      <h2 class="sec-title">三步，开演</h2>
      <div class="grid grid-3 steps">
        <div v-for="s in steps" :key="s.title" class="card step">
          <div class="step-icon">{{ s.icon }}</div>
          <div class="step-title">{{ s.title }}</div>
          <div class="step-desc">{{ s.desc }}</div>
        </div>
      </div>
    </section>

    <!-- 官方剧组 -->
    <section class="sec">
      <div class="sec-head">
        <h2 class="sec-title">招牌剧组</h2>
        <router-link to="/workshop" class="sec-more">进工坊造你自己的演员 →</router-link>
      </div>
      <div class="cast-strip">
        <div v-for="p in personas" :key="p.id" class="cast-chip card">
          <span class="cast-avatar">{{ p.avatar }}</span>
          <div>
            <div class="cast-name">{{ p.name }}<i>{{ p.archetype }}</i></div>
            <div class="cast-line">{{ p.tagline }}</div>
          </div>
        </div>
      </div>
    </section>

    <!-- 官方场景 -->
    <section class="sec">
      <div class="sec-head">
        <h2 class="sec-title">热门剧本</h2>
      </div>
      <div class="grid grid-3">
        <div v-for="s in scenarios" :key="s.id" class="card scene-card">
          <div class="scene-name">{{ s.name }}</div>
          <div class="scene-desc">{{ s.description }}</div>
          <div class="scene-cast">建议剧组 {{ s.suggestedCast }} 人</div>
        </div>
      </div>
    </section>

    <!-- CTA 收尾 -->
    <section class="final-cta">
      <div class="final-inner">
        <h2>今晚，让谁的剧场爆满？</h2>
        <p v-if="!store.loggedIn">注册即开拍，Mock 离线演员全程免 Key 可玩</p>
        <p v-else>剧组已就位，就等导演一声开麦</p>
        <router-link to="/wizard" class="btn btn-primary btn-lg">
          {{ store.loggedIn ? '🎬 去开拍' : '免费注册开拍' }}
        </router-link>
      </div>
    </section>

    <footer class="foot">
      群聊剧场 ChatTheater · Spring Boot 3 + Vue 3 + MySQL 8 + Python 自动化 ·
      <span>Real LLM ⇄ Mock 离线演员 双通道，永不开天窗</span>
    </footer>
  </div>
</template>

<style scoped>
/* ---------- Hero ---------- */
.hero {
  position: relative;
  padding: 88px 20px 64px;
  text-align: center;
  overflow: hidden;
}

.hero-glow {
  position: absolute;
  inset: -40% -20% auto;
  height: 130%;
  background:
    radial-gradient(600px 300px at 30% 30%, rgba(245, 197, 24, 0.13), transparent 70%),
    radial-gradient(500px 260px at 70% 40%, rgba(139, 123, 255, 0.12), transparent 70%);
  pointer-events: none;
}

.hero-inner {
  position: relative;
  max-width: 760px;
  margin: 0 auto;
}

.hero-badge {
  display: inline-block;
  font-size: 13px;
  color: var(--gold);
  border: 1px solid var(--gold-line);
  background: var(--gold-soft);
  padding: 5px 16px;
  border-radius: 999px;
  margin-bottom: 26px;
  letter-spacing: 1px;
}

.hero-title {
  font-size: 52px;
  font-weight: 900;
  line-height: 1.18;
  letter-spacing: 1px;
}

.hero-gold {
  background: linear-gradient(120deg, #ffe08a, #f5c518 45%, #ff9f43);
  -webkit-background-clip: text;
  background-clip: text;
  color: transparent;
}

.hero-sub {
  margin: 22px auto 0;
  color: var(--text-2);
  font-size: 16px;
  max-width: 560px;
}

.hero-cta {
  margin-top: 34px;
  display: flex;
  gap: 14px;
  justify-content: center;
}

.hero-stats {
  margin-top: 52px;
  display: flex;
  justify-content: center;
  gap: 40px;
  flex-wrap: wrap;
}

.hstat {
  display: flex;
  flex-direction: column;
  align-items: center;
}

.hstat b {
  font-size: 30px;
  font-weight: 800;
  font-family: var(--font-mono);
  color: var(--gold);
}

.hstat span {
  font-size: 12px;
  color: var(--text-3);
  margin-top: 3px;
}

/* ---------- 通用 Section ---------- */
.sec {
  max-width: var(--max-w);
  margin: 0 auto;
  padding: 36px 20px;
}

.sec-head {
  display: flex;
  justify-content: space-between;
  align-items: baseline;
  gap: 16px;
}

.sec-title {
  font-size: 24px;
  font-weight: 800;
  margin-bottom: 20px;
}

.sec-more {
  font-size: 13px;
  color: var(--gold);
  margin-bottom: 20px;
  white-space: nowrap;
}

/* ---------- 三步 ---------- */
.step {
  text-align: center;
  padding: 30px 22px;
}

.step-icon {
  font-size: 42px;
  margin-bottom: 14px;
}

.step-title {
  font-size: 17px;
  font-weight: 700;
  margin-bottom: 8px;
}

.step-desc {
  font-size: 13.5px;
  color: var(--text-3);
}

/* ---------- 剧组横排 ---------- */
.cast-strip {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(230px, 1fr));
  gap: 14px;
}

.cast-chip {
  display: flex;
  gap: 12px;
  align-items: center;
  padding: 14px 16px;
}

.cast-avatar {
  font-size: 34px;
}

.cast-name {
  font-weight: 700;
  font-size: 15px;
  display: flex;
  align-items: center;
  gap: 8px;
}

.cast-name i {
  font-style: normal;
  font-size: 11px;
  color: var(--gold);
  border: 1px solid var(--gold-line);
  border-radius: 999px;
  padding: 0 8px;
}

.cast-line {
  font-size: 12px;
  color: var(--text-3);
  margin-top: 3px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  max-width: 200px;
}

/* ---------- 场景 ---------- */
.scene-card {
  transition: border-color 0.15s, transform 0.15s;
}

.scene-card:hover {
  border-color: var(--gold-line);
  transform: translateY(-2px);
}

.scene-name {
  font-size: 17px;
  font-weight: 700;
  margin-bottom: 8px;
}

.scene-desc {
  font-size: 13px;
  color: var(--text-3);
  min-height: 58px;
}

.scene-cast {
  margin-top: 10px;
  font-size: 12px;
  color: var(--gold);
}

/* ---------- 收尾 CTA ---------- */
.final-cta {
  margin-top: 30px;
  padding: 70px 20px;
  text-align: center;
  background:
    radial-gradient(500px 240px at 50% 100%, rgba(245, 197, 24, 0.1), transparent 70%),
    var(--bg-soft);
  border-top: 1px solid var(--border-soft);
}

.final-inner h2 {
  font-size: 30px;
  font-weight: 900;
  margin-bottom: 12px;
}

.final-inner p {
  color: var(--text-2);
  margin-bottom: 26px;
}

/* ---------- Footer ---------- */
.foot {
  padding: 26px 20px 34px;
  text-align: center;
  font-size: 12.5px;
  color: var(--text-3);
  border-top: 1px solid var(--border-soft);
}

.foot span {
  color: var(--gold);
  opacity: 0.75;
}

@media (max-width: 640px) {
  .hero-title { font-size: 36px; }
  .br-md { display: none; }
  .hero-stats { gap: 26px; }
}
</style>
