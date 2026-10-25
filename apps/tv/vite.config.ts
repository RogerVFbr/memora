import { defineConfig } from 'vite';
import react from '@vitejs/plugin-react';

export default defineConfig({ plugins: [react()], base: './', publicDir: '../../packages/presentation/public', resolve: { dedupe: ['react', 'react-dom'] }, build: { target: 'chrome150' } });
