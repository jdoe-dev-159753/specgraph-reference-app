import { FormEvent, useState } from 'react'
import { useQuery } from '@tanstack/react-query'
import {
  Alert, Button, Container, List, ListItem, Paper, Stack, Table, TableBody, TableCell,
  TableHead, TableRow, TextField, Typography,
} from '@mui/material'

type Activity = {
  transactionId: string
  type: 'CARD' | 'PAYMENT' | 'CRYPTO'
  amount: number
  currency: string
  status: string
  createdAt: string
  details: Record<string, string>
}

type RiskEvidence = {
  transactionId: string
  ruleId: string
  ruleName: string
  triggeredAt: string
  scoreContribution: number
}

type CustomerSnapshot = { customerId: string; activities: Activity[]; riskEvidence: RiskEvidence[] }
type Request = { customerId: string; username: string; password: string }

const SEEDED_CUSTOMER = '11111111-1111-1111-1111-111111111111'

async function loadCustomer(request: Request): Promise<CustomerSnapshot> {
  const response = await fetch(`/api/customers/${request.customerId}`, {
    headers: { Authorization: `Basic ${btoa(`${request.username}:${request.password}`)}` },
  })
  if (response.status === 401) throw new Error('Authentication failed')
  if (response.status === 404) throw new Error('Customer not found')
  if (!response.ok) throw new Error(`Customer request failed (${response.status})`)
  return response.json()
}

export default function App() {
  const [customerId, setCustomerId] = useState(SEEDED_CUSTOMER)
  const [username, setUsername] = useState('operator-a')
  const [password, setPassword] = useState('demo-a')
  const [request, setRequest] = useState<Request | null>(null)
  const customer = useQuery({
    queryKey: ['customer', request],
    queryFn: () => loadCustomer(request!),
    enabled: request !== null,
    retry: false,
  })

  function submit(event: FormEvent) {
    event.preventDefault()
    setRequest({ customerId, username, password })
  }

  return (
    <Container maxWidth="lg" sx={{ py: 5 }}>
      <Stack spacing={3}>
        <Typography variant="h3" component="h1">Customer Activity Analytics</Typography>
        <Paper component="form" onSubmit={submit} sx={{ p: 3 }}>
          <Stack direction={{ xs: 'column', md: 'row' }} spacing={2}>
            <TextField label="Operator" value={username} onChange={e => setUsername(e.target.value)} />
            <TextField label="Password" type="password" value={password} onChange={e => setPassword(e.target.value)} />
            <TextField fullWidth label="Customer ID" value={customerId} onChange={e => setCustomerId(e.target.value)} />
            <Button type="submit" variant="contained">Search</Button>
          </Stack>
        </Paper>

        {customer.isFetching && <Typography>Loading customer activity…</Typography>}
        {customer.error && <Alert severity="error">{customer.error.message}</Alert>}
        {customer.data && (
          <>
            <Paper sx={{ p: 3 }}>
              <Typography variant="h5" gutterBottom>Activity for {customer.data.customerId}</Typography>
              <Table size="small">
                <TableHead><TableRow>
                  <TableCell>Type</TableCell><TableCell>Amount</TableCell><TableCell>Status</TableCell>
                  <TableCell>Time</TableCell><TableCell>Type details</TableCell>
                </TableRow></TableHead>
                <TableBody>
                  {customer.data.activities.map(activity => (
                    <TableRow key={activity.transactionId}>
                      <TableCell>{activity.type}</TableCell>
                      <TableCell>{activity.amount} {activity.currency}</TableCell>
                      <TableCell>{activity.status}</TableCell>
                      <TableCell>{new Date(activity.createdAt).toLocaleString()}</TableCell>
                      <TableCell>{Object.entries(activity.details).map(([key, value]) => `${key}: ${value}`).join(' · ')}</TableCell>
                    </TableRow>
                  ))}
                </TableBody>
              </Table>
            </Paper>
            <Paper sx={{ p: 3 }}>
              <Typography variant="h5">Risk evidence</Typography>
              <List dense>
                {customer.data.riskEvidence.map(evidence => (
                  <ListItem key={`${evidence.transactionId}-${evidence.ruleId}`}>
                    {evidence.ruleName} · +{evidence.scoreContribution} · {new Date(evidence.triggeredAt).toLocaleString()}
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
