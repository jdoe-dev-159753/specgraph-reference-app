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
  evidenceProvenance: Array<{
    sourceIdentity: string
    content: string
    retrievalMetadata: Record<string, string>
  }>
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
  await page.getByRole('button', { name: 'Run analysis' }).click()
  const analysisResponse = await analysisResponsePromise
  expect(analysisResponse.status()).toBe(201)

  const completed = await analysisResponse.json() as Analysis
  expect(completed.customerId).toBe(seededCustomer)
  expect(completed.operatorId).toBe('r3-demo-operator')
  expect(completed.riskLevel).toBe('MEDIUM')
  expect(completed.findingsSummary.length).toBeGreaterThan(0)
  expect(completed.recommendations.length).toBeGreaterThan(0)
  const staticPolicy = completed.evidenceProvenance.find(
    evidence => evidence.sourceIdentity === 'synthetic-policy:r3-review-baseline')
  expect(staticPolicy).toBeDefined()
  expect(staticPolicy?.retrievalMetadata).toMatchObject({
    adapter: 'static',
    corpus: 'synthetic',
    revision: 'r3',
  })

  await expect(page.getByTestId('analysis-result')).toBeVisible()
  await expect(page.getByTestId('analysis-result').getByTestId('analysis-risk-level')).toHaveText('MEDIUM')
  await expect(page.getByTestId('analysis-findings')).toContainText('Deterministic offline baseline')
  const currentGrounding = page.getByTestId('analysis-result').getByTestId('analysis-grounding-evidence')
  await expect(currentGrounding).toContainText('adapter: static')
  await expect(currentGrounding).toContainText('corpus: synthetic')
  await expect(currentGrounding).toContainText('revision: r3')
  await expect(currentGrounding).toContainText('synthetic-policy:r3-review-baseline')
  await expect(currentGrounding).toContainText('Escalation remains a human decision')

  const retainedAnalysis = page.getByTestId(`analysis-history-${completed.analysisId}`)
  await expect(retainedAnalysis).toContainText('r3-demo-operator')
  await expect(retainedAnalysis).toContainText('MEDIUM')
  await expect(retainedAnalysis.getByTestId('analysis-grounding-evidence')).toContainText('adapter: static')
  await expect(retainedAnalysis.getByTestId('analysis-grounding-evidence'))
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
  const reloadedAnalysis = page.getByTestId(`analysis-history-${completed.analysisId}`)
  await expect(reloadedAnalysis).toBeVisible()
  await expect(reloadedAnalysis).toContainText('r3-demo-operator')
  await expect(reloadedAnalysis).toContainText(completed.findingsSummary)
  await expect(reloadedAnalysis.getByTestId('analysis-grounding-evidence')).toContainText('adapter: static')
  await expect(reloadedAnalysis.getByTestId('analysis-grounding-evidence'))
    .toContainText('Escalation remains a human decision')
})
