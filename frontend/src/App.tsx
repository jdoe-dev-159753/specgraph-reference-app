import { FormEvent, useRef, useState } from 'react'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import {
  Alert, Box, Button, Chip, Container, List, ListItem, Paper, Stack, Table, TableBody,
  TableCell, TableContainer, TableHead, TableRow, TextField, Typography,
} from '@mui/material'

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

type PolicyEvidence = {
  sourceIdentity: string
  content: string
  retrievalMetadata: Record<string, string>
}

type Analysis = {
  analysisId: string
  customerId: string
  operatorId: string
  generatedAt: string
  riskLevel: 'LOW' | 'MEDIUM' | 'HIGH'
  findingsSummary: string
  recommendations: string[]
  evidenceProvenance: PolicyEvidence[]
}

type CustomerSnapshot = { customerId: string; activities: Activity[]; riskEvidence: RiskEvidence[] }
type Request = { customerId: string; submission: number }

type CsrfView = {
  headerName: string
  parameterName: string
  token: string
}

type AuthenticatedSession = {
  state: 'AUTHENTICATED'
  operatorId: string
  csrf: CsrfView
}

type UnauthenticatedSession = {
  state: 'UNAUTHENTICATED'
  csrf: CsrfView
}

type SecuritySession = AuthenticatedSession | UnauthenticatedSession
type RuntimeSession = { kind: 'LEGACY' } | { kind: 'SECURED'; session: SecuritySession }
type LoginRequest = { username: string; password: string; csrf: CsrfView }
type RunAnalysisRequest = { customerId: string; csrf?: CsrfView }

const SEEDED_CUSTOMER = '11111111-1111-1111-1111-111111111111'

function formatAmount(amount: string) {
  const match = /^(-?)(\d+)(?:\.(\d+))?$/.exec(amount)
  if (!match) return amount
  const [, sign, integer, fraction = ''] = match
  const grouped = integer.replace(/\B(?=(\d{3})+(?!\d))/g, "'")
  return `${sign}${grouped}.${fraction.padEnd(2, '0')}`
}

function GroundingEvidence({ evidence }: { evidence: PolicyEvidence[] }) {
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

async function loadRuntimeSession(): Promise<RuntimeSession> {
  const response = await fetch('/api/session', { credentials: 'same-origin' })
  if (response.status === 404) return { kind: 'LEGACY' }
  if (!response.ok) throw new Error(`Session request failed (${response.status})`)
  return { kind: 'SECURED', session: await response.json() as SecuritySession }
}

async function loginOperator(request: LoginRequest): Promise<void> {
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

async function logoutOperator(csrf: CsrfView): Promise<void> {
  const response = await fetch('/api/session/logout', {
    method: 'POST',
    credentials: 'same-origin',
    headers: { [csrf.headerName]: csrf.token },
  })
  if (!response.ok) throw new Error(`Logout failed (${response.status})`)
}

async function loadCustomer(request: Request): Promise<CustomerSnapshot> {
  const response = await fetch(`/api/customers/${request.customerId}`)
  if (response.status === 404) throw new Error('Customer not found')
  if (!response.ok) throw new Error(`Customer request failed (${response.status})`)
  return response.json()
}

async function loadAnalysisHistory(customerId: string): Promise<Analysis[]> {
  const response = await fetch(`/api/customers/${customerId}/analyses`)
  if (!response.ok) throw new Error(`Analysis history request failed (${response.status})`)
  return response.json()
}

async function runAnalysis(request: RunAnalysisRequest): Promise<Analysis> {
  const headers = request.csrf ? { [request.csrf.headerName]: request.csrf.token } : undefined
  const response = await fetch(`/api/customers/${request.customerId}/analyses`, { method: 'POST', headers })
  if (!response.ok) {
    const problem = await response.json().catch(() => null) as { detail?: string; reason?: string } | null
    const detail = problem?.detail ?? `Analysis request failed (${response.status})`
    throw new Error(problem?.reason ? `${detail} [${problem.reason}]` : detail)
  }
  return response.json()
}

function RiskLevelChip({ level }: { level: Analysis['riskLevel'] }) {
  const color = level === 'HIGH' ? 'error' : level === 'MEDIUM' ? 'warning' : 'success'
  return <Chip label={level} color={color} size="small" data-testid="analysis-risk-level" />
}

export default function App() {
  const [customerId, setCustomerId] = useState(SEEDED_CUSTOMER)
  const [request, setRequest] = useState<Request | null>(null)
  const [username, setUsername] = useState('')
  const [password, setPassword] = useState('')
  const submission = useRef(0)
  const queryClient = useQueryClient()

  const runtimeSession = useQuery({
    queryKey: ['runtime-session'],
    queryFn: loadRuntimeSession,
    retry: false,
  })
  const authenticatedSession = runtimeSession.data?.kind === 'SECURED' && runtimeSession.data.session.state === 'AUTHENTICATED'
    ? runtimeSession.data.session
    : null
  const unauthenticatedSession = runtimeSession.data?.kind === 'SECURED' && runtimeSession.data.session.state === 'UNAUTHENTICATED'
    ? runtimeSession.data.session
    : null
  const applicationEnabled = runtimeSession.data?.kind === 'LEGACY' || authenticatedSession !== null

  const customer = useQuery({
    queryKey: ['customer', request],
    queryFn: () => loadCustomer(request!),
    enabled: applicationEnabled && request !== null,
    retry: false,
  })
  const selectedCustomerId = customer.data?.customerId ?? null
  const history = useQuery({
    queryKey: ['analysis-history', selectedCustomerId],
    queryFn: () => loadAnalysisHistory(selectedCustomerId!),
    enabled: applicationEnabled && selectedCustomerId !== null,
    retry: false,
  })
  const analysis = useMutation({
    mutationFn: runAnalysis,
    onSuccess: async (_completed, analyzed) => {
      await queryClient.invalidateQueries({ queryKey: ['analysis-history', analyzed.customerId] })
    },
  })
  const login = useMutation({
    mutationFn: loginOperator,
    onSuccess: async () => {
      setPassword('')
      await queryClient.invalidateQueries({ queryKey: ['runtime-session'] })
    },
  })
  const logout = useMutation({
    mutationFn: logoutOperator,
    onSuccess: async () => {
      setRequest(null)
      analysis.reset()
      queryClient.removeQueries({ queryKey: ['customer'] })
      queryClient.removeQueries({ queryKey: ['analysis-history'] })
      await queryClient.invalidateQueries({ queryKey: ['runtime-session'] })
    },
  })

  function submit(event: FormEvent) {
    event.preventDefault()
    analysis.reset()
    submission.current += 1
    setRequest({ customerId, submission: submission.current })
  }

  function submitLogin(event: FormEvent) {
    event.preventDefault()
    if (!unauthenticatedSession) return
    login.mutate({ username, password, csrf: unauthenticatedSession.csrf })
  }

  const runtimeLabel = runtimeSession.data?.kind === 'SECURED' ? 'R4' : 'R3'

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
              <Stack direction={{ xs: 'column', md: 'row' }} spacing={2} sx={{ alignItems: 'stretch' }}>
                <TextField
                  fullWidth
                  label="Customer ID"
                  value={customerId}
                  onChange={e => setCustomerId(e.target.value)}
                  size="small"
                />
                <Button type="submit" variant="contained" sx={{ minWidth: 128 }}>Search</Button>
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
                      Customer {customer.data.customerId}
                    </Typography>
                  </Box>
                  <TableContainer>
                    <Table size="small" aria-label="Customer activity">
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
                              <TableCell>
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
                </Paper>

                <Paper data-testid="risk-evidence" sx={{ overflow: 'hidden' }}>
                  <Box sx={{ px: 3, py: 2.5, borderBottom: 1, borderColor: 'divider', borderLeft: 4, borderLeftColor: 'secondary.main' }}>
                    <Typography variant="h5">Risk evidence</Typography>
                    <Typography variant="body2" color="text.secondary" sx={{ mt: 0.5 }}>
                      Source-shaped deterministic evidence associated with the current activity set.
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
                        onClick={() => analysis.mutate({
                          customerId: customer.data.customerId,
                          csrf: authenticatedSession?.csrf,
                        })}
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
                      <Typography variant="subtitle2" sx={{ mt: 2, mb: 1 }}>Grounding evidence</Typography>
                      <GroundingEvidence evidence={analysis.data.evidenceProvenance} />
                    </Box>
                  )}

                  <Box sx={{ px: 3, py: 2.5 }}>
                    <Typography variant="h6">Analysis history</Typography>
                    {history.isFetching && <Typography sx={{ mt: 1 }}>Loading prior analyses…</Typography>}
                    {history.error && <Alert severity="error" sx={{ mt: 1 }}>{history.error.message}</Alert>}
                    {history.data?.length === 0 && (
                      <Typography color="text.secondary" sx={{ mt: 1 }}>No completed analyses have been retained for this customer.</Typography>
                    )}
                    <List data-testid="analysis-history" disablePadding sx={{ mt: 1 }}>
                      {history.data?.map(entry => (
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
                            <GroundingEvidence evidence={entry.evidenceProvenance} />
                          </Box>
                        </ListItem>
                      ))}
                    </List>
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
