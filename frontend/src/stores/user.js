import { defineStore } from 'pinia'
import { get, post, put, setToken, getToken } from '../api/http'

export const useUserStore = defineStore('user', {
  state: () => ({
    token: getToken(),
    user: null
  }),
  getters: {
    loggedIn: (s) => !!s.token,
    isAdmin: (s) => s.user?.role === 'ADMIN'
  },
  actions: {
    async login(username, password) {
      const data = await post('/api/auth/login', { username, password })
      setToken(data.token)
      this.token = data.token
      this.user = data.user
    },
    async register(username, password, nickname) {
      const data = await post('/api/auth/register', { username, password, nickname })
      setToken(data.token)
      this.token = data.token
      this.user = data.user
    },
    async fetchMe() {
      if (!this.token) return
      this.user = await get('/api/users/me')
    },
    async updateProfile(nickname, avatar) {
      this.user = await put('/api/users/me', { nickname, avatar })
    },
    logout() {
      setToken('')
      this.token = ''
      this.user = null
    }
  }
})
