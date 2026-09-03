import { expect, test } from '@playwright/test'

const baselineUrl = process.env.R4_BASELINE_URL ?? 'http://127.0.0.1:8084'
const bayesianUrl = process.env.R4_BAYESIAN_URL ?? 'http://127.0.0.1:8085'

async function signIn(
  page: import('@playwright/test').Page,
  baseUrl: string,
  operatorId: string,
  password: string,
) {
  await page.goto(baseUrl)
  await page.getByLabel('Operator ID').fill(operatorId)
  await page.getByLabel('Password').fill(password)
  await page.getByRole('button', { name: 'Sign in' }).click()
  await expect(page.getByTestId('operator-session')).toContainText(operatorId)
}

test('VFY-AUTH-001 prove R4 reviewer variants keep independent sessions in one browser context', async ({ browser }) => {
  const context = await browser.newContext()
  const page = await context.newPage()

  await signIn(page, baselineUrl, 'operator-alpha', 'alpha-demo-2026')
  let cookies = await context.cookies(baselineUrl)
  expect(cookies.some(cookie => cookie.name === 'SPECGRAPH_R4_BASELINE_SESSION')).toBe(true)
  expect(cookies.some(cookie => cookie.name === 'SPECGRAPH_R4_BAYESIAN_SESSION')).toBe(false)

  await signIn(page, bayesianUrl, 'operator-beta', 'beta-demo-2026')
  cookies = await context.cookies(bayesianUrl)
  expect(cookies.some(cookie => cookie.name === 'SPECGRAPH_R4_BASELINE_SESSION')).toBe(true)
  expect(cookies.some(cookie => cookie.name === 'SPECGRAPH_R4_BAYESIAN_SESSION')).toBe(true)

  await page.goto(baselineUrl)
  await expect(page.getByTestId('operator-session')).toContainText('operator-alpha')

  await page.goto(bayesianUrl)
  await expect(page.getByTestId('operator-session')).toContainText('operator-beta')

  await context.close()
})
