import { defineConfig } from 'vite'
import react from '@vitejs/plugin-react'

export default defineConfig({
  plugins: [react()],
  build: {
    rolldownOptions: {
      output: {
        codeSplitting: {
          groups: [
            {
              name: 'react-vendor',
              test: /node_modules[\\/](react|react-dom|react-router|react-router-dom)[\\/]/,
              priority: 3,
            },
            {
              name: 'antd-icons',
              test: /node_modules[\\/]@ant-design[\\/]icons[\\/]/,
              priority: 3,
            },
            {
              name: 'antd-vendor',
              test: /node_modules[\\/]antd[\\/]/,
              priority: 2,
            },
            {
              name: 'rc-vendor',
              test: /node_modules[\\/](@rc-component|rc-[^\\/]+)[\\/]/,
              priority: 2,
              maxSize: 450 * 1024,
            },
            {
              name: 'query-vendor',
              test: /node_modules[\\/]@tanstack[\\/]react-query[\\/]/,
              priority: 3,
            },
            {
              name: 'vendor',
              test: /node_modules[\\/]/,
              priority: 1,
              maxSize: 350 * 1024,
            },
          ],
        },
      },
    },
  },
  server: {
    proxy: {
      '/api': {
        target: 'http://localhost:8080',
        changeOrigin: true,
      },
    },
  },
})
