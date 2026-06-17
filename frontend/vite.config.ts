import { defineConfig } from 'vite'
import react from '@vitejs/plugin-react'

export default defineConfig({
  plugins: [react()],
  server: {
    port: 5173,
    strictPort: true,
    proxy: {
      '/api': {
        target: 'http://localhost:8080',
        changeOrigin: true,
        configure: (proxy, _options) => {
          proxy.on('proxyReq', (proxyReq, req, _res) => {
            let clientIp = req.socket.remoteAddress;
            if (clientIp === '::1') {
              clientIp = '127.0.0.1';
            }
            if (clientIp) {
              proxyReq.setHeader('X-Real-IP', clientIp);
              proxyReq.setHeader('X-Forwarded-For', clientIp);
            }
          });
        }
      },
    },
  },
})
