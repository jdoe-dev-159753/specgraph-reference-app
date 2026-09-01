import { expect, test } from '@playwright/test'

const seededCustomer = '11111111-1111-1111-1111-111111111111'
const growingCrossBorderCustomer = '33333333-3333-3333-3333-333333333333'
const unknownCustomer = '99999999-9999-9999-9999-999999999999'

const seededAssessmentIds = [
  '20000000-0000-0000-0000-000000000001',
  '20000000-0000-0000-0000-000000000002',
]
const growingScenarioAssessmentIds = [
  '20000000-0000-0000-0000-000000000003',
  '20000000-0000-0000-0000-000000000004',
  '20000000-0000-0000-0000-000000000005',
]

type Activity = {
  transactionId: string
  type: 'CARD' | 'PAYMENT' | 'CRYPTO'
  amount: string
  currency: string
  status: string
  createdAt: string
  details: Record<string, string | boolean | null>
}

type RiskEvidence = {
  assessmentId: string
  transactionId: string
  ruleId: string
  ruleName: string
  triggeredAt: string
  scoreContribution: number
}

test('VFY-CUSTOMER-READ-001 deployed R2 relational customer review', async ({ page }, testInfo) => {
  await page.goto('/')
  await expect(page.getByRole('heading', { name: 'Customer Activity Analytics' })).toBeVisible()
  await expect(page.getByText('Customer Care · R2')).toBeVisible()

  const customerId = page.getByLabel('Customer ID')
  await customerId.fill(seededCustomer)

  const responsePromise = page.waitForResponse(response =>
    response.url().endsWith(`/api/customers/${seededCustomer}`) && response.request().method() === 'GET')
  await page.getByRole('button', { name: 'Search' }).click()
  const response = await responsePromise

  expect(response.status()).toBe(200)
  const snapshot = await response.json()
  expect(snapshot.customerId).toBe(seededCustomer)
  expect(snapshot.activities.map((activity: Activity) => activity.type)).toEqual(
    expect.arrayContaining(['CARD', 'PAYMENT', 'CRYPTO']))
  expect(snapshot.riskEvidence.map((evidence: RiskEvidence) => evidence.ruleName)).toEqual(
    expect.arrayContaining(['Card not present high value', 'New crypto destination']))
  expect(snapshot.riskEvidence.map((evidence: RiskEvidence) => evidence.assessmentId).sort())
    .toEqual(seededAssessmentIds)

  await expect(page.getByRole('columnheader', { name: 'Transaction' })).toBeVisible()
  await expect(page.getByRole('columnheader', { name: 'Amount' })).toBeVisible()
  await expect(page.getByRole('columnheader', { name: 'Currency' })).toBeVisible()
  await expect(page.getByRole('columnheader', { name: 'Status' })).toBeVisible()
  await expect(page.getByRole('columnheader', { name: 'Time' })).toBeVisible()

  for (const activity of snapshot.activities as Activity[]) {
    expect(typeof activity.amount).toBe('string')
    const key = activity.type.toLowerCase()
    await expect(page.getByTestId(`activity-${key}-type`)).toHaveText(activity.type)
    await expect(page.getByTestId(`activity-${key}-transaction`)).toHaveText(activity.transactionId)
    await expect(page.getByTestId(`activity-${key}-amount`)).toHaveAttribute('data-amount', activity.amount)
    await expect(page.getByTestId(`activity-${key}-amount`)).not.toContainText(activity.currency)
    await expect(page.getByTestId(`activity-${key}-currency`)).toHaveText(activity.currency)
    await expect(page.getByTestId(`activity-${key}-status`)).toContainText(activity.status)
    await expect(page.getByTestId(`activity-${key}-time`)).toBeVisible()
    await expect(page.getByTestId(`activity-${key}-time`)).toHaveAttribute('data-created-at', activity.createdAt)
  }

  const card = (snapshot.activities as Activity[]).find(activity => activity.type === 'CARD')!
  expect(card.transactionId).toBe('aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaa1')
  expect(card.amount).toBe('248.50')
  expect(card.currency).toBe('CHF')
  expect(card.createdAt).toBe('2026-08-28T09:15:00Z')
  expect(typeof card.details.cardPresent).toBe('boolean')
  await expect(page.getByTestId('activity-card')).toContainText('cardPresent: false')
  await expect(page.getByTestId('activity-card')).toContainText('Alpine Camera')
  await expect(page.getByTestId('activity-payment')).toContainText('receiverBankCountry: DE')
  await expect(page.getByTestId('activity-crypto')).toContainText('blockchain: Bitcoin')

  const cardRisk = (snapshot.riskEvidence as RiskEvidence[])
    .find(evidence => evidence.assessmentId === seededAssessmentIds[0])!
  expect(cardRisk.transactionId).toBe('aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaa1')
  expect(cardRisk.ruleId).toBe('10000000-0000-0000-0000-000000000001')
  expect(cardRisk.ruleName).toBe('Card not present high value')
  expect(cardRisk.triggeredAt).toBe('2026-08-28T09:15:01Z')
  expect(cardRisk.scoreContribution).toBe(12.5)

  for (const evidence of snapshot.riskEvidence as RiskEvidence[]) {
    const item = page.getByTestId(`risk-evidence-${evidence.assessmentId}`)
    await expect(item).toBeVisible()
    await expect(item).toContainText(evidence.ruleName)
    await expect(item).toContainText(evidence.ruleId)
    await expect(item).toContainText(evidence.transactionId)
    await expect(item).toContainText(`+${evidence.scoreContribution}`)
    await expect(page.getByTestId(`risk-evidence-${evidence.assessmentId}-time`))
      .toHaveAttribute('data-triggered-at', evidence.triggeredAt)
  }

  await page.screenshot({ path: testInfo.outputPath('r2-postgresql-customer-review.png'), fullPage: true })

  await customerId.fill(growingCrossBorderCustomer)
  const scenarioPromise = page.waitForResponse(response =>
    response.url().endsWith(`/api/customers/${growingCrossBorderCustomer}`) && response.request().method() === 'GET')
  await page.getByRole('button', { name: 'Search' }).click()
  const scenarioResponse = await scenarioPromise
  expect(scenarioResponse.status()).toBe(200)
  const scenarioSnapshot = await scenarioResponse.json()
  expect(scenarioSnapshot.riskEvidence.map((evidence: RiskEvidence) => evidence.assessmentId).sort())
    .toEqual(growingScenarioAssessmentIds)
  await expect(page.getByTestId('customer-activity').getByText('EUR').first()).toBeVisible()
  await expect(page.getByTestId('activity-crypto-currency')).toHaveText('ETH')
  await expect(page.getByTestId('risk-evidence')).toContainText('Growing cross-border payment activity')
  await expect(page.getByTestId('risk-evidence')).toContainText('New crypto destination')

  await customerId.fill(unknownCustomer)
  const notFoundPromise = page.waitForResponse(response =>
    response.url().endsWith(`/api/customers/${unknownCustomer}`) && response.request().method() === 'GET')
  await page.getByRole('button', { name: 'Search' }).click()
  expect((await notFoundPromise).status()).toBe(404)
  await expect(page.getByRole('alert')).toContainText('Customer not found')
})

test('R2 UI renders repeated transaction-rule assessments by stable assessment identity', async ({ page }) => {
  const transactionId = 'aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaa1'
  const ruleId = '10000000-0000-0000-0000-000000000001'
  const firstAssessment = '20000000-0000-0000-0000-000000000001'
  const secondAssessment = '20000000-0000-0000-0000-000000000011'

  await page.route(`**/api/customers/${seededCustomer}`, async route => {
    await route.fulfill({
      status: 200,
      contentType: 'application/json',
      body: JSON.stringify({
        customerId: seededCustomer,
        activities: [],
        riskEvidence: [
          {
            assessmentId: firstAssessment,
            transactionId,
            ruleId,
            ruleName: 'Card not present high value',
            triggeredAt: '2026-08-28T09:15:01Z',
            scoreContribution: 12.5,
          },
          {
            assessmentId: secondAssessment,
            transactionId,
            ruleId,
            ruleName: 'Card not present high value',
            triggeredAt: '2026-08-28T09:16:01Z',
            scoreContribution: 7.5,
          },
        ],
      }),
    })
  })

  await page.goto('/')
  await page.getByLabel('Customer ID').fill(seededCustomer)
  await page.getByRole('button', { name: 'Search' }).click()

  await expect(page.getByTestId(`risk-evidence-${firstAssessment}`)).toBeVisible()
  await expect(page.getByTestId(`risk-evidence-${secondAssessment}`)).toBeVisible()
  await expect(page.getByTestId('risk-evidence').getByText('Card not present high value')).toHaveCount(2)
})
