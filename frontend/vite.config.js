import { defineConfig } from 'vite'
import vue from '@vitejs/plugin-vue'

// 开发期 /api 与 /api/theaters/*/stream 全部代理到后端，
// 前端同源访问，SSE 也不受跨域影响。
export default defineConfig({
  plugins: [vue()],
  server: {
    port: 5173,
    proxy: {
      '/api': {
        target: 'http://localhost:8091',
        changeOrigin: true
      }
    }
  }
})
