<script setup>
/**
 * 人设编辑器：新建（/workshop/new）与编辑（/workshop/:id/edit）共用。
 * 六维人设卡：名字 / 头像 / 原型 / 一句话人设 / 性格 / 说话风格 / 背景 / 雷点。
 */
import { ref, reactive, computed, onMounted, inject } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { personaApi } from '../../api/modules'
import EmptyState from '../../components/EmptyState.vue'

const route = useRoute()
const router = useRouter()
const toast = inject('toast', () => {})

const editId = route.params.id ? Number(route.params.id) : null
const isEdit = !!editId

const ARCHETYPES_FALLBACK = ['毒舌', '杠精', '慈祥', '卷王', '社恐', '吃瓜', '显眼包', '暴躁', '阴阳', '财迷']
const archetypes = ref([...ARCHETYPES_FALLBACK])
const EMOJIS = ['🎭', '🤡', '😡', '😎', '🥺', '🤓', '🤑', '😇', '🥸', '😤', '🤠', '👻', '🧐', '😼', '🤖', '👑']

const form = reactive({
  name: '',
  avatar: '🎭',
  archetype: '毒舌',
  tagline: '',
  traits: '',
  speakingStyle: '',
  background: '',
  taboo: ''
})

const loading = ref(true)
const saving = ref(false)

const LIMITS = { name: 20, tagline: 100, traits: 500, speakingStyle: 500, background: 1000, taboo: 500 }

const filled = computed(
  () => [form.name, form.tagline, form.traits, form.speakingStyle, form.background, form.taboo].filter((s) => s && s.trim()).length
)

onMounted(async () => {
  personaApi.archetypes().then((a) => {
    if (a?.length) archetypes.value = a
  }).catch(() => {})
  if (isEdit) {
    try {
      const p = await personaApi.detail(editId)
      Object.assign(form, {
        name: p.name || '',
        avatar: p.avatar || '🎭',
        archetype: p.archetype || '毒舌',
        tagline: p.tagline || '',
        traits: p.traits || '',
        speakingStyle: p.speakingStyle || '',
        background: p.background || '',
        taboo: p.taboo || ''
      })
    } catch (e) {
      toast(e.message, 'err')
      router.replace('/workshop')
      return
    }
  }
  loading.value = false
})

async function save() {
  if (saving.value) return
  if (!form.name.trim()) return toast('演员得有个名字', 'err')
  if (!form.tagline.trim()) return toast('一句话人设是灵魂，不能空', 'err')
  saving.value = true
  try {
    const body = { ...form, name: form.name.trim(), tagline: form.tagline.trim() }
    const saved = isEdit ? await personaApi.update(editId, body) : await personaApi.create(body)
    toast(isEdit ? '档案已更新' : `演员「${saved.name}」签约成功！`)
    router.push(`/workshop/${saved.id}`)
  } catch (e) {
    toast(e.message, 'err')
  } finally {
    saving.value = false
  }
}
</script>

<template>
  <div class="page">
    <div class="page-head">
      <div>
        <div class="page-title">{{ isEdit ? '编辑演员档案' : '造一位新演员' }}</div>
        <div class="page-sub">性格越鲜明，台上越好吵——六维档案决定它的戏路</div>
      </div>
    </div>

    <div v-if="loading" class="pe-loading">调档中……</div>

    <div v-else class="pe-grid">
      <div class="card pe-form">
        <!-- 名字 + 头像 -->
        <div class="pe-row2">
          <label class="field">
            <span>名字 <i class="req">*</i></span>
            <input v-model="form.name" :maxlength="LIMITS.name" placeholder="例：嘴替战神Lisa" />
            <em class="pe-cnt">{{ form.name.length }}/{{ LIMITS.name }}</em>
          </label>
          <div class="field">
            <span>头像</span>
            <div class="pe-emojis">
              <button
                v-for="e in EMOJIS"
                :key="e"
                class="pe-emoji"
                :class="{ on: form.avatar === e }"
                @click="form.avatar = e"
              >
                {{ e }}
              </button>
            </div>
          </div>
        </div>

        <!-- 原型 -->
        <div class="field">
          <span>性格原型 <i class="req">*</i></span>
          <div class="pe-archs">
            <button
              v-for="a in archetypes"
              :key="a"
              class="pe-arch"
              :class="{ on: form.archetype === a }"
              @click="form.archetype = a"
            >
              {{ a }}
            </button>
          </div>
          <em class="pe-hint">原型决定吵架时的基本战术</em>
        </div>

        <label class="field">
          <span>一句话人设 <i class="req">*</i>（群聊列表里最抓眼球的那句）</span>
          <input v-model="form.tagline" :maxlength="LIMITS.tagline" placeholder="例：把天聊死是我的本职工作" />
          <em class="pe-cnt">{{ form.tagline.length }}/{{ LIMITS.tagline }}</em>
        </label>

        <label class="field">
          <span>性格描述</span>
          <textarea v-model="form.traits" :maxlength="LIMITS.traits" rows="3"
            placeholder="例：外冷内热，刀子嘴豆腐心；被戳中软肋会突然破防"></textarea>
          <em class="pe-cnt">{{ form.traits.length }}/{{ LIMITS.traits }}</em>
        </label>

        <label class="field">
          <span>说话风格</span>
          <textarea v-model="form.speakingStyle" :maxlength="LIMITS.speakingStyle" rows="3"
            placeholder="例：短句连发，反问句浓度超标，爱用'就这？'开头"></textarea>
          <em class="pe-cnt">{{ form.speakingStyle.length }}/{{ LIMITS.speakingStyle }}</em>
        </label>

        <label class="field">
          <span>背景故事</span>
          <textarea v-model="form.background" :maxlength="LIMITS.background" rows="4"
            placeholder="例：前辩论队队长，因吵架吵到队友集体退队而封神"></textarea>
          <em class="pe-cnt">{{ form.background.length }}/{{ LIMITS.background }}</em>
        </label>

        <label class="field">
          <span>雷点（碰到就炸的情绪引信）</span>
          <textarea v-model="form.taboo" :maxlength="LIMITS.taboo" rows="2"
            placeholder="例：被说'你懂什么'会瞬间暴怒；提到前任会沉默"></textarea>
          <em class="pe-cnt">{{ form.taboo.length }}/{{ LIMITS.taboo }}</em>
        </label>

        <div class="pe-acts">
          <button class="btn btn-ghost" @click="router.back()">取消</button>
          <button class="btn btn-primary btn-lg" :disabled="saving || !form.name.trim() || !form.tagline.trim()" @click="save">
            {{ saving ? '签约中…' : isEdit ? '保存档案' : '🎭 签约上台' }}
          </button>
        </div>
      </div>

      <!-- 实时预览 -->
      <div class="pe-preview-col">
        <div class="card pe-preview">
          <div class="panel-title">📋 签约卡预览</div>
          <div class="pp-card">
            <div class="pp-head">
              <span class="pp-avatar">{{ form.avatar }}</span>
              <div>
                <div class="pp-name">{{ form.name || '未命名演员' }}</div>
                <div class="pp-arch">{{ form.archetype }}</div>
              </div>
            </div>
            <div class="pp-tagline">{{ form.tagline || '一句话人设会显示在这里' }}</div>
            <div class="pp-dim">档案完成度</div>
            <div class="pp-bar"><i :style="{ width: (filled / 6) * 100 + '%' }"></i></div>
            <div class="pp-dims">
              <span :class="{ ok: form.traits }">性格</span>
              <span :class="{ ok: form.speakingStyle }">话风</span>
              <span :class="{ ok: form.background }">背景</span>
              <span :class="{ ok: form.taboo }">雷点</span>
              <span :class="{ ok: form.name }">名字</span>
              <span :class="{ ok: form.tagline }">人设</span>
            </div>
          </div>
        </div>
        <div class="card pe-tips">
          <div class="panel-title">💡 造人设的秘诀</div>
          <ul class="pe-tip-list">
            <li><b>冲突性</b>：雷点是戏剧引擎——"被说 X 就炸"比"脾气好"好用十倍</li>
            <li><b>辨识度</b>：给一句口头禅，群里三句话就能认出它</li>
            <li><b>克制</b>：一段 50 字的鲜明性格，胜过 500 字的百科全书</li>
            <li><b>组合</b>：把反差原型放进同一剧组（慈祥 × 毒舌）效果最佳</li>
          </ul>
        </div>
      </div>
    </div>
  </div>
</template>

<style scoped>
.pe-loading {
  text-align: center;
  padding: 80px 0;
  color: var(--text-3);
}

.pe-grid {
  display: grid;
  grid-template-columns: 1.6fr 1fr;
  gap: 16px;
  align-items: start;
}

.pe-form {
  padding: 22px;
}

.pe-row2 {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 18px;
}

.field {
  display: block;
  margin-bottom: 16px;
}

.field > span {
  display: block;
  font-size: 13px;
  color: var(--text-2);
  margin-bottom: 7px;
  font-weight: 600;
}

.field input,
.field textarea {
  width: 100%;
}

.req {
  color: var(--heat);
  font-style: normal;
}

.pe-cnt {
  display: block;
  text-align: right;
  font-size: 11px;
  color: var(--text-3);
  font-family: var(--font-mono);
  margin-top: 4px;
}

.pe-hint {
  display: block;
  font-size: 11.5px;
  color: var(--text-3);
  font-style: normal;
  margin-top: 6px;
}

.pe-emojis {
  display: flex;
  flex-wrap: wrap;
  gap: 6px;
}

.pe-emoji {
  font-size: 20px;
  width: 38px;
  height: 38px;
  display: flex;
  align-items: center;
  justify-content: center;
  background: var(--panel-2);
  border: 1px solid var(--border);
  border-radius: 10px;
  transition: all 0.12s;
}

.pe-emoji:hover {
  border-color: var(--gold-line);
}

.pe-emoji.on {
  border-color: var(--gold);
  background: var(--gold-soft);
  box-shadow: 0 0 0 1px var(--gold-line);
}

.pe-archs {
  display: flex;
  flex-wrap: wrap;
  gap: 7px;
}

.pe-arch {
  background: var(--panel-2);
  border: 1px solid var(--border);
  color: var(--text-2);
  border-radius: 999px;
  padding: 6px 16px;
  font-size: 13px;
  font-weight: 600;
  transition: all 0.15s;
}

.pe-arch.on {
  color: var(--gold);
  border-color: var(--gold);
  background: var(--gold-soft);
}

.pe-acts {
  display: flex;
  justify-content: flex-end;
  gap: 10px;
  margin-top: 20px;
  padding-top: 16px;
  border-top: 1px solid var(--border-soft);
}

/* 预览列 */
.pe-preview-col {
  display: flex;
  flex-direction: column;
  gap: 16px;
  position: sticky;
  top: calc(var(--nav-h) + 12px);
}

.pe-preview,
.pe-tips {
  padding: 18px;
}

.pp-card {
  border: 1px solid var(--border-soft);
  border-radius: var(--radius);
  padding: 16px;
  background: var(--bg-soft);
}

.pp-head {
  display: flex;
  gap: 12px;
  align-items: center;
}

.pp-avatar {
  font-size: 40px;
  filter: drop-shadow(0 0 10px rgba(245, 197, 24, 0.3));
}

.pp-name {
  font-size: 17px;
  font-weight: 800;
}

.pp-arch {
  font-size: 12px;
  color: var(--violet);
  margin-top: 3px;
}

.pp-tagline {
  font-size: 13px;
  color: var(--text-2);
  margin-top: 12px;
  min-height: 20px;
  border-left: 2px solid var(--gold-line);
  padding-left: 10px;
}

.pp-dim {
  font-size: 11.5px;
  color: var(--text-3);
  margin: 14px 0 6px;
}

.pp-bar {
  height: 6px;
  border-radius: 999px;
  background: var(--panel-2);
  overflow: hidden;
}

.pp-bar i {
  display: block;
  height: 100%;
  background: linear-gradient(90deg, var(--gold), var(--violet));
  transition: width 0.3s ease;
}

.pp-dims {
  display: flex;
  flex-wrap: wrap;
  gap: 6px;
  margin-top: 10px;
}

.pp-dims span {
  font-size: 11px;
  color: var(--text-3);
  border: 1px dashed var(--border);
  border-radius: 999px;
  padding: 1px 9px;
  transition: all 0.2s;
}

.pp-dims span.ok {
  color: var(--gold);
  border-style: solid;
  border-color: var(--gold-line);
  background: var(--gold-soft);
}

.pe-tip-list {
  list-style: none;
  display: flex;
  flex-direction: column;
  gap: 12px;
  font-size: 13px;
  color: var(--text-2);
  line-height: 1.7;
}

.pe-tip-list b {
  color: var(--gold);
}

@media (max-width: 900px) {
  .pe-grid {
    grid-template-columns: 1fr;
  }

  .pe-preview-col {
    position: static;
  }

  .pe-row2 {
    grid-template-columns: 1fr;
  }
}
</style>
