import { defineConfig } from 'vitest/config';

// One emulator, shared state: run the files one after another
export default defineConfig({
  test: {
    include: ['test/**/*.test.js'],
    fileParallelism: false,
    testTimeout: 20000,
    hookTimeout: 30000,
  },
});
