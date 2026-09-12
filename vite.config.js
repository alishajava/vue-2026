import { defineConfig } from 'vite'
import vue from '@vitejs/plugin-vue'

// https://vitejs.dev/config/
export default defineConfig({
  base: '/vue-2026/',
  plugins: [vue()],
  server: {
    port: 5173
  }
})
