import { expect, test } from '@playwright/test'

const seededCustomer = '11111111-1111-1111-1111-111111111111'

type Analysis = {
  analysisId: string
  customerId: string
  operatorId: string
}

async function signIn(page: import('@playwright/test').Page, operatorId: string, password: string) {
  await page.getByLabel('Operator ID').fill(operatorId)
  await page.getByLabel('Password').fill(password)
  await page.getByRole('button', { name: 'Sign in' }).click()
}

async function loadCustomer(page: import('@playwright/test').Page) {
  await page.getByLabel('Customer ID').fill(seededCustomer)
  await page.getByRole('button', { name: 'Search' }).click()
  await expect(page.getByTestId('customer-activity')).toBeVisible()
}

async function runAnalysis(page: import('@playwright/test').Page): Promise<Analysis> {
  const responsePromise = page.waitForResponse(response =>
    response.url().endsWith(`/api/customers/${seededCustomer}/analyses`) &&
    response.request().method() === 'POST')
  await page.getByRole('button', { name: 'Run deterministic analysis' }).click()
  const response = await responsePromise
  expect(response.status()).toBe(201)
  return await response.json() as Analysis
}

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
