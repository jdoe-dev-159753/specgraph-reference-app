import { expect, test, type Locator } from '@playwright/test'
import { writeFile } from 'node:fs/promises'

const seededCustomer = '44444444-4444-4444-4444-444444444444'
const expectedDetectorSelection = process.env.EXPECT_DETECTORS ?? process.env.EXPECT_DETECTOR ?? 'none'
const expectedDetectors = expectedDetectorSelection === 'none'
  ? []
  : expectedDetectorSelection.split(',').map(value => value.trim()).filter(Boolean)
const expectedModelBackend = process.env.EXPECT_MODEL_BACKEND ?? 'deterministic'
const expectedModelIdentity = process.env.EXPECT_MODEL_IDENTITY ?? 'r3-offline-baseline-v1'
const expectedPromptIdentity = process.env.EXPECT_PROMPT_IDENTITY ?? 'grounded-analysis-v1'
const expectedExternalTransmission = process.env.EXPECT_EXTERNAL_TRANSMISSION ?? 'false'
const evidenceName = process.env.EVIDENCE_NAME ?? 'r4-complete-flow'

const detectorContracts: Record<string, { signalIdentity: string, library?: string }> = {
  'beta-binomial-review-elevation-v1': {
    signalIdentity: 'posterior-review-elevation-rate',
    library: 'apache-commons-math3-3.6.1',
  },
  'graded-review-fuzzy-v1': { signalIdentity: 'fuzzy-review-elevation' },
  'random-forest-review-v1': {
    signalIdentity: 'random-forest-review-elevation-vote',
    library: 'tribuo-4.3.2',
  },
}

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
  const customerResponsePromise = page.waitForResponse(response =>
    response.url().endsWith(`/api/customers/${seededCustomer}`) &&
    response.request().method() === 'GET')
  await page.getByRole('button', { name: 'Search' }).click()
  const customerResponse = await customerResponsePromise
  expect(customerResponse.status()).toBe(200)
  await expect(page.getByTestId('customer-activity')).toBeVisible()
  await expect(page.getByTestId('analysis-workspace')).toBeVisible()
}

async function annotateReviewerEvidence(page: import('@playwright/test').Page, completed: Analysis) {
  const detectorLabel = expectedDetectors.length === 0
    ? 'Stage 1: no-op baseline'
    : `Stage 1: ${expectedDetectors.join(' + ')}`
  const modelLabel = `Stage 3: ${completed.modelProvenance.backendIdentity} / ${completed.modelProvenance.modelIdentity}`

  await page.evaluate(({ detectorLabel, modelLabel }) => {
    document.querySelector('[data-testid="reviewer-evidence-provenance"]')?.remove()
    const banner = document.createElement('aside')
    banner.dataset.testid = 'reviewer-evidence-provenance'
    banner.setAttribute('aria-label', 'Executable reviewer evidence provenance')
    banner.style.boxSizing = 'border-box'
    banner.style.width = '100%'
    banner.style.padding = '12px 20px'
    banner.style.background = '#111827'
    banner.style.color = '#f9fafb'
    banner.style.fontFamily = 'ui-monospace, SFMono-Regular, Menlo, Consolas, monospace'
    banner.style.fontSize = '13px'
    banner.style.lineHeight = '1.5'
    banner.style.borderBottom = '3px solid #38bdf8'
    banner.textContent = `Executable evidence overlay · ${detectorLabel} · Stage 2: pgvector + all-MiniLM-L6-v2 · ${modelLabel}`
    document.body.prepend(banner)
  }, { detectorLabel, modelLabel })

  const banner = page.getByTestId('reviewer-evidence-provenance')
  await expect(banner).toContainText(detectorLabel)
  await expect(banner).toContainText('Stage 2: pgvector + all-MiniLM-L6-v2')
  await expect(banner).toContainText(modelLabel)
}

async function expectRenderedProvenance(container: Locator, completed: Analysis) {
  const provenance = container.getByTestId('analysis-provenance')
  await expect(provenance).toContainText('Stage 1 · Detector artifacts')
  await expect(provenance).toContainText('Stage 2 · Policy grounding')
  await expect(provenance).toContainText('Stage 3 · Model execution')

  const detector = provenance.getByTestId('analysis-detector-provenance')
  if (completed.detectorProvenance.length === 0) {
    await expect(detector).toContainText('No detector artifact was retained')
  } else {
    for (const [index, artifact] of completed.detectorProvenance.entries()) {
      const renderedArtifact = detector.getByTestId(`analysis-detector-artifact-${index}`)
      await expect(renderedArtifact).toContainText(`detector: ${artifact.detectorIdentity}`)
      await expect(renderedArtifact).toContainText(`signal: ${artifact.signalIdentity}`)
      await expect(renderedArtifact).toContainText(`score: ${artifact.score}`)
    }
  }

  const grounding = provenance.getByTestId('analysis-grounding-provenance')
  await expect(grounding.getByTestId('analysis-grounding-evidence')).toBeVisible()

  const model = provenance.getByTestId('analysis-model-provenance')
  await expect(model.getByTestId('analysis-model-backend')).toHaveText(`backend: ${completed.modelProvenance.backendIdentity}`)
  await expect(model.getByTestId('analysis-model-identity')).toHaveText(`model: ${completed.modelProvenance.modelIdentity}`)
  await expect(model.getByTestId('analysis-prompt-identity')).toHaveText(`prompt: ${completed.modelProvenance.promptIdentity}`)
  await expect(model.getByTestId('analysis-external-transmission')).toHaveText(
    `external transmission: ${completed.modelProvenance.metadata.externalTransmission === 'true'
      ? 'yes'
      : completed.modelProvenance.metadata.externalTransmission === 'false' ? 'no' : 'unknown'}`,
  )
}

test('VFY-AUTH-001 VFY-ANALYSIS-CONTRACT-001 VFY-RAG-001 VFY-HISTORY-001 VFY-DETERMINISM-001 prove the configured authenticated grounded R4 flow and retain reviewer evidence', async ({ page, request }, testInfo) => {
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
  await page.getByRole('button', { name: 'Run analysis' }).click()
  const analysisResponse = await analysisResponsePromise
  expect(analysisResponse.status()).toBe(201)
  const completed = await analysisResponse.json() as Analysis

  expect(completed.customerId).toBe(seededCustomer)
  expect(completed.operatorId).toBe('operator-alpha')
  expect(completed.riskLevel).toBe('HIGH')
  expect(completed.findingsSummary.trim()).not.toBe('')
  expect(completed.recommendations.length).toBeGreaterThan(0)

  if (expectedDetectors.length === 0) {
    expect(completed.detectorProvenance).toEqual([])
  } else {
    expect(completed.detectorProvenance.map(item => item.detectorIdentity)).toEqual(expectedDetectors)
    for (const detector of completed.detectorProvenance) {
      const contract = detectorContracts[detector.detectorIdentity]
      if (contract === undefined) {
        throw new Error(`unknown detector contract ${detector.detectorIdentity}`)
      }
      expect(detector.signalIdentity).toBe(contract.signalIdentity)
      expect(detector.score).toBeGreaterThanOrEqual(0)
      expect(detector.score).toBeLessThanOrEqual(1)
      if (contract.library !== undefined) {
        expect(detector.provenance.library).toBe(contract.library)
      }
    }
  }

  expect(completed.evidenceProvenance.length).toBeGreaterThan(0)
  for (const evidence of completed.evidenceProvenance) {
    expect(evidence.sourceIdentity).not.toBe('')
    expect(evidence.content.trim()).not.toBe('')
    expect(evidence.retrievalMetadata.adapter).toBe('pgvector')
    expect(evidence.retrievalMetadata.embeddingModel).toBe('all-MiniLM-L6-v2')
  }

  expect(completed.modelProvenance).toMatchObject({
    backendIdentity: expectedModelBackend,
    modelIdentity: expectedModelIdentity,
    promptIdentity: expectedPromptIdentity,
    metadata: { externalTransmission: expectedExternalTransmission },
  })
  const referenceKinds = new Set(completed.modelProvenance.evidenceReferences.map(reference => reference.kind))
  expect(referenceKinds).toContain('ACTIVITY')
  expect(referenceKinds).toContain('SOURCE_RISK')
  expect(referenceKinds).toContain('POLICY_RETRIEVAL')
  if (expectedDetectors.length === 0) {
    expect(referenceKinds).not.toContain('DETECTOR_SIGNAL')
  } else {
    expect(referenceKinds).toContain('DETECTOR_SIGNAL')
  }

  await expect(page.getByTestId('analysis-result')).toBeVisible()
  const currentGrounding = page.getByTestId('analysis-result').getByTestId('analysis-grounding-evidence')
  await expect(currentGrounding).toContainText('adapter: pgvector')
  await expect(currentGrounding).toContainText('embeddingModel: all-MiniLM-L6-v2')
  await expectRenderedProvenance(page.getByTestId('analysis-result'), completed)

  const retained = page.getByTestId(`analysis-history-${completed.analysisId}`)
  await expect(retained).toContainText('operator-alpha')
  await expect(retained).toContainText(completed.riskLevel)
  await expect(retained.getByTestId('analysis-grounding-evidence')).toContainText('adapter: pgvector')
  await expectRenderedProvenance(retained, completed)

  const afterResponse = await page.request.get(`/api/customers/${seededCustomer}`)
  expect(afterResponse.status()).toBe(200)
  const after = await afterResponse.json() as CustomerSnapshot
  expect(after.riskEvidence).toEqual(before.riskEvidence)

  await annotateReviewerEvidence(page, completed)
  await page.screenshot({ path: testInfo.outputPath(`${evidenceName}-customer-444.png`), fullPage: true })

  await page.reload()
  await expect(page.getByTestId('operator-session')).toContainText('operator-alpha')
  await loadCustomer(page)

  const historyResponse = await page.request.get(`/api/customers/${seededCustomer}/analyses`)
  expect(historyResponse.status()).toBe(200)
  const history = await historyResponse.json() as Analysis[]
  const reloaded = history.find(entry => entry.analysisId === completed.analysisId)
  expect(reloaded).toBeDefined()
  expect(reloaded?.operatorId).toBe('operator-alpha')
  expect(reloaded?.modelProvenance.backendIdentity).toBe(expectedModelBackend)
  expect(reloaded?.evidenceProvenance.some(evidence => evidence.retrievalMetadata.adapter === 'pgvector')).toBe(true)
  if (expectedDetectors.length > 0) {
    expect(reloaded?.detectorProvenance.map(item => item.detectorIdentity)).toEqual(expectedDetectors)
  }

  const reloadedEntry = page.getByTestId(`analysis-history-${completed.analysisId}`)
  await expect(reloadedEntry).toBeVisible()
  await expectRenderedProvenance(reloadedEntry, reloaded as Analysis)
})

test('records the full-composite detector scores against the crafted scenario order', async ({ page }, testInfo) => {
  test.skip(expectedDetectors.length !== 3, 'R5 full-composite evidence only')

  await page.goto('/')
  await signIn(page)
  const sessionResponse = await page.request.get('/api/session')
  expect(sessionResponse.status()).toBe(200)
  const session = await sessionResponse.json() as { csrf: { token: string } }
  const scenarios = [
    { name: 'ordinary', customerId: '22222222-2222-2222-2222-222222222222' },
    { name: 'mixed-seed', customerId: '11111111-1111-1111-1111-111111111111' },
    { name: 'growing-cross-border', customerId: '33333333-3333-3333-3333-333333333333' },
    { name: 'dense-mixed-risk', customerId: seededCustomer },
  ]
  const comparison: Array<{
    oracleRank: number
    scenario: string
    customerId: string
    scores: Record<string, number>
  }> = []

  for (const [oracleRank, scenario] of scenarios.entries()) {
    const response = await page.request.post(`/api/customers/${scenario.customerId}/analyses`, {
      headers: { 'X-CSRF-TOKEN': session.csrf.token },
    })
    expect(response.status()).toBe(201)
    const analysis = await response.json() as Analysis
    expect(analysis.detectorProvenance.map(item => item.detectorIdentity)).toEqual(expectedDetectors)
    comparison.push({
      oracleRank,
      scenario: scenario.name,
      customerId: scenario.customerId,
      scores: Object.fromEntries(analysis.detectorProvenance.map(item => [item.detectorIdentity, item.score])),
    })
  }

  const bayesian = comparison.map(row => row.scores['beta-binomial-review-elevation-v1'])
  expect(bayesian[0]).toBeLessThan(bayesian[1])
  expect(bayesian[1]).toBeLessThan(bayesian[2])
  expect(bayesian[2]).toBeLessThan(bayesian[3])
  const fuzzy = comparison.map(row => row.scores['graded-review-fuzzy-v1'])
  expect(fuzzy.slice(1).every(score => score > fuzzy[0])).toBe(true)
  const forest = comparison.map(row => row.scores['random-forest-review-v1'])
  expect(forest[3]).toBeGreaterThan(forest[0])

  await writeFile(
    testInfo.outputPath('detector-scenario-scores.json'),
    `${JSON.stringify({ semantics: 'diagnostic-only; heterogeneous scores are not calibrated probabilities', comparison }, null, 2)}\n`,
    'utf8',
  )
})

test('renders local Stage-3 provenance as retained internal execution without conflating detector or grounding evidence', async ({ page }) => {
  await page.goto('/')
  await signIn(page)
  await loadCustomer(page)

  const localAnalysis: Analysis = {
    analysisId: '55555555-5555-5555-5555-555555555555',
    customerId: seededCustomer,
    operatorId: 'operator-alpha',
    generatedAt: '2026-09-05T12:00:00Z',
    riskLevel: 'HIGH',
    findingsSummary: 'Local structured analysis grounded in the retained synthetic evidence.',
    recommendations: ['Review the retained source evidence.'],
    evidenceProvenance: [{
      sourceIdentity: 'synthetic-policy:local-stage-3-test',
      content: 'Synthetic reviewer policy evidence.',
      retrievalMetadata: {
        adapter: 'pgvector',
        embeddingModel: 'all-MiniLM-L6-v2',
      },
    }],
    detectorProvenance: [
      {
        detectorIdentity: 'beta-binomial-review-elevation-v1',
        signalIdentity: 'posterior-review-elevation-rate',
        score: 0.7,
        provenance: { detectorFamily: 'BAYESIAN' },
      },
      {
        detectorIdentity: 'graded-review-fuzzy-v1',
        signalIdentity: 'fuzzy-review-elevation',
        score: 0.8,
        provenance: { detectorFamily: 'FUZZY' },
      },
      {
        detectorIdentity: 'random-forest-review-v1',
        signalIdentity: 'random-forest-review-elevation-vote',
        score: 0.75,
        provenance: { detectorFamily: 'RANDOM_FOREST', library: 'Tribuo' },
      },
    ],
    modelProvenance: {
      backendIdentity: 'local',
      modelIdentity: 'ministral-3-8b-instruct-2512',
      promptIdentity: 'grounded-analysis-v1',
      evidenceReferences: [
        {
          kind: 'DETECTOR_SIGNAL',
          evidenceIdentity: 'beta-binomial-review-elevation-v1:posterior-review-elevation-rate',
        },
        {
          kind: 'DETECTOR_SIGNAL',
          evidenceIdentity: 'graded-review-fuzzy-v1:fuzzy-review-elevation',
        },
        {
          kind: 'DETECTOR_SIGNAL',
          evidenceIdentity: 'random-forest-review-v1:random-forest-review-elevation-vote',
        },
        { kind: 'POLICY_RETRIEVAL', evidenceIdentity: 'synthetic-policy:local-stage-3-test' },
      ],
      metadata: {
        runtime: 'lmstudio/llama.cpp',
        externalTransmission: 'false',
      },
    },
  }

  await page.route(new RegExp(`/api/customers/${seededCustomer}/analyses(?:\\?.*)?$`), async route => {
    if (route.request().method() === 'POST') {
      await route.fulfill({ status: 201, contentType: 'application/json', body: JSON.stringify(localAnalysis) })
      return
    }
    await route.fulfill({
      status: 200,
      contentType: 'application/json',
      headers: {
        'X-Page': '0',
        'X-Page-Size': '20',
        'X-Total-Count': '1',
        'X-Total-Pages': '1',
        'X-Has-Previous': 'false',
        'X-Has-Next': 'false',
      },
      body: JSON.stringify([localAnalysis]),
    })
  })

  await page.getByRole('button', { name: 'Run analysis' }).click()

  const current = page.getByTestId('analysis-result')
  await expectRenderedProvenance(current, localAnalysis)
  const detectorProvenance = current.getByTestId('analysis-detector-provenance')
  await expect(detectorProvenance).toContainText('beta-binomial-review-elevation-v1')
  await expect(detectorProvenance).toContainText('graded-review-fuzzy-v1')
  await expect(detectorProvenance).toContainText('random-forest-review-v1')
  const stage3 = current.getByTestId('analysis-model-provenance')
  await expect(stage3.getByTestId('analysis-model-backend')).toHaveText('backend: local')
  await expect(stage3.getByTestId('analysis-external-transmission')).toHaveText('external transmission: no')
  await expect(stage3).not.toContainText('random-forest-review-v1')

  const retained = page.getByTestId(`analysis-history-${localAnalysis.analysisId}`)
  await expect(retained).toBeVisible()
  await expectRenderedProvenance(retained, localAnalysis)
})
