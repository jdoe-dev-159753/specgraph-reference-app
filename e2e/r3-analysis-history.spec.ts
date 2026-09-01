import { expect, test } from '@playwright/test'

const seededCustomer = '11111111-1111-1111-1111-111111111111'

type Analysis = {
  analysisId: string
  customerId: string
  operatorId: string
  generatedAt: string
  riskLevel: 'LOW' | 'MEDIUM' | 'HIGH'
  findingsSummary: string
  recommendations: string[]
  evidenceProvenance: Array<{ sourceIdentity: string }>
}

test('VFY-ANALYSIS-001 R3 deterministic analysis is retained and reviewable after reload', async ({ page }, testInfo) => {
  await page.goto('/')
  await expect(page.getByText('Customer Care · R3')).toBeVisible()

  const customerId = page.getByLabel('Customer ID')
  await customerId.fill(seededCustomer)
  await page.getByRole('button', { name: 'Search' }).click()
  await expect(page.getByTestId('customer-activity')).toBeVisible()
  await expect(page.getByTestId('analysis-workspace')).toBeVisible()

  const analysisResponsePromise = page.waitForResponse(response =>
    response.url().endsWith(`/api/customers/${seededCustomer}/analyses`) &&
    response.request().method() === 'POST')
  await page.getByRole('button', { name: 'Run deterministic analysis' }).click()
  const analysisResponse = await analysisResponsePromise
  expect(analysisResponse.status()).toBe(201)

  const completed = await analysisResponse.json() as Analysis
  expect(completed.customerId).toBe(seededCustomer)
  expect(completed.operatorId).toBe('r3-demo-operator')
  expect(completed.riskLevel).toBe('MEDIUM')
  expect(completed.findingsSummary.length).toBeGreaterThan(0)
  expect(completed.recommendations.length).toBeGreaterThan(0)
  expect(completed.evidenceProvenance.map(evidence => evidence.sourceIdentity))
    .toContain('synthetic-policy:r3-review-baseline')

  await expect(page.getByTestId('analysis-result')).toBeVisible()
  await expect(page.getByTestId('analysis-result').getByTestId('analysis-risk-level')).toHaveText('MEDIUM')
  await expect(page.getByTestId('analysis-findings')).toContainText('Deterministic offline baseline')
  await expect(page.getByTestId(`analysis-history-${completed.analysisId}`)).toContainText('r3-demo-operator')
  await expect(page.getByTestId(`analysis-history-${completed.analysisId}`)).toContainText('MEDIUM')
  await expect(page.getByTestId(`analysis-history-${completed.analysisId}`))
    .toContainText('synthetic-policy:r3-review-baseline')

  await page.screenshot({ path: testInfo.outputPath('r3-analysis-history.png'), fullPage: true })

  await page.reload()
  await page.getByLabel('Customer ID').fill(seededCustomer)
  const historyResponsePromise = page.waitForResponse(response =>
    response.url().endsWith(`/api/customers/${seededCustomer}/analyses`) &&
    response.request().method() === 'GET')
  await page.getByRole('button', { name: 'Search' }).click()
  const historyResponse = await historyResponsePromise
  expect(historyResponse.status()).toBe(200)

  const history = await historyResponse.json() as Analysis[]
  expect(history.map(entry => entry.analysisId)).toContain(completed.analysisId)
  await expect(page.getByTestId(`analysis-history-${completed.analysisId}`)).toBeVisible()
  await expect(page.getByTestId(`analysis-history-${completed.analysisId}`)).toContainText('r3-demo-operator')
  await expect(page.getByTestId(`analysis-history-${completed.analysisId}`)).toContainText(completed.findingsSummary)
})
