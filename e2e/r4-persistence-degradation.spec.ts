import { expect, Page, test } from '@playwright/test'

const seededCustomer = '11111111-1111-1111-1111-111111111111'
const customerPath = `/api/customers/${seededCustomer}`
const analysisPath = `${customerPath}/analyses`

type Analysis = {
  analysisId: string
}

type AnalysisFailure = {
  status: number
  detail: string
  reason: string
}

const analysisFailures: AnalysisFailure[] = [
  {
    status: 502,
    detail: 'Risk-signal detector execution failed',
    reason: 'DETECTOR_FAILURE',
  },
  {
    status: 422,
    detail: 'No relevant policy evidence was available for the analysis',
    reason: 'INSUFFICIENT_GROUNDING',
  },
  {
    status: 502,
    detail: 'Policy evidence retrieval failed',
    reason: 'GROUNDING_FAILURE',
  },
  {
    status: 502,
    detail: 'Analysis model execution failed',
    reason: 'MODEL_FAILURE',
  },
  {
    status: 502,
    detail: 'Analysis model returned an invalid structured result',
    reason: 'INVALID_RESULT',
  },
  {
    status: 503,
    detail: 'Completed analysis could not be persisted',
    reason: 'PERSISTENCE_FAILURE',
  },
]

async function signIn(page: Page) {
  await page.getByLabel('Operator ID').fill('operator-alpha')
  await page.getByLabel('Password').fill('alpha-demo-2026')
  await page.getByRole('button', { name: 'Sign in' }).click()
  await expect(page.getByTestId('operator-session')).toContainText('operator-alpha')
}

async function loadCustomer(page: Page) {
  const responsePromise = page.waitForResponse(response =>
    response.url().endsWith(customerPath) && response.request().method() === 'GET')
  await page.getByLabel('Customer ID').fill(seededCustomer)
  await page.getByRole('button', { name: 'Search' }).click()
  expect((await responsePromise).status()).toBe(200)
  await expect(page.getByTestId('customer-activity')).toBeVisible()
  await expect(page.getByTestId('risk-evidence')).toBeVisible()
}

async function historyIds(page: Page) {
  const response = await page.request.get(analysisPath)
  expect(response.status()).toBe(200)
  return (await response.json() as Analysis[]).map(entry => entry.analysisId)
}

test('VFY-FAILURE-PATHS-001 database query failure is recoverable without stale customer evidence', async ({ page }) => {
  await page.goto('/')
  await signIn(page)

  let failedQueries = 0
  await page.route(`**${customerPath}`, async route => {
    failedQueries += 1
    await route.fulfill({
      status: 503,
      contentType: 'application/problem+json',
      body: JSON.stringify({
        title: 'Customer request failed',
        status: 503,
        detail: 'Customer evidence is temporarily unavailable',
      }),
    })
  })

  const failedResponsePromise = page.waitForResponse(response =>
    response.url().endsWith(customerPath) && response.request().method() === 'GET')
  await page.getByLabel('Customer ID').fill(seededCustomer)
  await page.getByRole('button', { name: 'Search' }).click({ clickCount: 3 })
  expect((await failedResponsePromise).status()).toBe(503)
  expect(failedQueries).toBe(1)

  await expect(page.getByRole('alert')).toContainText('Customer request failed (503)')
  await expect(page.getByTestId('customer-activity')).toHaveCount(0)
  await expect(page.getByTestId('risk-evidence')).toHaveCount(0)
  await expect(page.getByTestId('analysis-result')).toHaveCount(0)

  await page.unroute(`**${customerPath}`)
  await loadCustomer(page)
  await expect(page.getByRole('alert')).toHaveCount(0)
})

test('VFY-FAILURE-PATHS-001 analysis failures never appear completed, grounded or retained and the UI recovers', async ({ page }) => {
  await page.goto('/')
  await signIn(page)
  await loadCustomer(page)

  const sourceRiskEvidence = page.getByTestId('risk-evidence')
  const sourceRiskText = await sourceRiskEvidence.innerText()

  for (const failure of analysisFailures) {
    const historyBeforeFailure = await historyIds(page)
    let failedRequests = 0

    await page.route(`**${analysisPath}`, async route => {
      if (route.request().method() !== 'POST') {
        await route.continue()
        return
      }
      failedRequests += 1
      await route.fulfill({
        status: failure.status,
        contentType: 'application/problem+json',
        body: JSON.stringify({
          title: 'Analysis request failed',
          status: failure.status,
          detail: failure.detail,
          reason: failure.reason,
        }),
      })
    })

    const failedResponsePromise = page.waitForResponse(response =>
      response.url().endsWith(analysisPath) && response.request().method() === 'POST')
    await page.getByRole('button', { name: 'Run analysis' }).click({ clickCount: 3 })
    expect((await failedResponsePromise).status()).toBe(failure.status)
    expect(failedRequests).toBe(1)

    const visibleError = page.getByRole('alert')
    await expect(visibleError).toContainText(`${failure.detail} [${failure.reason}]`)
    await expect(visibleError).not.toContainText('Exception')
    await expect(visibleError).not.toContainText('at dev.')
    await expect(page.getByTestId('analysis-result')).toHaveCount(0)
    await expect(sourceRiskEvidence).toHaveText(sourceRiskText)
    expect(await historyIds(page)).toEqual(historyBeforeFailure)

    await page.unroute(`**${analysisPath}`)

    const recoveredResponsePromise = page.waitForResponse(response =>
      response.url().endsWith(analysisPath) && response.request().method() === 'POST')
    await page.getByRole('button', { name: 'Run analysis' }).click({ clickCount: 3 })
    const recoveredResponse = await recoveredResponsePromise
    expect(recoveredResponse.status()).toBe(201)
    const completed = await recoveredResponse.json() as Analysis

    await expect(page.getByRole('alert')).toHaveCount(0)
    await expect(page.getByTestId('analysis-result')).toBeVisible()
    await expect(page.getByTestId(`analysis-history-${completed.analysisId}`)).toBeVisible()

    await loadCustomer(page)
    await expect(page.getByTestId('analysis-result')).toHaveCount(0)
  }
})
