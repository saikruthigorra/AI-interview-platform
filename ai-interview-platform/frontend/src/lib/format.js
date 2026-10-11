export function scoreTone(score) {
  if (score == null) return 'slate'
  if (score >= 85) return 'emerald'
  if (score >= 70) return 'teal'
  if (score >= 50) return 'amber'
  return 'rose'
}

export const toneText = {
  emerald: 'text-emerald-300',
  teal: 'text-teal-300',
  amber: 'text-amber-300',
  rose: 'text-rose-300',
  slate: 'text-slate-400',
}

export const toneStroke = {
  emerald: '#34d399',
  teal: '#2dd4bf',
  amber: '#fbbf24',
  rose: '#fb7185',
  slate: '#64748b',
}

export const toneBar = {
  emerald: 'bg-emerald-400',
  teal: 'bg-teal-400',
  amber: 'bg-amber-400',
  rose: 'bg-rose-400',
  slate: 'bg-slate-500',
}

export function difficultyTone(difficulty) {
  switch ((difficulty || '').toUpperCase()) {
    case 'EASY':
      return 'border-emerald-400/30 bg-emerald-400/10 text-emerald-300'
    case 'HARD':
      return 'border-rose-400/30 bg-rose-400/10 text-rose-300'
    default:
      return 'border-amber-400/30 bg-amber-400/10 text-amber-300'
  }
}

export function statusTone(status) {
  return status === 'COMPLETED'
    ? 'border-teal-400/30 bg-teal-400/10 text-teal-300'
    : 'border-indigo-400/30 bg-indigo-400/10 text-indigo-300'
}

export function formatDate(value) {
  if (!value) return '—'
  const date = new Date(value)
  if (Number.isNaN(date.getTime())) return '—'
  return date.toLocaleString(undefined, {
    day: 'numeric',
    month: 'short',
    year: 'numeric',
    hour: '2-digit',
    minute: '2-digit',
  })
}

export function relativeTime(value) {
  if (!value) return ''
  const then = new Date(value).getTime()
  if (Number.isNaN(then)) return ''
  const seconds = Math.round((Date.now() - then) / 1000)
  if (seconds < 60) return 'just now'
  const minutes = Math.round(seconds / 60)
  if (minutes < 60) return `${minutes}m ago`
  const hours = Math.round(minutes / 60)
  if (hours < 24) return `${hours}h ago`
  const days = Math.round(hours / 24)
  if (days < 30) return `${days}d ago`
  return `${Math.round(days / 30)}mo ago`
}

export function initials(name) {
  if (!name) return '?'
  return name
    .split(/\s+/)
    .filter(Boolean)
    .slice(0, 2)
    .map((part) => part[0].toUpperCase())
    .join('')
}

export function formatClock(totalSeconds) {
  const s = Math.max(0, Math.floor(totalSeconds))
  const minutes = String(Math.floor(s / 60)).padStart(2, '0')
  const seconds = String(s % 60).padStart(2, '0')
  return `${minutes}:${seconds}`
}

export function wordCount(text) {
  if (!text || !text.trim()) return 0
  return text.trim().split(/\s+/).length
}
