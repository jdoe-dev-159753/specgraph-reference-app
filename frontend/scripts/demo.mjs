import { spawn } from 'node:child_process'

const backendHealthUrl = process.env.DEMO_BACKEND_HEALTH_URL || 'http://backend:8080/actuator/health'
const frontendHealthUrl = process.env.DEMO_FRONTEND_HEALTH_URL || 'http://127.0.0.1:5173/'
const demoUrl = process.env.DEMO_URL || 'http://localhost:5173/'
const timeoutMs = Number(process.env.DEMO_STARTUP_TIMEOUT_MS || 180000)
const pollMs = 500

async function waitFor(url, label) {
  const deadline = Date.now() + timeoutMs
  let lastError

  while (Date.now() < deadline) {
    try {
      const response = await fetch(url)
      if (response.ok) return
      lastError = new Error(`${label} returned HTTP ${response.status}`)
    } catch (error) {
      lastError = error
    }
    await new Promise(resolve => setTimeout(resolve, pollMs))
  }

  throw new Error(`${label} did not become ready within ${timeoutMs} ms: ${lastError ?? 'unknown error'}`)
}

await waitFor(backendHealthUrl, 'Backend')

const vite = spawn('npm', ['run', 'dev', '--', '--host', '0.0.0.0'], {
  stdio: 'inherit',
  env: process.env,
})

const forwardSignal = signal => {
  if (!vite.killed) vite.kill(signal)
}
process.on('SIGTERM', () => forwardSignal('SIGTERM'))
process.on('SIGINT', () => forwardSignal('SIGINT'))

try {
  await waitFor(frontendHealthUrl, 'Frontend')
  console.log(`\nDemo ready: ${demoUrl}\n`)
} catch (error) {
  forwardSignal('SIGTERM')
  throw error
}

vite.on('exit', (code, signal) => {
  if (signal) process.kill(process.pid, signal)
  process.exit(code ?? 1)
})
