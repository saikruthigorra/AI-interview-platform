import { useEffect, useState } from 'react'
import { Link } from 'react-router-dom'
import { api } from '../lib/api.js'
import { formatDate } from '../lib/format.js'
import { Card, ErrorNote, SectionHeader, Spinner, StatCard } from '../components/ui.jsx'

const SENTIMENT_TONE = {
  POSITIVE: 'border-emerald-400/30 bg-emerald-400/10 text-emerald-300',
  NEUTRAL: 'border-amber-400/30 bg-amber-400/10 text-amber-300',
  NEGATIVE: 'border-rose-400/30 bg-rose-400/10 text-rose-300',
}

function Stars({ value, size = 14 }) {
  return (
    <span className="inline-flex items-center gap-0.5" style={{ fontSize: size }} aria-label={`${value} out of 5`}>
      {[1, 2, 3, 4, 5].map((n) => (
        <span key={n} className={n <= Math.round(value) ? 'text-amber-300' : 'text-slate-600'}>
          ★
        </span>
      ))}
    </span>
  )
}

function TrendChart({ points }) {
  if (!points?.length) {
    return <p className="py-8 text-center text-sm text-slate-400">No completed interviews yet — run one to see the trend.</p>
  }
  const width = 640
  const height = 180
  const pad = { top: 18, right: 16, bottom: 28, left: 34 }
  const innerW = width - pad.left - pad.right
  const innerH = height - pad.top - pad.bottom
  const scores = points.map((p) => p.averageScore ?? 0)
  const min = Math.max(0, Math.min(...scores) - 8)
  const max = Math.min(100, Math.max(...scores) + 8)
  const span = Math.max(1, max - min)
  const x = (i) => pad.left + (points.length === 1 ? innerW / 2 : (i / (points.length - 1)) * innerW)
  const y = (v) => pad.top + innerH - ((v - min) / span) * innerH
  const path = points.map((p, i) => `${i === 0 ? 'M' : 'L'} ${x(i)} ${y(p.averageScore ?? 0)}`).join(' ')
  const area = `${path} L ${x(points.length - 1)} ${pad.top + innerH} L ${x(0)} ${pad.top + innerH} Z`

  return (
    <svg viewBox={`0 0 ${width} ${height}`} className="w-full" role="img" aria-label="Average score over time">
      {[0, 0.5, 1].map((f) => (
        <g key={f}>
          <line
            x1={pad.left}
            x2={width - pad.right}
            y1={pad.top + innerH * f}
            y2={pad.top + innerH * f}
            stroke="rgba(148,163,184,0.15)"
            strokeDasharray="3 5"
          />
          <text x={4} y={pad.top + innerH * f + 4} fill="#64748b" fontSize="10">
            {Math.round(max - span * f)}
          </text>
        </g>
      ))}
      <path d={area} fill="rgba(99,102,241,0.14)" />
      <path d={path} fill="none" stroke="#818cf8" strokeWidth="2.5" strokeLinecap="round" />
      {points.map((p, i) => (
        <g key={p.label + i}>
          <circle cx={x(i)} cy={y(p.averageScore ?? 0)} r="4" fill="#0a0f1c" stroke="#2dd4bf" strokeWidth="2.5" />
          <text x={x(i)} y={height - 8} fill="#64748b" fontSize="10" textAnchor="middle">
            {p.label}
          </text>
        </g>
      ))}
    </svg>
  )
}

function BarList({ items, tone = 'indigo', suffix = '' }) {
  if (!items?.length) {
    return <p className="text-sm text-slate-400">Nothing to show yet.</p>
  }
  const max = Math.max(...items.map((i) => i.value), 1)
  const tones = {
    indigo: 'from-indigo-400 to-indigo-300',
    rose: 'from-rose-400 to-rose-300',
    teal: 'from-teal-400 to-teal-300',
  }
  return (
    <div className="space-y-2.5">
      {items.map((item) => (
        <div key={item.name}>
          <div className="mb-1 flex items-center justify-between text-xs">
            <span className="truncate pr-3 text-slate-300">{item.name}</span>
            <span className="shrink-0 font-semibold text-slate-200">
              {item.value}
              {suffix}
            </span>
          </div>
          <div className="h-1.5 overflow-hidden rounded-full bg-white/10">
            <div
              className={`h-full rounded-full bg-gradient-to-r ${tones[tone]}`}
              style={{ width: `${(item.value / max) * 100}%`, transition: 'width .5s ease' }}
            />
          </div>
        </div>
      ))}
    </div>
  )
}

export default function Analytics() {
  const [data, setData] = useState(null)
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState('')

  const load = () => {
    setLoading(true)
    setError('')
    api.analytics
      .dashboard()
      .then(setData)
      .catch((e) => setError(e.message))
      .finally(() => setLoading(false))
  }

  useEffect(load, [])

  if (loading) return <Spinner label="Crunching the numbers…" />
  if (!data) {
    return (
      <div className="space-y-4">
        <ErrorNote message={error || 'Could not load analytics'} onRetry={load} />
      </div>
    )
  }

  const s = data.summary

  return (
    <div className="fade-in space-y-6">
      <SectionHeader
        title="Analysis of interviews, reviews & feedback"
        subtitle="Outcomes, concept-level gaps and student sentiment, computed from every graded answer"
        action={
          <button type="button" className="btn btn-ghost !py-2 text-xs" onClick={load}>
            ↻ Refresh
          </button>
        }
      />

      <ErrorNote message={error} onRetry={load} />

      <div className="grid gap-4 sm:grid-cols-2 xl:grid-cols-4">
        <StatCard
          label="Average score"
          value={s.averageScore == null ? '—' : s.averageScore}
          hint={`${s.completedInterviews} completed interview(s)`}
          accent="teal"
          icon="🎯"
        />
        <StatCard
          label="Average rating"
          value={s.averageRating == null ? '—' : `${s.averageRating}/5`}
          hint={`from ${s.feedbackCount} student review(s)`}
          accent="amber"
          icon="⭐"
        />
        <StatCard
          label="Completion rate"
          value={s.completionRate == null ? '—' : `${s.completionRate}%`}
          hint={`${s.totalInterviews} started in total`}
          accent="indigo"
          icon="✅"
        />
        <StatCard
          label="Positive sentiment"
          value={s.positiveShare == null ? '—' : `${s.positiveShare}%`}
          hint={s.averageSecondsPerAnswer ? `~${Math.round(s.averageSecondsPerAnswer)}s per answer` : 'no answers yet'}
          accent="sky"
          icon="💬"
        />
      </div>

      <div className="grid gap-6 lg:grid-cols-[1.6fr_1fr]">
        <Card>
          <SectionHeader title="Score trend" subtitle="Average interview score per day" />
          <TrendChart points={data.scoreTrend} />
        </Card>

        <Card>
          <SectionHeader title="Dimension averages" subtitle="Across every graded answer" />
          <div className="space-y-3">
            {data.dimensionAverages.length ? (
              data.dimensionAverages.map((d) => (
                <div key={d.name}>
                  <div className="mb-1 flex justify-between text-xs">
                    <span className="text-slate-400">{d.name}</span>
                    <span className="font-semibold text-slate-200">{d.value}</span>
                  </div>
                  <div className="h-1.5 overflow-hidden rounded-full bg-white/10">
                    <div
                      className="h-full rounded-full bg-gradient-to-r from-indigo-400 to-teal-300"
                      style={{ width: `${d.value}%`, transition: 'width .5s ease' }}
                    />
                  </div>
                </div>
              ))
            ) : (
              <p className="text-sm text-slate-400">No graded answers yet.</p>
            )}
          </div>
        </Card>
      </div>

      <div className="grid gap-6 lg:grid-cols-2">
        <Card>
          <SectionHeader
            title="Weakest concepts"
            subtitle="Rubric concepts students most often miss — your revision syllabus, in priority order"
          />
          <BarList items={data.weakestConcepts} tone="rose" suffix="×" />
        </Card>
        <Card>
          <SectionHeader title="Concepts students nail" subtitle="Recurring matched keywords — what's working" />
          {data.strongestConcepts.length ? (
            <div className="flex flex-wrap gap-2">
              {data.strongestConcepts.map((c) => (
                <span key={c.name} className="chip border-emerald-400/25 bg-emerald-400/10 text-emerald-200">
                  {c.name} · {c.value}
                </span>
              ))}
            </div>
          ) : (
            <p className="text-sm text-slate-400">No matched concepts yet.</p>
          )}

          <div className="mt-6 border-t border-white/5 pt-4">
            <p className="mb-3 text-xs font-semibold uppercase tracking-wider text-slate-400">
              Review sentiment distribution
            </p>
            <div className="flex h-2.5 overflow-hidden rounded-full bg-white/10">
              {data.sentimentSplit.map((seg, i) => (
                <div
                  key={seg.name}
                  title={`${seg.name}: ${seg.count}`}
                  style={{ width: `${seg.value}%` }}
                  className={
                    ['bg-emerald-400', 'bg-amber-400', 'bg-rose-400'][i % 3]
                  }
                />
              ))}
            </div>
            <div className="mt-2 flex flex-wrap gap-3 text-xs text-slate-400">
              {data.sentimentSplit.map((seg, i) => (
                <span key={seg.name} className="inline-flex items-center gap-1.5">
                  <span className={`h-2 w-2 rounded-full ${['bg-emerald-400', 'bg-amber-400', 'bg-rose-400'][i % 3]}`} />
                  {seg.name} · {seg.count}
                </span>
              ))}
            </div>
          </div>
        </Card>
      </div>

      <div className="grid gap-6 lg:grid-cols-2">
        <Card>
          <SectionHeader title="Performance by skill" subtitle="Interview scores for the skills students declared" />
          {data.skillPerformance.length ? (
            <table className="w-full text-sm">
              <thead>
                <tr className="text-left text-xs uppercase tracking-wider text-slate-500">
                  <th className="pb-2 font-medium">Skill</th>
                  <th className="pb-2 text-right font-medium">Sessions</th>
                  <th className="pb-2 text-right font-medium">Avg score</th>
                  <th className="pb-2 text-right font-medium">Avg rating</th>
                </tr>
              </thead>
              <tbody className="divide-y divide-white/5">
                {data.skillPerformance.map((row) => (
                  <tr key={row.skill}>
                    <td className="py-2 text-slate-200">{row.skill}</td>
                    <td className="py-2 text-right text-slate-400">{row.sessions}</td>
                    <td className="py-2 text-right font-semibold text-slate-100">{row.averageScore ?? '—'}</td>
                    <td className="py-2 text-right text-slate-300">{row.averageRating ?? '—'}</td>
                  </tr>
                ))}
              </tbody>
            </table>
          ) : (
            <p className="text-sm text-slate-400">No sessions with declared skills yet.</p>
          )}
        </Card>

        <Card>
          <SectionHeader title="Performance by role" subtitle="Where students stand per target role" />
          {data.rolePerformance.length ? (
            <table className="w-full text-sm">
              <thead>
                <tr className="text-left text-xs uppercase tracking-wider text-slate-500">
                  <th className="pb-2 font-medium">Role</th>
                  <th className="pb-2 text-right font-medium">Sessions</th>
                  <th className="pb-2 text-right font-medium">Avg score</th>
                  <th className="pb-2 text-right font-medium">Avg rating</th>
                </tr>
              </thead>
              <tbody className="divide-y divide-white/5">
                {data.rolePerformance.map((row) => (
                  <tr key={row.role}>
                    <td className="py-2 text-slate-200">{row.role}</td>
                    <td className="py-2 text-right text-slate-400">{row.sessions}</td>
                    <td className="py-2 text-right font-semibold text-slate-100">{row.averageScore ?? '—'}</td>
                    <td className="py-2 text-right text-slate-300">{row.averageRating ?? '—'}</td>
                  </tr>
                ))}
              </tbody>
            </table>
          ) : (
            <p className="text-sm text-slate-400">No completed interviews yet.</p>
          )}
        </Card>
      </div>

      <Card>
        <SectionHeader
          title="Question calibration"
          subtitle="Questions sorted by average score — flag the ones that are too hard, too easy, or well pitched"
        />
        {data.questionCalibration.length ? (
          <div className="overflow-x-auto">
            <table className="w-full min-w-[720px] text-sm">
              <thead>
                <tr className="text-left text-xs uppercase tracking-wider text-slate-500">
                  <th className="pb-2 font-medium">Question</th>
                  <th className="pb-2 font-medium">Difficulty</th>
                  <th className="pb-2 text-right font-medium">Asked</th>
                  <th className="pb-2 text-right font-medium">Avg score</th>
                  <th className="pb-2 text-right font-medium">Verdict</th>
                </tr>
              </thead>
              <tbody className="divide-y divide-white/5">
                {data.questionCalibration.map((q) => (
                  <tr key={q.questionId}>
                    <td className="max-w-md py-2 pr-4">
                      <p className="truncate text-slate-200" title={q.text}>
                        {q.text}
                      </p>
                      <p className="text-xs text-slate-500">
                        {q.role}
                        {q.topic ? ` · ${q.topic}` : ''}
                      </p>
                    </td>
                    <td className="py-2">
                      <span className="chip">{q.difficulty}</span>
                    </td>
                    <td className="py-2 text-right text-slate-400">{q.timesAsked}</td>
                    <td className="py-2 text-right font-semibold text-slate-100">{q.averageScore}</td>
                    <td className="py-2 text-right">
                      <span
                        className={`chip ${
                          q.verdict === 'Well calibrated'
                            ? 'border-teal-400/30 bg-teal-400/10 text-teal-300'
                            : q.verdict === 'Need more data'
                              ? ''
                              : 'border-amber-400/30 bg-amber-400/10 text-amber-300'
                        }`}
                      >
                        {q.verdict}
                      </span>
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        ) : (
          <p className="text-sm text-slate-400">No answers graded yet.</p>
        )}
      </Card>

      <Card>
        <SectionHeader
          title="Recent reviews & feedback"
          subtitle="What students said, with the sentiment the platform derived"
          action={
            <Link to="/candidates" className="text-xs font-semibold text-indigo-300 hover:text-indigo-200">
              See students →
            </Link>
          }
        />
        {data.recentFeedback.length ? (
          <div className="grid gap-4 md:grid-cols-2">
            {data.recentFeedback.map((review) => (
              <div key={review.id} className="rounded-xl border border-white/10 bg-white/[0.03] p-4">
                <div className="flex flex-wrap items-center justify-between gap-2">
                  <div className="flex items-center gap-3">
                    <Stars value={review.rating} />
                    <span className={`chip ${SENTIMENT_TONE[review.sentiment] || ''}`}>
                      {review.sentiment} · {review.sentimentScore}
                    </span>
                  </div>
                  <span className="text-xs text-slate-500">{formatDate(review.submittedAt)}</span>
                </div>
                {review.comment && (
                  <p className="mt-2 text-sm leading-relaxed text-slate-300">“{review.comment}”</p>
                )}
                <p className="mt-2 text-xs text-slate-500">
                  {review.candidateName} · {review.role}
                  {review.overallScore != null && ` · scored ${review.overallScore}/100`} ·{' '}
                  <Link to={`/interview/${review.sessionId}/report`} className="text-indigo-300 hover:text-indigo-200">
                    view report
                  </Link>
                </p>
              </div>
            ))}
          </div>
        ) : (
          <p className="text-sm text-slate-400">
            No reviews yet — finish an interview and leave a rating from its report page.
          </p>
        )}
      </Card>
    </div>
  )
}
