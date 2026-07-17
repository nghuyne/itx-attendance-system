import { defineConfig } from 'vitest/config';

// Unit/component test level (R-002, R-005 mitigation groundwork) — separate
// from Playwright (tests/e2e, tests/e2e-real), which stays the E2E level.
// jsdom + Testing Library are intentionally not added here: no test needs a
// DOM yet. Add them when the first component test (P1-009, Bước 4) lands.
export default defineConfig({
  test: {
    environment: 'node',
    include: ['tests/unit/**/*.test.ts'],
    coverage: {
      provider: 'v8',
      reporter: ['text', 'html', 'lcov'],
      include: ['src/**/*.{ts,tsx}'],
    },
  },
});
