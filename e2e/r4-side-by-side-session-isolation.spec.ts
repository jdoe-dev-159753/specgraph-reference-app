/**
 * Browser acceptance for cookie isolation between simultaneous R4 reviewer variants.
 *
 * @remarks
 * One browser context visits two origins and proves each Compose variant retains
 * its own operator session cookie. This establishes demo isolation at the browser
 * boundary; it does not by itself prove database/network isolation between stacks.
 *
 * @module
 */
import { expect, test } from '@playwright/test'

/** Baseline origin has its own configured session-cookie namespace. */
const baselineUrl = process.env.R4_BASELINE_URL ?? 'http://127.0.0.1:8084'
/** Bayesian origin must retain a different operator beside the baseline session. */
const bayesianUrl = process.env.R4_BAYESIAN_URL ?? 'http://127.0.0.1:8085'

/** Signs into a named origin so the scenario can compare origin-scoped session cookies. */
export async function signIn(
  page: import('@playwright/test').Page,
  baseUrl: string,
  operatorId: string,
  password: string,
) {
  await page.goto(baseUrl)
  await page.getByLabel('Operator ID').fill(operatorId)
  await page.getByLabel('Password').fill(password)
  await page.getByRole('button', { name: 'Sign in' }).click()
  await expect(page.getByTestId('operator-session')).toContainText(operatorId)
}

/** Proves baseline and Bayesian origins preserve distinct identities during side-by-side review. */
test('VFY-AUTH-001 prove R4 reviewer variants keep independent sessions in one browser context', async ({ browser }) => {
  const context = await browser.newContext()
  const page = await context.newPage()

  await signIn(page, baselineUrl, 'operator-alpha', 'alpha-demo-2026')
  let cookies = await context.cookies(baselineUrl)
  expect(cookies.some(cookie => cookie.name === 'SPECGRAPH_R4_BASELINE_SESSION')).toBe(true)
  expect(cookies.some(cookie => cookie.name === 'SPECGRAPH_R4_BAYESIAN_SESSION')).toBe(false)

  await signIn(page, bayesianUrl, 'operator-beta', 'beta-demo-2026')
  cookies = await context.cookies(bayesianUrl)
  expect(cookies.some(cookie => cookie.name === 'SPECGRAPH_R4_BASELINE_SESSION')).toBe(true)
  expect(cookies.some(cookie => cookie.name === 'SPECGRAPH_R4_BAYESIAN_SESSION')).toBe(true)

  await page.goto(baselineUrl)
  await expect(page.getByTestId('operator-session')).toContainText('operator-alpha')

  await page.goto(bayesianUrl)
  await expect(page.getByTestId('operator-session')).toContainText('operator-beta')

  await context.close()
})
