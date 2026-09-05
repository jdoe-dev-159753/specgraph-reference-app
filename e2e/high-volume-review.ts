/**
 * Reusable dense-browser scenario for bounded customer review and duplicate-action recovery.
 *
 * @remarks
 * The in-browser routes deliberately model 250 activities while releasing selected
 * responses through gates. This proves that repeated clicks create one request,
 * paging remains bounded, failures clear stale evidence, and analysis guards recover.
 * It is a deterministic UI concurrency fixture, not persistence or performance proof.
 *
 * @module
 */
import { expect, Page } from '@playwright/test'

/** Dedicated fixture identity keeps route interception away from repository seed scenarios. */
const denseCustomer = '55555555-5555-5555-5555-555555555555'
/** Stable absent identity used to distinguish not-found from malformed input. */
const unknownCustomer = '99999999-9999-9999-9999-999999999999'
/** Deliberately malformed identity owns the public bad-request path. */
const invalidCustomer = 'invalid-customer'
/** Fixed seed makes the repeated filter sequence replayable across runs. */
const interactionSeed = 0x125
/** Route boundary covers customer paging/filter variants but excludes nested analysis traffic. */
const denseCustomerReviewUrl = new RegExp(`/api/customers/${denseCustomer}(?:\\?.*)?$`)
/** Separate nested route retains analysis request counts and history independently from customer reads. */
const denseAnalysisUrl = new RegExp(`/api/customers/${denseCustomer}/analyses(?:\\?.*)?$`)
/** Expected client-error route is limited to the two explicit invalid identities. */
const invalidCustomerReviewUrl = new RegExp(
  `/api/customers/(?:${unknownCustomer}|${invalidCustomer})?(?:\\?.*)?$`,
)

/** Minimal retained-analysis contract required by the dense browser fixture. */
export type StoredAnalysis = {
  analysisId: string
  customerId: string
  operatorId: string
  generatedAt: string
  riskLevel: 'MEDIUM'
  findingsSummary: string
  recommendations: string[]
  evidenceProvenance: unknown[]
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
    evidenceReferences: Array<{
      kind: 'ACTIVITY' | 'SOURCE_RISK' | 'DETECTOR_SIGNAL' | 'POLICY_RETRIEVAL'
      evidenceIdentity: string
    }>
    metadata: Record<string, string>
  }
}

/** Creates stable high-volume rows whose ordering, identities, and decimal strings are assertion-friendly. */
export function activity(index: number, status = index % 2 === 0 ? 'Completed' : 'Declined') {
  const suffix = String(index).padStart(12, '0')
  return {
    transactionId: `55555555-5555-5555-5556-${suffix}`,
    type: 'CARD',
    amount: `${100 + index}.00`,
    currency: 'CHF',
    status,
    createdAt: new Date(Date.UTC(2026, 0, 1, 0, index)).toISOString(),
    details: {
      cardPan: '**** **** **** 0000',
      cardType: 'VISA',
      merchantName: 'Dense Fixture Merchant',
      mccCode: '5999',
      cardPresent: true,
      authorizationCode: `DENSE-${index}`,
      declineReason: status === 'Completed' ? null : 'Synthetic decline',
    },
  }
}

/** Generates reproducible filter choices so repeated-interaction coverage is not a flaky random walk. */
export function seededStatuses(seed: number, count: number) {
  let state = seed >>> 0
  return Array.from({ length: count }, () => {
    state = (Math.imul(state, 1664525) + 1013904223) >>> 0
    return (state & 1) === 0 ? '' : 'Completed'
  })
}

/**
 * Exercises paging, filtering, expected 400/404 recovery, request de-duplication,
 * retained analysis, and a mobile viewport through the browser boundary.
 */
export async function runHighVolumeReviewScenario(page: Page) {
  const browserErrors: string[] = []
  const pageErrors: string[] = []
  const retainedAnalyses: StoredAnalysis[] = []
  let customerRequests = 0
  let analysisRequests = 0
  let releaseFirstCustomerResponse!: () => void
  let releaseSecondCustomerResponse!: () => void
  let releaseFirstAnalysisResponse!: () => void
  let releaseSecondAnalysisResponse!: () => void
  const firstCustomerResponseGate = new Promise<void>(resolve => { releaseFirstCustomerResponse = () => resolve() })
  const secondCustomerResponseGate = new Promise<void>(resolve => { releaseSecondCustomerResponse = () => resolve() })
  const firstAnalysisResponseGate = new Promise<void>(resolve => { releaseFirstAnalysisResponse = () => resolve() })
  const secondAnalysisResponseGate = new Promise<void>(resolve => { releaseSecondAnalysisResponse = () => resolve() })

  page.on('console', message => {
    if (message.type() === 'error') browserErrors.push(message.text())
  })
  page.on('pageerror', error => pageErrors.push(error.message))

  await page.route(denseCustomerReviewUrl, async route => {
    customerRequests += 1
    if (customerRequests === 1) await firstCustomerResponseGate
    if (customerRequests === 2) await secondCustomerResponseGate
    const url = new URL(route.request().url())
    const pageIndex = Number(url.searchParams.get('page') ?? '0')
    const pageSize = Number(url.searchParams.get('pageSize') ?? '50')
    const status = url.searchParams.get('status')
    const totalActivities = status?.toLowerCase() === 'completed' ? 125 : 250
    const firstIndex = pageIndex * pageSize + 1
    const lastIndex = Math.min(totalActivities, firstIndex + pageSize - 1)
    const activities = Array.from(
      { length: Math.max(0, lastIndex - firstIndex + 1) },
      (_unused, offset) => {
        const logicalIndex = firstIndex + offset
        const sourceIndex = status?.toLowerCase() === 'completed' ? logicalIndex * 2 : logicalIndex
        return activity(sourceIndex, status ?? undefined)
      },
    )
    const pageTransactionIds = new Set(activities.map(item => item.transactionId))
    const riskEvidence = Array.from({ length: 25 }, (_unused, index) => {
      const sourceIndex = (index + 1) * 10
      const transactionId = `55555555-5555-5555-5556-${String(sourceIndex).padStart(12, '0')}`
      return {
        assessmentId: `66666666-6666-6666-6666-${String(index + 1).padStart(12, '0')}`,
        transactionId,
        ruleId: '10000000-0000-0000-0000-000000000001',
        ruleName: 'Dense fixture review rule',
        triggeredAt: new Date(Date.UTC(2026, 0, 1, 0, sourceIndex, 1)).toISOString(),
        scoreContribution: 5,
      }
    }).filter(item => pageTransactionIds.has(item.transactionId))

    await route.fulfill({
      status: 200,
      contentType: 'application/json',
      body: JSON.stringify({
        customerId: denseCustomer,
        activities,
        riskEvidence,
        page: pageIndex,
        pageSize,
        totalActivities,
        totalRiskEvidence: 25,
        totalPages: Math.ceil(totalActivities / pageSize),
        hasPrevious: pageIndex > 0,
        hasNext: (pageIndex + 1) * pageSize < totalActivities,
      }),
    })
  })

  await page.route(invalidCustomerReviewUrl, async route => {
    const notFound = new URL(route.request().url()).pathname.endsWith(unknownCustomer)
    await route.fulfill({
      status: notFound ? 404 : 400,
      contentType: 'application/problem+json',
      body: JSON.stringify({ detail: notFound ? 'Customer not found' : 'Invalid customer identifier' }),
    })
  })

  await page.route(denseAnalysisUrl, async route => {
    if (route.request().method() === 'POST') {
      analysisRequests += 1
      if (analysisRequests === 1) await firstAnalysisResponseGate
      if (analysisRequests === 2) await secondAnalysisResponseGate
      const completed: StoredAnalysis = {
        analysisId: `77777777-7777-7777-7777-${String(analysisRequests).padStart(12, '0')}`,
        customerId: denseCustomer,
        operatorId: 'r3-demo-operator',
        generatedAt: new Date(Date.UTC(2026, 0, 2, 0, 0, analysisRequests)).toISOString(),
        riskLevel: 'MEDIUM',
        findingsSummary: `Deterministic dense analysis ${analysisRequests}`,
        recommendations: ['Retain bounded reviewer evidence'],
        evidenceProvenance: [],
        detectorProvenance: [],
        modelProvenance: {
          backendIdentity: 'deterministic',
          modelIdentity: 'r3-offline-baseline-v1',
          promptIdentity: 'grounded-analysis-v1',
          evidenceReferences: [],
          metadata: { externalTransmission: 'false' },
        },
      }
      retainedAnalyses.unshift(completed)
      await route.fulfill({
        status: 201,
        contentType: 'application/json',
        body: JSON.stringify(completed),
      })
      return
    }

    await route.fulfill({
      status: 200,
      contentType: 'application/json',
      headers: {
        'X-Page': '0',
        'X-Page-Size': '20',
        'X-Total-Count': String(retainedAnalyses.length),
        'X-Total-Pages': retainedAnalyses.length === 0 ? '0' : '1',
        'X-Has-Previous': 'false',
        'X-Has-Next': 'false',
      },
      body: JSON.stringify(retainedAnalyses),
    })
  })

  await page.setViewportSize({ width: 390, height: 844 })
  await page.goto('/')
  const customerId = page.getByLabel('Customer ID')
  const search = page.getByRole('button', { name: 'Search' })
  await customerId.fill(denseCustomer)

  const firstResponse = page.waitForResponse(response =>
    response.url().endsWith(`/api/customers/${denseCustomer}`) && response.request().method() === 'GET')
  await search.click({ clickCount: 3 })
  await expect(search).toBeDisabled()
  releaseFirstCustomerResponse()
  expect((await firstResponse).status()).toBe(200)
  await expect(search).toBeEnabled()
  expect(customerRequests).toBe(1)

  await expect(page.getByTestId('customer-activity')).toContainText('250 matching activities')
  await expect(page.locator('[data-testid="activity-card"]')).toHaveCount(50)
  await expect(page.getByTestId('activity-pagination')).toContainText('1–50 of 250')
  const firstPageTransactions = await page.locator('[data-testid="activity-card-transaction"]').allTextContents()
  expect(new Set(firstPageTransactions).size).toBe(50)

  const secondPageResponse = page.waitForResponse(response => {
    const url = new URL(response.url())
    return url.pathname === `/api/customers/${denseCustomer}` && url.searchParams.get('page') === '1'
  })
  const secondPageRequest = page.waitForRequest(request => {
    const url = new URL(request.url())
    return url.pathname === `/api/customers/${denseCustomer}` && url.searchParams.get('page') === '1'
  })
  const pagination = page.getByTestId('activity-pagination')
  await pagination.scrollIntoViewIfNeeded()
  await expect(pagination.getByLabel('Go to next page')).toBeVisible()
  await pagination.getByLabel('Go to next page').click({ clickCount: 3 })
  await expect(search).toBeDisabled()
  await secondPageRequest
  expect(customerRequests).toBe(2)
  releaseSecondCustomerResponse()
  expect((await secondPageResponse).status()).toBe(200)
  await expect(pagination).toContainText('51–100 of 250')
  await expect(page.locator('[data-testid="activity-card"]')).toHaveCount(50)

  await page.getByLabel('Status').fill('Completed')
  const filteredResponse = page.waitForResponse(response => {
    const url = new URL(response.url())
    return url.pathname === `/api/customers/${denseCustomer}`
      && url.searchParams.get('status') === 'Completed'
      && url.searchParams.get('page') === null
  })
  await search.click()
  expect((await filteredResponse).status()).toBe(200)
  await expect(page.getByTestId('customer-activity')).toContainText('125 matching activities')
  await expect(pagination).toContainText('1–50 of 125')
  await expect(page.locator('[data-testid="activity-card-status"]')).toHaveCount(50)
  await expect(page.locator('[data-testid="activity-card-status"]')).toHaveText(Array(50).fill('Completed'))

  await customerId.fill('')
  const emptyResponse = page.waitForResponse(response =>
    new URL(response.url()).pathname.endsWith('/api/customers/'))
  await search.click()
  expect((await emptyResponse).status()).toBe(400)
  await expect(page.getByRole('alert')).toContainText('Invalid customer activity filters')
  await expect(page.getByTestId('customer-activity')).toHaveCount(0)

  await customerId.fill(invalidCustomer)
  const invalidResponse = page.waitForResponse(response =>
    new URL(response.url()).pathname.endsWith(`/${invalidCustomer}`))
  await search.click()
  expect((await invalidResponse).status()).toBe(400)
  await expect(page.getByRole('alert')).toContainText('Invalid customer activity filters')
  await expect(page.getByTestId('customer-activity')).toHaveCount(0)

  await customerId.fill(unknownCustomer)
  const unknownResponse = page.waitForResponse(response =>
    new URL(response.url()).pathname.endsWith(`/${unknownCustomer}`))
  await search.click()
  expect((await unknownResponse).status()).toBe(404)
  await expect(page.getByRole('alert')).toContainText('Customer not found')

  await customerId.fill(denseCustomer)
  await page.getByLabel('Status').fill('')
  const recoveryResponse = page.waitForResponse(response =>
    response.url().endsWith(`/api/customers/${denseCustomer}`) && response.request().method() === 'GET')
  await search.click()
  expect((await recoveryResponse).status()).toBe(200)
  await expect(page.getByTestId('customer-activity')).toContainText('250 matching activities')

  const analysisButton = page.getByRole('button', { name: 'Run analysis' })
  const firstAnalysisResponse = page.waitForResponse(response =>
    response.url().endsWith(`/api/customers/${denseCustomer}/analyses`)
      && response.request().method() === 'POST')
  await analysisButton.click({ clickCount: 3 })
  await expect(page.getByRole('button', { name: 'Analyzing…' })).toBeDisabled()
  await expect(search).toBeDisabled()
  releaseFirstAnalysisResponse()
  expect((await firstAnalysisResponse).status()).toBe(201)
  await expect(analysisButton).toBeEnabled()
  expect(analysisRequests).toBe(1)
  await expect(page.getByTestId('analysis-result')).toHaveCount(1)
  await expect(page.getByTestId('analysis-findings')).toContainText('Deterministic dense analysis 1')
  const currentProvenance = page.getByTestId('analysis-result').getByTestId('analysis-provenance')
  await expect(currentProvenance.getByTestId('analysis-detector-provenance'))
    .toContainText('No detector artifact was retained')
  await expect(currentProvenance.getByTestId('analysis-model-backend')).toHaveText('backend: deterministic')
  await expect(currentProvenance.getByTestId('analysis-external-transmission'))
    .toHaveText('external transmission: no')
  await expect(page.getByTestId('analysis-history-77777777-7777-7777-7777-000000000001')).toBeVisible()

  const secondAnalysisResponse = page.waitForResponse(response =>
    response.url().endsWith(`/api/customers/${denseCustomer}/analyses`)
      && response.request().method() === 'POST')
  await analysisButton.click({ clickCount: 3 })
  await expect(page.getByRole('button', { name: 'Analyzing…' })).toBeDisabled()
  await expect(search).toBeDisabled()
  releaseSecondAnalysisResponse()
  expect((await secondAnalysisResponse).status()).toBe(201)
  await expect(analysisButton).toBeEnabled()
  expect(analysisRequests).toBe(2)
  await expect(page.getByTestId('analysis-result')).toHaveCount(1)
  await expect(page.getByTestId('analysis-history').locator('[data-testid^="analysis-history-"]')).toHaveCount(2)
  await expect(page.getByTestId('analysis-history-77777777-7777-7777-7777-000000000002')).toBeVisible()

  const boundedStatuses = seededStatuses(interactionSeed, 6)
  for (const seededStatus of boundedStatuses) {
    await page.getByLabel('Status').fill(seededStatus)
    const seededResponse = page.waitForResponse(response => {
      const url = new URL(response.url())
      return url.pathname === `/api/customers/${denseCustomer}`
        && (url.searchParams.get('status') ?? '') === seededStatus
        && response.request().method() === 'GET'
    })
    await search.click()
    expect((await seededResponse).status()).toBe(200)
    await expect(page.getByTestId('customer-activity')).toContainText(
      seededStatus === 'Completed' ? '125 matching activities' : '250 matching activities',
    )
  }
  expect(customerRequests).toBe(4 + boundedStatuses.length)

  await page.evaluate(() => window.scrollTo(0, 0))
  await expect(customerId).toBeVisible()
  expect(pageErrors).toEqual([])
  expect(browserErrors.filter(message => !(
    message.startsWith('Failed to load resource:')
    && (message.includes('400') || message.includes('404'))
  ))).toEqual([])
}
