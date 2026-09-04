import { expect, test } from '@playwright/test'

const seededCustomer = '11111111-1111-1111-1111-111111111111'
const analysisPath = `/api/customers/${seededCustomer}/analyses`

type Analysis = {
  analysisId: string
}

async function signIn(page: import('@playwright/test').Page) {
  await page.getByLabel('Operator ID').fill('operator-alpha')
  await page.getByLabel('Password').fill('alpha-demo-2026')
  await page.getByRole('button', { name: 'Sign in' }).click()
  await expect(page.getByTestId('operator-session')).toContainText('operator-alpha')
}

test('VFY-FAILURE-PATHS-001 persistence failure cannot appear completed or retained and the deployed UI recovers', async ({ page }) => {
  await page.goto('/')
  await signIn(page)

  const initialHistoryPromise = page.waitForResponse(response =>
    response.url().endsWith(analysisPath) && response.request().method() === 'GET')
  await page.getByLabel('Customer ID').fill(seededCustomer)
  await page.getByRole('button', { name: 'Search' }).click()
  const initialHistoryResponse = await initialHistoryPromise
  expect(initialHistoryResponse.status()).toBe(200)
  const initialHistory = await initialHistoryResponse.json() as Analysis[]

  await expect(page.getByTestId('customer-activity')).toBeVisible()
  const sourceRiskEvidence = page.getByTestId('risk-evidence')
  await expect(sourceRiskEvidence).toBeVisible()
  const sourceRiskText = await sourceRiskEvidence.innerText()

  await page.route(`**${analysisPath}`, async route => {
    if (route.request().method() !== 'POST') {
      await route.continue()
      return
    }
    await route.fulfill({
      status: 503,
      contentType: 'application/problem+json',
      body: JSON.stringify({
        title: 'Analysis request failed',
        status: 503,
        detail: 'Completed analysis could not be persisted',
        reason: 'PERSISTENCE_FAILURE',
      }),
    })
  })

  const failedResponsePromise = page.waitForResponse(response =>
    response.url().endsWith(analysisPath) && response.request().method() === 'POST')
  await page.getByRole('button', { name: 'Run analysis' }).click()
  const failedResponse = await failedResponsePromise
  expect(failedResponse.status()).toBe(503)

  await expect(page.getByRole('alert')).toContainText(
    'Completed analysis could not be persisted [PERSISTENCE_FAILURE]')
  await expect(page.getByTestId('analysis-result')).toHaveCount(0)
  await expect(sourceRiskEvidence).toHaveText(sourceRiskText)

  const historyAfterFailureResponse = await page.request.get(analysisPath)
  expect(historyAfterFailureResponse.status()).toBe(200)
  const historyAfterFailure = await historyAfterFailureResponse.json() as Analysis[]
  expect(historyAfterFailure.map(entry => entry.analysisId))
    .toEqual(initialHistory.map(entry => entry.analysisId))

  await page.unroute(`**${analysisPath}`)

  const recoveredResponsePromise = page.waitForResponse(response =>
    response.url().endsWith(analysisPath) && response.request().method() === 'POST')
  await page.getByRole('button', { name: 'Run analysis' }).click()
  const recoveredResponse = await recoveredResponsePromise
  expect(recoveredResponse.status()).toBe(201)
  const completed = await recoveredResponse.json() as Analysis

  await expect(page.getByRole('alert')).toHaveCount(0)
  await expect(page.getByTestId('analysis-result')).toBeVisible()
  await expect(page.getByTestId(`analysis-history-${completed.analysisId}`)).toBeVisible()
})
