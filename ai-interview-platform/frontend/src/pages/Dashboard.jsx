import { useEffect, useState } from 'react'
import { Link, useNavigate } from 'react-router-dom'
import { useAuth } from '../auth/AuthContext.jsx'
import { api } from '../lib/api.js'
import { relativeTime, scoreTone, toneBar, toneText } from '../lib/format.js'
import { Avatar, Card, ErrorNote, ScoreRing, SectionHeader, Spinner, StatCard, StatusPill } from '../components/ui.jsx'

/**
 * One route, two homes — and two different looks.
 *   student           → teal practice hub (matches the student shell)
 *   recruiter / admin → the indigo hiring command centre
 */
export default function Dashboard() {
  const { user, isRecruiter } = useAuth()
  return isRecruiter ? <RecruitingHome /> : <StudentHome name={user?.name} />
}

/* ─────────────────────────── Student: practice hub ─────────────────────────── */

function StudentHome({ name }) {
  const [code, setCode] = useState('')
  const navigate = useNavigate()
  const firstName = (name || 'there').split(' ')[0]

  const openAssessment = (event) => {
    event.preventDefault()
    const clean = code.trim().replace(/^.*\/oa\//i, '').toUpperCase()
    if (clean) navigate(`/oa/${clean}`)
  }

  return (
    <div className="fade-in space-y-6">
      <div className="rounded-3xl border border-teal-200/15 bg-gradient-to-br from-teal-400/[0.12] via-transparent to-emerald-400/[0.06] p-6 sm:p-8">
        <p className="text-[11px] font-semibold uppercase tracking-widest text-teal-300">Welcome back</p>
        <h1 className="mt-1 text-2xl font-bold tracking-tight sm:text-3xl">
          Hi {firstName} 👋 ready to <span className="bg-gradient-to-r from-teal-200 to-emerald-300 bg-clip-text text-transparent">practise</span>?
        </h1>
        <p className="mt-2 max-w-2xl text-sm text-slate-300">
          Run a practice interview, answer AI-generated questions and get scored feedback the moment you submit.
          Nothing here is shared with anyone else.
        </p>
      </div>

      <div className="grid gap-6 lg:grid-cols-2">
        <div className="student-card flex flex-col justify-between gap-5 p-6">
          <div>
            <SectionHeader
              title="Practice interview"
              subtitle="Questions are written for your skills and target role — answer at your own pace."
            />
            <ul className="space-y-2 text-sm text-slate-300">
              <li className="flex gap-2">
                <span className="text-teal-300">✓</span> Pick your target role, skills and difficulty
              </li>
              <li className="flex gap-2">
                <span className="text-teal-300">✓</span> Every answer is scored instantly, with what was missing
              </li>
              <li className="flex gap-2">
                <span className="text-teal-300">✓</span> Finish with an AI debrief: strengths, gaps and a model answer
              </li>
            </ul>
          </div>
          <Link to="/interview/new" className="btn btn-teal w-full justify-center">
            ▶ Start a practice interview
          </Link>
        </div>

        <div className="student-card flex flex-col justify-between gap-5 p-6">
          <div>
            <SectionHeader
              title="Take a hosted assessment"
              subtitle="Got an OA link or code from a recruiter? No account needed — paste it and go."
            />
            <form onSubmit={openAssessment} className="flex gap-2">
              <input
                className="input flex-1 font-mono uppercase tracking-widest"
                placeholder="e.g. DEMO26"
                value={code}
                maxLength={16}
                onChange={(e) => setCode(e.target.value)}
              />
              <button type="submit" className="btn btn-teal shrink-0" disabled={!code.trim()}>
                Open
              </button>
            </form>
            <p className="mt-2 text-xs text-slate-500">
              The code lives at the end of the link your recruiter shared — <span className="font-mono">/oa/CODE</span>.
            </p>
          </div>
          <div className="rounded-2xl border border-teal-400/20 bg-teal-400/[0.07] p-3 text-xs text-teal-100/80">
            🌱 Tip: structure answers as <span className="font-semibold">Situation → Action → Result</span> — the
            scorer rewards concrete detail.
          </div>
        </div>
      </div>

      <div className="student-card p-6">
        <SectionHeader title="How the AI grades you" subtitle="Same pipeline recruiters use — no waiting for a human reviewer" />
        <div className="grid gap-4 sm:grid-cols-3">
          {[
            { icon: '🎯', title: 'Rubric-matched', text: 'Your answer is compared against the key points the question expects, plus your skills.' },
            { icon: '⚡', title: 'Instant feedback', text: 'Each answer comes back scored with strengths and exactly what was missing.' },
            { icon: '📋', title: 'Final debrief', text: 'A summary report with overall score, revision list and ideal answers.' },
          ].map((f) => (
            <div key={f.title} className="rounded-2xl border border-teal-200/10 bg-white/[0.03] p-4">
              <p className="text-lg">{f.icon}</p>
              <p className="mt-2 text-sm font-semibold text-slate-100">{f.title}</p>
              <p className="mt-1 text-xs text-slate-400">{f.text}</p>
            </div>
          ))}
        </div>
      </div>
    </div>
  )
}

/* ───────────────────── Recruiter / admin: command centre ───────────────────── */

function RecruitingHome() {
  const [stats, setStats] = useState(null)
  const [error, setError] = useState('')
  const [loading, setLoading] = useState(true)

  const load = () => {
    setLoading(true)
    setError('')
    api
      .stats()
      .then(setStats)
      .catch((e) => setError(e.message))
      .finally(() => setLoading(false))
  }

  useEffect(load, [])

  if (loading) return <Spinner label="Loading dashboard…" />

  return (
    <div className="fade-in space-y-6">
      <div className="flex flex-wrap items-end justify-between gap-4">
        <div>
          <h1 className="text-2xl font-bold tracking-tight">
            Interview <span className="gradient-text">command centre</span>
          </h1>
          <p className="mt-1 text-sm text-slate-400">
            {stats?.totalQuestions ?? 0} questions in the bank · {stats?.aiGeneratedQuestions ?? 0} AI-generated ·{' '}
            {stats?.inProgressSessions ?? 0} interview(s) in flight
          </p>
        </div>
        <div className="flex gap-2">
          <Link to="/candidates" className="btn btn-ghost">
            Manage candidates
          </Link>
          <Link to="/interview/new" className="btn btn-primary">
            + New interview
          </Link>
        </div>
      </div>

      <ErrorNote message={error} onRetry={load} />

      <div className="grid gap-4 sm:grid-cols-2 xl:grid-cols-4">
        <StatCard label="Candidates" value={stats?.totalCandidates ?? 0} hint="people in the pipeline" accent="indigo" icon="👥" />
        <StatCard
          label="Interviews run"
          value={stats?.totalSessions ?? 0}
          hint={`${stats?.completedSessions ?? 0} completed`}
          accent="sky"
          icon="🎯"
        />
        <StatCard
          label="Average score"
          value={stats?.averageScore == null ? '—' : stats.averageScore}
          hint="across completed interviews"
          accent="teal"
          icon="📈"
        />
        <StatCard
          label="In progress"
          value={stats?.inProgressSessions ?? 0}
          hint="sessions waiting to finish"
          accent="amber"
          icon="⏳"
        />
      </div>

      <div className="grid gap-6 lg:grid-cols-3">
        <Card className="lg:col-span-2">
          <SectionHeader
            title="Recent interviews"
            subtitle="Latest completed sessions, newest first"
            action={
              <Link to="/candidates" className="text-xs font-semibold text-indigo-300 hover:text-indigo-200">
                View all →
              </Link>
            }
          />
          {stats?.recent?.length ? (
            <div className="divide-y divide-white/5">
              {stats.recent.map((s) => (
                <div key={s.id} className="flex items-center gap-4 py-3">
                  <Avatar name={s.candidateName} size={40} />
                  <div className="min-w-0 flex-1">
                    <p className="truncate text-sm font-semibold text-slate-100">{s.candidateName}</p>
                    <p className="truncate text-xs text-slate-400">
                      {s.role} · {s.difficulty.toLowerCase()} · {relativeTime(s.completedAt || s.startedAt)}
                    </p>
                  </div>
                  <div className="hidden sm:block">
                    <StatusPill status={s.status} />
                  </div>
                  <ScoreRing value={s.overallScore} size={56} stroke={6} />
                  <Link to={`/interview/${s.id}/report`} className="btn btn-ghost !px-3 !py-1.5 text-xs">
                    Report
                  </Link>
                </div>
              ))}
            </div>
          ) : (
            <p className="py-8 text-center text-sm text-slate-400">No completed interviews yet — run your first one.</p>
          )}
        </Card>

        <div className="space-y-6">
          <Card>
            <SectionHeader title="Score distribution" subtitle="Completed interviews by band" />
            <div className="space-y-3">
              {(stats?.scoreBands ?? []).map((band) => {
                const max = Math.max(1, ...(stats?.scoreBands ?? []).map((b) => b.count))
                return (
                  <div key={band.label}>
                    <div className="mb-1 flex justify-between text-xs">
                      <span className="text-slate-400">{band.label}</span>
                      <span className="font-semibold text-slate-200">{band.count}</span>
                    </div>
                    <div className="h-2 overflow-hidden rounded-full bg-white/10">
                      <div
                        className="h-full rounded-full bg-gradient-to-r from-indigo-400 to-teal-300"
                        style={{ width: `${(band.count / max) * 100}%`, transition: 'width .5s ease' }}
                      />
                    </div>
                  </div>
                )
              })}
              {!stats?.scoreBands?.length && <p className="text-sm text-slate-400">No data yet.</p>}
            </div>
          </Card>

          <Card>
            <SectionHeader title="Readiness by role" subtitle="Average score per role interviewed" />
            <div className="space-y-3">
              {(stats?.byRole ?? []).map((row) => {
                const tone = scoreTone(row.averageScore)
                return (
                  <div key={row.role} className="flex items-center gap-3">
                    <span className="min-w-0 flex-1 truncate text-sm text-slate-300">{row.role}</span>
                    <span className="text-xs text-slate-500">{row.sessions}×</span>
                    <span className={`text-sm font-semibold ${toneText[tone]}`}>{row.averageScore}</span>
                    <div className="h-1.5 w-16 overflow-hidden rounded-full bg-white/10">
                      <div
                        className={`h-full rounded-full ${toneBar[tone]}`}
                        style={{ width: `${row.averageScore}%` }}
                      />
                    </div>
                  </div>
                )
              })}
              {!stats?.byRole?.length && <p className="text-sm text-slate-400">No completed interviews yet.</p>}
            </div>
          </Card>
        </div>
      </div>

      <div className="grid gap-4 sm:grid-cols-2 xl:grid-cols-4">
        <Link to="/interview/new" className="card card-hover p-5">
          <p className="text-sm font-semibold text-slate-100">▶ Start an interview</p>
          <p className="mt-1 text-xs text-slate-400">Pick a candidate, role and difficulty. Questions are generated on the spot.</p>
        </Link>
        <Link to="/questions" className="card card-hover p-5">
          <p className="text-sm font-semibold text-slate-100">☰ Curate the question bank</p>
          <p className="mt-1 text-xs text-slate-400">Add your own questions with rubric keywords the scorer will grade against.</p>
        </Link>
        <Link to="/candidates" className="card card-hover p-5">
          <p className="text-sm font-semibold text-slate-100">◉ Track candidates</p>
          <p className="mt-1 text-xs text-slate-400">History, scores and reports per person so nobody slips through.</p>
        </Link>
        <Link to="/assessments" className="card card-hover p-5">
          <p className="text-sm font-semibold text-slate-100">▣ Host an online assessment</p>
          <p className="mt-1 text-xs text-slate-400">Create an OA, let the AI write the paper, share the link and read the cohort analysis.</p>
        </Link>
      </div>
    </div>
  )
}
