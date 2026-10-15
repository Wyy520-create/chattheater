<script setup>
import { ref } from 'vue'
import { useRouter } from 'vue-router'
import { useUserStore } from '../../stores/user'

const store = useUserStore()
const router = useRouter()

const username = ref('')
const password = ref('')
const nickname = ref('')
const err = ref('')
const busy = ref(false)

async function submit() {
  if (busy.value) return
  err.value = ''
  if (!username.value.trim() || username.value.trim().length < 3) {
    err.value = '用户名至少 3 个字符'
    return
  }
  if (password.value.length < 6) {
    err.value = '密码至少 6 位'
    return
  }
  busy.value = true
  try {
    await store.register(username.value.trim(), password.value, nickname.value.trim() || username.value.trim())
    router.push('/wizard')
  } catch (e) {
    err.value = e.message
  } finally {
    busy.value = false
  }
}
</script>

<template>
  <div class="auth-page">
    <div class="auth-card card">
      <div class="auth-mask">🎬</div>
      <h2>领取你的导演证</h2>
      <p class="auth-sub">注册即开拍，免 API Key 也能玩</p>

      <form @submit.prevent="submit">
        <label class="field">
          <span>用户名</span>
          <input v-model="username" placeholder="3 个字符以上" autocomplete="username" />
        </label>
        <label class="field">
          <span>昵称（导演艺名）</span>
          <input v-model="nickname" placeholder="不填则用用户名" />
        </label>
        <label class="field">
          <span>密码</span>
          <input v-model="password" type="password" placeholder="至少 6 位" autocomplete="new-password" />
        </label>

        <div v-if="err" class="auth-err">{{ err }}</div>

        <button class="btn btn-primary btn-lg auth-btn" :disabled="busy" type="submit">
          {{ busy ? '注册中…' : '注册并开拍' }}
        </button>
      </form>

      <p class="auth-swap">
        已有导演证？
        <router-link to="/login">去登录 →</router-link>
      </p>
    </div>
  </div>
</template>

<style scoped src="../auth/auth.css"></style>
