/**
 * Deterministic browser-evidence configuration shared by delivery workflows.
 *
 * @remarks
 * One worker and zero retries keep request counts and failure evidence attributable
 * to a single run. Trace is always retained; screenshots and video concentrate on
 * failures. `EVIDENCE_NAME` isolates concurrent workflow artifacts without changing
 * scenario semantics.
 *
 * @module
 */
import { defineConfig } from '@playwright/test'

/** Names output folders without changing the test selection or retry policy. */
const evidenceName = process.env.EVIDENCE_NAME || 'playwright'

/** Browser evidence policy consumed directly by Playwright and CI. */
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
