import { useMemo, useState } from 'react'
import { Link, useNavigate } from 'react-router-dom'
import { useAuth } from '../auth/AuthContext.jsx'

const RULES = [
  { id: 'length', label: 'At least 10 characters', test: (p) => p.length >= 10 && p.length <= 72 },
  { id: 'lower', label: 'A lower-case letter', test: (p) => /[a-z]/.test(p) },
  { id: 'upper', label: 'An upper-case letter', test: (p) => /[A-Z]/.test(p) },
  { id: 'digit', label: 'A digit', test: (p) => /\d/.test(p) },
  { id: 'symbol', label: 'A symbol', test: (p) => /[^A-Za-z0-9]/.test(p) },
  { id: 'run', label: 'No 5 repeated characters', test: (p) => !/(.)\1{4,}/.test(p) },
]

export default function Register() {
  const { register } = useAuth()
  const navigate = useNavigate()
  const [form, setForm] = useState({
    name: '',
    email: '',
    password: '',
    confirmPassword: '',
    role: 'RECRUITER',
  })
  const [error, setError] = useState('')
  const [busy, setBusy] = useState(false)

  const checks = useMemo(
    () => RULES.map((rule) => ({ ...rule, ok: rule.test(form.password) })),
    [form.password],
  )
  const matches = form.password.length > 0 && form.password === form.confirmPassword
  const strength = checks.filter((c) => c.ok).length
  const canSubmit = checks.every((c) => c.ok) && matches && form.name.trim() && form.email.trim()

  const submit = async (event) => {
    event.preventDefault()
    if (!canSubmit) return
    setBusy(true)
    setError('')
    try {
      await register({
        name: form.name.trim(),
        email: form.email.trim(),
        password: form.password,
        confirmPassword: form.confirmPassword,
        role: form.role,
      })
      navigate('/', { replace: true })
    } catch (e) {
      setError(e.message)
      setBusy(false)
    }
  }

  const strengthLabel = ['Very weak', 'Weak', 'Fair', 'Good', 'Strong', 'Excellent', 'Excellent'][strength]
  const strengthTone =
    strength <= 2 ? 'bg-rose-400' : strength <= 4 ? 'bg-amber-400' : 'bg-emerald-400'

  return (
    <div className="flex min-h-full items-center justify-center px-4 py-10">
      <div className="w-full max-w-lg">
        <div className="mb-8 text-center">
          <span className="mx-auto mb-4 flex h-14 w-14 items-center justify-center rounded-2xl bg-gradient-to-br from-indigo-500 to-teal-400 text-xl font-black text-ink-950">
            iQ
          </span>
          <h1 className="text-2xl font-bold tracking-tight">
            Create your <span className="gradient-text">account</span>
          </h1>
          <p className="mt-1 text-sm text-slate-400">
            Passwords are hashed with BCrypt and checked against a strict policy.
          </p>
        </div>

        <form onSubmit={submit} className="card space-y-4 p-6">
          <div className="grid gap-4 sm:grid-cols-2">
            <div>
              <label className="label" htmlFor="name">
                Full name
              </label>
              <input
                id="name"
                className="input"
                required
                value={form.name}
                onChange={(e) => setForm({ ...form, name: e.target.value })}
                placeholder="Priya Sharma"
              />
            </div>
            <div>
              <label className="label" htmlFor="role">
                I am a
              </label>
              <select
                id="role"
                className="input"
                value={form.role}
                onChange={(e) => setForm({ ...form, role: e.target.value })}
              >
                <option value="RECRUITER">Recruiter / interviewer</option>
                <option value="STUDENT">Student / candidate</option>
              </select>
            </div>
          </div>

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
              value={form.email}
              onChange={(e) => setForm({ ...form, email: e.target.value })}
              placeholder="you@company.com"
            />
          </div>

          <div className="grid gap-4 sm:grid-cols-2">
            <div>
              <label className="label" htmlFor="password">
                Password
              </label>
              <input
                id="password"
                type="password"
                className="input"
                autoComplete="new-password"
                required
                value={form.password}
                onChange={(e) => setForm({ ...form, password: e.target.value })}
                placeholder="••••••••••"
              />
            </div>
            <div>
              <label className="label" htmlFor="confirm">
                Confirm password
              </label>
              <input
                id="confirm"
                type="password"
                className="input"
                autoComplete="new-password"
                required
                value={form.confirmPassword}
                onChange={(e) => setForm({ ...form, confirmPassword: e.target.value })}
                placeholder="••••••••••"
              />
            </div>
          </div>

          {form.password && (
            <div>
              <div className="mb-2 flex items-center justify-between text-xs">
                <span className="text-slate-400">Strength</span>
                <span className="font-semibold text-slate-200">{strengthLabel}</span>
              </div>
              <div className="h-1.5 overflow-hidden rounded-full bg-white/10">
                <div
                  className={`h-full rounded-full ${strengthTone}`}
                  style={{ width: `${(strength / RULES.length) * 100}%`, transition: 'width .3s ease' }}
                />
              </div>
              <ul className="mt-3 grid gap-1.5 sm:grid-cols-2">
                {checks.map((check) => (
                  <li key={check.id} className="flex items-center gap-2 text-xs">
                    <span className={check.ok ? 'text-emerald-400' : 'text-slate-600'}>
                      {check.ok ? '✓' : '○'}
                    </span>
                    <span className={check.ok ? 'text-slate-300' : 'text-slate-500'}>{check.label}</span>
                  </li>
                ))}
                <li className="flex items-center gap-2 text-xs">
                  <span className={matches ? 'text-emerald-400' : 'text-slate-600'}>{matches ? '✓' : '○'}</span>
                  <span className={matches ? 'text-slate-300' : 'text-slate-500'}>Passwords match</span>
                </li>
              </ul>
            </div>
          )}

          {error && (
            <p className="rounded-xl border border-rose-500/30 bg-rose-500/10 px-3 py-2 text-sm text-rose-200">
              {error}
            </p>
          )}

          <button type="submit" className="btn btn-primary w-full" disabled={busy || !canSubmit}>
            {busy ? 'Creating account…' : 'Create account'}
          </button>
          <p className="text-center text-xs text-slate-500">
            Accounts can never self-assign admin — roles above yours require an administrator.
          </p>
        </form>

        <p className="mt-5 text-center text-sm text-slate-400">
          Already have an account?{' '}
          <Link to="/login" className="font-semibold text-indigo-300 hover:text-indigo-200">
            Sign in
          </Link>
        </p>
      </div>
    </div>
  )
}
