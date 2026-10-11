import { useEffect, useState } from 'react'
import { api } from '../lib/api.js'
import { Card } from './ui.jsx'

const SENTIMENT_TONE = {
  POSITIVE: 'border-emerald-400/30 bg-emerald-400/10 text-emerald-300',
  NEUTRAL: 'border-amber-400/30 bg-amber-400/10 text-amber-300',
  NEGATIVE: 'border-rose-400/30 bg-rose-400/10 text-rose-300',
}

/** Rate + review a completed interview. One review per session (upsert server-side). */
export default function ReviewWidget({ sessionId, completed }) {
  const [feedback, setFeedback] = useState(null)
  const [rating, setRating] = useState(0)
  const [hover, setHover] = useState(0)
  const [comment, setComment] = useState('')
  const [editing, setEditing] = useState(false)
  const [busy, setBusy] = useState(false)
  const [error, setError] = useState('')

  useEffect(() => {
    if (!completed) return
    api.feedback
      .forSession(sessionId)
      .then((existing) => {
        if (existing) {
          setFeedback(existing)
          setRating(existing.rating)
          setComment(existing.comment || '')
        } else {
          setEditing(true)
        }
      })
      .catch(() => setEditing(true))
  }, [sessionId, completed])

  if (!completed) {
    return (
      <Card>
        <p className="text-sm text-slate-400">
          Reviews unlock once the interview is completed — that way the rating reflects the full session.
        </p>
      </Card>
    )
  }

  const submit = async (event) => {
    event.preventDefault()
    if (!rating) {
      setError('Pick a star rating first')
      return
    }
    setBusy(true)
    setError('')
    try {
      const saved = await api.feedback.submit(sessionId, { rating, comment: comment.trim() || null })
      setFeedback(saved)
      setEditing(false)
    } catch (e) {
      setError(e.message)
    } finally {
      setBusy(false)
    }
  }

  return (
    <Card className="no-print">
      <div className="flex flex-wrap items-center justify-between gap-3">
        <h2 className="text-sm font-semibold uppercase tracking-wider text-slate-300">
          Your review of this interview
        </h2>
        {feedback && !editing && (
          <button type="button" className="text-xs font-semibold text-indigo-300 hover:text-indigo-200" onClick={() => setEditing(true)}>
            Edit review
          </button>
        )}
      </div>

      {feedback && !editing ? (
        <div className="mt-3 space-y-2">
          <div className="flex flex-wrap items-center gap-3">
            <span className="text-lg" aria-label={`${feedback.rating} out of 5`}>
              {[1, 2, 3, 4, 5].map((n) => (
                <span key={n} className={n <= feedback.rating ? 'text-amber-300' : 'text-slate-600'}>
                  ★
                </span>
              ))}
            </span>
            <span className={`chip ${SENTIMENT_TONE[feedback.sentiment] || ''}`}>
              {feedback.sentiment} · {feedback.sentimentScore}/100
            </span>
            {feedback.positiveTerms?.length > 0 && (
              <span className="text-xs text-slate-500">matched: {feedback.positiveTerms.slice(0, 4).join(', ')}</span>
            )}
            {feedback.negativeTerms?.length > 0 && (
              <span className="text-xs text-slate-500">flagged: {feedback.negativeTerms.slice(0, 4).join(', ')}</span>
            )}
          </div>
          {feedback.comment && <p className="text-sm leading-relaxed text-slate-300">“{feedback.comment}”</p>}
          <p className="text-xs text-slate-500">Thanks — your feedback feeds the analytics dashboard.</p>
        </div>
      ) : (
        <form onSubmit={submit} className="mt-3 space-y-3">
          <div className="flex items-center gap-1" onMouseLeave={() => setHover(0)}>
            {[1, 2, 3, 4, 5].map((n) => (
              <button
                key={n}
                type="button"
                aria-label={`${n} star${n > 1 ? 's' : ''}`}
                className="text-2xl leading-none transition hover:scale-110"
                onMouseEnter={() => setHover(n)}
                onClick={() => setRating(n)}
              >
                <span className={(hover || rating) >= n ? 'text-amber-300' : 'text-slate-600'}>★</span>
              </button>
            ))}
            <span className="ml-2 text-xs text-slate-400">
              {rating ? ['Terrible', 'Poor', 'Okay', 'Good', 'Excellent'][rating - 1] : 'Rate the session'}
            </span>
          </div>
          <textarea
            className="input min-h-[80px]"
            placeholder="How did this interview feel? Were the questions fair for a student at your level? (sentiment is scored from your words)"
            value={comment}
            onChange={(e) => setComment(e.target.value)}
          />
          {error && <p className="text-sm text-rose-300">{error}</p>}
          <div className="flex justify-end gap-2">
            {feedback && (
              <button type="button" className="btn btn-ghost" onClick={() => setEditing(false)}>
                Cancel
              </button>
            )}
            <button type="submit" className="btn btn-primary" disabled={busy || !rating}>
              {busy ? 'Saving…' : feedback ? 'Update review' : 'Submit review'}
            </button>
          </div>
        </form>
      )}
    </Card>
  )
}
