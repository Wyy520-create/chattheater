/**
 * HTTP 封装：统一 Bearer token、ApiResult 解包、401 处理、错误规范化。
 * 前后端同源（dev 走 Vite 代理），无需 BASE 前缀。
 */

const TOKEN_KEY = 'ct_token'

export class ApiError extends Error {
  constructor(code, msg) {
    super(msg)
    this.code = code
  }
}

export function getToken() {
  return localStorage.getItem(TOKEN_KEY) || ''
}

export function setToken(token) {
  if (token) localStorage.setItem(TOKEN_KEY, token)
  else localStorage.removeItem(TOKEN_KEY)
}

export async function http(method, url, body) {
  const token = getToken()
  let res
  try {
    res = await fetch(url, {
      method,
      headers: {
        'Content-Type': 'application/json',
        ...(token ? { Authorization: `Bearer ${token}` } : {})
      },
      body: body === undefined ? undefined : JSON.stringify(body)
    })
  } catch {
    throw new ApiError(-1, '网络异常，请稍后重试')
  }
  let json
  try {
    json = await res.json()
  } catch {
    throw new ApiError(res.status, `服务异常（HTTP ${res.status}）`)
  }
  if (res.status === 401) {
    setToken('')
    // 静默清理登录态；由调用方决定跳转（页面守卫会兜底）
    throw new ApiError(401, json.msg || '未登录或登录已过期')
  }
  if (json.code !== 0) {
    throw new ApiError(json.code, json.msg || '请求失败')
  }
  return json.data
}

export const get = (url) => http('GET', url)
export const post = (url, body) => http('POST', url, body)
export const put = (url, body) => http('PUT', url, body)
export const del = (url) => http('DELETE', url)

/** 组装查询串（过滤空值）。 */
export function qs(params = {}) {
  const s = Object.entries(params)
    .filter(([, v]) => v !== undefined && v !== null && v !== '')
    .map(([k, v]) => `${encodeURIComponent(k)}=${encodeURIComponent(v)}`)
    .join('&')
  return s ? `?${s}` : ''
}
