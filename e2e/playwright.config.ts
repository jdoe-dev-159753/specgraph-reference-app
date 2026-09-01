import { defineConfig } from '@playwright/test'

const evidenceName = process.env.EVIDENCE_NAME || 'playwright'

export default defineConfig({
  testDir: '.',
  testMatch: /.*\.spec\.ts/,
  retries: 0,
  workers: 1,
  outputDir: `test-results/${evidenceName}-artifacts`,
  reporter: [
    ['list'],
    ['junit', { outputFile: `test-results/${evidenceName}.xml` }],
    ['html', { outputFolder: `playwright-report/${evidenceName}`, open: 'never' }],
  ],
  use: {
    baseURL: process.env.BASE_URL || 'http://127.0.0.1:5173',
    trace: 'on',
    screenshot: 'only-on-failure',
    video: 'retain-on-failure',
  },
})
