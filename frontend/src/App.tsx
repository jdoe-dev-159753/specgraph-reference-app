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

const SEEDED_CUSTOMER = '11111111-1111-1111-1111-111111111111'

function formatAmount(amount: string) {
  const match = /^(-?)(\d+)(?:\.(\d+))?$/.exec(amount)
  if (!match) return amount
  const [, sign, integer, fraction = ''] = match
  const grouped = integer.replace(/\B(?=(\d{3})+(?!\d))/g, "'")
  return `${sign}${grouped}.${fraction.padEnd(2, '0')}`
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

async function runAnalysis(customerId: string): Promise<Analysis> {
  const response = await fetch(`/api/customers/${customerId}/analyses`, { method: 'POST' })
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
  const submission = useRef(0)
  const queryClient = useQueryClient()

  const customer = useQuery({
    queryKey: ['customer', request],
    queryFn: () => loadCustomer(request!),
    enabled: request !== null,
    retry: false,
  })
  const selectedCustomerId = customer.data?.customerId ?? null
  const history = useQuery({
    queryKey: ['analysis-history', selectedCustomerId],
    queryFn: () => loadAnalysisHistory(selectedCustomerId!),
    enabled: selectedCustomerId !== null,
    retry: false,
  })
  const analysis = useMutation({
    mutationFn: runAnalysis,
    onSuccess: async (_completed, analyzedCustomerId) => {
      await queryClient.invalidateQueries({ queryKey: ['analysis-history', analyzedCustomerId] })
    },
  })

  function submit(event: FormEvent) {
    event.preventDefault()
    analysis.reset()
    submission.current += 1
    setRequest({ customerId, submission: submission.current })
  }

  return (
    <Container maxWidth="lg" sx={{ py: { xs: 3, md: 5 } }}>
      <Stack spacing={3}>
        <Box>
          <Box sx={{ width: 52, height: 4, bgcolor: 'secondary.main', mb: 2 }} />
          <Typography variant="overline" color="text.secondary" sx={{ letterSpacing: '0.14em', fontWeight: 700 }}>
            Customer Care · R3
          </Typography>
          <Typography variant="h3" component="h1" sx={{ mt: 0.25 }}>Customer Activity Analytics</Typography>
          <Typography color="text.secondary" sx={{ mt: 1, maxWidth: 820 }}>
            PostgreSQL-backed customer evidence with deterministic offline analysis and reviewable persisted history.
          </Typography>
        </Box>

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
                <Stack direction={{ xs: 'column', md: 'row' }} spacing={2} justifyContent="space-between" alignItems={{ md: 'center' }}>
                  <Box>
                    <Typography variant="h5">Deterministic analysis</Typography>
                    <Typography variant="body2" color="text.secondary" sx={{ mt: 0.5 }}>
                      Offline R3 baseline. Policy evidence is synthetic and escalation remains a human decision.
                    </Typography>
                  </Box>
                  <Button
                    variant="contained"
                    color="secondary"
                    disabled={analysis.isPending}
                    onClick={() => analysis.mutate(customer.data.customerId)}
                  >
                    {analysis.isPending ? 'Analyzing…' : 'Run deterministic analysis'}
                  </Button>
                </Stack>
              </Box>

              {analysis.error && <Alert severity="error" sx={{ m: 2 }}>{analysis.error.message}</Alert>}
              {analysis.data && (
                <Box data-testid="analysis-result" sx={{ px: 3, py: 2.5, borderBottom: 1, borderColor: 'divider' }}>
                  <Stack direction="row" spacing={1.5} alignItems="center" sx={{ mb: 1.5 }}>
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
                    Operator {analysis.data.operatorId} · {new Date(analysis.data.generatedAt).toLocaleString()} · evidence {analysis.data.evidenceProvenance.map(item => item.sourceIdentity).join(', ')}
                  </Typography>
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
                      <Stack direction="row" spacing={1.5} alignItems="center">
                        <RiskLevelChip level={entry.riskLevel} />
                        <Typography variant="body2" sx={{ fontWeight: 600 }}>{entry.operatorId}</Typography>
                        <Typography variant="caption" color="text.secondary">{new Date(entry.generatedAt).toLocaleString()}</Typography>
                      </Stack>
                      <Typography variant="body2" sx={{ mt: 1 }}>{entry.findingsSummary}</Typography>
                      <Typography variant="caption" color="text.secondary">
                        {entry.recommendations.join(' · ')} · evidence {entry.evidenceProvenance.map(item => item.sourceIdentity).join(', ')}
                      </Typography>
                    </ListItem>
                  ))}
                </List>
              </Box>
            </Paper>
          </>
        )}
      </Stack>
    </Container>
  )
}
