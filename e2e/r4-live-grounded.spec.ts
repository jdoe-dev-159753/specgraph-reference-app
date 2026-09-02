import { expect, test } from '@playwright/test'
import { writeFile } from 'node:fs/promises'

const seededCustomer = '11111111-1111-1111-1111-111111111111'

type EvidenceReference = {
  kind: 'ACTIVITY' | 'SOURCE_RISK' | 'DETECTOR_SIGNAL' | 'POLICY_RETRIEVAL'
  evidenceIdentity: string
}

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
  riskEvidence: unknown[]
}

async function signIn(page: import('@playwright/test').Page) {
  await page.goto('/')
  await page.getByLabel('Operator ID').fill('operator-alpha')
  await page.getByLabel('Password').fill('alpha-demo-2026')
  await page.getByRole('button', { name: 'Sign in' }).click()
  await expect(page.getByTestId('operator-session')).toContainText('operator-alpha')
}

async function loadCustomer(page: import('@playwright/test').Page) {
  await page.getByLabel('Customer ID').fill(seededCustomer)
  await page.getByRole('button', { name: 'Search' }).click()
  await expect(page.getByTestId('customer-activity')).toBeVisible()
}

test('VFY-R4-LIVE-001 composes Bayesian signals, pgvector grounding and OpenAI synthesis', async ({ page }, testInfo) => {
  await signIn(page)
  await loadCustomer(page)

  const beforeResponse = await page.request.get(`/api/customers/${seededCustomer}`)
  expect(beforeResponse.status()).toBe(200)
  const before = await beforeResponse.json() as CustomerSnapshot

  const responsePromise = page.waitForResponse(response =>
    response.url().endsWith(`/api/customers/${seededCustomer}/analyses`) &&
    response.request().method() === 'POST')

  await page.getByRole('button', { name: 'Run deterministic analysis' }).click()
  const response = await responsePromise
  expect(response.status()).toBe(201)
  const analysis = await response.json() as Analysis

  expect(analysis.customerId).toBe(seededCustomer)
  expect(analysis.operatorId).toBe('operator-alpha')
  expect(['LOW', 'MEDIUM', 'HIGH']).toContain(analysis.riskLevel)
  expect(analysis.findingsSummary.trim()).not.toBe('')
  expect(analysis.recommendations.length).toBeGreaterThan(0)

  expect(analysis.detectorProvenance).not.toHaveLength(0)
  const bayesian = analysis.detectorProvenance.find(signal =>
    signal.detectorIdentity === 'beta-binomial-review-elevation-v1')
  expect(bayesian).toBeDefined()
  expect(bayesian?.signalIdentity).toBe('posterior-review-elevation-rate')
  expect(bayesian?.score).toBeGreaterThanOrEqual(0)
  expect(bayesian?.score).toBeLessThanOrEqual(1)
  expect(bayesian?.provenance.library).toBe('apache-commons-math3-3.6.1')

  expect(analysis.evidenceProvenance).not.toHaveLength(0)
  for (const evidence of analysis.evidenceProvenance) {
    expect(evidence.sourceIdentity).not.toBe('')
    expect(evidence.content.trim()).not.toBe('')
    expect(evidence.retrievalMetadata.adapter).toBe('pgvector')
    expect(evidence.retrievalMetadata.embeddingModel).toBe('all-MiniLM-L6-v2')
  }

  expect(analysis.modelProvenance.backendIdentity).toBe('openai')
  expect(analysis.modelProvenance.modelIdentity).not.toBe('')
  expect(analysis.modelProvenance.promptIdentity).toBe('openai-grounded-analysis-v1')
  expect(analysis.modelProvenance.metadata.externalTransmission).toBe('true')
  expect(analysis.modelProvenance.metadata.dataPolicy).toBe('synthetic-demo-only')

  const referenceKinds = new Set(analysis.modelProvenance.evidenceReferences.map(reference => reference.kind))
  expect(referenceKinds).toContain('ACTIVITY')
  expect(referenceKinds).toContain('DETECTOR_SIGNAL')
  expect(referenceKinds).toContain('POLICY_RETRIEVAL')
  expect(referenceKinds).toContain('SOURCE_RISK')

  const afterResponse = await page.request.get(`/api/customers/${seededCustomer}`)
  expect(afterResponse.status()).toBe(200)
  const after = await afterResponse.json() as CustomerSnapshot
  expect(after.riskEvidence).toEqual(before.riskEvidence)

  const historyResponse = await page.request.get(`/api/customers/${seededCustomer}/analyses`)
  expect(historyResponse.status()).toBe(200)
  const history = await historyResponse.json() as Analysis[]
  expect(history.some(entry =>
    entry.analysisId === analysis.analysisId &&
    entry.operatorId === 'operator-alpha' &&
    entry.modelProvenance.backendIdentity === 'openai')).toBe(true)

  const reviewerEvidence = {
    analysisId: analysis.analysisId,
    customerId: analysis.customerId,
    operatorId: analysis.operatorId,
    generatedAt: analysis.generatedAt,
    riskLevel: analysis.riskLevel,
    detectorProvenance: analysis.detectorProvenance,
    retrievalProvenance: analysis.evidenceProvenance.map(evidence => ({
      sourceIdentity: evidence.sourceIdentity,
      retrievalMetadata: evidence.retrievalMetadata,
    })),
    modelProvenance: analysis.modelProvenance,
    sourceRiskUnchanged: true,
  }
  await writeFile(
    testInfo.outputPath('r4-live-grounded-proof.json'),
    `${JSON.stringify(reviewerEvidence, null, 2)}\n`,
    'utf8',
  )
  await page.screenshot({ path: testInfo.outputPath('r4-live-grounded-analysis.png'), fullPage: true })
})
