import { defineConfig, devices, type Project } from '@playwright/test';

// Real-API tests hit the backend directly (no page.route() mocking) and need
// the Docker stack from docker-compose.yml (mysql/minio/mailhog/backend)
// actually running, which is only guaranteed in CI. Opt in locally with
// E2E_REAL_API=1 once you've run `docker compose up -d`.
const projects: Project[] = [
  {
    name: 'chromium',
    testDir: './tests/e2e',
    use: {
      ...devices['Desktop Chrome'],
      // react-webcam needs a MediaStream; these flags give Chromium a
      // synthetic camera feed instead of prompting for permission.
      permissions: ['camera'],
      launchOptions: {
        slowMo: 500,
        args: ['--use-fake-device-for-media-stream', '--use-fake-ui-for-media-stream'],
      },
    },
  },
];

if (process.env.CI || process.env.E2E_REAL_API) {
  projects.push({
    name: 'real-api',
    testDir: './tests/e2e-real',
    // Fresh-DB logins can burn through LoginRateLimitFilter's 5/min/IP budget
    // and need to wait out a refill (see support/api.ts) — give tests room.
    timeout: 180_000,
    use: {
      baseURL: process.env.API_BASE_URL || 'http://localhost:8080',
    },
  });
}

export default defineConfig({
  fullyParallel: true,
  forbidOnly: !!process.env.CI,
  retries: process.env.CI ? 2 : 0,
  workers: process.env.CI ? 1 : undefined,
  reporter: 'html',
  use: {
    baseURL: 'http://localhost:5173',
    trace: 'on-first-retry',
    screenshot: 'on',
    video: 'on',
  },
  projects,
  webServer: {
    command: 'npm run dev',
    url: 'http://localhost:5173',
    reuseExistingServer: !process.env.CI,
    timeout: 120 * 1000,
  },
});
