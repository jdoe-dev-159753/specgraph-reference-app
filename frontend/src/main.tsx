/**
 * Browser composition root for the single-page reviewer application.
 *
 * @remarks
 * The runtime intentionally shares one TanStack Query cache across session,
 * customer, and analysis workflows and applies the repository's restrained
 * evidence-first visual language through one MUI theme. No domain or HTTP
 * authority is defined here; those remain in `App.tsx` and `openapi.yaml`.
 *
 * @module
 */
import React from 'react'
import ReactDOM from 'react-dom/client'
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { CssBaseline, ThemeProvider, createTheme } from '@mui/material'
import App from './App'

/** Shared server-state cache whose scoped removal on logout prevents protected evidence leaking across operators. */
export const queryClient = new QueryClient()

/**
 * Presentation policy for a dense reviewer screen: high contrast evidence tables,
 * low ornament, tabular readability, and responsive layout without a separate
 * component-design subsystem.
 */
export const theme = createTheme({
  palette: {
    mode: 'light',
    primary: { main: '#a63a24', dark: '#7f2819', contrastText: '#ffffff' },
    secondary: { main: '#e6532f' },
    text: { primary: '#202020', secondary: '#696969' },
    background: { default: '#f5f5f2', paper: '#ffffff' },
    divider: '#deded8',
  },
  typography: {
    fontFamily: 'Inter, "Segoe UI", Arial, sans-serif',
    h3: { fontWeight: 700, letterSpacing: '-0.035em' },
    h5: { fontWeight: 650, letterSpacing: '-0.015em' },
    button: { textTransform: 'none', fontWeight: 700 },
  },
  shape: { borderRadius: 4 },
  components: {
    MuiCssBaseline: {
      styleOverrides: { body: { minHeight: '100vh' } },
    },
    MuiPaper: {
      defaultProps: { elevation: 0 },
      styleOverrides: { root: { backgroundImage: 'none', border: '1px solid #deded8' } },
    },
    MuiButton: {
      styleOverrides: { contained: { boxShadow: 'none' } },
    },
    MuiTableHead: {
      styleOverrides: {
        root: {
          backgroundColor: '#252525',
          '& .MuiTableCell-head': {
            color: '#ffffff',
            fontSize: '0.72rem',
            fontWeight: 700,
            letterSpacing: '0.07em',
            textTransform: 'uppercase',
            borderBottom: 0,
          },
        },
      },
    },
    MuiTableCell: {
      styleOverrides: { root: { borderBottomColor: '#e8e8e3' } },
    },
  },
})

/** The HTML shell owns exactly one required mount point; absence is a packaging defect and therefore fails fast. */
const applicationRoot = document.getElementById('root')!

ReactDOM.createRoot(applicationRoot).render(
  <React.StrictMode>
    <ThemeProvider theme={theme}>
      <CssBaseline />
      <QueryClientProvider client={queryClient}><App /></QueryClientProvider>
    </ThemeProvider>
  </React.StrictMode>
)
