import { defineConfig } from 'vite';
import { fileURLToPath } from 'node:url';
import { dirname, resolve } from 'node:path';

const root = dirname(fileURLToPath(import.meta.url));

export default defineConfig({
  configFile: false,
  publicDir: false,
  build: {
    outDir: resolve(root, 'dist'),
    emptyOutDir: false,
    lib: {
      entry: resolve(root, 'src/content/prompt.ts'),
      formats: ['iife'],
      name: 'BringMyCartContent',
      fileName: () => 'content.js'
    },
    rollupOptions: { output: { inlineDynamicImports: true } }
  }
});
