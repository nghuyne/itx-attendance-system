import { defineConfig } from 'vitest/config';
import react from '@vitejs/plugin-react';

// Unit/component test level (R-002, R-005 mitigation groundwork) — separate
// from Playwright (tests/e2e, tests/e2e-real), which stays the E2E level.
// Plain unit tests (e.g. formatters.test.ts) stay on the fast 'node' environment;
// component tests (P1-009, Bước 4) opt into jsdom via environmentMatchGlobs so
// they can render with @testing-library/react.
export default defineConfig({
  plugins: [react()],
  test: {
    environment: 'node',
    environmentMatchGlobs: [['tests/unit/components/**', 'jsdom']],
    setupFiles: ['./tests/unit/setup.ts'],
    include: ['tests/unit/**/*.test.ts', 'tests/unit/**/*.test.tsx'],
    coverage: {
      provider: 'v8',
      reporter: ['text', 'html', 'lcov'],
      include: ['src/**/*.{ts,tsx}'],
    },
  },
});
