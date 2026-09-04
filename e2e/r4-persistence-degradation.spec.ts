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

function canonicalVisibleText(value: string) {
  return value.replace(/\s+/g, ' ').trim()
}

test('VFY-FAILURE-PATHS-001 database query failure is recoverable without stale customer evidence', async ({ page }) => {
  await page.goto('/')
  await signIn(page)

  let customerRequests = 0
  page.on('request', request => {
    if (request.url().endsWith(customerPath) && request.method() === 'GET') customerRequests += 1
  })

  const failedResponsePromise = page.waitForResponse(response =>
    response.url().endsWith(customerPath) && response.request().method() === 'GET')
  await page.getByLabel('Customer ID').fill(seededCustomer)
  await page.getByRole('button', { name: 'Search' }).click({ clickCount: 3 })
  const failedResponse = await failedResponsePromise
  expect(failedResponse.status()).toBe(500)
  expect(failedResponse.headers()['content-type']).toContain('application/problem+json')
  const failureBody = await failedResponse.text()
  expect(JSON.parse(failureBody)).toMatchObject({
    status: 500,
    detail: 'Customer data could not be loaded',
    reason: 'CUSTOMER_DATA_FAILURE',
  })
  expect(failureBody).not.toContain('Injected customer database query failure')
  expect(failureBody).not.toContain('java.lang')
  expect(failureBody).not.toContain('at dev.specgraph')
  expect(customerRequests).toBe(1)

  await expect(page.getByRole('alert')).toContainText('Customer request failed (500)')
  await expect(page.getByTestId('customer-activity')).toHaveCount(0)
  await expect(page.getByTestId('risk-evidence')).toHaveCount(0)
  await expect(page.getByTestId('analysis-result')).toHaveCount(0)

  await loadCustomer(page)
  expect(customerRequests).toBe(2)
  await expect(page.getByRole('alert')).toHaveCount(0)
})

test('VFY-FAILURE-PATHS-001 analysis failures never appear completed, grounded or retained and the UI recovers', async ({ page }) => {
  await page.goto('/')
  await signIn(page)
  await loadCustomer(page)

  const sourceRiskEvidence = page.getByTestId('risk-evidence')
  const sourceRiskText = canonicalVisibleText(await sourceRiskEvidence.innerText())
  let analysisRequests = 0
  page.on('request', request => {
    if (request.url().endsWith(analysisPath) && request.method() === 'POST') analysisRequests += 1
  })

  for (const failure of analysisFailures) {
    const historyBeforeFailure = await historyIds(page)
    const requestsBeforeFailure = analysisRequests

    const failedResponsePromise = page.waitForResponse(response =>
      response.url().endsWith(analysisPath) && response.request().method() === 'POST')
    await page.getByRole('button', { name: 'Run analysis' }).click({ clickCount: 3 })
    const failedResponse = await failedResponsePromise
    expect(failedResponse.status()).toBe(failure.status)
    expect(failedResponse.headers()['content-type']).toContain('application/problem+json')
    expect(analysisRequests).toBe(requestsBeforeFailure + 1)

    const visibleError = page.getByRole('alert')
    await expect(visibleError).toContainText(`${failure.detail} [${failure.reason}]`)
    await expect(visibleError).not.toContainText('Exception')
    await expect(visibleError).not.toContainText('at dev.')
    await expect(page.getByTestId('analysis-result')).toHaveCount(0)
    expect(canonicalVisibleText(await sourceRiskEvidence.innerText())).toBe(sourceRiskText)
    expect(await historyIds(page)).toEqual(historyBeforeFailure)

    const recoveredResponsePromise = page.waitForResponse(response =>
      response.url().endsWith(analysisPath) && response.request().method() === 'POST')
    await page.getByRole('button', { name: 'Run analysis' }).click({ clickCount: 3 })
    const recoveredResponse = await recoveredResponsePromise
    expect(recoveredResponse.status()).toBe(201)
    expect(analysisRequests).toBe(requestsBeforeFailure + 2)
    const completed = await recoveredResponse.json() as Analysis

    await expect(page.getByRole('alert')).toHaveCount(0)
    await expect(page.getByTestId('analysis-result')).toBeVisible()
    await expect(page.getByTestId(`analysis-history-${completed.analysisId}`)).toBeVisible()

    await loadCustomer(page)
    await expect(page.getByTestId('analysis-result')).toHaveCount(0)
  }
})
