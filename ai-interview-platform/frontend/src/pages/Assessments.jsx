import { useCallback, useEffect, useMemo, useState } from 'react'
import { Link } from 'react-router-dom'
import { api } from '../lib/api.js'
import {
  Badge,
  Card,
  DiffBadge,
  EmptyState,
  ErrorNote,
  Modal,
  ScoreBar,
  ScoreRing,
  SectionHeader,
  Spinner,
  StatCard,
  StatusPill,
} from '../components/ui.jsx'

const DIFFICULTIES = ['EASY', 'MEDIUM', 'HARD']
const LEVELS = ['STUDENT', 'INTERN', 'JUNIOR', 'MID', 'SENIOR']

const EMPTY_FORM = {
  title: '',
  company: '',
  description: '',
  role: '',
  skills: '',
  difficulty: 'MEDIUM',
  level: 'JUNIOR',
  questionCount: 5,
  durationMinutes: 45,
  passingScore: 60,
}

function fmt(date) {
  if (!date) return '—'
  return new Date(date).toLocaleString(undefined, {
    day: 'numeric',
    month: 'short',
    hour: '2-digit',
    minute: '2-digit',
  })
}

function fmtDay(date) {
  if (!date) return '—'
  return new Date(date).toLocaleDateString(undefined, { day: 'numeric', month: 'short', year: 'numeric' })
}

function copy(text) {
  navigator.clipboard?.writeText(text).catch(() => {})
}

// ── create form ─────────────────────────────────────────────────────────────

function AssessmentForm({ initial, onSubmit, onCancel, busy, error }) {
  const [form, setForm] = useState({ ...EMPTY_FORM, ...initial })
  const set = (key) => (event) => setForm((prev) => ({ ...prev, [key]: event.target.value }))

  const submit = (event) => {
    event.preventDefault()
    onSubmit({
      ...form,
      questionCount: Number(form.questionCount),
      durationMinutes: Number(form.durationMinutes),
      passingScore: Number(form.passingScore),
      skills: form.skills
        .split(',')
        .map((s) => s.trim())
        .filter(Boolean),
    })
  }

  return (
    <form onSubmit={submit} className="space-y-4">
      <div className="grid gap-4 sm:grid-cols-2">
        <label className="block">
          <span className="label">Assessment title</span>
          <input className="input" required value={form.title} onChange={set('title')} placeholder="Campus Placement OA — Backend" />
        </label>
        <label className="block">
          <span className="label">Company</span>
          <input className="input" value={form.company} onChange={set('company')} placeholder="Acme Corp" />
        </label>
        <label className="block">
          <span className="label">Role</span>
          <input className="input" required value={form.role} onChange={set('role')} placeholder="Backend Engineer" />
        </label>
        <label className="block">
          <span className="label">Skills (comma separated — drives the AI)</span>
          <input className="input" value={form.skills} onChange={set('skills')} placeholder="Java, SQL, System Design" />
        </label>
        <label className="block">
          <span className="label">Difficulty</span>
          <select className="input" value={form.difficulty} onChange={set('difficulty')}>
            {DIFFICULTIES.map((d) => (
              <option key={d}>{d}</option>
            ))}
          </select>
        </label>
        <label className="block">
          <span className="label">Candidate level</span>
          <select className="input" value={form.level} onChange={set('level')}>
            {LEVELS.map((l) => (
              <option key={l}>{l}</option>
            ))}
          </select>
        </label>
        <label className="block">
          <span className="label">Questions (3–30)</span>
          <input className="input" type="number" min="3" max="30" required value={form.questionCount} onChange={set('questionCount')} />
        </label>
        <label className="block">
          <span className="label">Duration, minutes (5–240)</span>
          <input className="input" type="number" min="5" max="240" required value={form.durationMinutes} onChange={set('durationMinutes')} />
        </label>
        <label className="block">
          <span className="label">Pass mark (%)</span>
          <input className="input" type="number" min="0" max="100" required value={form.passingScore} onChange={set('passingScore')} />
        </label>
      </div>
      <label className="block">
        <span className="label">Description shown to candidates</span>
        <textarea className="input min-h-[96px]" value={form.description} onChange={set('description')} placeholder="Screening round for final-year students. 45 minutes, auto-graded with AI feedback." />
      </label>
      {error && <ErrorNote message={error} />}
      <div className="flex justify-end gap-2">
        <button type="button" className="btn" onClick={onCancel}>
          Cancel
        </button>
        <button type="submit" className="btn btn-primary" disabled={busy}>
          {busy ? 'Saving…' : initial?.id ? 'Save changes' : 'Create assessment'}
        </button>
      </div>
    </form>
  )
}

// ── AI question set ────────────────────────────────────────────────────────

function QuestionList({ assessment, questions, onGenerate, onRemove, busy, error }) {
  const frozen = assessment.status !== 'DRAFT'
  return (
    <div className="space-y-4">
      <div className="flex flex-wrap items-center justify-between gap-3">
        <p className="text-sm text-slate-400">
          {questions.length === 0
            ? 'No questions yet — the AI writes the whole set from the role, skills and difficulty.'
            : `${questions.length} question(s) generated for ${assessment.role}.`}
          {frozen && ' · frozen once published'}
        </p>
        <button className="btn btn-primary" onClick={onGenerate} disabled={busy || frozen}>
          {busy ? 'Generating…' : questions.length ? 'Regenerate with AI' : 'Generate with AI'}
        </button>
      </div>
      {error && <ErrorNote message={error} />}
      <ol className="space-y-3">
        {questions.map((q) => (
          <li key={q.assessmentQuestionId} className="rounded-xl border border-white/10 bg-white/[0.03] p-4">
            <div className="flex items-start justify-between gap-3">
              <div className="min-w-0">
                <div className="mb-1 flex flex-wrap items-center gap-2 text-[11px] uppercase tracking-wider text-slate-500">
                  <span className="font-mono text-slate-400">Q{q.position + 1}</span>
                  {q.topic && <Badge>{q.topic}</Badge>}
                  <DiffBadge difficulty={q.difficulty} />
                  {q.source === 'AI' && <Badge className="border-indigo-400/40 text-indigo-200">AI</Badge>}
                  {q.skillTags?.map((tag) => (
                    <span key={tag} className="rounded-full border border-teal-400/30 bg-teal-400/10 px-2 py-0.5 text-[10px] font-semibold normal-case tracking-normal text-teal-200">
                      {tag}
                    </span>
                  ))}
                </div>
                <p className="text-sm text-slate-100">{q.text}</p>
              </div>
              {!frozen && (
                <button className="btn btn-ghost !px-2 !py-1 text-xs" onClick={() => onRemove(q.assessmentQuestionId)}>
                  Remove
                </button>
              )}
            </div>
          </li>
        ))}
      </ol>
    </div>
  )
}

// ── attempts + per-attempt AI analysis ─────────────────────────────────────

function AttemptAnalysis({ assessmentId, sessionId, onClose }) {
  const [detail, setDetail] = useState(null)
  const [error, setError] = useState('')

  useEffect(() => {
    api.assessments
      .attempt(assessmentId, sessionId)
      .then(setDetail)
      .catch((e) => setError(e.message))
  }, [assessmentId, sessionId])

  return (
    <Modal open title="AI analysis of this attempt" onClose={onClose}>
      {error && <ErrorNote message={error} />}
      {!detail && !error && <Spinner label="Loading the analysis…" />}
      {detail && (
        <div className="space-y-5">
          <div className="flex flex-wrap items-center gap-4 rounded-xl border border-white/10 bg-white/[0.03] p-4">
            <ScoreRing value={detail.attempt.score ?? 0} caption="overall" />
            <div className="min-w-0 flex-1">
              <p className="font-semibold text-slate-100">{detail.attempt.candidateName}</p>
              <p className="text-xs text-slate-400">{detail.attempt.candidateEmail}</p>
              <p className="mt-2 text-xs text-slate-400">
                {detail.attempt.answered}/{detail.attempt.total} answered · pass mark {detail.passingScore}% ·{' '}
                {detail.attempt.passed == null ? 'still in progress' : detail.attempt.passed ? 'PASS' : 'BELOW PASS MARK'}
              </p>
              <p className="mt-1 text-[11px] uppercase tracking-wider text-slate-500">engine: {detail.engine}</p>
            </div>
          </div>

          {detail.aiSummary && (
            <div className="rounded-xl border border-indigo-400/20 bg-indigo-500/5 p-4">
              <p className="mb-1 text-[11px] uppercase tracking-widest text-indigo-300">AI debrief</p>
              <p className="text-sm text-slate-200">{detail.aiSummary}</p>
            </div>
          )}

          <div className="grid gap-3 sm:grid-cols-2">
            {detail.strengths?.length > 0 && (
              <div className="rounded-xl border border-teal-400/20 bg-teal-400/5 p-3">
                <p className="mb-1 text-[11px] uppercase tracking-widest text-teal-300">Strengths</p>
                <ul className="space-y-1 text-xs text-slate-300">
                  {detail.strengths.map((s, i) => (
                    <li key={i}>• {s}</li>
                  ))}
                </ul>
              </div>
            )}
            {detail.gaps?.length > 0 && (
              <div className="rounded-xl border border-amber-400/20 bg-amber-400/5 p-3">
                <p className="mb-1 text-[11px] uppercase tracking-widest text-amber-300">Gaps</p>
                <ul className="space-y-1 text-xs text-slate-300">
                  {detail.gaps.map((s, i) => (
                    <li key={i}>• {s}</li>
                  ))}
                </ul>
              </div>
            )}
          </div>

          <div className="space-y-3">
            {detail.answers.map((a) => (
              <div key={a.answerId ?? a.position} className="rounded-xl border border-white/10 bg-white/[0.02] p-4">
                <div className="mb-2 flex flex-wrap items-center justify-between gap-2">
                  <div className="flex items-center gap-2 text-[11px] uppercase tracking-wider text-slate-500">
                    <span className="font-mono text-slate-400">Q{a.position + 1}</span>
                    {a.topic && <Badge>{a.topic}</Badge>}
                    <DiffBadge difficulty={a.difficulty} />
                    {a.timeTakenSeconds != null && <span>{a.timeTakenSeconds}s</span>}
                  </div>
                  <span className="rounded-lg bg-white/5 px-2 py-1 text-sm font-bold text-slate-100">{a.score}/100</span>
                </div>
                <p className="text-sm font-medium text-slate-100">{a.questionText}</p>
                <p className="mt-2 whitespace-pre-wrap rounded-lg border border-white/5 bg-ink-950/50 p-3 text-xs text-slate-300">
                  {a.answerText}
                </p>
                <div className="mt-3 grid gap-2 sm:grid-cols-4">
                  <ScoreBar label="Technical" value={a.technicalScore ?? 0} />
                  <ScoreBar label="Depth" value={a.depthScore ?? 0} />
                  <ScoreBar label="Communication" value={a.communicationScore ?? 0} />
                  <ScoreBar label="Clarity" value={a.clarityScore ?? 0} />
                </div>
                {a.feedback && <p className="mt-3 text-xs text-slate-300">{a.feedback}</p>}
                <div className="mt-3 grid gap-3 sm:grid-cols-2">
                  {a.strengths?.length > 0 && (
                    <div className="text-xs text-slate-400">
                      <p className="mb-1 font-semibold text-teal-300">Did well</p>
                      <ul className="space-y-1">
                        {a.strengths.map((s, i) => (
                          <li key={i}>• {s}</li>
                        ))}
                      </ul>
                    </div>
                  )}
                  {a.improvements?.length > 0 && (
                    <div className="text-xs text-slate-400">
                      <p className="mb-1 font-semibold text-amber-300">To improve</p>
                      <ul className="space-y-1">
                        {a.improvements.map((s, i) => (
                          <li key={i}>• {s}</li>
                        ))}
                      </ul>
                    </div>
                  )}
                </div>
                {a.idealAnswer && (
                  <details className="mt-3 text-xs">
                    <summary className="cursor-pointer text-slate-400 hover:text-slate-200">Model answer</summary>
                    <p className="mt-2 whitespace-pre-wrap rounded-lg border border-white/5 bg-ink-950/50 p-3 text-slate-300">
                      {a.idealAnswer}
                    </p>
                  </details>
                )}
              </div>
            ))}
          </div>
        </div>
      )}
    </Modal>
  )
}

function AttemptsTab({ assessment, onOpenAttempt }) {
  const [attempts, setAttempts] = useState(null)
  const [error, setError] = useState('')

  useEffect(() => {
    api.assessments
      .attempts(assessment.id)
      .then(setAttempts)
      .catch((e) => setError(e.message))
  }, [assessment.id])

  if (error) return <ErrorNote message={error} />
  if (!attempts) return <Spinner label="Loading attempts…" />
  if (attempts.length === 0)
    return (
      <EmptyState
        title="No attempts yet"
        hint={
          assessment.status === 'PUBLISHED'
            ? `Share ${assessment.sharePath} with candidates — attempts appear here the moment they start.`
            : 'Publish the assessment to start accepting attempts.'
        }
      />
    )

  return (
    <div className="overflow-x-auto">
      <table className="w-full text-sm">
        <thead>
          <tr className="border-b border-white/10 text-left text-[11px] uppercase tracking-wider text-slate-500">
            <th className="py-2 pr-4">Candidate</th>
            <th className="py-2 pr-4">Status</th>
            <th className="py-2 pr-4">Progress</th>
            <th className="py-2 pr-4">Score</th>
            <th className="py-2 pr-4">Result</th>
            <th className="py-2 pr-4">Started</th>
            <th className="py-2" />
          </tr>
        </thead>
        <tbody>
          {attempts.map((a) => (
            <tr key={a.sessionId} className="border-b border-white/5 last:border-0">
              <td className="py-3 pr-4">
                <p className="font-medium text-slate-100">{a.candidateName}</p>
                <p className="text-xs text-slate-500">{a.candidateEmail}</p>
              </td>
              <td className="py-3 pr-4">
                <StatusPill status={a.status} />
                {a.timedOut && <span className="ml-2 text-[11px] text-amber-300">time up</span>}
              </td>
              <td className="py-3 pr-4 text-slate-300">
                {a.answered}/{a.total}
              </td>
              <td className="py-3 pr-4 font-semibold text-slate-100">{a.score == null ? '—' : a.score.toFixed(1)}</td>
              <td className="py-3 pr-4">
                {a.passed == null ? (
                  <span className="text-xs text-slate-500">pending</span>
                ) : a.passed ? (
                  <span className="rounded-full border border-teal-400/40 bg-teal-400/10 px-2 py-0.5 text-[11px] font-semibold text-teal-200">
                    Passed
                  </span>
                ) : (
                  <span className="rounded-full border border-rose-400/40 bg-rose-400/10 px-2 py-0.5 text-[11px] font-semibold text-rose-200">
                    Below pass mark
                  </span>
                )}
              </td>
              <td className="py-3 pr-4 text-xs text-slate-400">{fmt(a.startedAt)}</td>
              <td className="py-3 text-right">
                <button className="btn btn-ghost !px-2 !py-1 text-xs" onClick={() => onOpenAttempt(a.sessionId)}>
                  AI analysis
                </button>
              </td>
            </tr>
          ))}
        </tbody>
      </table>
    </div>
  )
}

// ── cohort analytics ───────────────────────────────────────────────────────

function AnalyticsTab({ assessment }) {
  const [data, setData] = useState(null)
  const [error, setError] = useState('')

  useEffect(() => {
    api.assessments
      .analytics(assessment.id)
      .then(setData)
      .catch((e) => setError(e.message))
  }, [assessment.id])

  const maxBand = useMemo(
    () => (data?.distribution ? Math.max(1, ...data.distribution.map((b) => b.count)) : 1),
    [data],
  )

  if (error) return <ErrorNote message={error} />
  if (!data) return <Spinner label="Crunching the cohort…" />
  if (data.attempts === 0)
    return <EmptyState title="Nothing to analyse yet" hint="Cohort analytics appear once candidates attempt this OA." />

  return (
    <div className="space-y-6">
      <div className="grid gap-3 sm:grid-cols-2 xl:grid-cols-4">
        <StatCard label="Attempts" value={data.attempts} hint={`${data.completed} completed · ${data.inProgress} in progress`} icon="◉" />
        <StatCard label="Average score" value={data.averageScore == null ? '—' : data.averageScore.toFixed(1)} hint={`highest ${data.highestScore?.toFixed(1) ?? '—'}`} accent="teal" icon="◈" />
        <StatCard label="Pass rate" value={data.passRate == null ? '—' : `${Math.round(data.passRate)}%`} hint={`pass mark ${assessment.passingScore}%`} accent="amber" icon="✓" />
        <StatCard label="Avg time / answer" value={data.averageSecondsPerAnswer == null ? '—' : `${Math.round(data.averageSecondsPerAnswer)}s`} hint="across completed attempts" accent="indigo" icon="⏱" />
      </div>

      {data.aiCohortSummary && (
        <div className="rounded-xl border border-indigo-400/20 bg-indigo-500/5 p-4">
          <p className="mb-1 text-[11px] uppercase tracking-widest text-indigo-300">AI cohort read-out</p>
          <p className="text-sm text-slate-200">{data.aiCohortSummary}</p>
        </div>
      )}

      <div className="grid gap-6 lg:grid-cols-2">
        <Card className="p-5">
          <SectionHeader title="Score distribution" subtitle="How the cohort spread across the bands" />
          <div className="space-y-3">
            {data.distribution.map((band) => (
              <div key={band.label}>
                <div className="mb-1 flex items-center justify-between text-xs">
                  <span className="text-slate-300">{band.label}</span>
                  <span className="font-semibold text-slate-200">{band.count}</span>
                </div>
                <div className="h-2 overflow-hidden rounded-full bg-white/5">
                  <div
                    className="h-full rounded-full bg-gradient-to-r from-indigo-500 to-teal-400"
                    style={{ width: `${(band.count / maxBand) * 100}%` }}
                  />
                </div>
              </div>
            ))}
          </div>
        </Card>

        <Card className="p-5">
          <SectionHeader title="Leaderboard" subtitle="Ranked by score" />
          <ol className="space-y-2">
            {data.leaderboard.map((row, index) => (
              <li key={row.sessionId} className="flex items-center gap-3 rounded-lg border border-white/5 bg-white/[0.02] px-3 py-2">
                <span className="w-6 text-center text-xs font-bold text-slate-500">{index + 1}</span>
                <div className="min-w-0 flex-1">
                  <p className="truncate text-sm font-medium text-slate-100">{row.candidateName}</p>
                  <p className="truncate text-[11px] text-slate-500">{row.candidateEmail}</p>
                </div>
                <span className={`text-sm font-bold ${row.passed ? 'text-teal-300' : 'text-slate-300'}`}>
                  {row.score == null ? '—' : row.score.toFixed(1)}
                </span>
              </li>
            ))}
          </ol>
        </Card>
      </div>

      <Card className="p-5">
        <SectionHeader title="Per-question performance" subtitle="Where the cohort struggled — and whether the question discriminates" />
        <div className="space-y-3">
          {data.perQuestion.map((q) => (
            <div key={q.questionId} className="rounded-xl border border-white/10 bg-white/[0.02] p-4">
              <div className="mb-2 flex flex-wrap items-center justify-between gap-2">
                <div className="flex flex-wrap items-center gap-2 text-[11px] uppercase tracking-wider text-slate-500">
                  <span className="font-mono text-slate-400">Q{q.position + 1}</span>
                  <DiffBadge difficulty={q.difficulty} />
                  <span>{q.answered} answer(s)</span>
                </div>
                <span className="rounded-full border border-white/10 bg-white/5 px-2 py-0.5 text-[11px] text-slate-300">{q.verdict}</span>
              </div>
              <p className="text-sm text-slate-100">{q.text}</p>
              <div className="mt-3 grid gap-3 sm:grid-cols-2">
                <ScoreBar label="Avg score" value={q.averageScore ?? 0} />
                <div>
                  <div className="mb-1 flex items-center justify-between text-[11px] text-slate-400">
                    <span>Pass rate</span>
                    <span className="font-semibold text-slate-200">
                      {q.passRate == null ? '—' : `${Math.round(q.passRate)}%`}
                    </span>
                  </div>
                  <div className="h-2 overflow-hidden rounded-full bg-white/5">
                    <div className="h-full rounded-full bg-teal-400/70" style={{ width: `${(q.passRate ?? 0) * 100}%` }} />
                  </div>
                </div>
              </div>
            </div>
          ))}
        </div>
      </Card>

      <div className="grid gap-6 lg:grid-cols-3">
        <Card className="p-5">
          <SectionHeader title="Weakest concepts" subtitle="Most-missed rubric points" />
          <ul className="flex flex-wrap gap-2">
            {data.weakestConcepts.length === 0 && <li className="text-xs text-slate-500">Not enough data yet.</li>}
            {data.weakestConcepts.map((c) => (
              <li key={c.name} className="rounded-full border border-amber-400/30 bg-amber-400/10 px-3 py-1 text-xs text-amber-100">
                {c.name} · {c.count}
              </li>
            ))}
          </ul>
        </Card>
        <Card className="p-5">
          <SectionHeader title="Strongest concepts" subtitle="What the cohort knows well" />
          <ul className="flex flex-wrap gap-2">
            {data.strongestConcepts.length === 0 && <li className="text-xs text-slate-500">Not enough data yet.</li>}
            {data.strongestConcepts.map((c) => (
              <li key={c.name} className="rounded-full border border-teal-400/30 bg-teal-400/10 px-3 py-1 text-xs text-teal-100">
                {c.name} · {c.count}
              </li>
            ))}
          </ul>
        </Card>
        <Card className="p-5">
          <SectionHeader title="Skill breakdown" subtitle="Average score per assessed skill" />
          <div className="space-y-3">
            {data.skillBreakdown.length === 0 && <p className="text-xs text-slate-500">No tagged skills on this paper.</p>}
            {data.skillBreakdown.map((s) => (
              <ScoreBar key={s.skill} label={`${s.skill} (${s.attempts})`} value={s.averageScore ?? 0} />
            ))}
          </div>
        </Card>
      </div>
    </div>
  )
}

// ── page ───────────────────────────────────────────────────────────────────

export default function Assessments() {
  const [list, setList] = useState(null)
  const [error, setError] = useState('')
  const [selectedId, setSelectedId] = useState(null)
  const [tab, setTab] = useState('questions')
  const [questions, setQuestions] = useState([])
  const [creating, setCreating] = useState(false)
  const [editing, setEditing] = useState(false)
  const [busy, setBusy] = useState(false)
  const [formError, setFormError] = useState('')
  const [notice, setNotice] = useState('')
  const [openAttempt, setOpenAttempt] = useState(null)

  const load = useCallback(async () => {
    try {
      setList(await api.assessments.list())
      setError('')
    } catch (e) {
      setError(e.message)
    }
  }, [])

  useEffect(() => {
    load()
  }, [load])

  const selected = useMemo(() => list?.find((a) => a.id === selectedId) ?? null, [list, selectedId])

  useEffect(() => {
    if (!selected) {
      setQuestions([])
      return
    }
    api.assessments
      .questions(selected.id)
      .then(setQuestions)
      .catch((e) => setError(e.message))
  }, [selected?.id, selected?.questionsReady, selected?.status])

  const guard = async (fn) => {
    setBusy(true)
    setFormError('')
    setNotice('')
    try {
      await fn()
    } catch (e) {
      setFormError(e.message)
    } finally {
      setBusy(false)
    }
  }

  const create = (body) =>
    guard(async () => {
      const created = await api.assessments.create(body)
      setCreating(false)
      await load()
      setSelectedId(created.id)
      setTab('questions')
      setNotice('Draft created. Generate the question set with AI, then publish.')
    })

  const saveEdit = (body) =>
    guard(async () => {
      await api.assessments.update(selected.id, body)
      setEditing(false)
      await load()
      setNotice('Draft updated.')
    })

  const generate = () =>
    guard(async () => {
      const qs = await api.assessments.generate(selected.id)
      setQuestions(qs)
      await load()
      setNotice(`${qs.length} questions written by the AI for ${selected.role}.`)
    })

  const removeQuestion = (aqId) =>
    guard(async () => {
      setQuestions(await api.assessments.removeQuestion(selected.id, aqId))
      await load()
    })

  const publish = () =>
    guard(async () => {
      const published = await api.assessments.publish(selected.id)
      await load()
      setNotice(`Published — share this link: ${window.location.origin}${published.sharePath}`)
    })

  const close = () =>
    guard(async () => {
      await api.assessments.close(selected.id)
      await load()
      setNotice('Assessment closed. Existing attempts stay readable.')
    })

  const remove = () =>
    guard(async () => {
      await api.assessments.remove(selected.id)
      setSelectedId(null)
      await load()
      setNotice('Draft deleted.')
    })

  const shareUrl = selected?.sharePath ? `${window.location.origin}${selected.sharePath}` : null

  return (
    <div className="space-y-6">
      <SectionHeader
        title="Assessments (OA)"
        subtitle="Host an online assessment: the AI writes the paper, grades every answer and analyses the whole cohort."
        action={
          <button className="btn btn-primary" onClick={() => setCreating((v) => !v)}>
            {creating ? 'Close form' : '+ New assessment'}
          </button>
        }
      />

      {creating && (
        <Card className="p-5">
          <SectionHeader title="New assessment" subtitle="The AI writes the question set from these details" />
          <AssessmentForm onSubmit={create} onCancel={() => setCreating(false)} busy={busy} error={formError} />
        </Card>
      )}

      {notice && (
        <div className="rounded-xl border border-teal-400/25 bg-teal-400/5 p-3 text-sm text-teal-100">{notice}</div>
      )}
      {error && <ErrorNote message={error} onRetry={load} />}
      {!list && !error && <Spinner label="Loading your assessments…" />}

      {list && list.length === 0 && !creating && (
        <EmptyState
          title="No assessments yet"
          hint="Create one, let the AI generate the questions, publish it and share the link with candidates."
          action={
            <button className="btn btn-primary" onClick={() => setCreating(true)}>
              + New assessment
            </button>
          }
        />
      )}

      <div className="grid gap-6 xl:grid-cols-[minmax(0,360px)_minmax(0,1fr)]">
        {list && list.length > 0 && (
          <div className="space-y-3">
            {list.map((a) => (
              <button
                key={a.id}
                type="button"
                onClick={() => {
                  setSelectedId(a.id)
                  setTab('questions')
                  setNotice('')
                  setFormError('')
                }}
                className={`w-full rounded-xl border p-4 text-left transition ${
                  a.id === selectedId
                    ? 'border-indigo-400/50 bg-indigo-500/10'
                    : 'border-white/10 bg-white/[0.03] hover:border-white/20'
                }`}
              >
                <div className="flex items-start justify-between gap-2">
                  <p className="font-semibold text-slate-100">{a.title}</p>
                  <StatusPill status={a.status} />
                </div>
                <p className="mt-1 text-xs text-slate-400">
                  {a.company || '—'} · {a.role} · <DiffBadge difficulty={a.difficulty} />
                </p>
                <div className="mt-3 flex flex-wrap gap-3 text-[11px] text-slate-400">
                  <span>{a.questionsReady}/{a.questionCount} questions</span>
                  <span>{a.attemptCount} attempts</span>
                  <span>{a.averageScore == null ? 'no scores yet' : `avg ${a.averageScore.toFixed(1)}`}</span>
                  <span className="font-mono text-slate-300">{a.accessCode || 'unpublished'}</span>
                </div>
              </button>
            ))}
          </div>
        )}

        {selected && (
          <Card className="p-5">
            <div className="flex flex-wrap items-start justify-between gap-3">
              <div>
                <h3 className="text-lg font-bold text-slate-50">{selected.title}</h3>
                <p className="text-sm text-slate-400">
                  {selected.company || '—'} · {selected.role} · {selected.durationMinutes} min · pass {selected.passingScore}%
                </p>
                <div className="mt-2 flex flex-wrap items-center gap-2 text-[11px] text-slate-400">
                  <StatusPill status={selected.status} />
                  {selected.skills?.map((s) => (
                    <Badge key={s}>{s}</Badge>
                  ))}
                  {selected.deadline && <span>closes {fmtDay(selected.deadline)}</span>}
                  <span>owner {selected.ownerName}</span>
                </div>
              </div>
              <div className="flex flex-wrap gap-2">
                {selected.status === 'DRAFT' && (
                  <button className="btn" onClick={() => setEditing((v) => !v)}>
                    {editing ? 'Cancel edit' : 'Edit'}
                  </button>
                )}
                {selected.status === 'DRAFT' && (
                  <button className="btn btn-primary" onClick={publish} disabled={busy}>
                    Publish & get link
                  </button>
                )}
                {selected.status === 'PUBLISHED' && (
                  <>
                    <button className="btn" onClick={() => copy(shareUrl)}>
                      Copy candidate link
                    </button>
                    <Link className="btn" to={selected.sharePath} target="_blank" rel="noreferrer">
                      Preview
                    </Link>
                    <button className="btn" onClick={close} disabled={busy}>
                      Close OA
                    </button>
                  </>
                )}
                {selected.status === 'CLOSED' && (
                  <button className="btn" onClick={() => copy(shareUrl)} disabled={!shareUrl}>
                    Copy candidate link
                  </button>
                )}
                <button className="btn btn-ghost" onClick={remove} disabled={busy || selected.attemptCount > 0}>
                  Delete
                </button>
              </div>
            </div>

            {selected.status === 'PUBLISHED' && shareUrl && (
              <div className="mt-4 flex flex-wrap items-center gap-3 rounded-xl border border-indigo-400/25 bg-indigo-500/5 p-3">
                <div className="min-w-0 flex-1">
                  <p className="text-[11px] uppercase tracking-widest text-indigo-300">Candidate link — no account needed</p>
                  <p className="truncate font-mono text-sm text-slate-200">{shareUrl}</p>
                </div>
                <span className="rounded-lg border border-white/10 bg-ink-950/60 px-3 py-1 font-mono text-sm text-slate-100">
                  {selected.accessCode}
                </span>
                <button className="btn" onClick={() => copy(shareUrl)}>
                  Copy
                </button>
                <a className="btn" href={shareUrl} target="_blank" rel="noreferrer">
                  Open
                </a>
              </div>
            )}

            {editing && (
              <div className="mt-5 rounded-xl border border-white/10 bg-white/[0.02] p-4">
                <AssessmentForm
                  initial={{
                    ...selected,
                    skills: (selected.skills || []).join(', '),
                  }}
                  onSubmit={saveEdit}
                  onCancel={() => setEditing(false)}
                  busy={busy}
                  error={formError}
                />
              </div>
            )}

            <div className="mt-5 flex gap-1 border-b border-white/10">
              {[
                ['questions', 'Question set'],
                ['attempts', `Attempts${selected.attemptCount ? ` (${selected.attemptCount})` : ''}`],
                ['analytics', 'Cohort analytics'],
              ].map(([key, label]) => (
                <button
                  key={key}
                  type="button"
                  onClick={() => setTab(key)}
                  className={`-mb-px border-b-2 px-4 py-2 text-sm font-medium transition ${
                    tab === key ? 'border-indigo-400 text-slate-100' : 'border-transparent text-slate-400 hover:text-slate-200'
                  }`}
                >
                  {label}
                </button>
              ))}
            </div>

            <div className="pt-5">
              {formError && <ErrorNote message={formError} />}
              {tab === 'questions' && (
                <QuestionList
                  assessment={selected}
                  questions={questions}
                  onGenerate={generate}
                  onRemove={removeQuestion}
                  busy={busy}
                  error=""
                />
              )}
              {tab === 'attempts' && <AttemptsTab assessment={selected} onOpenAttempt={setOpenAttempt} />}
              {tab === 'analytics' && <AnalyticsTab assessment={selected} />}
            </div>
          </Card>
        )}
      </div>

      {openAttempt != null && selected && (
        <AttemptAnalysis assessmentId={selected.id} sessionId={openAttempt} onClose={() => setOpenAttempt(null)} />
      )}
    </div>
  )
}
