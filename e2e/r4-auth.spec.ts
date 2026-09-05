/**
 * R4 browser acceptance for authentication, operator attribution, logout cleanup, and guard recovery.
 *
 * @remarks
 * Uses the real deployed security boundary to prove anonymous rejection, invalid
 * credential handling, two distinct operators, retained attribution, and session
 * invalidation. The delayed-response scenario specifically detects a stale local
 * submission guard surviving logout; it does not model concurrent server writes.
 *
 * @module
 */
import { expect, test } from '@playwright/test'

/** Shared customer makes operator attribution, rather than data selection, the varying dimension. */
const seededCustomer = '11111111-1111-1111-1111-111111111111'

/** Minimal success contract required to prove persisted operator attribution. */
export type Analysis = {
  analysisId: string
  customerId: string
  operatorId: string
}

/** Drives the public login form so tests observe the same CSRF/session workflow as a reviewer. */
export async function signIn(page: import('@playwright/test').Page, operatorId: string, password: string) {
  await page.getByLabel('Operator ID').fill(operatorId)
  await page.getByLabel('Password').fill(password)
  await page.getByRole('button', { name: 'Sign in' }).click()
}

/** Enters the protected workspace and waits for server-confirmed customer evidence. */
export async function loadCustomer(page: import('@playwright/test').Page) {
  await page.getByLabel('Customer ID').fill(seededCustomer)
  await page.getByRole('button', { name: 'Search' }).click()
  await expect(page.getByTestId('customer-activity')).toBeVisible()
}

/** Couples the visible analysis action to its HTTP completion before inspecting attribution. */
export async function runAnalysis(page: import('@playwright/test').Page): Promise<Analysis> {
  const responsePromise = page.waitForResponse(response =>
    response.url().endsWith(`/api/customers/${seededCustomer}/analyses`) &&
    response.request().method() === 'POST')
  await page.getByRole('button', { name: 'Run analysis' }).click()
  const response = await responsePromise
  expect(response.status()).toBe(201)
  return await response.json() as Analysis
}

/** Proves fail-closed navigation and distinct persisted attribution across two sequential sessions. */
test('VFY-AUTH-001 protects the browser workflow and retains distinct operator attribution', async ({ page, request }, testInfo) => {
  const anonymousApi = await request.get(`/api/customers/${seededCustomer}`)
  expect(anonymousApi.status()).toBe(401)

  await page.goto('/')
  await expect(page.getByText('Customer Care · R4')).toBeVisible()
  await expect(page.getByTestId('operator-login')).toBeVisible()
  await expect(page.getByLabel('Customer ID')).toHaveCount(0)

  await signIn(page, 'operator-alpha', 'not-the-password')
  await expect(page.getByText('Invalid username or password')).toBeVisible()
  await expect(page.getByTestId('operator-login')).toBeVisible()

  await signIn(page, 'operator-alpha', 'alpha-demo-2026')
  await expect(page.getByTestId('operator-session')).toContainText('operator-alpha')
  await expect(page.getByTestId('operator-login')).toHaveCount(0)

  await loadCustomer(page)
  const alphaAnalysis = await runAnalysis(page)
  expect(alphaAnalysis.operatorId).toBe('operator-alpha')
  await expect(page.getByTestId(`analysis-history-${alphaAnalysis.analysisId}`)).toContainText('operator-alpha')

  await page.getByRole('button', { name: 'Sign out' }).click()
  await expect(page.getByTestId('operator-login')).toBeVisible()
  await expect(page.getByLabel('Customer ID')).toHaveCount(0)

  await signIn(page, 'operator-beta', 'beta-demo-2026')
  await expect(page.getByTestId('operator-session')).toContainText('operator-beta')

  await loadCustomer(page)
  const betaAnalysis = await runAnalysis(page)
  expect(betaAnalysis.operatorId).toBe('operator-beta')

  const alphaHistory = page.getByTestId(`analysis-history-${alphaAnalysis.analysisId}`)
  const betaHistory = page.getByTestId(`analysis-history-${betaAnalysis.analysisId}`)
  await expect(alphaHistory).toContainText('operator-alpha')
  await expect(betaHistory).toContainText('operator-beta')

  await page.screenshot({ path: testInfo.outputPath('r4-multi-operator-history.png'), fullPage: true })

  await page.getByRole('button', { name: 'Sign out' }).click()
  await expect(page.getByTestId('operator-login')).toBeVisible()

  const afterLogout = await page.request.get(`/api/customers/${seededCustomer}`)
  expect(afterLogout.status()).toBe(401)
})

/** Proves client-side de-duplication state cannot strand the next operator after session turnover. */
test('VFY-AUTH-001 releases an in-flight analysis guard across logout and login', async ({ page }) => {
  let analysisRequests = 0
  let releaseFirstAnalysis!: () => void
  const firstAnalysisGate = new Promise<void>(resolve => { releaseFirstAnalysis = () => resolve() })

  await page.route(new RegExp(`/api/customers/${seededCustomer}/analyses$`), async route => {
    if (route.request().method() !== 'POST') {
      await route.continue()
      return
    }

    analysisRequests += 1
    if (analysisRequests !== 1) {
      await route.continue()
      return
    }

    await firstAnalysisGate
    await route.fulfill({
      status: 401,
      contentType: 'application/problem+json',
      body: JSON.stringify({ detail: 'The original operator session ended' }),
    })
  })

  await page.goto('/')
  await signIn(page, 'operator-alpha', 'alpha-demo-2026')
  await expect(page.getByTestId('operator-session')).toContainText('operator-alpha')
  await loadCustomer(page)

  const firstAnalysisRequest = page.waitForRequest(request =>
    request.url().endsWith(`/api/customers/${seededCustomer}/analyses`)
      && request.method() === 'POST')
  await page.getByRole('button', { name: 'Run analysis' }).click()
  await firstAnalysisRequest
  await expect.poll(() => analysisRequests).toBe(1)

  await page.getByRole('button', { name: 'Sign out' }).click()
  await expect(page.getByTestId('operator-login')).toBeVisible()

  await signIn(page, 'operator-beta', 'beta-demo-2026')
  await expect(page.getByTestId('operator-session')).toContainText('operator-beta')
  await loadCustomer(page)
  const recoveredAnalysis = await runAnalysis(page)

  expect(analysisRequests).toBe(2)
  expect(recoveredAnalysis.operatorId).toBe('operator-beta')
  await expect(page.getByTestId(`analysis-history-${recoveredAnalysis.analysisId}`)).toContainText('operator-beta')

  const firstAnalysisResponse = page.waitForResponse(response =>
    response.url().endsWith(`/api/customers/${seededCustomer}/analyses`)
      && response.request().method() === 'POST'
      && response.status() === 401)
  releaseFirstAnalysis()
  expect((await firstAnalysisResponse).status()).toBe(401)
})
