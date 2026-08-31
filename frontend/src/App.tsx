import { Container, Paper, Stack, Typography } from '@mui/material'

export default function App() {
  return (
    <Container maxWidth="lg" sx={{ py: 6 }}>
      <Stack spacing={3}>
        <Typography variant="h3" component="h1">Customer Activity Analytics</Typography>
        <Paper sx={{ p: 3 }}>
          <Typography variant="h6">R0 architectural shell</Typography>
          <Typography color="text.secondary">
            The operator dashboard is running. The authenticated customer read slice is activated in R1.
          </Typography>
        </Paper>
      </Stack>
    </Container>
  )
}
