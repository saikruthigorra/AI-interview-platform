import { useEffect, useMemo, useRef, useState } from 'react'
import { Link, useNavigate, useParams } from 'react-router-dom'
import { api } from '../lib/api.js'
import { formatClock, wordCount } from '../lib/format.js'
import { Card, DiffBadge, ErrorNote, ScoreBar, ScoreRing, Spinner } from '../components/ui.jsx'

export default function InterviewRunner() {
  const { id } = useParams()
  const navigate = useNavigate()
  const [detail, setDetail] = useState(null)
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState('')
  const [answerText, setAnswerText] = useState('')
  const [submitting, setSubmitting] = useState(false)
  const [evaluation, setEvaluation] = useState(null)
  const [elapsed, setElapsed] = useState(0)
  const [showIdeal, setShowIdeal] = useState(false)
  const textareaRef = useRef(null)

  useEffect(() => {
    api
      .interviews.detail(id)
      .then((d) => {
        if (d.session.status === 'COMPLETED') {
          navigate(`/interview/${id}/report`, { replace: true })
          return
        }
        setDetail(d)
      })
      .catch((e) => setError(e.message))
      .finally(() => setLoading(false))
  }, [id, navigate])

  useEffect(() => {
    if (evaluation) return
    const timer = setInterval(() => setElapsed((s) => s + 1), 1000)
    return () => clearInterval(timer)
  }, [evaluation])

  const questions = detail?.questions ?? []
  const answered = detail?.answers?.length ?? 0
  const currentIndex = useMemo(() => Math.min(answered, Math.max(0, questions.length - 1)), [answered, questions.length])
  const current = questions[currentIndex]

  const submit = async () => {
    if (!answerText.trim() || !current) return
    setSubmitting(true)
    setError('')
    try {
      const result = await api.interviews.submitAnswer(id, {
        answerText: answerText.trim(),
        timeTakenSeconds: elapsed,
      })
      setEvaluation(result)
      setDetail((prev) =>
        prev
          ? {
              ...prev,
              session: { ...prev.session, answeredCount: prev.session.answeredCount + 1 },
              answers: [...prev.answers, result],
              questions: prev.questions.map((q, i) => (i === currentIndex ? { ...q, answered: true } : q)),
            }
          : prev,
      )
    } catch (e) {
      setError(e.message)
    } finally {
      setSubmitting(false)
    }
  }

  const next = () => {
    if (answered >= questions.length || evaluation?.sessionCompleted) {
      navigate(`/interview/${id}/report`)
      return
    }
    setEvaluation(null)
    setAnswerText('')
    setElapsed(0)
    setShowIdeal(false)
    requestAnimationFrame(() => textareaRef.current?.focus())
  }

  const finishEarly = async () => {
    if (!window.confirm('Finish the interview now and generate the report with the answers so far?')) return
    try {
      await api.interviews.finish(id)
      navigate(`/interview/${id}/report`)
    } catch (e) {
      setError(e.message)
    }
  }

  if (loading) return <Spinner label="Loading interview…" />
  if (!detail || !current) {
    return (
      <div className="mx-auto max-w-3xl space-y-4">
        <ErrorNote message={error || 'This interview has no questions.'} />
        <Link to="/" className="btn btn-ghost">
          Back to dashboard
        </Link>
      </div>
    )
  }

  const progress = Math.round(((currentIndex + (evaluation ? 1 : 0)) / questions.length) * 100)
  const words = wordCount(answerText)

  return (
    <div className="fade-in mx-auto max-w-4xl space-y-5">
      <div className="flex flex-wrap items-center justify-between gap-3">
        <div>
          <p className="text-xs uppercase tracking-widest text-slate-500">Interview · {detail.session.role}</p>
          <h1 className="text-xl font-bold text-slate-50">{detail.session.candidateName}</h1>
        </div>
        <div className="flex items-center gap-2">
          <span className="chip">Question {currentIndex + 1} of {questions.length}</span>
          <span className={`chip ${evaluation ? '' : 'border-teal-400/30 bg-teal-400/10 text-teal-300'}`}>
            {evaluation ? 'answered' : `⏱ ${formatClock(elapsed)}`}
          </span>
          <button type="button" className="btn btn-ghost !py-1.5 text-xs" onClick={finishEarly}>
            Finish early
          </button>
        </div>
      </div>

      <div className="h-1.5 overflow-hidden rounded-full bg-white/10">
        <div
          className="h-full rounded-full bg-gradient-to-r from-indigo-400 to-teal-300"
          style={{ width: `${progress}%`, transition: 'width .4s ease' }}
        />
      </div>

      <ErrorNote message={error} />

      <Card className="space-y-4">
        <div className="flex flex-wrap items-center gap-2">
          <DiffBadge difficulty={evaluation ? evaluation.difficulty : current.difficulty} />
          {(evaluation ? evaluation.topic : current.topic) && (
            <span className="chip">{evaluation ? evaluation.topic : current.topic}</span>
          )}
          <span className="chip border-white/10 text-slate-400">rubric-graded</span>
        </div>
        <h2 className="text-lg font-semibold leading-relaxed text-slate-100">
          {evaluation ? evaluation.questionText : current.text}
        </h2>

        {!evaluation ? (
          <>
            <textarea
              ref={textareaRef}
              className="input min-h-[190px]"
              placeholder="Answer as you would out loud: state your approach, explain the reasoning, give an example, then mention trade-offs…"
              value={answerText}
              onChange={(e) => setAnswerText(e.target.value)}
              onKeyDown={(e) => {
                if ((e.metaKey || e.ctrlKey) && e.key === 'Enter') submit()
              }}
              autoFocus
            />
            <div className="flex flex-wrap items-center justify-between gap-3">
              <p className="text-xs text-slate-500">
                {words} words {words > 0 && words < 40 && '· aim for 90–150 for a strong score'} · ⌘/Ctrl + Enter to
                submit
              </p>
              <button type="button" className="btn btn-primary" onClick={submit} disabled={submitting || !answerText.trim()}>
                {submitting ? 'Grading…' : 'Submit answer'}
              </button>
            </div>
          </>
        ) : (
          <div className="space-y-5">
            <div className="rounded-2xl border border-white/10 bg-ink-900/60 p-4 text-sm text-slate-300 whitespace-pre-wrap">
              {evaluation.answerText}
            </div>

            <div className="grid gap-5 md:grid-cols-[auto_1fr]">
              <div className="flex flex-col items-center gap-2">
                <ScoreRing value={evaluation.score} size={104} caption="score" />
                <p className="text-[11px] text-slate-500">answered in {formatClock(evaluation.timeTakenSeconds ?? elapsed)}</p>
              </div>
              <div className="space-y-3">
                <ScoreBar label="Technical coverage" value={evaluation.technicalScore} />
                <ScoreBar label="Depth" value={evaluation.depthScore} />
                <ScoreBar label="Communication" value={evaluation.communicationScore} />
                <ScoreBar label="Clarity" value={evaluation.clarityScore} />
              </div>
            </div>

            <div className="rounded-2xl border border-indigo-400/20 bg-indigo-500/5 p-4">
              <p className="text-xs font-semibold uppercase tracking-wider text-indigo-300">AI feedback</p>
              <p className="mt-1.5 text-sm leading-relaxed text-slate-200">{evaluation.feedback}</p>
            </div>

            <div className="grid gap-4 sm:grid-cols-2">
              {evaluation.strengths?.length > 0 && (
                <div>
                  <p className="mb-2 text-xs font-semibold uppercase tracking-wider text-emerald-300">Strengths</p>
                  <ul className="space-y-1.5 text-sm text-slate-300">
                    {evaluation.strengths.map((s, i) => (
                      <li key={i} className="flex gap-2">
                        <span className="text-emerald-400">✓</span>
                        {s}
                      </li>
                    ))}
                  </ul>
                </div>
              )}
              {evaluation.improvements?.length > 0 && (
                <div>
                  <p className="mb-2 text-xs font-semibold uppercase tracking-wider text-amber-300">To improve</p>
                  <ul className="space-y-1.5 text-sm text-slate-300">
                    {evaluation.improvements.map((s, i) => (
                      <li key={i} className="flex gap-2">
                        <span className="text-amber-400">→</span>
                        {s}
                      </li>
                    ))}
                  </ul>
                </div>
              )}
            </div>

            {(evaluation.keywordsMatched?.length > 0 || evaluation.missingPoints?.length > 0) && (
              <div className="flex flex-wrap gap-2">
                {evaluation.keywordsMatched?.map((k) => (
                  <span key={k} className="chip border-emerald-400/30 bg-emerald-400/10 text-emerald-300">
                    ✓ {k}
                  </span>
                ))}
                {evaluation.missingPoints?.map((k) => (
                  <span key={k} className="chip border-rose-400/30 bg-rose-400/10 text-rose-300">
                    ✕ {k}
                  </span>
                ))}
              </div>
            )}

            {evaluation.idealAnswer && (
              <div>
                <button
                  type="button"
                  className="text-xs font-semibold text-indigo-300 hover:text-indigo-200"
                  onClick={() => setShowIdeal((v) => !v)}
                >
                  {showIdeal ? '▾ Hide model answer' : '▸ Show a model answer'}
                </button>
                {showIdeal && (
                  <p className="mt-2 rounded-xl border border-white/10 bg-white/[0.03] p-3 text-sm leading-relaxed text-slate-300">
                    {evaluation.idealAnswer}
                  </p>
                )}
              </div>
            )}

            <div className="flex justify-end">
              <button type="button" className="btn btn-primary" onClick={next}>
                {evaluation.sessionCompleted || answered >= questions.length
                  ? 'View the report →'
                  : `Next question (${Math.min(answered + 1, questions.length)}/${questions.length}) →`}
              </button>
            </div>
          </div>
        )}
      </Card>
    </div>
  )
}
