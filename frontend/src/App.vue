<script setup>
import { ref, provide } from 'vue'
import { useRouter } from 'vue-router'
import { useUserStore } from './stores/user'
import NavBar from './components/NavBar.vue'

// 轻量全局 toast：inject('toast')('文本', 'ok'|'err')
const toasts = ref([])
let toastId = 0
provide('toast', (text, type = 'ok') => {
  const id = ++toastId
  toasts.value.push({ id, text, type })
  setTimeout(() => {
    toasts.value = toasts.value.filter((t) => t.id !== id)
  }, 2600)
})

const store = useUserStore()
const router = useRouter()
if (store.loggedIn && !store.user) {
  store.fetchMe().catch(() => store.logout())
}

function onLogout() {
  store.logout()
  router.push('/')
}
</script>

<template>
  <NavBar @logout="onLogout" />
  <router-view />
  <div class="toast-layer">
    <div v-for="t in toasts" :key="t.id" class="toast-bar" :class="`toast-${t.type}`">
      {{ t.text }}
    </div>
  </div>
</template>

<style scoped>
.toast-layer {
  pointer-events: none;
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 8px;
}
</style>
