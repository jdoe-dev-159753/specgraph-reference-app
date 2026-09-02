import { expect, test } from '@playwright/test'

const seededCustomer = '44444444-4444-4444-4444-444444444444'

type EvidenceReference = {
  kind: 'ACTIVITY' | 'SOURCE_RISK' | 'DETECTOR_SIGNAL' | 'POLICY_RETRIEVAL'
  evidenceIdentity: string
}

type Analysis = {
  analysisId: string
  customerId: string
  operatorId: string
  riskLevel: 'LOW' | 'MEDIUM' | 'HIGH'
  findingsSummary: string
  recommendations: string[]
  evidenceProvenance: Array<{
    sourceIdentity: string
    content: string
    retrievalMetadata: Record<string, string>
  }>
  detectorProvenance: Array<{
    detectorIdentity: string
    signalIdentity: string
    score: number
    provenance: Record<string, string>
  }>
  modelProvenance: {
    backendIdentity: string
    modelIdentity: string
    promptIdentity: string
    evidenceReferences: EvidenceReference[]
    metadata: Record<string, string>
  }
}

type CustomerSnapshot = {
  customerId: string
  activities: Array<{ type: 'CARD' | 'PAYMENT' | 'CRYPTO' }>
  riskEvidence: unknown[]
}

async function signIn(page: import('@playwright/test').Page) {
  await page.getByLabel('Operator ID').fill('operator-alpha')
  await page.getByLabel('Password').fill('alpha-demo-2026')
  await page.getByRole('button', { name: 'Sign in' }).click()
  await expect(page.getByTestId('operator-session')).toContainText('operator-alpha')
}

async function loadCustomer(page: import('@playwright/test').Page) {
  await page.getByLabel('Customer ID').fill(seededCustomer)
  await page.getByRole('button', { name: 'Search' }).click()
  await expect(page.getByTestId('customer-activity')).toBeVisible()
  await expect(page.getByTestId('analysis-workspace')).toBeVisible()
}

test('VFY-AUTH-001 VFY-ANALYSIS-CONTRACT-001 VFY-RAG-001 VFY-HISTORY-001 VFY-DETERMINISM-001 prove the complete authenticated grounded R4 flow', async ({ page, request }, testInfo) => {
  const anonymous = await request.get(`/api/customers/${seededCustomer}`)
  expect(anonymous.status()).toBe(401)

  await page.goto('/')
  await expect(page.getByText('Customer Care · R4')).toBeVisible()
  await expect(page.getByTestId('operator-login')).toBeVisible()
  await signIn(page)
  await loadCustomer(page)

  const beforeResponse = await page.request.get(`/api/customers/${seededCustomer}`)
  expect(beforeResponse.status()).toBe(200)
  const before = await beforeResponse.json() as CustomerSnapshot
  expect(before.customerId).toBe(seededCustomer)
  expect(new Set(before.activities.map(activity => activity.type))).toEqual(new Set(['CARD', 'PAYMENT', 'CRYPTO']))
  expect(before.riskEvidence.length).toBeGreaterThan(0)

  await expect(page.getByTestId('customer-activity')).toContainText('CARD')
  await expect(page.getByTestId('customer-activity')).toContainText('PAYMENT')
  await expect(page.getByTestId('customer-activity')).toContainText('CRYPTO')

  const analysisResponsePromise = page.waitForResponse(response =>
    response.url().endsWith(`/api/customers/${seededCustomer}/analyses`) &&
    response.request().method() === 'POST')
  await page.getByRole('button', { name: 'Run deterministic analysis' }).click()
  const analysisResponse = await analysisResponsePromise
  expect(analysisResponse.status()).toBe(201)
  const completed = await analysisResponse.json() as Analysis

  expect(completed.customerId).toBe(seededCustomer)
  expect(completed.operatorId).toBe('operator-alpha')
  expect(completed.riskLevel).toBe('HIGH')
  expect(completed.findingsSummary.trim()).not.toBe('')
  expect(completed.recommendations.length).toBeGreaterThan(0)

  expect(completed.detectorProvenance).toEqual([])
  expect(completed.evidenceProvenance.length).toBeGreaterThan(0)
  for (const evidence of completed.evidenceProvenance) {
    expect(evidence.sourceIdentity).not.toBe('')
    expect(evidence.content.trim()).not.toBe('')
    expect(evidence.retrievalMetadata.adapter).toBe('pgvector')
    expect(evidence.retrievalMetadata.embeddingModel).toBe('all-MiniLM-L6-v2')
  }

  expect(completed.modelProvenance).toMatchObject({
    backendIdentity: 'deterministic',
    modelIdentity: 'r3-offline-baseline-v1',
    promptIdentity: 'grounded-analysis-v1',
    metadata: { externalTransmission: 'false' },
  })
  const referenceKinds = new Set(completed.modelProvenance.evidenceReferences.map(reference => reference.kind))
  expect(referenceKinds).toContain('ACTIVITY')
  expect(referenceKinds).toContain('SOURCE_RISK')
  expect(referenceKinds).toContain('POLICY_RETRIEVAL')

  await expect(page.getByTestId('analysis-result')).toBeVisible()
  const currentGrounding = page.getByTestId('analysis-result').getByTestId('analysis-grounding-evidence')
  await expect(currentGrounding).toContainText('adapter: pgvector')
  await expect(currentGrounding).toContainText('embeddingModel: all-MiniLM-L6-v2')

  const retained = page.getByTestId(`analysis-history-${completed.analysisId}`)
  await expect(retained).toContainText('operator-alpha')
  await expect(retained).toContainText(completed.riskLevel)
  await expect(retained.getByTestId('analysis-grounding-evidence')).toContainText('adapter: pgvector')

  const afterResponse = await page.request.get(`/api/customers/${seededCustomer}`)
  expect(afterResponse.status()).toBe(200)
  const after = await afterResponse.json() as CustomerSnapshot
  expect(after.riskEvidence).toEqual(before.riskEvidence)

  await page.screenshot({ path: testInfo.outputPath('r4-customer-444-complete-grounded-history.png'), fullPage: true })

  await page.reload()
  await expect(page.getByTestId('operator-session')).toContainText('operator-alpha')
  await loadCustomer(page)

  const historyResponse = await page.request.get(`/api/customers/${seededCustomer}/analyses`)
  expect(historyResponse.status()).toBe(200)
  const history = await historyResponse.json() as Analysis[]
  const reloaded = history.find(entry => entry.analysisId === completed.analysisId)
  expect(reloaded).toBeDefined()
  expect(reloaded?.operatorId).toBe('operator-alpha')
  expect(reloaded?.modelProvenance.backendIdentity).toBe('deterministic')
  expect(reloaded?.evidenceProvenance.some(evidence => evidence.retrievalMetadata.adapter === 'pgvector')).toBe(true)

  await expect(page.getByTestId(`analysis-history-${completed.analysisId}`)).toBeVisible()
})
