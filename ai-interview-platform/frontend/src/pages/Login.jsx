import { useEffect, useState } from 'react'
import { Link, useNavigate } from 'react-router-dom'
import { useAuth } from '../auth/AuthContext.jsx'
import { api } from '../lib/api.js'

const ROLE_LABELS = {
  RECRUITER: 'Hosts interviews & OAs',
  STUDENT: 'Takes interviews & OAs',
}

export default function Login() {
  const { login } = useAuth()
  const navigate = useNavigate()
  const [email, setEmail] = useState('')
  const [password, setPassword] = useState('')
  const [error, setError] = useState('')
  const [busy, setBusy] = useState(false)
  const [demo, setDemo] = useState(null)

  // Seeded demo logins, so a first-time visitor can try both sides immediately.
  useEffect(() => {
    api
      .demoAccounts()
      .then((data) => setDemo(data?.enabled && data.accounts?.length ? data.accounts : null))
      .catch(() => setDemo(null))
  }, [])

  const submit = async (event) => {
    event.preventDefault()
    setBusy(true)
    setError('')
    try {
      await login(email.trim(), password)
      navigate('/', { replace: true })
    } catch (e) {
      const remaining = e.payload?.attemptsRemaining
      setError(
        remaining != null && remaining > 0
          ? `${e.message} · ${remaining} attempt(s) before this account is temporarily locked`
          : e.message,
      )
      setBusy(false)
    }
  }

  return (
    <div className="flex min-h-full items-center justify-center px-4 py-10">
      <div className="w-full max-w-md">
        <div className="mb-8 text-center">
          <span className="mx-auto mb-4 flex h-14 w-14 items-center justify-center rounded-2xl bg-gradient-to-br from-indigo-500 to-teal-400 text-xl font-black text-ink-950">
            iQ
          </span>
          <h1 className="text-2xl font-bold tracking-tight">
            Welcome <span className="gradient-text">back</span>
          </h1>
          <p className="mt-1 text-sm text-slate-400">Sign in to run and review AI interviews.</p>
        </div>

        <form onSubmit={submit} className="card space-y-4 p-6">
          <div>
            <label className="label" htmlFor="email">
              Email
            </label>
            <input
              id="email"
              type="email"
              className="input"
              autoComplete="email"
              required
              value={email}
              onChange={(e) => setEmail(e.target.value)}
              placeholder="you@company.com"
            />
          </div>
          <div>
            <label className="label" htmlFor="password">
              Password
            </label>
            <input
              id="password"
              type="password"
              className="input"
              autoComplete="current-password"
              required
              value={password}
              onChange={(e) => setPassword(e.target.value)}
              placeholder="••••••••••"
            />
          </div>

          {error && (
            <p className="rounded-xl border border-rose-500/30 bg-rose-500/10 px-3 py-2 text-sm text-rose-200">
              {error}
            </p>
          )}

          <button type="submit" className="btn btn-primary w-full" disabled={busy}>
            {busy ? 'Signing in…' : 'Sign in'}
          </button>

          <p className="text-center text-xs text-slate-500">
            Failed attempts are rate-limited and lock the account for 15 minutes after 5 tries.
          </p>
        </form>

        {demo && (
          <div className="mt-5 rounded-2xl border border-indigo-400/25 bg-indigo-500/5 p-4">
            <p className="text-[11px] font-semibold uppercase tracking-widest text-indigo-300">Demo accounts</p>
            <p className="mt-1 text-xs text-slate-400">
              Seeded for this demo — pick one to fill the form, then press Sign in.
            </p>
            <ul className="mt-3 space-y-2">
              {demo.map((account) => (
                <li
                  key={account.email}
                  className="flex flex-wrap items-center justify-between gap-2 rounded-xl border border-white/10 bg-ink-950/40 px-3 py-2"
                >
                  <div className="min-w-0">
                    <p className="text-xs font-semibold text-slate-100">
                      {account.role === 'RECRUITER' ? 'Recruiter' : 'Student'}
                      <span className="ml-2 font-normal text-slate-500">{ROLE_LABELS[account.role]}</span>
                    </p>
                    <p className="truncate font-mono text-[11px] text-slate-400">{account.email}</p>
                  </div>
                  <button
                    type="button"
                    className="btn !px-3 !py-1 text-xs"
                    onClick={() => {
                      setEmail(account.email)
                      setPassword(account.password)
                      setError('')
                    }}
                  >
                    Use
                  </button>
                </li>
              ))}
            </ul>
          </div>
        )}

        <p className="mt-5 text-center text-sm text-slate-400">
          New here?{' '}
          <Link to="/register" className="font-semibold text-indigo-300 hover:text-indigo-200">
            Create an account
          </Link>
        </p>
      </div>
    </div>
  )
}
