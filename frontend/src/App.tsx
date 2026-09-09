/**
 * Operator-facing customer review and grounded-analysis workflow.
 *
 * @remarks
 * React composition stays here while transport contracts and HTTP behavior live
 * in `customerApi.ts`. Editable filters remain separate from submitted queries,
 * protected content is gated on runtime session state, and source risk, detector,
 * retrieval and model provenance remain distinct evidence layers. See FR-ACT-001,
 * FR-AUTH-001, FR-HIST-002, FR-RAG-001, NFR-SEC-001 and ADR-004.
 *
 * @module
 */
import { FormEvent, useRef, useState } from 'react'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import {
  Alert, Box, Button, Chip, Container, List, ListItem, MenuItem, Paper, Stack, Table, TableBody,
  TableCell, TableContainer, TableHead, TablePagination, TableRow, TextField, Typography,
} from '@mui/material'
import {
  Analysis,
  DEFAULT_ACTIVITY_PAGE_SIZE,
  DEFAULT_HISTORY_PAGE_SIZE,
  DetectorProvenance,
  ModelProvenance,
  PolicyEvidence,
  Request,
  ScenarioFamily,
  SEEDED_CUSTOMER,
  formatAmount,
  generateScenario,
  loadAnalysisHistory,
  loadCustomer,
  loadRuntimeSession,
  loadScenarioAvailability,
  loginOperator,
  logoutOperator,
  runAnalysis,
} from './customerApi'

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
  /** Editable replay seed; the server parses the exact signed 64-bit value. */
  const [scenarioSeed, setScenarioSeed] = useState('20260906')
  /** Selected coherent demonstration story family. */
  const [scenarioFamily, setScenarioFamily] = useState<ScenarioFamily>('CROSS_BORDER_GROWTH')
  /** Monotonic discriminator that makes otherwise identical explicit searches distinct query keys. */
  const submission = useRef(0)
  /** Synchronous duplicate-click guard released by the customer query's `finally` path. */
  const customerSubmissionInFlight = useRef(false)
  /** Synchronous duplicate-analysis guard released on settle and explicitly across logout. */
  const analysisSubmissionInFlight = useRef(false)
  /** Monotonic intent token prevents late scenario completions from replacing newer reviewer navigation. */
  const scenarioRequestId = useRef(0)
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
  /** Runtime discovery exposes the optional lab only when the protected POST endpoint is available. */
  const scenarioAvailability = useQuery({ queryKey: ['demo-scenario-availability'], queryFn: loadScenarioAvailability, enabled: authenticatedSession !== null, retry: false })

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
  /** Generated identity becomes the submitted customer only after the server confirms persistence. */
  const scenario = useMutation({
    mutationFn: generateScenario,
    onSuccess: (generated, request) => {
      if (request.requestId !== scenarioRequestId.current) return
      analysisSubmissionInFlight.current = false
      setCustomerId(generated.customerId)
      setHistoryPage(0)
      analysis.reset()
      submission.current += 1
      setRequest({
        customerId: generated.customerId,
        submission: submission.current,
        page: 0,
        pageSize: DEFAULT_ACTIVITY_PAGE_SIZE,
        activityType: '',
        status: '',
        createdFrom: '',
        createdTo: '',
      })
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
      scenarioRequestId.current += 1
      setRequest(null)
      setHistoryPage(0)
      analysis.reset()
      scenario.reset()
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
    scenarioRequestId.current += 1
    if (scenario.data?.customerId !== customerId) scenario.reset()
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
            {scenarioAvailability.error && <Alert severity="error" data-testid="scenario-availability-error">{scenarioAvailability.error.message}</Alert>}
            {scenarioAvailability.data === true && <Paper
              component="form"
              onSubmit={event => {
                event.preventDefault()
                if (analysisSubmissionInFlight.current || scenario.isPending) return
                scenarioRequestId.current += 1
                scenario.mutate({ seed: scenarioSeed, family: scenarioFamily, requestId: scenarioRequestId.current, csrf: authenticatedSession?.csrf })
              }}
              data-testid="scenario-lab"
              sx={{ p: { xs: 2, md: 3 } }}
            >
              <Typography variant="h5">Replayable scenario lab</Typography>
              <Typography variant="body2" color="text.secondary" sx={{ mt: 0.5 }}>
                Optional synthetic demo input; it is not production data or evidence of criminal conduct.
              </Typography>
              <Stack direction={{ xs: 'column', md: 'row' }} spacing={2} sx={{ mt: 2 }}>
                <TextField label="Scenario seed" value={scenarioSeed} onChange={event => setScenarioSeed(event.target.value)} size="small" />
                <TextField
                  select
                  label="Scenario family"
                  value={scenarioFamily}
                  onChange={event => setScenarioFamily(event.target.value as ScenarioFamily)}
                  size="small"
                  sx={{ minWidth: 230 }}
                >
                  <MenuItem value="ORDINARY_LOCAL">Ordinary local activity</MenuItem>
                  <MenuItem value="CROSS_BORDER_GROWTH">Growing cross-border activity</MenuItem>
                  <MenuItem value="MIXED_RED_FLAGS">Mixed red-flag pattern</MenuItem>
                </TextField>
                <Button type="submit" variant="outlined" disabled={scenario.isPending || analysis.isPending}>Generate and load</Button>
              </Stack>
              {scenario.error && <Alert severity="error" sx={{ mt: 2 }}>{scenario.error.message}</Alert>}
              {scenario.data && customer.data?.customerId === scenario.data.customerId && (
                <Alert severity="info" sx={{ mt: 2 }} data-testid="scenario-provenance">
                  {scenario.data.family} · seed {scenario.data.seed} · {scenario.data.generatorIdentity} · customer {scenario.data.customerId}
                </Alert>
              )}
            </Paper>}
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
