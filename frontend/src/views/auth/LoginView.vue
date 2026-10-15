<script setup>
import { ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { useUserStore } from '../../stores/user'

const store = useUserStore()
const router = useRouter()
const route = useRoute()

const username = ref('')
const password = ref('')
const err = ref('')
const busy = ref(false)

async function submit() {
  if (busy.value) return
  err.value = ''
  if (!username.value.trim() || !password.value) {
    err.value = '请输入用户名和密码'
    return
  }
  busy.value = true
  try {
    await store.login(username.value.trim(), password.value)
    router.push(route.query.redirect || '/wizard')
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
      <div class="auth-mask">🎭</div>
      <h2>导演，好久不见</h2>
      <p class="auth-sub">登录后回到你的剧组</p>

      <form @submit.prevent="submit">
        <label class="field">
          <span>用户名</span>
          <input v-model="username" placeholder="输入用户名" autocomplete="username" />
        </label>
        <label class="field">
          <span>密码</span>
          <input v-model="password" type="password" placeholder="输入密码" autocomplete="current-password" />
        </label>

        <div v-if="err" class="auth-err">{{ err }}</div>

        <button class="btn btn-primary btn-lg auth-btn" :disabled="busy" type="submit">
          {{ busy ? '登录中…' : '登录' }}
        </button>
      </form>

      <p class="auth-swap">
        还没有导演证？
        <router-link to="/register">去注册 →</router-link>
      </p>
    </div>
  </div>
</template>

<style scoped src="../auth/auth.css"></style>
