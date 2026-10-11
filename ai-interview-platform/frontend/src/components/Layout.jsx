import { useEffect, useState } from 'react'
import { NavLink, Outlet, useNavigate } from 'react-router-dom'
import { useAuth } from '../auth/AuthContext.jsx'
import { api } from '../lib/api.js'
import { Avatar } from './ui.jsx'

/* ─────────────────────────────────────────────────────────────────────────────
   Two consoles, deliberately two different-looking apps.

   Recruiter / admin  →  "Recruiting console": indigo sidebar shell, dense
                         tooling (pipeline, cohort analytics, OA hosting, bank).
   Student            →  "Student practice": teal top-navigation shell, airy
                         single-column layout, practice-first.
   ──────────────────────────────────────────────────────────────────────────── */

const RECRUITER_NAV = [
  { to: '/', label: 'Dashboard', icon: '◈', end: true },
  { to: '/analytics', label: 'Analytics', icon: '◔' },
  { to: '/candidates', label: 'Students', icon: '◉' },
  { to: '/interview/new', label: 'New interview', icon: '▶' },
  { to: '/questions', label: 'Question bank', icon: '☰' },
  { to: '/assessments', label: 'Assessments (OA)', icon: '▣' },
]

const STUDENT_NAV = [
  { to: '/', label: 'My dashboard', icon: '◈', end: true },
  { to: '/interview/new', label: 'Practice interview', icon: '▶' },
]

export default function Layout() {
  const { isRecruiter } = useAuth()
  return isRecruiter ? <RecruiterShell /> : <StudentShell />
}

/* ── Recruiter / admin: sidebar console (unchanged look) ───────────────────── */

function RecruiterShell() {
  const [meta, setMeta] = useState(null)
  const { user, logout, isAdmin } = useAuth()
  const navigate = useNavigate()

  useEffect(() => {
    api.meta().then(setMeta).catch(() => setMeta(null))
  }, [])

  const items = [...RECRUITER_NAV, ...(isAdmin ? [{ to: '/team', label: 'Team & access', icon: '⚙' }] : [])]

  return (
    <div className="flex min-h-full">
      <aside className="no-print sticky top-0 hidden h-screen w-64 shrink-0 flex-col border-r border-white/10 bg-ink-950/70 px-4 py-6 backdrop-blur lg:flex">
        <div className="mb-8 flex items-center gap-3 px-2">
          <span className="flex h-10 w-10 items-center justify-center rounded-xl bg-gradient-to-br from-indigo-500 to-teal-400 text-lg font-black text-ink-950">
            iQ
          </span>
          <div>
            <p className="text-base font-bold tracking-tight text-slate-50">InterviewIQ</p>
            <p className="text-[11px] font-semibold uppercase tracking-widest text-indigo-300">
              Recruiting console
            </p>
          </div>
        </div>

        <nav className="flex flex-1 flex-col gap-1">
          {items.map((item) => (
            <NavLink
              key={item.to}
              to={item.to}
              end={item.end}
              className={({ isActive }) =>
                `flex items-center gap-3 rounded-xl px-3 py-2.5 text-sm font-medium transition ${
                  isActive
                    ? 'bg-indigo-500/15 text-indigo-200 shadow-[inset_0_0_0_1px_rgba(129,140,248,0.35)]'
                    : 'text-slate-400 hover:bg-white/5 hover:text-slate-100'
                }`
              }
            >
              <span className="w-4 text-center text-xs opacity-80">{item.icon}</span>
              {item.label}
            </NavLink>
          ))}
        </nav>

        <div className="space-y-2 rounded-xl border border-white/10 bg-white/[0.03] p-3 text-[11px] text-slate-400">
          <div className="flex items-center justify-between">
            <span>Engine</span>
            <span className="font-semibold text-slate-200">
              {meta ? (meta.llmEnabled ? meta.aiEngine : 'heuristic (offline)') : '…'}
            </span>
          </div>
          <div className="flex items-center justify-between">
            <span>Database</span>
            <span className="font-semibold text-slate-200">{meta ? meta.database : '…'}</span>
          </div>
          <div className="flex items-center justify-between">
            <span>Profile</span>
            <span className="font-mono text-slate-300">{meta ? meta.profile : '…'}</span>
          </div>
        </div>
      </aside>

      <div className="flex min-w-0 flex-1 flex-col">
        <header className="no-print sticky top-0 z-30 flex items-center justify-between gap-4 border-b border-white/10 bg-ink-950/70 px-4 py-3 backdrop-blur lg:px-8">
          <div className="flex items-center gap-3 lg:hidden">
            <span className="flex h-8 w-8 items-center justify-center rounded-lg bg-gradient-to-br from-indigo-500 to-teal-400 text-sm font-black text-ink-950">
              iQ
            </span>
            <span className="font-semibold">InterviewIQ</span>
          </div>
          <div className="hidden text-sm text-slate-400 lg:block">
            AI-generated questions from student skills · instant scored feedback · review analytics
          </div>
          <div className="flex items-center gap-2">
            <NavLink to="/interview/new" className="btn btn-primary !py-2 hidden sm:inline-flex">
              New interview
            </NavLink>
            {user && (
              <div className="flex items-center gap-2 rounded-xl border border-white/10 bg-white/[0.04] py-1 pl-1 pr-2">
                <Avatar name={user.name} size={30} />
                <div className="hidden leading-tight sm:block">
                  <p className="text-xs font-semibold text-slate-100">{user.name}</p>
                  <p className="text-[10px] font-semibold uppercase tracking-wider text-indigo-300">{user.role}</p>
                </div>
                <button
                  type="button"
                  title="Sign out"
                  className="ml-1 rounded-lg px-2 py-1 text-xs text-slate-400 transition hover:bg-white/10 hover:text-slate-100"
                  onClick={async () => {
                    await logout()
                    navigate('/login', { replace: true })
                  }}
                >
                  ⎋
                </button>
              </div>
            )}
          </div>
        </header>

        <main className="min-w-0 flex-1 px-4 py-6 lg:px-8 lg:py-8">
          <Outlet context={{ meta }} />
        </main>

        <footer className="no-print border-t border-white/10 px-4 py-4 text-xs text-slate-500 lg:px-8">
          React 19 · Tailwind CSS v4 · Spring Boot 3 + Spring Security (JWT) · TiDB (MySQL-compatible)
        </footer>
      </div>
    </div>
  )
}

/* ── Student: teal top-nav shell, no sidebar, focused single column ────────── */

function StudentShell() {
  const { user, logout } = useAuth()
  const navigate = useNavigate()

  const pillClass = ({ isActive }) =>
    `flex shrink-0 items-center gap-2 rounded-full px-4 py-2 text-sm font-medium transition ${
      isActive
        ? 'bg-teal-400/15 text-teal-100 shadow-[inset_0_0_0_1px_rgba(45,212,191,0.4)]'
        : 'text-slate-400 hover:bg-white/5 hover:text-slate-100'
    }`

  return (
    <div className="student-bg flex min-h-full flex-col">
      <header className="no-print sticky top-0 z-30 border-b border-teal-200/10 bg-[#04100e]/80 backdrop-blur">
        <div className="mx-auto flex w-full max-w-5xl items-center justify-between gap-3 px-4 py-3">
          <div className="flex items-center gap-2.5">
            <span className="flex h-9 w-9 items-center justify-center rounded-2xl bg-gradient-to-br from-teal-300 to-emerald-400 text-base font-black text-[#03201c]">
              iQ
            </span>
            <div className="leading-tight">
              <p className="text-sm font-bold tracking-tight text-slate-50">InterviewIQ</p>
              <p className="text-[10px] font-semibold uppercase tracking-widest text-teal-300">Student practice</p>
            </div>
          </div>

          <nav className="hidden items-center gap-1 rounded-full border border-white/10 bg-white/[0.04] p-1 sm:flex">
            {STUDENT_NAV.map((item) => (
              <NavLink key={item.to} to={item.to} end={item.end} className={pillClass}>
                <span className="text-xs opacity-80">{item.icon}</span>
                {item.label}
              </NavLink>
            ))}
          </nav>

          <div className="flex items-center gap-2">
            <NavLink to="/interview/new" className="btn btn-teal !rounded-full !py-2 hidden sm:inline-flex">
              ▶ Practice
            </NavLink>
            {user && (
              <div className="flex items-center gap-2 rounded-full border border-teal-200/15 bg-white/[0.04] py-1 pl-1 pr-2">
                <Avatar name={user.name} size={30} />
                <div className="hidden leading-tight sm:block">
                  <p className="text-xs font-semibold text-slate-100">{user.name}</p>
                  <p className="text-[10px] font-semibold uppercase tracking-wider text-teal-300">Student</p>
                </div>
                <button
                  type="button"
                  title="Sign out"
                  className="ml-1 rounded-full px-2 py-1 text-xs text-slate-400 transition hover:bg-white/10 hover:text-slate-100"
                  onClick={async () => {
                    await logout()
                    navigate('/login', { replace: true })
                  }}
                >
                  ⎋
                </button>
              </div>
            )}
          </div>
        </div>

        {/* mobile nav */}
        <nav className="flex gap-1 overflow-x-auto px-4 pb-3 sm:hidden">
          {STUDENT_NAV.map((item) => (
            <NavLink key={item.to} to={item.to} end={item.end} className={pillClass}>
              <span className="text-xs opacity-80">{item.icon}</span>
              {item.label}
            </NavLink>
          ))}
        </nav>
      </header>

      <main className="mx-auto w-full max-w-5xl flex-1 px-4 py-8">
        <Outlet context={{ meta: null }} />
      </main>

      <footer className="no-print border-t border-teal-200/10 px-4 py-5 text-center text-xs text-slate-500">
        Your practice space — every answer is graded the moment you submit it.
      </footer>
    </div>
  )
}
