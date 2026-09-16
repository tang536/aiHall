import { defineConfig } from 'vite'
import vue from '@vitejs/plugin-vue'
import AutoImport from 'unplugin-auto-import/vite'
import Components from 'unplugin-vue-components/vite'
import { ElementPlusResolver } from 'unplugin-vue-components/resolvers'
import { fileURLToPath, URL } from 'node:url'

export default defineConfig({
  plugins: [
    vue(),
    AutoImport({
      resolvers: [ElementPlusResolver()],
    }),
    Components({
      resolvers: [ElementPlusResolver()],
    }),
  ],
  resolve: {
    alias: {
      '@': fileURLToPath(new URL('./src', import.meta.url))
    }
  },
  server: {
    host: '0.0.0.0',
    port: 5173,
    proxy: {
      '/api': {
        target: 'http://localhost:8080',
        changeOrigin: true,
        configure: (proxy) => {
          // SSE 流式响应不缓冲：确保 AI 流式回复逐字推送
          proxy.on('proxyRes', (proxyRes) => {
            const ct = proxyRes.headers['content-type']
            if (ct && ct.includes('text/event-stream')) {
              proxyRes.headers['cache-control'] = 'no-cache, no-transform'
              proxyRes.headers['connection'] = 'keep-alive'
              proxyRes.headers['x-accel-buffering'] = 'no'
            }
          })
        }
      },
      '/uploads': {
        target: 'http://localhost:8080',
        changeOrigin: true
      },
      // 私聊 WebSocket（原生 ws 协议代理，用于实时收发消息）
      '/ws': {
        target: 'ws://localhost:8080',
        changeOrigin: true,
        ws: true
      }
    }
  },
  build: {
    // 拆分大依赖，消除「chunk > 500kB」告警；echarts 体积最大，单独成块便于浏览器缓存
    chunkSizeWarningLimit: 1200,
    rollupOptions: {
      output: {
        manualChunks: {
          echarts: ['echarts'],
          'element-plus': ['element-plus', '@element-plus/icons-vue'],
          'vue-vendor': ['vue', 'vue-router', 'pinia'],
          axios: ['axios'],
        },
      },
    },
  },
})
