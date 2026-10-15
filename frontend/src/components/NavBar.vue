<script setup>
import { computed } from 'vue'
import { useRoute } from 'vue-router'
import { useUserStore } from '../stores/user'

defineEmits(['logout'])

const store = useUserStore()
const route = useRoute()

const links = computed(() => {
  const base = [
    { to: '/wizard', label: '开拍' },
    { to: '/plaza', label: '广场' },
    { to: '/pk', label: 'PK 擂台' },
    { to: '/workshop', label: '人设工坊' }
  ]
  if (store.loggedIn) {
    base.push({ to: '/my-theaters', label: '我的剧场' })
    base.push({ to: '/me', label: '个人中心' })
  }
  if (store.isAdmin) {
    base.push({ to: '/admin', label: '运营' })
  }
  return base
})

function active(to) {
  return route.path === to || route.path.startsWith(to + '/')
}
</script>

<template>
  <header class="nav">
    <div class="nav-inner">
      <router-link to="/" class="brand">
        <span class="brand-mask">🎭</span>
        <span class="brand-name">群聊剧场<em>ChatTheater</em></span>
      </router-link>

      <nav class="links">
        <router-link
          v-for="l in links"
          :key="l.to"
          :to="l.to"
          class="link"
          :class="{ on: active(l.to) }"
        >
          {{ l.label }}
        </router-link>
      </nav>

      <div class="nav-right">
        <router-link v-if="!store.loggedIn" to="/login" class="btn btn-ghost btn-sm">登录</router-link>
        <router-link v-if="!store.loggedIn" to="/register" class="btn btn-primary btn-sm">注册</router-link>
        <template v-else>
          <router-link to="/me" class="user-chip" :title="store.user?.username">
            <span class="user-avatar">{{ store.user?.avatar || '🎬' }}</span>
            <span class="user-nick">{{ store.user?.nickname || store.user?.username }}</span>
          </router-link>
          <button class="btn btn-ghost btn-sm" @click="$emit('logout')">退出</button>
        </template>
      </div>
    </div>
  </header>
</template>

<style scoped>
.nav {
  height: var(--nav-h);
  background: rgba(14, 16, 22, 0.82);
  backdrop-filter: blur(14px);
  border-bottom: 1px solid var(--border-soft);
  position: sticky;
  top: 0;
  z-index: 100;
}

.nav-inner {
  max-width: var(--max-w);
  margin: 0 auto;
  height: 100%;
  padding: 0 20px;
  display: flex;
  align-items: center;
  gap: 28px;
}

.brand {
  display: flex;
  align-items: center;
  gap: 9px;
  font-weight: 800;
  font-size: 17px;
}

.brand-mask {
  font-size: 24px;
  filter: drop-shadow(0 0 12px rgba(245, 197, 24, 0.5));
}

.brand-name {
  display: flex;
  flex-direction: column;
  line-height: 1.15;
}

.brand-name em {
  font-style: normal;
  font-size: 10px;
  font-weight: 600;
  letter-spacing: 2.5px;
  color: var(--gold);
  opacity: 0.8;
}

.links {
  display: flex;
  gap: 4px;
  flex: 1;
  overflow-x: auto;
}

.link {
  padding: 7px 13px;
  border-radius: var(--radius-s);
  color: var(--text-2);
  font-size: 14px;
  font-weight: 500;
  white-space: nowrap;
  transition: color 0.15s, background 0.15s;
}

.link:hover {
  color: var(--text);
  background: var(--panel);
}

.link.on {
  color: var(--gold);
  background: var(--gold-soft);
}

.nav-right {
  display: flex;
  align-items: center;
  gap: 10px;
}

.user-chip {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 4px 12px 4px 5px;
  border-radius: 999px;
  border: 1px solid var(--border);
  background: var(--panel);
  max-width: 160px;
}

.user-avatar {
  font-size: 20px;
}

.user-nick {
  font-size: 13px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

@media (max-width: 760px) {
  .brand-name em { display: none; }
  .links { gap: 0; }
  .user-nick { display: none; }
}
</style>
