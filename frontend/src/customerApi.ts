/**
 * Browser transport contracts and HTTP helpers for Customer Activity Analytics.
 *
 * @remarks
 * This module mirrors the deployed HTTP shapes without becoming their authority.
 * It keeps transport, pagination compatibility, CSRF handling and exact-decimal
 * formatting outside the React composition component so those behaviors can be
 * verified independently. See FR-ACT-001, FR-AUTH-001, FR-HIST-002, FR-RAG-001,
 * NFR-SEC-001 and ADR-004.
 *
 * @module
 */

/** Activity transport shape preserved as source evidence, including decimal text and type-specific details. */
export type Activity = {
  transactionId: string
  type: 'CARD' | 'PAYMENT' | 'CRYPTO'
  amount: string
  currency: string
  status: string
  createdAt: string
  details: Record<string, string | boolean | null>
}

/** Source-system assessment associated with one transaction; it is not a detector or model conclusion. */
export type RiskEvidence = {
  assessmentId: string
  transactionId: string
  ruleId: string
  ruleName: string
  triggeredAt: string
  scoreContribution: number
}

/** Policy passage retained with retrieval metadata so a reviewer can inspect grounding. */
export type PolicyEvidence = {
  sourceIdentity: string
  content: string
  retrievalMetadata: Record<string, string>
}

/** One Stage-1 detector artifact whose score remains specific to its detector semantics. */
export type DetectorProvenance = {
  detectorIdentity: string
  signalIdentity: string
  score: number
  provenance: Record<string, string>
}

/** Stable reference from model provenance back to one bounded input-evidence class. */
export type EvidenceReference = {
  kind: 'ACTIVITY' | 'SOURCE_RISK' | 'DETECTOR_SIGNAL' | 'POLICY_RETRIEVAL'
  evidenceIdentity: string
}

/** Stage-3 execution identity and evidence references, including external-transmission disclosure. */
export type ModelProvenance = {
  backendIdentity: string
  modelIdentity: string
  promptIdentity: string
  evidenceReferences: EvidenceReference[]
  metadata: Record<string, string>
}

/** Completed, retained advisory analysis; failure responses never use this success shape. */
export type Analysis = {
  analysisId: string
  customerId: string
  operatorId: string
  generatedAt: string
  riskLevel: 'LOW' | 'MEDIUM' | 'HIGH'
  findingsSummary: string
  recommendations: string[]
  evidenceProvenance: PolicyEvidence[]
  detectorProvenance: DetectorProvenance[]
  modelProvenance: ModelProvenance
}

/** Bounded operator page with page-scoped risk evidence and server-owned totals. */
export type CustomerSnapshot = {
  customerId: string
  activities: Activity[]
  riskEvidence: RiskEvidence[]
  page: number
  pageSize: number
  totalActivities: number
  totalRiskEvidence: number
  totalPages: number
  hasPrevious: boolean
  hasNext: boolean
}

/** Immutable submitted customer query, separated from editable controls to make refetch identity explicit. */
export type Request = {
  customerId: string
  submission: number
  page: number
  pageSize: number
  activityType: '' | Activity['type']
  status: string
  createdFrom: string
  createdTo: string
}

/** Historical array response combined with pagination metadata transported in HTTP headers. */
export type AnalysisHistoryPage = {
  entries: Analysis[]
  page: number
  pageSize: number
  totalEntries: number
  totalPages: number
  hasPrevious: boolean
  hasNext: boolean
}

/** Server-issued CSRF material; the server controls the header name and token. */
export type CsrfView = {
  headerName: string
  parameterName: string
  token: string
}

/** Secured session variant that permits protected reviewer capabilities. */
export type AuthenticatedSession = {
  state: 'AUTHENTICATED'
  operatorId: string
  csrf: CsrfView
}

/** Secured runtime bootstrap state that exposes only the login flow and its CSRF token. */
export type UnauthenticatedSession = {
  state: 'UNAUTHENTICATED'
  csrf: CsrfView
}

/** Exhaustive secured-session state returned by the public bootstrap endpoint. */
export type SecuritySession = AuthenticatedSession | UnauthenticatedSession
/** Compatibility boundary: pre-security rings lack `/api/session`, while secured rings fail closed. */
export type RuntimeSession = { kind: 'LEGACY' } | { kind: 'SECURED'; session: SecuritySession }
/** Form login command carrying the server-issued CSRF material. */
export type LoginRequest = { username: string; password: string; csrf: CsrfView }
/** Analysis command; CSRF is required only when the runtime activated security. */
export type RunAnalysisRequest = { customerId: string; csrf?: CsrfView }
/** Seeded demonstration families remain visibly non-authoritative and replayable. */
export type ScenarioFamily = 'ORDINARY_LOCAL' | 'CROSS_BORDER_GROWTH' | 'MIXED_RED_FLAGS'
/** Server-confirmed identity and provenance for one materialized demonstration scenario. */
export type GeneratedScenario = {
  customerId: string
  seed: string
  family: ScenarioFamily
  generatorIdentity: string
  activityCount: number
  riskEvidenceCount: number
}
/** Demo generation command; string transport preserves the complete signed 64-bit seed. */
export type GenerateScenarioRequest = { seed: string; family: ScenarioFamily; requestId: number; csrf?: CsrfView }

/** Repository-owned seed that makes the first reviewer interaction immediately demonstrable. */
export const SEEDED_CUSTOMER = '11111111-1111-1111-1111-111111111111'
/** Matches the HTTP default while keeping each rendered activity page bounded. */
export const DEFAULT_ACTIVITY_PAGE_SIZE = 50
/** Matches the retained-history HTTP default independently from activity pagination. */
export const DEFAULT_HISTORY_PAGE_SIZE = 20

/** Formats exact decimal transport text without first coercing it through binary floating point. */
export function formatAmount(amount: string) {
  const match = /^(-?)(\d+)(?:\.(\d+))?$/.exec(amount)
  if (!match) return amount
  const [, sign, integer, fraction = ''] = match
  const grouped = integer.replace(/\B(?=(\d{3})+(?!\d))/g, "'")
  return `${sign}${grouped}.${fraction.padEnd(2, '0')}`
}

/** Converts a populated local date-time control to the instant expected by the HTTP contract. */
export function optionalInstant(value: string) {
  return value ? new Date(value).toISOString() : ''
}

/** Bootstraps security state; only an absent legacy endpoint enables compatibility mode. */
export async function loadRuntimeSession(): Promise<RuntimeSession> {
  const response = await fetch('/api/session', { credentials: 'same-origin' })
  if (response.status === 404) return { kind: 'LEGACY' }
  if (!response.ok) throw new Error(`Session request failed (${response.status})`)
  return { kind: 'SECURED', session: await response.json() as SecuritySession }
}

/** Establishes an operator session using form semantics and explicit CSRF protection. */
export async function loginOperator(request: LoginRequest): Promise<void> {
  const response = await fetch('/api/session/login', {
    method: 'POST',
    credentials: 'same-origin',
    headers: {
      'Content-Type': 'application/x-www-form-urlencoded',
      [request.csrf.headerName]: request.csrf.token,
    },
    body: new URLSearchParams({ username: request.username, password: request.password }),
  })
  if (response.status === 401) throw new Error('Invalid username or password')
  if (!response.ok) throw new Error(`Login failed (${response.status})`)
}

/** Invalidates the current operator session through the protected logout command. */
export async function logoutOperator(csrf: CsrfView): Promise<void> {
  const response = await fetch('/api/session/logout', {
    method: 'POST',
    credentials: 'same-origin',
    headers: { [csrf.headerName]: csrf.token },
  })
  if (!response.ok) throw new Error(`Logout failed (${response.status})`)
}

/** Builds the bounded customer-review URL while retaining the historical unfiltered first-page path. */
export function customerUrl(request: Request) {
  const base = `/api/customers/${request.customerId}`
  const params = new URLSearchParams()
  if (request.page !== 0) params.set('page', String(request.page))
  if (request.pageSize !== DEFAULT_ACTIVITY_PAGE_SIZE) params.set('pageSize', String(request.pageSize))
  if (request.activityType) params.set('type', request.activityType)
  if (request.status.trim()) params.set('status', request.status.trim())
  if (request.createdFrom) params.set('createdFrom', optionalInstant(request.createdFrom))
  if (request.createdTo) params.set('createdTo', optionalInstant(request.createdTo))
  const query = params.toString()
  return query ? `${base}?${query}` : base
}

/** Loads one operator page and maps expected client-actionable failures to stable messages. */
export async function loadCustomer(request: Request): Promise<CustomerSnapshot> {
  const response = await fetch(customerUrl(request))
  if (response.status === 404) throw new Error('Customer not found')
  if (response.status === 400) throw new Error('Invalid customer activity filters')
  if (!response.ok) throw new Error(`Customer request failed (${response.status})`)
  return response.json()
}

/** Reconstructs the history page from its compatible array body and pagination headers. */
export async function loadAnalysisHistory(
  customerId: string,
  page: number,
  pageSize: number,
): Promise<AnalysisHistoryPage> {
  const base = `/api/customers/${customerId}/analyses`
  const params = new URLSearchParams()
  if (page !== 0) params.set('page', String(page))
  if (pageSize !== DEFAULT_HISTORY_PAGE_SIZE) params.set('pageSize', String(pageSize))
  const query = params.toString()
  const response = await fetch(query ? `${base}?${query}` : base)
  if (!response.ok) throw new Error(`Analysis history request failed (${response.status})`)
  const entries = await response.json() as Analysis[]
  const headerNumber = (name: string, fallback: number) => {
    const raw = response.headers.get(name)
    if (raw === null || raw.trim() === '') return fallback
    const value = Number(raw)
    return Number.isFinite(value) ? value : fallback
  }
  return {
    entries,
    page: headerNumber('X-Page', page),
    pageSize: headerNumber('X-Page-Size', pageSize),
    totalEntries: headerNumber('X-Total-Count', entries.length),
    totalPages: headerNumber('X-Total-Pages', entries.length === 0 ? 0 : 1),
    hasPrevious: response.headers.get('X-Has-Previous') === 'true',
    hasNext: response.headers.get('X-Has-Next') === 'true',
  }
}

/** Runs the protected advisory workflow and preserves the bounded public failure reason for the operator. */
export async function runAnalysis(request: RunAnalysisRequest): Promise<Analysis> {
  const headers = request.csrf ? { [request.csrf.headerName]: request.csrf.token } : undefined
  const response = await fetch(`/api/customers/${request.customerId}/analyses`, { method: 'POST', headers })
  if (!response.ok) {
    const problem = await response.json().catch(() => null) as { detail?: string; reason?: string } | null
    const detail = problem?.detail ?? `Analysis request failed (${response.status})`
    throw new Error(problem?.reason ? `${detail} [${problem.reason}]` : detail)
  }
  return response.json()
}

/** Materializes one optional replayable story without changing the fixed regression catalogue. */
export async function generateScenario(request: GenerateScenarioRequest): Promise<GeneratedScenario> {
  const headers: Record<string, string> = { 'Content-Type': 'application/json' }
  if (request.csrf) headers[request.csrf.headerName] = request.csrf.token
  const response = await fetch('/api/demo/scenarios', {
    method: 'POST',
    headers,
    body: JSON.stringify({ seed: request.seed, family: request.family }),
  })
  if (response.status === 404) throw new Error('Optional scenario generation is disabled in this runtime')
  if (response.status === 400) throw new Error('Seed must be a signed 64-bit integer')
  if (!response.ok) throw new Error(`Scenario generation failed (${response.status})`)
  return response.json()
}

/** Discovers the optional runtime capability without coupling visibility to a delivery-ring label. */
export async function loadScenarioAvailability() {
  const response = await fetch('/api/demo/scenarios', { method: 'OPTIONS', credentials: 'same-origin' })
  if (response.status === 404 || response.status === 405) return false
  if (!response.ok) throw new Error(`Scenario availability request failed (${response.status})`)
  return response.headers.get('Allow')?.split(',').some(method => method.trim() === 'POST') === true
}
