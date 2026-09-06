/**
 * End-to-end evidence for the fully composed authenticated, detected, grounded, and retained flow.
 *
 * @remarks
 * Environment variables select expected adapter identities without weakening the
 * invariant assertions. The primary scenario proves composition and provenance;
 * the detector-order scenario is a crafted-data diagnostic rather than calibration;
 * the local-model scenario isolates rendering semantics with a browser route double.
 *
 * @module
 */
import { expect, test, type Locator } from '@playwright/test'
import { writeFile } from 'node:fs/promises'

/** Densest crafted customer maximizes visible evidence across all composed stages. */
const seededCustomer = '44444444-4444-4444-4444-444444444444'
/** Workflow-owned detector expectation supports baseline, single, and composite evidence with one scenario. */
const expectedDetectorSelection = process.env.EXPECT_DETECTORS ?? process.env.EXPECT_DETECTOR ?? 'none'
/** Ordered detector identities remain visible independently; they are never averaged in the test. */
const expectedDetectors = expectedDetectorSelection === 'none'
  ? []
  : expectedDetectorSelection.split(',').map(value => value.trim()).filter(Boolean)
/** Expected Stage-3 adapter identity supplied by the variant manifest. */
const expectedModelBackend = process.env.EXPECT_MODEL_BACKEND ?? 'deterministic'
/** Expected model identity prevents a successful response from hiding adapter drift. */
const expectedModelIdentity = process.env.EXPECT_MODEL_IDENTITY ?? 'r3-offline-baseline-v1'
/** Prompt identity is independently asserted so model selection cannot obscure prompt drift. */
const expectedPromptIdentity = process.env.EXPECT_PROMPT_IDENTITY ?? 'grounded-analysis-v1'
/** Transmission expectation makes local/deterministic versus external handling explicit. */
const expectedExternalTransmission = process.env.EXPECT_EXTERNAL_TRANSMISSION ?? 'false'
/** Expected delivery ring proves the browser label belongs to the built topology. */
const expectedDeliveryRing = process.env.EXPECT_DELIVERY_RING ?? 'R4'
/** Evidence namespace separates parallel gallery/release artifacts. */
const evidenceName = process.env.EVIDENCE_NAME ?? 'r4-complete-flow'

/** Known detector-specific contracts preserve heterogeneous identities and library provenance. */
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

/** Evidence-reference subset used to prove that model provenance cites every activated stage. */
export type EvidenceReference = {
  kind: 'ACTIVITY' | 'SOURCE_RISK' | 'DETECTOR_SIGNAL' | 'POLICY_RETRIEVAL'
  evidenceIdentity: string
}

/** Complete browser-facing result contract asserted across current and retained views. */
export type Analysis = {
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

/** Customer subset needed to prove all activity families and unchanged source-risk evidence. */
export type CustomerSnapshot = {
  customerId: string
  activities: Array<{ type: 'CARD' | 'PAYMENT' | 'CRYPTO' }>
  riskEvidence: unknown[]
}

/** Enters the fixed reviewer identity used by immutable R4/R5 evidence runs. */
export async function signIn(page: import('@playwright/test').Page) {
  await page.getByLabel('Operator ID').fill('operator-alpha')
  await page.getByLabel('Password').fill('alpha-demo-2026')
  await page.getByRole('button', { name: 'Sign in' }).click()
  await expect(page.getByTestId('operator-session')).toContainText('operator-alpha')
}

/** Loads the dense crafted customer and waits for both evidence and analysis workspaces. */
export async function loadCustomer(page: import('@playwright/test').Page) {
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

/** Adds an evidence-only screenshot banner derived from the observed result and expected runtime configuration. */
export async function annotateReviewerEvidence(page: import('@playwright/test').Page, completed: Analysis) {
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

/** Applies one provenance oracle to both the immediate result and its retained-history representation. */
export async function expectRenderedProvenance(container: Locator, completed: Analysis) {
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

/** Proves the configured full flow through HTTP, browser rendering, persistence, and screenshot evidence. */
test('VFY-AUTH-001 VFY-ANALYSIS-CONTRACT-001 VFY-RAG-001 VFY-HISTORY-001 VFY-REPRODUCIBILITY-001 VFY-DETERMINISM-001 VFY-DELIVERY-001 prove the executable configured authenticated grounded R4 flow and retain reviewer evidence', async ({ page, request }, testInfo) => {
  const anonymous = await request.get(`/api/customers/${seededCustomer}`)
  expect(anonymous.status()).toBe(401)

  await page.goto('/')
  await expect(page.getByText(`Customer Care · ${expectedDeliveryRing}`)).toBeVisible()
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
  const analysisBody = await analysisResponse.text()
  expect(analysisResponse.status(), analysisBody).toBe(201)
  const completed = JSON.parse(analysisBody) as Analysis

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

/** Records bounded detector diagnostics; only explicitly asserted order relations are treated as oracle facts. */
test('records full-composite scores when configured and otherwise proves composite mode is absent', async ({ page }, testInfo) => {
  // R4 deliberately runs this shared file without the three-detector R5 configuration. Keeping
  // that branch executable avoids hiding the controlled V&V scenario elsewhere in this file.
  if (expectedDetectors.length !== 3) {
    expect(expectedDetectors).not.toHaveLength(3)
    return
  }

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
    const responseBody = await response.text()
    expect(response.status(), responseBody).toBe(201)
    const analysis = JSON.parse(responseBody) as Analysis
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

/** Isolates UI semantics for local execution provenance; the route double does not prove LM Studio connectivity. */
test('renders local Stage-3 provenance as retained internal execution without conflating detector or grounding evidence', async ({ page }) => {
  await page.goto('/')
  await signIn(page)

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

  await loadCustomer(page)
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
