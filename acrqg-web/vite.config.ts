import { defineConfig, loadEnv } from 'vite'
import vue from '@vitejs/plugin-vue'
import { fileURLToPath, URL } from 'node:url'

// https://vitejs.dev/config/
export default defineConfig(({ mode }) => {
  const env = loadEnv(mode, process.cwd(), '')
  const apiTarget = env.VITE_DEV_API_PROXY_TARGET || 'http://localhost:8080'

  return {
    plugins: [vue()],
    resolve: {
      alias: {
        // 路径别名 @ -> src/，与 tsconfig.json 中的 paths 保持一致
        '@': fileURLToPath(new URL('./src', import.meta.url)),
      },
    },
    server: {
      host: '0.0.0.0',
      port: 5173,
      proxy: {
        // 开发环境将 /api 反代到后端 Spring Boot 服务，避免跨域
        '/api': {
          target: apiTarget,
          changeOrigin: true,
          // 后端基础路径已是 /api/v1/...，因此保持路径不变（不 rewrite）
        },
      },
    },
    build: {
      outDir: 'dist',
      sourcemap: mode !== 'production',
      // chunk 拆分：第三方库分块，便于 nginx 缓存命中
      rollupOptions: {
        output: {
          manualChunks: {
            'vendor-vue': ['vue', 'vue-router', 'pinia'],
            'vendor-element': ['element-plus', '@element-plus/icons-vue'],
            'vendor-charts': ['echarts', 'vue-echarts'],
            'vendor-utils': ['axios', 'dayjs'],
          },
        },
      },
    },
    test: {
      globals: true,
      environment: 'jsdom',
      coverage: {
        provider: 'v8',
        reporter: ['text', 'lcov'],
      },
    },
  }
})
