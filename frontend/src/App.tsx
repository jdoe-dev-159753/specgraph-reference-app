/**
 * Operator-facing customer review and grounded-analysis workflow.
 *
 * @remarks
 * The module mirrors the HTTP shapes in `openapi.yaml` without becoming their
 * authority. It keeps editable filters separate from submitted queries, gates
 * protected content on the runtime session, prevents duplicate customer and
 * analysis submissions, and renders source risk, detector, retrieval, and model
 * provenance as distinct evidence layers. See `FR-ACT-001`, `FR-AUTH-001`,
 * `FR-HIST-002`, `FR-RAG-001`, `NFR-SEC-001`, and ADR-004.
 *
 * @module
 */
import { FormEvent, useRef, useState } from 'react'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import {
  Alert, Box, Button, Chip, Container, List, ListItem, MenuItem, Paper, Stack, Table, TableBody,
  TableCell, TableContainer, TableHead, TablePagination, TableRow, TextField, Typography,
} from '@mui/material'

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

/** Repository-owned seed that makes the first reviewer interaction immediately demonstrable. */
const SEEDED_CUSTOMER = '11111111-1111-1111-1111-111111111111'
/** Matches the HTTP default while keeping each rendered activity page bounded. */
const DEFAULT_ACTIVITY_PAGE_SIZE = 50
/** Matches the retained-history HTTP default independently from activity pagination. */
const DEFAULT_HISTORY_PAGE_SIZE = 20

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

/** Renders retained Stage-2 passages and retrieval identity without presenting them as source risk. */
export function GroundingEvidence({ evidence }: { evidence: PolicyEvidence[] }) {
  if (evidence.length === 0) {
    return <Typography color="text.secondary">No policy grounding evidence retained.</Typography>
  }

  return (
    <Stack spacing={1.5} data-testid="analysis-grounding-evidence">
      {evidence.map(item => {
        const metadata = item.retrievalMetadata
        const sourceDocument = metadata.sourceDocument ?? item.sourceIdentity
        const chunkPosition = metadata.chunkIndex
          ? `chunk ${metadata.chunkIndex}${metadata.totalChunks ? `/${metadata.totalChunks}` : ''}`
          : null
        const similarity = metadata.similarityScore
        return (
          <Paper
            key={item.sourceIdentity}
            variant="outlined"
            data-testid={`policy-evidence-${item.sourceIdentity}`}
            sx={{ p: 1.5 }}
          >
            <Stack direction="row" spacing={1} useFlexGap sx={{ mb: 1, flexWrap: 'wrap' }}>
              {metadata.adapter && <Chip label={`adapter: ${metadata.adapter}`} size="small" variant="outlined" />}
              {metadata.corpus && <Chip label={`corpus: ${metadata.corpus}`} size="small" variant="outlined" />}
              {metadata.revision && <Chip label={`revision: ${metadata.revision}`} size="small" variant="outlined" />}
              {metadata.embeddingModel && <Chip label={`embeddingModel: ${metadata.embeddingModel}`} size="small" variant="outlined" />}
              {similarity && <Chip label={`similarity: ${similarity}`} size="small" variant="outlined" />}
            </Stack>
            <Typography variant="body2" sx={{ fontWeight: 600 }}>
              {sourceDocument}{chunkPosition ? ` · ${chunkPosition}` : ''}
            </Typography>
            <Typography variant="body2" sx={{ mt: 0.75 }}>{item.content}</Typography>
            <Typography
              variant="caption"
              color="text.secondary"
              sx={{ mt: 0.75, display: 'block', fontFamily: 'monospace', overflowWrap: 'anywhere' }}
            >
              {item.sourceIdentity}
              {metadata.embeddingModel ? ` · embedding ${metadata.embeddingModel}` : ''}
            </Typography>
          </Paper>
        )
      })}
    </Stack>
  )
}

/** Keeps heterogeneous Stage-1 artifacts separate instead of aggregating uncalibrated scores in the UI. */
export function DetectorArtifacts({ artifacts }: { artifacts: DetectorProvenance[] }) {
  return (
    <Box data-testid="analysis-detector-provenance">
      <Typography variant="subtitle2">Stage 1 · Detector artifacts</Typography>
      {artifacts.length === 0 ? (
        <Typography variant="body2" color="text.secondary" sx={{ mt: 0.5 }}>
          No detector artifact was retained for this analysis.
        </Typography>
      ) : (
        <Stack spacing={1} sx={{ mt: 0.75 }}>
          {artifacts.map((artifact, index) => (
            <Paper
              key={`${artifact.detectorIdentity}:${artifact.signalIdentity}:${index}`}
              variant="outlined"
              data-testid={`analysis-detector-artifact-${index}`}
              sx={{ p: 1.25 }}
            >
              <Stack direction="row" spacing={1} useFlexGap sx={{ flexWrap: 'wrap' }}>
                <Chip label={`detector: ${artifact.detectorIdentity}`} size="small" variant="outlined" />
                <Chip label={`signal: ${artifact.signalIdentity}`} size="small" variant="outlined" />
                <Chip label={`score: ${artifact.score}`} size="small" variant="outlined" />
              </Stack>
              {Object.keys(artifact.provenance).length > 0 && (
                <Typography
                  variant="caption"
                  color="text.secondary"
                  sx={{ mt: 0.75, display: 'block', fontFamily: 'monospace', overflowWrap: 'anywhere' }}
                >
                  {Object.entries(artifact.provenance).map(([key, value]) => `${key}: ${value}`).join(' · ')}
                </Typography>
              )}
            </Paper>
          ))}
        </Stack>
      )}
    </Box>
  )
}

/** Makes Stage-3 backend/model/prompt identity and data-transmission posture reviewer-visible. */
export function ModelExecution({ provenance }: { provenance: ModelProvenance }) {
  const externalTransmission = provenance.metadata.externalTransmission
  const externalTransmissionLabel = externalTransmission === 'true'
    ? 'yes'
    : externalTransmission === 'false' ? 'no' : 'unknown'
  return (
    <Box data-testid="analysis-model-provenance">
      <Typography variant="subtitle2">Stage 3 · Model execution</Typography>
      <Stack direction="row" spacing={1} useFlexGap sx={{ mt: 0.75, flexWrap: 'wrap' }}>
        <Chip data-testid="analysis-model-backend" label={`backend: ${provenance.backendIdentity}`} size="small" variant="outlined" />
        <Chip data-testid="analysis-model-identity" label={`model: ${provenance.modelIdentity}`} size="small" variant="outlined" />
        <Chip data-testid="analysis-prompt-identity" label={`prompt: ${provenance.promptIdentity}`} size="small" variant="outlined" />
        <Chip
          data-testid="analysis-external-transmission"
          label={`external transmission: ${externalTransmissionLabel}`}
          size="small"
          color={externalTransmission === 'true' ? 'warning' : externalTransmission === 'false' ? 'success' : 'default'}
          variant="outlined"
        />
      </Stack>
    </Box>
  )
}

/** Presents the three analysis stages in execution order while preserving their evidence boundaries. */
export function AnalysisProvenance({ analysis }: { analysis: Analysis }) {
  return (
    <Stack spacing={2} data-testid="analysis-provenance">
      <DetectorArtifacts artifacts={analysis.detectorProvenance} />
      <Box data-testid="analysis-grounding-provenance">
        <Typography variant="subtitle2" sx={{ mb: 1 }}>Stage 2 · Policy grounding</Typography>
        <GroundingEvidence evidence={analysis.evidenceProvenance} />
      </Box>
      <ModelExecution provenance={analysis.modelProvenance} />
    </Stack>
  )
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
  /** Treats missing or malformed optional pagination headers as compatibility fallbacks, never numeric zero. */
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

/** Maps the bounded demonstration risk vocabulary to a consistent visual severity. */
export function RiskLevelChip({ level }: { level: Analysis['riskLevel'] }) {
  const color = level === 'HIGH' ? 'error' : level === 'MEDIUM' ? 'warning' : 'success'
  return <Chip label={level} color={color} size="small" data-testid="analysis-risk-level" />
}

/**
 * Composes session, customer review, analysis, and history into one operator workflow.
 *
 * @remarks
 * Draft controls do not fetch until submitted. Query keys own server-state identity;
 * refs synchronously reject repeat clicks before React Query updates pending state.
 * Logout clears protected cached data before exposing the login view. Analysis
 * success resets history to its newest page and invalidates only that customer's
 * history. These are observable security and recoverability guarantees, not
 * presentation conveniences.
 */
export default function App() {
  /** Editable customer identifier; it cannot trigger I/O until copied into `request`. */
  const [customerId, setCustomerId] = useState(SEEDED_CUSTOMER)
  /** Last submitted customer query and pagination identity, or null before/after protected review. */
  const [request, setRequest] = useState<Request | null>(null)
  /** Draft activity-type filter retained separately from the submitted request. */
  const [activityType, setActivityType] = useState<Request['activityType']>('')
  /** Draft status filter; trimming occurs only when the submitted URL is built. */
  const [activityStatus, setActivityStatus] = useState('')
  /** Draft lower time bound in browser-local input form. */
  const [createdFrom, setCreatedFrom] = useState('')
  /** Draft upper time bound in browser-local input form. */
  const [createdTo, setCreatedTo] = useState('')
  /** Independently selected retained-history page for the currently loaded customer. */
  const [historyPage, setHistoryPage] = useState(0)
  /** History page size bounded by the HTTP contract, independent from activity pagination. */
  const [historyPageSize, setHistoryPageSize] = useState(DEFAULT_HISTORY_PAGE_SIZE)
  /** Ephemeral login identity; never copied into retained customer or analysis state. */
  const [username, setUsername] = useState('')
  /** Ephemeral password cleared after successful login. */
  const [password, setPassword] = useState('')
  /** Monotonic discriminator that makes otherwise identical explicit searches distinct query keys. */
  const submission = useRef(0)
  /** Synchronous duplicate-click guard released by the customer query's `finally` path. */
  const customerSubmissionInFlight = useRef(false)
  /** Synchronous duplicate-analysis guard released on settle and explicitly across logout. */
  const analysisSubmissionInFlight = useRef(false)
  /** Shared cache authority used for scoped invalidation and protected-data removal. */
  const queryClient = useQueryClient()

  /** Session bootstrap is never retried implicitly because security failures require explicit evidence. */
  const runtimeSession = useQuery({
    queryKey: ['runtime-session'],
    queryFn: loadRuntimeSession,
    retry: false,
  })
  /** Derived exhaustive session variants keep authorization decisions out of ad-hoc truthiness checks. */
  const authenticatedSession = runtimeSession.data?.kind === 'SECURED' && runtimeSession.data.session.state === 'AUTHENTICATED'
    ? runtimeSession.data.session
    : null
  /** Login is rendered only for an explicit secured-runtime unauthenticated state, never during bootstrap ambiguity. */
  const unauthenticatedSession = runtimeSession.data?.kind === 'SECURED' && runtimeSession.data.session.state === 'UNAUTHENTICATED'
    ? runtimeSession.data.session
    : null
  /** Protected workspace gate: legacy compatibility or positively authenticated secured session. */
  const applicationEnabled = runtimeSession.data?.kind === 'LEGACY' || authenticatedSession !== null

  /** Submitted customer server state; disabled until both session and request gates are satisfied. */
  const customer = useQuery({
    queryKey: ['customer', request],
    queryFn: async () => {
      try {
        return await loadCustomer(request!)
      } finally {
        customerSubmissionInFlight.current = false
      }
    },
    enabled: applicationEnabled && request !== null,
    retry: false,
  })
  /** Server-confirmed identity prevents history requests from following an unsubmitted or failed draft identifier. */
  const selectedCustomerId = customer.data?.customerId ?? null
  /** Retained history is scoped to the server-confirmed customer, not the editable identifier. */
  const history = useQuery({
    queryKey: ['analysis-history', selectedCustomerId, historyPage, historyPageSize],
    queryFn: () => loadAnalysisHistory(selectedCustomerId!, historyPage, historyPageSize),
    enabled: applicationEnabled && selectedCustomerId !== null,
    retry: false,
  })
  /** Successful analysis returns history to newest-first page zero before scoped invalidation. */
  const analysis = useMutation({
    mutationFn: runAnalysis,
    onSuccess: async (_completed, analyzed) => {
      setHistoryPage(0)
      await queryClient.invalidateQueries({ queryKey: ['analysis-history', analyzed.customerId] })
    },
  })
  /** Login refreshes the session authority and erases the password after success. */
  const login = useMutation({
    mutationFn: loginOperator,
    onSuccess: async () => {
      setPassword('')
      await queryClient.invalidateQueries({ queryKey: ['runtime-session'] })
    },
  })
  /** Logout removes protected customer/history state and releases synchronous guards before re-bootstrap. */
  const logout = useMutation({
    mutationFn: logoutOperator,
    onSuccess: async () => {
      customerSubmissionInFlight.current = false
      analysisSubmissionInFlight.current = false
      setRequest(null)
      setHistoryPage(0)
      analysis.reset()
      queryClient.removeQueries({ queryKey: ['customer'] })
      queryClient.removeQueries({ queryKey: ['analysis-history'] })
      await queryClient.invalidateQueries({ queryKey: ['runtime-session'] })
    },
  })

  /** Freezes draft filters into a new page-zero request and rejects rapid duplicate submissions. */
  function submit(event: FormEvent) {
    event.preventDefault()
    if (customerSubmissionInFlight.current) return
    customerSubmissionInFlight.current = true
    analysisSubmissionInFlight.current = false
    analysis.reset()
    setHistoryPage(0)
    submission.current += 1
    setRequest({
      customerId,
      submission: submission.current,
      page: 0,
      pageSize: DEFAULT_ACTIVITY_PAGE_SIZE,
      activityType,
      status: activityStatus,
      createdFrom,
      createdTo,
    })
  }

  /** Changes only submitted paging state and returns to server-owned bounded retrieval. */
  function updateActivityPage(page: number, pageSize = request?.pageSize ?? DEFAULT_ACTIVITY_PAGE_SIZE) {
    if (!request || customerSubmissionInFlight.current) return
    customerSubmissionInFlight.current = true
    submission.current += 1
    setRequest({ ...request, page, pageSize, submission: submission.current })
  }

  /** Refuses login without the unauthenticated session variant that owns valid CSRF material. */
  function submitLogin(event: FormEvent) {
    event.preventDefault()
    if (!unauthenticatedSession) return
    login.mutate({ username, password, csrf: unauthenticatedSession.csrf })
  }

  /** Runs analysis only for a server-loaded customer and releases its guard on every terminal outcome. */
  function submitAnalysis() {
    if (!customer.data || analysisSubmissionInFlight.current) return
    analysisSubmissionInFlight.current = true
    analysis.mutate({
      customerId: customer.data.customerId,
      csrf: authenticatedSession?.csrf,
    }, {
      onSettled: () => {
        analysisSubmissionInFlight.current = false
      },
    })
  }

  /** Reviewer-facing maturity label follows the immutable delivery-ring build argument for secured runtimes. */
  const securedRuntimeLabel = import.meta.env.VITE_DELIVERY_RING === 'R5' ? 'R5' : 'R4'
  /** Anonymous and deterministic operation remains identified as the R3 boundary. */
  const runtimeLabel = runtimeSession.data?.kind === 'SECURED' ? securedRuntimeLabel : 'R3'

  return (
    <Container maxWidth="lg" sx={{ py: { xs: 3, md: 5 } }}>
      <Stack spacing={3}>
        <Box>
          <Box sx={{ width: 52, height: 4, bgcolor: 'secondary.main', mb: 2 }} />
          <Stack direction={{ xs: 'column', md: 'row' }} spacing={2} sx={{ justifyContent: 'space-between', alignItems: { md: 'flex-start' } }}>
            <Box>
              <Typography variant="overline" color="text.secondary" sx={{ letterSpacing: '0.14em', fontWeight: 700 }}>
                Customer Care · {runtimeLabel}
              </Typography>
              <Typography variant="h3" component="h1" sx={{ mt: 0.25 }}>Customer Activity Analytics</Typography>
              <Typography color="text.secondary" sx={{ mt: 1, maxWidth: 820 }}>
                PostgreSQL-backed customer evidence with grounded analysis, inspectable provenance and reviewable history.
              </Typography>
            </Box>
            {authenticatedSession && (
              <Stack direction="row" spacing={1} sx={{ alignItems: 'center' }} data-testid="operator-session">
                <Chip label={authenticatedSession.operatorId} size="small" color="primary" />
                <Button
                  size="small"
                  variant="outlined"
                  disabled={logout.isPending}
                  onClick={() => logout.mutate(authenticatedSession.csrf)}
                >
                  {logout.isPending ? 'Signing out…' : 'Sign out'}
                </Button>
              </Stack>
            )}
          </Stack>
        </Box>

        {runtimeSession.isFetching && <Typography>Loading operator session…</Typography>}
        {runtimeSession.error && <Alert severity="error">{runtimeSession.error.message}</Alert>}
        {logout.error && <Alert severity="error">{logout.error.message}</Alert>}

        {unauthenticatedSession && (
          <Paper component="form" onSubmit={submitLogin} data-testid="operator-login" sx={{ p: { xs: 2, md: 3 }, maxWidth: 560 }}>
            <Typography variant="h5">Operator sign in</Typography>
            <Typography variant="body2" color="text.secondary" sx={{ mt: 0.5 }}>
              Demo identities are local to this reference application. Try operator-alpha / alpha-demo-2026 or operator-beta / beta-demo-2026.
            </Typography>
            <Stack spacing={2} sx={{ mt: 2 }}>
              <TextField
                label="Operator ID"
                value={username}
                onChange={event => setUsername(event.target.value)}
                autoComplete="username"
                size="small"
              />
              <TextField
                label="Password"
                type="password"
                value={password}
                onChange={event => setPassword(event.target.value)}
                autoComplete="current-password"
                size="small"
              />
              {login.error && <Alert severity="error">{login.error.message}</Alert>}
              <Button type="submit" variant="contained" disabled={login.isPending || !username || !password}>
                {login.isPending ? 'Signing in…' : 'Sign in'}
              </Button>
            </Stack>
          </Paper>
        )}

        {applicationEnabled && (
          <>
            <Paper component="form" onSubmit={submit} sx={{ p: { xs: 2, md: 3 } }}>
              <Stack spacing={2}>
                <Stack direction={{ xs: 'column', md: 'row' }} spacing={2} sx={{ alignItems: 'stretch' }}>
                  <TextField
                    fullWidth
                    label="Customer ID"
                    value={customerId}
                    onChange={e => setCustomerId(e.target.value)}
                    size="small"
                  />
                  <Button
                    type="submit"
                    variant="contained"
                    disabled={customer.isFetching || analysis.isPending}
                    sx={{ minWidth: 128 }}
                  >
                    Search
                  </Button>
                </Stack>
                <Stack direction={{ xs: 'column', md: 'row' }} spacing={2} useFlexGap sx={{ flexWrap: 'wrap' }}>
                  <TextField
                    select
                    label="Activity type"
                    value={activityType}
                    onChange={event => setActivityType(event.target.value as Request['activityType'])}
                    size="small"
                    sx={{ minWidth: 160 }}
                  >
                    <MenuItem value="">All types</MenuItem>
                    <MenuItem value="CARD">CARD</MenuItem>
                    <MenuItem value="PAYMENT">PAYMENT</MenuItem>
                    <MenuItem value="CRYPTO">CRYPTO</MenuItem>
                  </TextField>
                  <TextField
                    label="Status"
                    value={activityStatus}
                    onChange={event => setActivityStatus(event.target.value)}
                    placeholder="Completed"
                    size="small"
                    sx={{ minWidth: 160 }}
                  />
                  <TextField
                    label="Created from"
                    type="datetime-local"
                    value={createdFrom}
                    onChange={event => setCreatedFrom(event.target.value)}
                    slotProps={{ inputLabel: { shrink: true } }}
                    size="small"
                  />
                  <TextField
                    label="Created to"
                    type="datetime-local"
                    value={createdTo}
                    onChange={event => setCreatedTo(event.target.value)}
                    slotProps={{ inputLabel: { shrink: true } }}
                    size="small"
                  />
                </Stack>
              </Stack>
            </Paper>

            {customer.isFetching && <Typography>Loading customer activity…</Typography>}
            {customer.error && <Alert severity="error">{customer.error.message}</Alert>}
            {customer.data && (
              <>
                <Paper data-testid="customer-activity" sx={{ overflow: 'hidden' }}>
                  <Box sx={{ px: 3, py: 2.5, borderBottom: 1, borderColor: 'divider' }}>
                    <Typography variant="h5">Customer activity</Typography>
                    <Typography variant="body2" color="text.secondary" sx={{ mt: 0.5 }}>
                      Customer {customer.data.customerId} · {customer.data.totalActivities} matching activit{customer.data.totalActivities === 1 ? 'y' : 'ies'}
                    </Typography>
                  </Box>
                  <TableContainer sx={{ maxHeight: 520 }}>
                    <Table stickyHeader size="small" aria-label="Customer activity">
                      <TableHead><TableRow>
                        <TableCell>Type</TableCell>
                        <TableCell>Transaction</TableCell>
                        <TableCell align="right">Amount</TableCell>
                        <TableCell>Currency</TableCell>
                        <TableCell>Status</TableCell>
                        <TableCell>Time</TableCell>
                        <TableCell>Type details</TableCell>
                      </TableRow></TableHead>
                      <TableBody>
                        {customer.data.activities.map(activity => {
                          const activityKey = activity.type.toLowerCase()
                          return (
                            <TableRow key={activity.transactionId} hover data-testid={`activity-${activityKey}`}>
                              <TableCell>
                                <Chip
                                  label={activity.type}
                                  size="small"
                                  variant="outlined"
                                  data-testid={`activity-${activityKey}-type`}
                                />
                              </TableCell>
                              <TableCell
                                data-testid={`activity-${activityKey}-transaction`}
                                sx={{ whiteSpace: 'nowrap', fontFamily: 'monospace' }}
                              >
                                {activity.transactionId}
                              </TableCell>
                              <TableCell
                                align="right"
                                data-testid={`activity-${activityKey}-amount`}
                                data-amount={activity.amount}
                                sx={{ fontVariantNumeric: 'tabular-nums', fontWeight: 600 }}
                              >
                                {formatAmount(activity.amount)}
                              </TableCell>
                              <TableCell data-testid={`activity-${activityKey}-currency`} sx={{ fontWeight: 600 }}>
                                {activity.currency}
                              </TableCell>
                              <TableCell data-testid={`activity-${activityKey}-status`}>
                                <Chip label={activity.status} size="small" />
                              </TableCell>
                              <TableCell
                                data-testid={`activity-${activityKey}-time`}
                                data-created-at={activity.createdAt}
                                sx={{ whiteSpace: 'nowrap' }}
                              >
                                {new Date(activity.createdAt).toLocaleString()}
                              </TableCell>
                              <TableCell sx={{ minWidth: 260, maxWidth: 420 }}>
                                {Object.entries(activity.details)
                                  .filter(([, value]) => value !== null)
                                  .map(([key, value]) => `${key}: ${String(value)}`)
                                  .join(' · ')}
                              </TableCell>
                            </TableRow>
                          )
                        })}
                      </TableBody>
                    </Table>
                  </TableContainer>
                  <TablePagination
                    component="div"
                    data-testid="activity-pagination"
                    count={customer.data.totalActivities}
                    page={customer.data.page}
                    rowsPerPage={customer.data.pageSize}
                    rowsPerPageOptions={[25, 50, 100, 200]}
                    onPageChange={(_event, page) => updateActivityPage(page)}
                    onRowsPerPageChange={event => updateActivityPage(0, Number(event.target.value))}
                  />
                </Paper>

                <Paper data-testid="risk-evidence" sx={{ overflow: 'hidden' }}>
                  <Box sx={{ px: 3, py: 2.5, borderBottom: 1, borderColor: 'divider', borderLeft: 4, borderLeftColor: 'secondary.main' }}>
                    <Typography variant="h5">Risk evidence</Typography>
                    <Typography variant="body2" color="text.secondary" sx={{ mt: 0.5 }}>
                      Source-shaped deterministic evidence associated with the current activity page ({customer.data.totalRiskEvidence} matching assessment{customer.data.totalRiskEvidence === 1 ? '' : 's'} overall).
                    </Typography>
                  </Box>
                  <List dense disablePadding>
                    {customer.data.riskEvidence.map(evidence => (
                      <ListItem
                        key={evidence.assessmentId}
                        data-testid={`risk-evidence-${evidence.assessmentId}`}
                        divider
                        sx={{ px: 3, py: 1.5, display: 'flex', justifyContent: 'space-between', gap: 2 }}
                      >
                        <Box>
                          <Typography variant="body2" sx={{ fontWeight: 600 }}>{evidence.ruleName}</Typography>
                          <Typography
                            variant="caption"
                            color="text.secondary"
                            data-testid={`risk-evidence-${evidence.assessmentId}-time`}
                            data-triggered-at={evidence.triggeredAt}
                          >
                            {evidence.ruleId} · transaction {evidence.transactionId} · {new Date(evidence.triggeredAt).toLocaleString()}
                          </Typography>
                        </Box>
                        <Chip label={`+${evidence.scoreContribution}`} size="small" color="primary" variant="outlined" />
                      </ListItem>
                    ))}
                  </List>
                </Paper>

                <Paper data-testid="analysis-workspace" sx={{ overflow: 'hidden' }}>
                  <Box sx={{ px: 3, py: 2.5, borderBottom: 1, borderColor: 'divider' }}>
                    <Stack
                      direction={{ xs: 'column', md: 'row' }}
                      spacing={2}
                      sx={{ justifyContent: 'space-between', alignItems: { md: 'center' } }}
                    >
                      <Box>
                        <Typography variant="h5">Grounded analysis</Typography>
                        <Typography variant="body2" color="text.secondary" sx={{ mt: 0.5 }}>
                          Analysis runs through the configured backend. Grounding and execution provenance remain inspectable and separate from source risk evidence.
                        </Typography>
                      </Box>
                      <Button
                        variant="contained"
                        color="secondary"
                        disabled={analysis.isPending}
                        onClick={submitAnalysis}
                      >
                        {analysis.isPending ? 'Analyzing…' : 'Run analysis'}
                      </Button>
                    </Stack>
                  </Box>

                  {analysis.error && <Alert severity="error" sx={{ m: 2 }}>{analysis.error.message}</Alert>}
                  {analysis.data && (
                    <Box data-testid="analysis-result" sx={{ px: 3, py: 2.5, borderBottom: 1, borderColor: 'divider' }}>
                      <Stack direction="row" spacing={1.5} sx={{ mb: 1.5, alignItems: 'center' }}>
                        <Typography variant="h6">Completed analysis</Typography>
                        <RiskLevelChip level={analysis.data.riskLevel} />
                      </Stack>
                      <Typography data-testid="analysis-findings">{analysis.data.findingsSummary}</Typography>
                      <Typography variant="subtitle2" sx={{ mt: 2 }}>Recommendations</Typography>
                      <List dense>
                        {analysis.data.recommendations.map(recommendation => (
                          <ListItem key={recommendation} sx={{ py: 0.25 }}>• {recommendation}</ListItem>
                        ))}
                      </List>
                      <Typography variant="caption" color="text.secondary">
                        Operator {analysis.data.operatorId} · {new Date(analysis.data.generatedAt).toLocaleString()}
                      </Typography>
                      <Box sx={{ mt: 2 }}>
                        <AnalysisProvenance analysis={analysis.data} />
                      </Box>
                    </Box>
                  )}

                  <Box sx={{ px: 3, py: 2.5 }}>
                    <Typography variant="h6">Analysis history</Typography>
                    {history.isFetching && <Typography sx={{ mt: 1 }}>Loading prior analyses…</Typography>}
                    {history.error && <Alert severity="error" sx={{ mt: 1 }}>{history.error.message}</Alert>}
                    {history.data?.entries.length === 0 && (
                      <Typography color="text.secondary" sx={{ mt: 1 }}>No completed analyses have been retained for this customer.</Typography>
                    )}
                    <List data-testid="analysis-history" disablePadding sx={{ mt: 1 }}>
                      {history.data?.entries.map(entry => (
                        <ListItem
                          key={entry.analysisId}
                          data-testid={`analysis-history-${entry.analysisId}`}
                          divider
                          sx={{ px: 0, py: 2, display: 'block' }}
                        >
                          <Stack direction="row" spacing={1.5} sx={{ alignItems: 'center' }}>
                            <RiskLevelChip level={entry.riskLevel} />
                            <Typography variant="body2" sx={{ fontWeight: 600 }}>{entry.operatorId}</Typography>
                            <Typography variant="caption" color="text.secondary">{new Date(entry.generatedAt).toLocaleString()}</Typography>
                          </Stack>
                          <Typography variant="body2" sx={{ mt: 1 }}>{entry.findingsSummary}</Typography>
                          <Typography variant="caption" color="text.secondary">
                            {entry.recommendations.join(' · ')}
                          </Typography>
                          <Box sx={{ mt: 1.5 }}>
                            <AnalysisProvenance analysis={entry} />
                          </Box>
                        </ListItem>
                      ))}
                    </List>
                    {history.data && (
                      <TablePagination
                        component="div"
                        data-testid="analysis-history-pagination"
                        count={history.data.totalEntries}
                        page={history.data.page}
                        rowsPerPage={history.data.pageSize}
                        rowsPerPageOptions={[10, 20, 50, 100]}
                        onPageChange={(_event, page) => setHistoryPage(page)}
                        onRowsPerPageChange={event => {
                          setHistoryPage(0)
                          setHistoryPageSize(Number(event.target.value))
                        }}
                      />
                    )}
                  </Box>
                </Paper>
              </>
            )}
          </>
        )}
      </Stack>
    </Container>
  )
}
