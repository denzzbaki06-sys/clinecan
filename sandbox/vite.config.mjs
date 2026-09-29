import react from '@vitejs/plugin-react'
export default { root: '/workspace', base: './', plugins: [react()], publicDir: false,
  build: { outDir: '/workspace/dist', emptyOutDir: true, sourcemap: false, assetsInlineLimit: 0 } }
