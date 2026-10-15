import { createRouter, createWebHistory } from 'vue-router'
import { useUserStore } from '../stores/user'

const routes = [
  { path: '/', name: 'landing', component: () => import('../views/LandingView.vue') },
  { path: '/login', name: 'login', component: () => import('../views/auth/LoginView.vue') },
  { path: '/register', name: 'register', component: () => import('../views/auth/RegisterView.vue') },
  { path: '/wizard', name: 'wizard', component: () => import('../views/wizard/WizardView.vue'), meta: { requiresAuth: true } },
  { path: '/theaters/:id', name: 'room', component: () => import('../views/room/ChatRoomView.vue') },
  { path: '/my-theaters', name: 'my-theaters', component: () => import('../views/me/MyTheatersView.vue'), meta: { requiresAuth: true } },
  { path: '/workshop', name: 'workshop', component: () => import('../views/workshop/WorkshopView.vue') },
  { path: '/workshop/new', name: 'persona-new', component: () => import('../views/workshop/PersonaEditView.vue'), meta: { requiresAuth: true } },
  { path: '/workshop/:id/edit', name: 'persona-edit', component: () => import('../views/workshop/PersonaEditView.vue'), meta: { requiresAuth: true } },
  { path: '/workshop/:id', name: 'persona-detail', component: () => import('../views/workshop/PersonaDetailView.vue') },
  { path: '/plaza', name: 'plaza', component: () => import('../views/plaza/PlazaView.vue') },
  { path: '/pk', name: 'pk', component: () => import('../views/pk/PkView.vue') },
  { path: '/plaza/:id/hall', name: 'hall', component: () => import('../views/plaza/HallView.vue') },
  { path: '/me', name: 'me', component: () => import('../views/me/MeView.vue'), meta: { requiresAuth: true } },
  { path: '/admin', name: 'admin', component: () => import('../views/admin/AdminView.vue'), meta: { requiresAuth: true, requiresAdmin: true } },
  { path: '/:pathMatch(.*)*', name: 'not-found', component: () => import('../views/NotFoundView.vue') }
]

const router = createRouter({
  history: createWebHistory(),
  routes,
  scrollBehavior() {
    return { top: 0 }
  }
})

router.beforeEach(async (to) => {
  const store = useUserStore()
  // 刷新后 user 向未加载：守卫先等 fetchMe 完成，避免 requiresAdmin 误拦
  if (store.loggedIn && !store.user) {
    await store.fetchMe().catch(() => store.logout())
  }
  if (to.meta.requiresAuth && !store.loggedIn) {
    return { name: 'login', query: { redirect: to.fullPath } }
  }
  if (to.meta.requiresAdmin && !store.isAdmin) {
    return { name: 'landing' }
  }
  return true
})

export default router
