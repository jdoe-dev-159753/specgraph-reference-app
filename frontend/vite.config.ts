/**
 * Development/build boundary for the browser adapter.
 *
 * @remarks
 * Development proxies `/api` to the selected local backend so browser code keeps
 * the same-origin paths used by the packaged Spring Boot runtime. The backend
 * target is an operator-controlled build setting, not a durable application
 * contract. Production output remains static assets consumed by the Java image.
 *
 * @module
 */
import { defineConfig, loadEnv } from 'vite'
import react from '@vitejs/plugin-react'

/** Creates the Vite configuration while limiting the development-only proxy to the `/api` boundary. */
export const createViteConfiguration = defineConfig(({ mode }) => {
  const env = loadEnv(mode, '.', '')
  return {
    plugins: [react()],
    server: {
      host: '0.0.0.0',
      port: 5173,
      proxy: {
        '/api': {
          target: env.VITE_API_TARGET || 'http://localhost:8080',
          changeOrigin: true,
        },
      },
    },
  }
})

export default createViteConfiguration
