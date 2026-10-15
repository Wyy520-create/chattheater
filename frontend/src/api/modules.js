/**
 * 全部后端接口的分组封装（与后端 Controller 一一对应）。
 */
import { get, post, put, del, qs } from './http'

export const authApi = {
  register: (body) => post('/api/auth/register', body),
  login: (body) => post('/api/auth/login', body)
}

export const personaApi = {
  archetypes: () => get('/api/personas/archetypes'),
  market: (params) => get(`/api/personas${qs(params)}`),
  detail: (id) => get(`/api/personas/${id}`),
  mine: () => get('/api/personas/mine'),
  create: (body) => post('/api/personas', body),
  update: (id, body) => put(`/api/personas/${id}`, body),
  remove: (id) => del(`/api/personas/${id}`),
  toggleLike: (id) => post(`/api/personas/${id}/like`),
  tryChat: (id, message) => post(`/api/personas/${id}/try-chat`, { message })
}

export const scenarioApi = {
  list: () => get('/api/scenarios'),
  detail: (id) => get(`/api/scenarios/${id}`)
}

export const theaterApi = {
  create: (body) => post('/api/theaters', body),
  mine: () => get('/api/theaters/mine'),
  detail: (id) => get(`/api/theaters/${id}`),
  messages: (id, params) => get(`/api/theaters/${id}/messages${qs(params)}`),
  postMessage: (id, content) => post(`/api/theaters/${id}/messages`, { content }),
  director: (id, body) => post(`/api/theaters/${id}/director`, body),
  end: (id, reason) => post(`/api/theaters/${id}/end`, { reason }),
  toggleLike: (id) => post(`/api/theaters/${id}/like`),
  toggleFavorite: (id) => post(`/api/theaters/${id}/favorite`),
  likeMessage: (messageId) => post(`/api/messages/${messageId}/like`),
  eventCards: () => get('/api/theaters/event-cards')
}

export const plazaApi = {
  live: (params) => get(`/api/plaza/live${qs(params)}`),
  ended: (params) => get(`/api/plaza/ended${qs(params)}`),
  detail: (id) => get(`/api/plaza/${id}`),
  highlights: (id) => get(`/api/plaza/${id}/highlights`),
  stats: () => get('/api/plaza/stats')
}

export const meApi = {
  overview: () => get('/api/me/overview'),
  favorites: () => get('/api/me/favorites')
}

/** 观众投票改剧本。 */
export const pollApi = {
  create: (theaterId, body) => post(`/api/theaters/${theaterId}/polls`, body),
  active: (theaterId) => get(`/api/theaters/${theaterId}/polls/active`),
  vote: (pollId, optionIdx) => post(`/api/polls/${pollId}/vote`, { optionIdx })
}

/** 多剧组 PK 擂台。 */
export const pkApi = {
  create: (body) => post('/api/pk', body),
  list: () => get('/api/pk/list'),
  detail: (id) => get(`/api/pk/${id}`),
  vote: (id, side) => post(`/api/pk/${id}/vote`, { side })
}

export const adminApi = {
  overview: () => get('/api/admin/overview'),
  cost: () => get('/api/admin/cost'),
  words: (params) => get(`/api/admin/review/words${qs(params)}`),
  addWord: (body) => post('/api/admin/review/words', body),
  deleteWord: (id) => del(`/api/admin/review/words/${id}`),
  logs: (params) => get(`/api/admin/review/logs${qs(params)}`),
  personas: (params) => get(`/api/admin/review/personas${qs(params)}`),
  personaStatus: (id, status) => put(`/api/admin/review/personas/${id}/status`, { status })
}

/** SSE 直播流地址（token 走查询参数，EventSource 无法带 Header）。 */
export function streamUrl(theaterId, token) {
  return `/api/theaters/${theaterId}/stream${token ? `?token=${encodeURIComponent(token)}` : ''}`
}
