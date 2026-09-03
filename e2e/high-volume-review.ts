import { expect, Page } from '@playwright/test'

const denseCustomer = '55555555-5555-5555-5555-555555555555'
const denseCustomerReviewUrl = new RegExp(`/api/customers/${denseCustomer}(?:\\?.*)?$`)

function activity(index: number, status = index % 2 === 0 ? 'Completed' : 'Declined') {
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

export async function runHighVolumeReviewScenario(page: Page) {
  await page.route(denseCustomerReviewUrl, async route => {
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

  await page.goto('/')
  const customerId = page.getByLabel('Customer ID')
  await customerId.fill(denseCustomer)

  const firstRequest = page.waitForRequest(request =>
    request.url().endsWith(`/api/customers/${denseCustomer}`) && request.method() === 'GET')
  await page.getByRole('button', { name: 'Search' }).click()
  await firstRequest

  await expect(page.getByTestId('customer-activity')).toContainText('250 matching activities')
  await expect(page.locator('[data-testid="activity-card"]')).toHaveCount(50)
  await expect(page.getByTestId('activity-pagination')).toContainText('1–50 of 250')

  const secondPageRequest = page.waitForRequest(request => {
    const url = new URL(request.url())
    return url.pathname === `/api/customers/${denseCustomer}` && url.searchParams.get('page') === '1'
  })
  await page.getByLabel('Go to next page').click()
  await secondPageRequest
  await expect(page.getByTestId('activity-pagination')).toContainText('51–100 of 250')
  await expect(page.locator('[data-testid="activity-card"]')).toHaveCount(50)

  await page.getByLabel('Status').fill('Completed')
  const filteredRequest = page.waitForRequest(request => {
    const url = new URL(request.url())
    return url.pathname === `/api/customers/${denseCustomer}`
      && url.searchParams.get('status') === 'Completed'
      && url.searchParams.get('page') === null
  })
  await page.getByRole('button', { name: 'Search' }).click()
  await filteredRequest
  await expect(page.getByTestId('customer-activity')).toContainText('125 matching activities')
  await expect(page.getByTestId('activity-pagination')).toContainText('1–50 of 125')
  await expect(page.locator('[data-testid="activity-card-status"]')).toHaveCount(50)
  await expect(page.locator('[data-testid="activity-card-status"]')).toHaveText(Array(50).fill('Completed'))
}
