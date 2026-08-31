import { expect, test } from '@playwright/test'

const seededCustomer = '11111111-1111-1111-1111-111111111111'
const unknownCustomer = '99999999-9999-9999-9999-999999999999'

type Activity = {
  transactionId: string
  type: 'CARD' | 'PAYMENT' | 'CRYPTO'
  amount: number | string
  currency: string
  status: string
  createdAt: string
}

type RiskEvidence = {
  transactionId: string
  ruleId: string
  ruleName: string
  triggeredAt: string
  scoreContribution: number
}

test('VFY-CUSTOMER-READ-001 deployed R1 customer review', async ({ page }, testInfo) => {
  await page.goto('/')
  await expect(page.getByRole('heading', { name: 'Customer Activity Analytics' })).toBeVisible()

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

  await expect(page.getByRole('columnheader', { name: 'Transaction' })).toBeVisible()
  await expect(page.getByRole('columnheader', { name: 'Amount' })).toBeVisible()
  await expect(page.getByRole('columnheader', { name: 'Currency' })).toBeVisible()
  await expect(page.getByRole('columnheader', { name: 'Status' })).toBeVisible()
  await expect(page.getByRole('columnheader', { name: 'Time' })).toBeVisible()

  const card = snapshot.activities.find((activity: Activity) => activity.type === 'CARD') as Activity
  expect(card).toBeTruthy()
  await expect(page.getByTestId('activity-card-transaction')).toHaveText(card.transactionId)
  await expect(page.getByTestId('activity-card-amount')).toHaveAttribute('data-amount', String(card.amount))
  await expect(page.getByTestId('activity-card-amount')).not.toContainText(card.currency)
  await expect(page.getByTestId('activity-card-currency')).toHaveText(card.currency)
  await expect(page.getByTestId('activity-card-status')).toContainText(card.status)
  await expect(page.getByTestId('activity-card-time')).toBeVisible()
  await expect(page.getByTestId('activity-card-time')).toHaveAttribute('data-created-at', card.createdAt)

  await expect(page.getByTestId('activity-card')).toContainText('Alpine Camera')
  await expect(page.getByTestId('activity-payment')).toContainText('receiverBankCountry: DE')
  await expect(page.getByTestId('activity-crypto')).toContainText('blockchain: Bitcoin')

  for (const evidence of snapshot.riskEvidence as RiskEvidence[]) {
    const item = page.getByTestId(`risk-evidence-${evidence.transactionId}-${evidence.ruleId}`)
    await expect(item).toBeVisible()
    await expect(item).toContainText(evidence.ruleName)
    await expect(item).toContainText(evidence.ruleId)
    await expect(item).toContainText(evidence.transactionId)
    await expect(item).toContainText(`+${evidence.scoreContribution}`)
    await expect(page.getByTestId(`risk-evidence-${evidence.transactionId}-${evidence.ruleId}-time`))
      .toHaveAttribute('data-triggered-at', evidence.triggeredAt)
  }

  await page.screenshot({ path: testInfo.outputPath('r1-customer-review.png'), fullPage: true })

  await customerId.fill(unknownCustomer)
  const notFoundPromise = page.waitForResponse(response =>
    response.url().endsWith(`/api/customers/${unknownCustomer}`) && response.request().method() === 'GET')
  await page.getByRole('button', { name: 'Search' }).click()
  expect((await notFoundPromise).status()).toBe(404)
  await expect(page.getByRole('alert')).toContainText('Customer not found')
})
