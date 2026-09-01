import { FormEvent, useRef, useState } from 'react'
import { useQuery } from '@tanstack/react-query'
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
  transactionId: string
  ruleId: string
  ruleName: string
  triggeredAt: string
  scoreContribution: number
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

export default function App() {
  const [customerId, setCustomerId] = useState(SEEDED_CUSTOMER)
  const [request, setRequest] = useState<Request | null>(null)
  const submission = useRef(0)
  const customer = useQuery({
    queryKey: ['customer', request],
    queryFn: () => loadCustomer(request!),
    enabled: request !== null,
    retry: false,
  })

  function submit(event: FormEvent) {
    event.preventDefault()
    submission.current += 1
    setRequest({ customerId, submission: submission.current })
  }

  return (
    <Container maxWidth="lg" sx={{ py: { xs: 3, md: 5 } }}>
      <Stack spacing={3}>
        <Box>
          <Box sx={{ width: 52, height: 4, bgcolor: 'secondary.main', mb: 2 }} />
          <Typography variant="overline" color="text.secondary" sx={{ letterSpacing: '0.14em', fontWeight: 700 }}>
            Customer Care · R2
          </Typography>
          <Typography variant="h3" component="h1" sx={{ mt: 0.25 }}>Customer Activity Analytics</Typography>
          <Typography color="text.secondary" sx={{ mt: 1, maxWidth: 820 }}>
            PostgreSQL-backed customer activity and source-shaped risk evidence through the same review contract.
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
                    key={`${evidence.transactionId}-${evidence.ruleId}`}
                    data-testid={`risk-evidence-${evidence.transactionId}-${evidence.ruleId}`}
                    divider
                    sx={{ px: 3, py: 1.5, display: 'flex', justifyContent: 'space-between', gap: 2 }}
                  >
                    <Box>
                      <Typography variant="body2" sx={{ fontWeight: 600 }}>{evidence.ruleName}</Typography>
                      <Typography
                        variant="caption"
                        color="text.secondary"
                        data-testid={`risk-evidence-${evidence.transactionId}-${evidence.ruleId}-time`}
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
          </>
        )}
      </Stack>
    </Container>
  )
}
