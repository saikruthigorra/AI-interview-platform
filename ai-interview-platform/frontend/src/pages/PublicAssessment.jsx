import { useCallback, useEffect, useMemo, useRef, useState } from 'react'
import { Link, useParams } from 'react-router-dom'
import { api } from '../lib/api.js'
import { Badge, DiffBadge, ErrorNote, ScoreBar, ScoreRing, Spinner } from '../components/ui.jsx'

const tokenKey = (code) => `iiq_oa_${code.toUpperCase()}`

function countdown(expiresAt) {
  if (!expiresAt) return null
  const remaining = Math.max(0, Math.floor((new Date(expiresAt).getTime() - Date.now()) / 1000))
  const mm = String(Math.floor(remaining / 60)).padStart(2, '0')
  const ss = String(remaining % 60).padStart(2, '0')
  return { remaining, label: `${mm}:${ss}` }
}

function Shell({ children }) {
  return (
    <div className="mx-auto flex min-h-screen w-full max-w-4xl flex-col px-4 py-8 lg:py-12">
      <header className="mb-8 flex items-center gap-3">
        <span className="flex h-10 w-10 items-center justify-center rounded-xl bg-gradient-to-br from-indigo-500 to-teal-400 text-lg font-black text-ink-950">
          iQ
        </span>
        <div>
          <p className="text-base font-bold tracking-tight text-slate-50">InterviewIQ</p>
          <p className="text-[11px] uppercase tracking-widest text-slate-500">Online assessment</p>
        </div>
      </header>
      <main className="flex-1">{children}</main>
      <footer className="mt-10 text-center text-xs text-slate-600">
        Powered by the InterviewIQ AI interview platform · <Link to="/login" className="hover:text-slate-400">Recruiter sign in</Link>
      </footer>
    </div>
  )
}

function Closed({ info, fallback }) {
  return (
    <div className="card p-10 text-center">
      <p className="text-2xl">🔒</p>
      <h1 className="mt-3 text-xl font-bold text-slate-100">{info?.title || 'Assessment unavailable'}</h1>
      <p className="mt-2 text-sm text-slate-400">
        {fallback || info?.reason || 'This link is not accepting attempts right now.'}
      </p>
    </div>
  )
}

function Intro({ info, onStart, busy, error, resumable, onResume }) {
  const [form, setForm] = useState({ name: '', email: '' })
  const set = (key) => (event) => setForm((prev) => ({ ...prev, [key]: event.target.value }))

  return (
    <div className="space-y-6">
      <div className="card p-6 lg:p-8">
        <div className="flex flex-wrap items-center gap-2 text-[11px] uppercase tracking-widest text-slate-500">
          <span>{info.company || 'InterviewIQ'}</span>
          {info.role && <Badge>{info.role}</Badge>}
          {info.difficulty && <DiffBadge difficulty={info.difficulty} />}
        </div>
        <h1 className="mt-3 text-2xl font-black tracking-tight text-slate-50 lg:text-3xl">{info.title}</h1>
        {info.description && <p className="mt-3 whitespace-pre-wrap text-sm text-slate-300">{info.description}</p>}

        <dl className="mt-6 grid gap-3 sm:grid-cols-4">
          {[
            ['Questions', info.questionCount],
            ['Time limit', `${info.durationMinutes} min`],
            ['Pass mark', `${info.passingScore}%`],
            ['Skills', info.skills?.length ? info.skills.join(', ') : 'General'],
          ].map(([label, value]) => (
            <div key={label} className="rounded-xl border border-white/10 bg-white/[0.03] p-3">
              <dt className="text-[10px] uppercase tracking-widest text-slate-500">{label}</dt>
              <dd className="mt-1 text-sm font-semibold text-slate-100">{value}</dd>
            </div>
          ))}
        </dl>

        <ul className="mt-6 space-y-2 text-xs text-slate-400">
          <li>• One question at a time. Your answer is graded by AI the moment you submit it.</li>
          <li>• Scores and feedback stay hidden until you finish, so the rest of the paper stays fair.</li>
          <li>• The timer starts when you press start and cannot be paused. The attempt submits itself when time runs out.</li>
          <li>• You can close this tab and come back on the same device — your progress is kept.</li>
        </ul>
      </div>

      <div className="card p-6 lg:p-8">
        <h2 className="text-lg font-bold text-slate-100">Your details</h2>
        <p className="mt-1 text-sm text-slate-400">
          No account needed. We use your name and email to show the recruiter who completed the assessment.
        </p>
        <form
          className="mt-4 grid gap-4 sm:grid-cols-2"
          onSubmit={(event) => {
            event.preventDefault()
            onStart(form)
          }}
        >
          <label className="block">
            <span className="label">Full name</span>
            <input className="input" required value={form.name} onChange={set('name')} placeholder="Your name" />
          </label>
          <label className="block">
            <span className="label">Email</span>
            <input className="input" type="email" required value={form.email} onChange={set('email')} placeholder="you@example.com" />
          </label>
          {error && (
            <div className="sm:col-span-2">
              <ErrorNote message={error} />
            </div>
          )}
          <div className="sm:col-span-2 flex flex-wrap items-center gap-3">
            <button className="btn btn-primary" type="submit" disabled={busy}>
              {busy ? 'Starting…' : 'Start assessment'}
            </button>
            {resumable && (
              <button type="button" className="btn" onClick={onResume} disabled={busy}>
                Resume my attempt
              </button>
            )}
          </div>
        </form>
      </div>
    </div>
  )
}

function Runner({ attempt, onAnswer, onSubmit, busy, error, secondsLeft }) {
  const [draft, setDraft] = useState('')
  const startedAt = useRef(Date.now())

  const current = useMemo(() => attempt.questions.find((q) => !q.answered) ?? null, [attempt])
  const progress = attempt.totalQuestions ? (attempt.answeredCount / attempt.totalQuestions) * 100 : 0
  const urgent = secondsLeft != null && secondsLeft < 120

  const submit = (event) => {
    event.preventDefault()
    if (!draft.trim()) return
    onAnswer(draft.trim(), Math.round((Date.now() - startedAt.current) / 1000))
    setDraft('')
    startedAt.current = Date.now()
  }

  if (!current) {
    return (
      <div className="card p-8 text-center">
        <Spinner label="Wrapping up…" />
      </div>
    )
  }

  return (
    <div className="space-y-5">
      <div className="card flex flex-wrap items-center justify-between gap-4 p-4">
        <div>
          <p className="text-[11px] uppercase tracking-widest text-slate-500">{attempt.assessmentTitle}</p>
          <p className="text-sm font-semibold text-slate-100">
            Question {current.position + 1} of {attempt.totalQuestions}
          </p>
        </div>
        <div className="flex items-center gap-4">
          <div className="min-w-[200px]">
            <div className="mb-1 flex items-center justify-between text-[11px] text-slate-400">
              <span>Progress</span>
              <span className="font-semibold text-slate-200">{attempt.answeredCount}/{attempt.totalQuestions}</span>
            </div>
            <div className="h-2 overflow-hidden rounded-full bg-white/5">
              <div className="h-full rounded-full bg-gradient-to-r from-indigo-500 to-teal-400" style={{ width: `${progress}%` }} />
            </div>
          </div>
          <div
            className={`rounded-xl border px-3 py-2 text-center font-mono text-lg font-bold ${
              urgent ? 'border-rose-400/40 bg-rose-400/10 text-rose-200' : 'border-white/10 bg-white/5 text-slate-100'
            }`}
          >
            {secondsLeft == null ? '--:--' : countdown(attempt.expiresAt)?.label}
            <span className="block text-[10px] font-normal uppercase tracking-widest text-slate-500">time left</span>
          </div>
        </div>
      </div>

      <form className="card space-y-4 p-6" onSubmit={submit}>
        <div className="flex flex-wrap items-center gap-2 text-[11px] uppercase tracking-wider text-slate-500">
          <span className="font-mono text-slate-400">Q{current.position + 1}</span>
          {current.topic && <Badge>{current.topic}</Badge>}
          <DiffBadge difficulty={current.difficulty} />
        </div>
        <p className="text-lg font-semibold leading-relaxed text-slate-50">{current.text}</p>
        <label className="block">
          <span className="label">Your answer</span>
          <textarea
            className="input min-h-[200px]"
            required
            value={draft}
            onChange={(event) => setDraft(event.target.value)}
            placeholder="Explain your reasoning the way you would to an interviewer. Structure, trade-offs and examples score best."
          />
        </label>
        {error && <ErrorNote message={error} />}
        <div className="flex flex-wrap items-center justify-between gap-3">
          <p className="text-xs text-slate-500">The AI grades this answer on submit. You'll see the analysis at the end.</p>
          <div className="flex gap-2">
            {attempt.answeredCount > 0 && (
              <button type="button" className="btn" onClick={onSubmit} disabled={busy}>
                Finish early & see analysis
              </button>
            )}
            <button className="btn btn-primary" type="submit" disabled={busy || !draft.trim()}>
              {busy ? 'Submitting…' : 'Submit answer →'}
            </button>
          </div>
        </div>
      </form>
    </div>
  )
}

function Results({ attempt }) {
  const passed = attempt.passed
  return (
    <div className="space-y-5">
      <div className="card p-6">
        <div className="flex flex-wrap items-center gap-6">
          <ScoreRing value={attempt.overallScore ?? 0} caption={`pass mark ${attempt.passingScore}%`} />
          <div className="min-w-0 flex-1">
            <p className="text-[11px] uppercase tracking-widest text-slate-500">{attempt.company || 'InterviewIQ'} · {attempt.assessmentTitle}</p>
            <h1 className="mt-1 text-2xl font-black tracking-tight text-slate-50">
              {passed == null ? 'Attempt submitted' : passed ? 'Nice work — you passed 🎉' : 'Attempt submitted'}
            </h1>
            <p className="mt-1 text-sm text-slate-400">
              Thanks {attempt.candidateName?.split(' ')[0] || 'there'} — your responses were graded by AI and sent to the recruiter.
            </p>
            <div className="mt-3 flex flex-wrap items-center gap-3">
              <span
                className={`rounded-full border px-3 py-1 text-xs font-semibold ${
                  passed ? 'border-teal-400/40 bg-teal-400/10 text-teal-200' : 'border-rose-400/40 bg-rose-400/10 text-rose-200'
                }`}
              >
                {passed ? 'Above pass mark' : `Below pass mark (${attempt.passingScore}%)`}
              </span>
              <span className="text-xs text-slate-400">
                {attempt.answeredCount}/{attempt.totalQuestions} answered · engine {attempt.engine}
              </span>
            </div>
          </div>
        </div>
      </div>

      {attempt.aiSummary && (
        <div className="card p-5">
          <p className="mb-2 text-[11px] uppercase tracking-widest text-indigo-300">AI performance summary</p>
          <p className="text-sm text-slate-200">{attempt.aiSummary}</p>
        </div>
      )}

      <div className="grid gap-5 sm:grid-cols-2">
        {attempt.strengths?.length > 0 && (
          <div className="card p-5">
            <p className="mb-2 text-[11px] uppercase tracking-widest text-teal-300">What worked</p>
            <ul className="space-y-1 text-sm text-slate-300">
              {attempt.strengths.map((s, i) => (
                <li key={i}>• {s}</li>
              ))}
            </ul>
          </div>
        )}
        {attempt.gaps?.length > 0 && (
          <div className="card p-5">
            <p className="mb-2 text-[11px] uppercase tracking-widest text-amber-300">What to improve</p>
            <ul className="space-y-1 text-sm text-slate-300">
              {attempt.gaps.map((s, i) => (
                <li key={i}>• {s}</li>
              ))}
            </ul>
          </div>
        )}
      </div>

      <div className="space-y-4">
        {attempt.answers.map((answer, index) => (
          <div key={index} className="card p-5">
            <div className="flex flex-wrap items-center justify-between gap-2">
              <div className="flex flex-wrap items-center gap-2 text-[11px] uppercase tracking-wider text-slate-500">
                <span className="font-mono text-slate-400">Q{answer.position + 1}</span>
                {answer.topic && <Badge>{answer.topic}</Badge>}
                <DiffBadge difficulty={answer.difficulty} />
              </div>
              <span className="rounded-lg bg-white/5 px-2 py-1 text-sm font-bold text-slate-100">
                {answer.analysis?.score ?? '—'}/100
              </span>
            </div>
            <p className="mt-3 text-sm font-semibold text-slate-100">{answer.questionText}</p>
            <p className="mt-2 whitespace-pre-wrap rounded-lg border border-white/5 bg-ink-950/50 p-3 text-xs text-slate-300">
              {answer.answerText}
            </p>

            {answer.analysis && (
              <>
                <div className="mt-4 grid gap-3 sm:grid-cols-4">
                  <ScoreBar label="Technical" value={answer.analysis.technicalScore ?? 0} />
                  <ScoreBar label="Depth" value={answer.analysis.depthScore ?? 0} />
                  <ScoreBar label="Communication" value={answer.analysis.communicationScore ?? 0} />
                  <ScoreBar label="Clarity" value={answer.analysis.clarityScore ?? 0} />
                </div>
                {answer.analysis.feedback && <p className="mt-3 text-sm text-slate-300">{answer.analysis.feedback}</p>}
                <div className="mt-4 grid gap-4 sm:grid-cols-2">
                  {answer.analysis.strengths?.length > 0 && (
                    <div className="text-xs text-slate-400">
                      <p className="mb-1 font-semibold text-teal-300">Did well</p>
                      <ul className="space-y-1">
                        {answer.analysis.strengths.map((s, i) => (
                          <li key={i}>• {s}</li>
                        ))}
                      </ul>
                    </div>
                  )}
                  {answer.analysis.missingPoints?.length > 0 && (
                    <div className="text-xs text-slate-400">
                      <p className="mb-1 font-semibold text-amber-300">Missed points</p>
                      <ul className="space-y-1">
                        {answer.analysis.missingPoints.map((s, i) => (
                          <li key={i}>• {s}</li>
                        ))}
                      </ul>
                    </div>
                  )}
                </div>
                {answer.analysis.idealAnswer && (
                  <details className="mt-3 text-xs">
                    <summary className="cursor-pointer text-slate-400 hover:text-slate-200">Model answer</summary>
                    <p className="mt-2 whitespace-pre-wrap rounded-lg border border-white/5 bg-ink-950/50 p-3 text-slate-300">
                      {answer.analysis.idealAnswer}
                    </p>
                  </details>
                )}
              </>
            )}
          </div>
        ))}
      </div>

      <p className="text-center text-sm text-slate-500">You can close this tab — the recruiter already has your results.</p>
    </div>
  )
}

export default function PublicAssessment() {
  const { code } = useParams()
  const [info, setInfo] = useState(null)
  const [attempt, setAttempt] = useState(null)
  const [error, setError] = useState('')
  const [actionError, setActionError] = useState('')
  const [busy, setBusy] = useState(false)
  const [secondsLeft, setSecondsLeft] = useState(null)
  const [loading, setLoading] = useState(true)

  const savedToken = useMemo(() => localStorage.getItem(tokenKey(code)), [code])

  const loadAttempt = useCallback(
    async (token) => {
      const data = await api.publicOa.attempt(token)
      setAttempt(data)
      return data
    },
    [],
  )

  useEffect(() => {
    let cancelled = false
    ;(async () => {
      try {
        const data = await api.publicOa.info(code)
        if (cancelled) return
        setInfo(data)
        if (savedToken) {
          try {
            await loadAttempt(savedToken)
          } catch {
            localStorage.removeItem(tokenKey(code))
          }
        }
      } catch (e) {
        if (!cancelled) setError(e.message)
      } finally {
        if (!cancelled) setLoading(false)
      }
    })()
    return () => {
      cancelled = true
    }
  }, [code, savedToken, loadAttempt])

  // Timer: ticks while in progress and auto-submits when time runs out.
  useEffect(() => {
    if (!attempt || attempt.completed || !attempt.expiresAt) {
      setSecondsLeft(null)
      return
    }
    const tick = () => {
      const cd = countdown(attempt.expiresAt)
      setSecondsLeft(cd?.remaining ?? null)
      if (cd && cd.remaining <= 0) {
        api.publicOa
          .submit(attempt.attemptToken)
          .then(setAttempt)
          .catch(() => {})
      }
    }
    tick()
    const timer = setInterval(tick, 1000)
    return () => clearInterval(timer)
  }, [attempt])

  const start = async (form) => {
    setBusy(true)
    setActionError('')
    try {
      const data = await api.publicOa.start(code, form)
      localStorage.setItem(tokenKey(code), data.attemptToken)
      setAttempt(data)
    } catch (e) {
      setActionError(e.message)
    } finally {
      setBusy(false)
    }
  }

  const resume = async () => {
    setBusy(true)
    setActionError('')
    try {
      await loadAttempt(savedToken)
    } catch (e) {
      setActionError(e.message)
      localStorage.removeItem(tokenKey(code))
    } finally {
      setBusy(false)
    }
  }

  const answer = async (answerText, timeTakenSeconds) => {
    setBusy(true)
    setActionError('')
    try {
      const ack = await api.publicOa.answer(attempt.attemptToken, { answerText, timeTakenSeconds })
      const updated = await loadAttempt(attempt.attemptToken)
      if (ack.completed) {
        setAttempt(updated)
      }
    } catch (e) {
      setActionError(e.message)
    } finally {
      setBusy(false)
    }
  }

  const finish = async () => {
    setBusy(true)
    setActionError('')
    try {
      setAttempt(await api.publicOa.submit(attempt.attemptToken))
    } catch (e) {
      setActionError(e.message)
    } finally {
      setBusy(false)
    }
  }

  if (loading) {
    return (
      <Shell>
        <div className="card p-10 text-center">
          <Spinner label="Loading the assessment…" />
        </div>
      </Shell>
    )
  }

  if (error) {
    return (
      <Shell>
        <Closed fallback={error} />
      </Shell>
    )
  }

  return (
    <Shell>
      {attempt ? (
        attempt.completed ? (
          <Results attempt={attempt} />
        ) : (
          <Runner
            attempt={attempt}
            onAnswer={answer}
            onSubmit={finish}
            busy={busy}
            error={actionError}
            secondsLeft={secondsLeft}
          />
        )
      ) : info && !info.openToAttempts ? (
        <Closed info={info} />
      ) : info ? (
        <Intro
          info={info}
          onStart={start}
          onResume={resume}
          resumable={Boolean(savedToken)}
          busy={busy}
          error={actionError}
        />
      ) : null}
    </Shell>
  )
}
