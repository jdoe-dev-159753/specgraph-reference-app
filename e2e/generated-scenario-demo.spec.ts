/** Opt-in browser evidence for replayable synthetic inputs; fixed-persona gates do not select this file. */
import { expect, test, type Page } from '@playwright/test'

/** Bounded synthetic scenario families exposed by the optional demonstration endpoint. */
type Family = 'ORDINARY_LOCAL' | 'CROSS_BORDER_GROWTH' | 'MIXED_RED_FLAGS'
/** Replay provenance and source-shape counts returned after materializing one seeded scenario. */
type GeneratedScenario = {
  customerId: string
  seed: string
  family: Family
  generatorIdentity: string
  activityCount: number
  riskEvidenceCount: number
}
/** Minimal analysis surface used to compare downstream behavior across generated scenarios. */
type Analysis = {
  analysisId: string
  customerId: string
  riskLevel: 'LOW' | 'MEDIUM' | 'HIGH'
  findingsSummary: string
  detectorProvenance: Array<{ detectorIdentity: string; score: number }>
  modelProvenance: { evidenceReferences: unknown[] }
}

/** Canonical fixed customer used to prove generated scenarios do not replace regression fixtures. */
const fixedCustomer = '44444444-4444-4444-4444-444444444444'

/** Authenticates through the reviewer-visible operator path and waits for the optional scenario lab. */
async function signIn(page: Page) {
  await page.goto('/')
  await page.getByLabel('Operator ID').fill('operator-alpha')
  await page.getByLabel('Password').fill('alpha-demo-2026')
  await page.getByRole('button', { name: 'Sign in' }).click()
  await expect(page.getByTestId('scenario-lab')).toBeVisible()
}

/** Generates one seeded source-shaped scenario through the UI and verifies returned replay provenance. */
async function generate(page: Page, seed: string, family: Family, option: string, riskEvidenceCount: number) {
  await page.getByLabel('Scenario seed').fill(seed)
  await page.getByLabel('Scenario family').click()
  await page.getByRole('option', { name: option }).click()
  const responsePromise = page.waitForResponse(response => response.url().endsWith('/api/demo/scenarios') &&
    response.request().method() === 'POST')
  await page.getByRole('button', { name: 'Generate and load' }).click()
  const response = await responsePromise
  const body = await response.text()
  expect(response.status(), body).toBe(200)
  const scenario = JSON.parse(body) as GeneratedScenario
  expect(scenario).toMatchObject({ seed, family, riskEvidenceCount, activityCount: 6,
    generatorIdentity: 'jdk-splittable-random-v1' })
  await expect(page.getByTestId('scenario-provenance')).toContainText(`seed ${seed}`)
  await expect(page.getByTestId('customer-activity')).toContainText(scenario.customerId)
  return scenario
}

/** Executes the configured analysis path for the selected generated customer and returns its observable result. */
async function analyze(page: Page, customerId: string) {
  const responsePromise = page.waitForResponse(response => response.url().endsWith(
    `/api/customers/${customerId}/analyses`) && response.request().method() === 'POST')
  await page.getByRole('button', { name: 'Run analysis' }).click()
  const response = await responsePromise
  const body = await response.text()
  expect(response.status(), body).toBe(201)
  return JSON.parse(body) as Analysis
}

/** Proves distinct seeded source evidence reaches the same configured analysis pipeline without replacing fixed personas. */
test('VFY-REPRODUCIBILITY-001 compares generated evidence through the configured analysis path', async ({ page }) => {
  await signIn(page)
  const ordinary = await generate(page, '8675308', 'ORDINARY_LOCAL', 'Ordinary local activity', 0)
  const ordinaryAnalysis = await analyze(page, ordinary.customerId)
  const mixed = await generate(page, '8675309', 'MIXED_RED_FLAGS', 'Mixed red-flag pattern', 4)
  const mixedAnalysis = await analyze(page, mixed.customerId)
  expect(mixed.customerId).not.toBe(ordinary.customerId)
  expect([mixedAnalysis.riskLevel, mixedAnalysis.findingsSummary])
    .not.toEqual([ordinaryAnalysis.riskLevel, ordinaryAnalysis.findingsSummary])
  if (mixedAnalysis.detectorProvenance.length > 0) {
    expect(mixedAnalysis.detectorProvenance.map(item => item.detectorIdentity))
      .toEqual(ordinaryAnalysis.detectorProvenance.map(item => item.detectorIdentity))
    expect(mixedAnalysis.detectorProvenance.map(item => item.score))
      .not.toEqual(ordinaryAnalysis.detectorProvenance.map(item => item.score))
  }
  expect(mixedAnalysis.modelProvenance.evidenceReferences)
    .not.toEqual(ordinaryAnalysis.modelProvenance.evidenceReferences)
  await page.getByLabel('Customer ID').fill(fixedCustomer)
  await page.getByRole('button', { name: 'Search' }).click()
  await expect(page.getByTestId('customer-activity')).toContainText(fixedCustomer)
  await expect(page.getByTestId('scenario-provenance')).toHaveCount(0)
})

/** Proves generation remains serialized behind an in-flight analysis before another scenario can be loaded. */
test('VFY-REPRODUCIBILITY-001 serializes scenario generation behind an in-flight analysis', async ({ page }) => {
  await signIn(page)
  await page.getByLabel('Customer ID').fill(fixedCustomer)
  await page.getByRole('button', { name: 'Search' }).click()
  await expect(page.getByTestId('analysis-workspace')).toBeVisible()
  let releaseAnalysis!: () => void
  const analysisGate = new Promise<void>(resolve => { releaseAnalysis = resolve })
  await page.route('**/api/customers/*/analyses', async route => {
    if (route.request().method() !== 'POST') return route.continue()
    await analysisGate
    await route.continue()
  })
  const requestPromise = page.waitForRequest(request => request.url().endsWith(
    `/api/customers/${fixedCustomer}/analyses`) && request.method() === 'POST')
  await page.getByRole('button', { name: 'Run analysis' }).click()
  await requestPromise
  const generateButton = page.getByRole('button', { name: 'Generate and load' })
  await expect(generateButton).toBeDisabled()
  const responsePromise = page.waitForResponse(response => response.url().endsWith(
    `/api/customers/${fixedCustomer}/analyses`) && response.request().method() === 'POST')
  releaseAnalysis()
  expect((await responsePromise).status()).toBe(201)
  await expect(generateButton).toBeEnabled()
  await page.getByLabel('Scenario seed').fill('8675310')
  const generatedPromise = page.waitForResponse(response => response.url().endsWith('/api/demo/scenarios') &&
    response.request().method() === 'POST')
  await generateButton.click()
  expect((await generatedPromise).status()).toBe(200)
  await expect(page.getByTestId('scenario-provenance')).toContainText('seed 8675310')
})
