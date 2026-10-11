import { useEffect, useState } from 'react'
import { Link, useNavigate, useParams } from 'react-router-dom'
import { api } from '../lib/api.js'
import { formatDate } from '../lib/format.js'
import { Avatar, Card, DiffBadge, ErrorNote, ScoreBar, ScoreRing, Spinner } from '../components/ui.jsx'
import ReviewWidget from '../components/ReviewWidget.jsx'

export default function Report() {
  const { id } = useParams()
  const navigate = useNavigate()
  const [detail, setDetail] = useState(null)
  const [error, setError] = useState('')
  const [loading, setLoading] = useState(true)
  const [open, setOpen] = useState({})

  const load = () => {
    setLoading(true)
    api
      .interviews.report(id)
      .then((d) => {
        setDetail(d)
        setOpen(d.answers.length ? { [d.answers[0].id]: true } : {})
      })
      .catch((e) => setError(e.message))
      .finally(() => setLoading(false))
  }

  useEffect(load, [id])

  const removeSession = async () => {
    if (!window.confirm('Delete this interview and its answers? This cannot be undone.')) return
    try {
      await api.interviews.remove(id)
      navigate('/')
    } catch (e) {
      setError(e.message)
    }
  }

  if (loading) return <Spinner label="Loading report…" />
  if (!detail) {
    return (
      <div className="mx-auto max-w-3xl space-y-4">
        <ErrorNote message={error} onRetry={load} />
        <Link to="/" className="btn btn-ghost">
          Back to dashboard
        </Link>
      </div>
    )
  }

  const { session, answers, questions } = detail
  const completed = session.status === 'COMPLETED'

  return (
    <div className="fade-in mx-auto max-w-5xl space-y-6">
      <div className="flex flex-wrap items-center justify-between gap-3 no-print">
        <button type="button" className="btn btn-ghost !py-2 text-xs" onClick={() => navigate(-1)}>
          ← Back
        </button>
        <div className="flex gap-2">
          <button type="button" className="btn btn-ghost !py-2 text-xs" onClick={() => window.print()}>
            ⎙ Print / PDF
          </button>
          <button
            type="button"
            className="btn btn-ghost !py-2 text-xs"
            onClick={() => navigate(`/interview/new?candidateId=${session.candidateId}`)}
          >
            ↻ Re-interview
          </button>
          <button type="button" className="btn btn-danger !py-2 text-xs" onClick={removeSession}>
            Delete
          </button>
        </div>
      </div>

      <ErrorNote message={error} />

      <Card className="overflow-hidden">
        <div className="flex flex-wrap items-center gap-6">
          <Avatar name={session.candidateName} size={64} />
          <div className="min-w-0 flex-1">
            <p className="text-xs uppercase tracking-widest text-slate-500">Interview report</p>
            <h1 className="text-2xl font-bold tracking-tight text-slate-50">{session.candidateName}</h1>
            <p className="mt-1 text-sm text-slate-400">
              {session.role} · {session.difficulty.toLowerCase()} · started {formatDate(session.startedAt)}
            </p>
            <div className="mt-3 flex flex-wrap gap-2">
              <span className="chip">{answers.length}/{questions.length} answered</span>
              <span className="chip">engine: {session.engine}</span>
              <span className={`chip ${completed ? 'border-teal-400/30 bg-teal-400/10 text-teal-300' : 'border-amber-400/30 bg-amber-400/10 text-amber-300'}`}>
                {completed ? 'completed' : 'in progress'}
              </span>
            </div>
          </div>
          <ScoreRing value={session.overallScore} size={116} stroke={9} caption="overall" />
        </div>
      </Card>

      <ReviewWidget sessionId={session.id} completed={completed} />

      {completed && (
        <div className="grid gap-6 lg:grid-cols-[1.5fr_1fr]">
          <Card>
            <h2 className="text-sm font-semibold uppercase tracking-wider text-indigo-300">AI debrief</h2>
            <p className="mt-3 text-sm leading-relaxed text-slate-200">{session.aiSummary}</p>
          </Card>
          <div className="space-y-4">
            <Card>
              <h2 className="text-xs font-semibold uppercase tracking-wider text-emerald-300">Strengths</h2>
              <ul className="mt-2 space-y-1.5 text-sm text-slate-300">
                {(session.strengths ?? []).map((s, i) => (
                  <li key={i} className="flex gap-2">
                    <span className="text-emerald-400">✓</span>
                    {s}
                  </li>
                ))}
                {!session.strengths?.length && <li className="text-slate-500">None recorded.</li>}
              </ul>
            </Card>
            <Card>
              <h2 className="text-xs font-semibold uppercase tracking-wider text-amber-300">Gaps &amp; risks</h2>
              <ul className="mt-2 space-y-1.5 text-sm text-slate-300">
                {(session.gaps ?? []).map((s, i) => (
                  <li key={i} className="flex gap-2">
                    <span className="text-amber-400">→</span>
                    {s}
                  </li>
                ))}
                {!session.gaps?.length && <li className="text-slate-500">None recorded.</li>}
              </ul>
            </Card>
          </div>
        </div>
      )}

      <div>
        <h2 className="mb-3 text-lg font-semibold text-slate-100">Question by question</h2>
        <div className="space-y-3">
          {answers.map((answer, index) => {
            const isOpen = !!open[answer.id]
            return (
              <Card key={answer.id} className="overflow-hidden !p-0">
                <button
                  type="button"
                  className="flex w-full items-center gap-4 px-5 py-4 text-left transition hover:bg-white/[0.03]"
                  onClick={() => setOpen((prev) => ({ ...prev, [answer.id]: !prev[answer.id] }))}
                >
                  <span className="flex h-8 w-8 shrink-0 items-center justify-center rounded-lg bg-white/5 text-sm font-semibold text-slate-300">
                    {index + 1}
                  </span>
                  <span className="min-w-0 flex-1">
                    <span className="block truncate text-sm font-medium text-slate-100">{answer.questionText}</span>
                    <span className="mt-1 flex items-center gap-2">
                      <DiffBadge difficulty={answer.difficulty} />
                      {answer.topic && <span className="text-xs text-slate-500">{answer.topic}</span>}
                    </span>
                  </span>
                  <span
                    className={`text-lg font-bold ${
                      answer.score >= 70 ? 'text-teal-300' : answer.score >= 50 ? 'text-amber-300' : 'text-rose-300'
                    }`}
                  >
                    {answer.score}
                  </span>
                  <span className="text-slate-500">{isOpen ? '▾' : '▸'}</span>
                </button>

                {isOpen && (
                  <div className="space-y-4 border-t border-white/5 px-5 py-4">
                    <div>
                      <p className="text-xs font-semibold uppercase tracking-wider text-slate-400">Their answer</p>
                      <p className="mt-1.5 whitespace-pre-wrap text-sm leading-relaxed text-slate-300">{answer.answerText}</p>
                    </div>

                    <div className="grid gap-4 sm:grid-cols-2">
                      <div className="space-y-2.5">
                        <ScoreBar label="Technical coverage" value={answer.technicalScore} />
                        <ScoreBar label="Depth" value={answer.depthScore} />
                        <ScoreBar label="Communication" value={answer.communicationScore} />
                        <ScoreBar label="Clarity" value={answer.clarityScore} />
                      </div>
                      <div className="rounded-xl border border-white/10 bg-white/[0.03] p-3">
                        <p className="text-xs font-semibold uppercase tracking-wider text-indigo-300">Feedback</p>
                        <p className="mt-1 text-sm leading-relaxed text-slate-300">{answer.feedback}</p>
                      </div>
                    </div>

                    {(answer.keywordsMatched?.length > 0 || answer.missingPoints?.length > 0) && (
                      <div className="flex flex-wrap gap-2">
                        {answer.keywordsMatched?.map((k) => (
                          <span key={k} className="chip border-emerald-400/30 bg-emerald-400/10 text-emerald-300">
                            ✓ {k}
                          </span>
                        ))}
                        {answer.missingPoints?.map((k) => (
                          <span key={k} className="chip border-rose-400/30 bg-rose-400/10 text-rose-300">
                            ✕ {k}
                          </span>
                        ))}
                      </div>
                    )}

                    {answer.idealAnswer && (
                      <div>
                        <p className="text-xs font-semibold uppercase tracking-wider text-slate-400">Model answer</p>
                        <p className="mt-1 text-sm leading-relaxed text-slate-400">{answer.idealAnswer}</p>
                      </div>
                    )}
                  </div>
                )}
              </Card>
            )
          })}
          {!answers.length && (
            <Card>
              <p className="text-sm text-slate-400">No answers were submitted in this session.</p>
            </Card>
          )}
        </div>
      </div>

      <p className="pb-4 text-center text-xs text-slate-600">
        Generated by InterviewIQ · {answers.length} answers graded by {session.engine} · report id #{session.id}
      </p>
    </div>
  )
}
