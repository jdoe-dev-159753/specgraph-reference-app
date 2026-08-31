import React from 'react'
import ReactDOM from 'react-dom/client'
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { CssBaseline, ThemeProvider, createTheme } from '@mui/material'
import App from './App'

const queryClient = new QueryClient()

const theme = createTheme({
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

ReactDOM.createRoot(document.getElementById('root')!).render(
  <React.StrictMode>
    <ThemeProvider theme={theme}>
      <CssBaseline />
      <QueryClientProvider client={queryClient}><App /></QueryClientProvider>
    </ThemeProvider>
  </React.StrictMode>
)
