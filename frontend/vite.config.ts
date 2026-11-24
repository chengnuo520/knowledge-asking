import { fileURLToPath, URL } from 'node:url'
import { defineConfig } from 'vite'
import vue from '@vitejs/plugin-vue'

/**
 * 开发：vite 起在 5173，/api 代理到 Spring Boot 8080
 * 生产：产物直接写进后端 static/app，随 jar 一起发布，base=/app/
 *       （后端 SpaWebConfig 负责 /app/** 的 history 模式兜底）
 */
export default defineConfig({
  plugins: [vue()],
  base: '/app/',
  resolve: {
    alias: { '@': fileURLToPath(new URL('./src', import.meta.url)) }
  },
  server: {
    port: 5173,
    proxy: {
      '/api': { target: 'http://localhost:8080', changeOrigin: true }
    }
  },
  build: {
    outDir: '../src/main/resources/static/app',
    emptyOutDir: true,
    chunkSizeWarningLimit: 1200
  }
})
