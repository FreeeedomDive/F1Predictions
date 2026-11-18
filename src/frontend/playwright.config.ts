import { defineConfig, devices } from '@playwright/test'
import { fileURLToPath } from 'node:url'

export default defineConfig({
  testDir: './e2e',
  workers: 1,
  forbidOnly: !!process.env.CI,
  reporter: [['list'], ['html', { open: 'never' }]],
  use: {
    ...devices['Desktop Chrome'],
    baseURL: 'http://localhost:18089',
    trace: 'retain-on-failure',
    screenshot: 'only-on-failure',
  },
  webServer: [
    {
      name: 'backend',
      command: './gradlew :bootstrap:bootRun --args=--spring.profiles.active=e2e',
      cwd: fileURLToPath(new URL('../', import.meta.url)),
      url: 'http://127.0.0.1:18081/actuator/health',
      timeout: 180_000,
      reuseExistingServer: false,
      stdout: 'pipe',
    },
    {
      name: 'frontend',
      command: 'npm run build && npm run preview -- --host 0.0.0.0 --port 15174 --strictPort',
      url: 'http://127.0.0.1:15174',
      timeout: 120_000,
      reuseExistingServer: false,
      env: { VITE_API_BASE_URL: '' },
      stdout: 'pipe',
    },
  ],
})
