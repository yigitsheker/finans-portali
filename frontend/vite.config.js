import { defineConfig } from 'vite'
import react from '@vitejs/plugin-react-swc'

// https://vite.dev/config/
export default defineConfig({
  plugins: [react()],
  server: {
    host: '0.0.0.0',
    port: 5173,
    strictPort: true,
    // Bind-mounts on Windows / macOS don't reliably emit FS events into
    // the container — polling is the safest cross-platform fix for the
    // dev-compose flow. Cheap on this codebase (~700 modules).
    watch: { usePolling: true, interval: 300 },
    // Proxy `/api` to the backend service so the same relative URLs work
    // identically to the nginx prod setup. The hostname resolves via the
    // shared docker network defined in docker-compose.yml.
    proxy: {
      '/api': {
        target: 'http://finans-backend:8080',
        changeOrigin: true,
      },
    },
    // Page is served on host port 80 (mapped from container port 5173);
    // tell Vite's HMR websocket to advertise that public port so the
    // browser's WS handshake doesn't try to reach :5173 directly.
    hmr: {
      clientPort: 80,
    },
  },
})
