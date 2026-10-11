import { scoreTone, toneBar, toneStroke, toneText } from '../lib/format.js'

export function Card({ className = '', children }) {
  return <div className={`card p-5 ${className}`}>{children}</div>
}

export function SectionHeader({ title, subtitle, action }) {
  return (
    <div className="mb-4 flex flex-wrap items-end justify-between gap-3">
      <div>
        <h2 className="text-lg font-semibold text-slate-100">{title}</h2>
        {subtitle && <p className="mt-0.5 text-sm text-slate-400">{subtitle}</p>}
      </div>
      {action}
    </div>
  )
}

export function Badge({ children, className = '' }) {
  return <span className={`chip ${className}`}>{children}</span>
}

export function DiffBadge({ difficulty }) {
  const tone =
    difficulty === 'EASY'
      ? 'border-emerald-400/30 bg-emerald-400/10 text-emerald-300'
      : difficulty === 'HARD'
        ? 'border-rose-400/30 bg-rose-400/10 text-rose-300'
        : 'border-amber-400/30 bg-amber-400/10 text-amber-300'
  return <span className={`chip ${tone}`}>{difficulty}</span>
}

const STATUS_TONES = {
  COMPLETED: ['Completed', 'border-teal-400/30 bg-teal-400/10 text-teal-300', 'bg-teal-300'],
  IN_PROGRESS: ['In progress', 'border-indigo-400/30 bg-indigo-400/10 text-indigo-300', 'bg-indigo-300'],
  DRAFT: ['Draft', 'border-slate-400/30 bg-slate-400/10 text-slate-300', 'bg-slate-300'],
  PUBLISHED: ['Published', 'border-indigo-400/30 bg-indigo-400/10 text-indigo-300', 'bg-indigo-300'],
  CLOSED: ['Closed', 'border-rose-400/30 bg-rose-400/10 text-rose-300', 'bg-rose-300'],
}

export function StatusPill({ status }) {
  const [label, tone, dot] = STATUS_TONES[status] ?? STATUS_TONES.IN_PROGRESS
  return (
    <span className={`chip ${tone}`}>
      <span className={`h-1.5 w-1.5 rounded-full ${dot}`} />
      {label}
    </span>
  )
}

export function ScoreRing({ value, size = 88, stroke = 8, caption }) {
  const score = value == null ? 0 : Math.round(value)
  const tone = scoreTone(value)
  const radius = (size - stroke) / 2
  const circumference = 2 * Math.PI * radius
  const dash = (Math.min(100, Math.max(0, score)) / 100) * circumference

  return (
    <div className="relative inline-flex items-center justify-center" style={{ width: size, height: size }}>
      <svg width={size} height={size} className="-rotate-90">
        <circle cx={size / 2} cy={size / 2} r={radius} fill="none" stroke="rgba(148,163,184,0.18)" strokeWidth={stroke} />
        <circle
          cx={size / 2}
          cy={size / 2}
          r={radius}
          fill="none"
          stroke={toneStroke[tone]}
          strokeWidth={stroke}
          strokeLinecap="round"
          strokeDasharray={`${dash} ${circumference}`}
          style={{ transition: 'stroke-dasharray 0.6s ease' }}
        />
      </svg>
      <div className="absolute inset-0 flex flex-col items-center justify-center">
        <span className={`text-xl font-bold ${toneText[tone]}`}>{value == null ? '—' : score}</span>
        {caption && <span className="text-[10px] uppercase tracking-wider text-slate-500">{caption}</span>}
      </div>
    </div>
  )
}

export function ScoreBar({ label, value, max = 100 }) {
  const tone = scoreTone(value)
  const pct = Math.max(0, Math.min(100, ((value ?? 0) / max) * 100))
  return (
    <div>
      <div className="mb-1 flex items-center justify-between text-xs">
        <span className="text-slate-400">{label}</span>
        <span className={`font-semibold ${toneText[tone]}`}>{value ?? '—'}</span>
      </div>
      <div className="h-1.5 overflow-hidden rounded-full bg-white/10">
        <div className={`h-full rounded-full ${toneBar[tone]}`} style={{ width: `${pct}%`, transition: 'width .5s ease' }} />
      </div>
    </div>
  )
}

export function StatCard({ label, value, hint, accent = 'indigo', icon }) {
  const accents = {
    indigo: 'from-indigo-500/20 to-indigo-500/0 text-indigo-300',
    teal: 'from-teal-500/20 to-teal-500/0 text-teal-300',
    amber: 'from-amber-500/20 to-amber-500/0 text-amber-300',
    rose: 'from-rose-500/20 to-rose-500/0 text-rose-300',
    sky: 'from-sky-500/20 to-sky-500/0 text-sky-300',
  }
  return (
    <div className={`card relative overflow-hidden p-5 bg-gradient-to-br ${accents[accent]}`}>
      <div className="flex items-start justify-between">
        <div>
          <p className="text-xs font-medium uppercase tracking-wider text-slate-400">{label}</p>
          <p className="mt-2 text-3xl font-bold text-slate-50">{value}</p>
          {hint && <p className="mt-1 text-xs text-slate-400">{hint}</p>}
        </div>
        {icon && <span className="text-2xl opacity-70">{icon}</span>}
      </div>
    </div>
  )
}

export function Spinner({ label = 'Loading…' }) {
  return (
    <div className="flex items-center gap-3 py-10 text-sm text-slate-400">
      <span className="h-4 w-4 animate-spin rounded-full border-2 border-indigo-400 border-t-transparent" />
      {label}
    </div>
  )
}

export function ErrorNote({ message, onRetry }) {
  if (!message) return null
  return (
    <div className="flex flex-wrap items-center justify-between gap-3 rounded-xl border border-rose-500/30 bg-rose-500/10 px-4 py-3 text-sm text-rose-200">
      <span>{message}</span>
      {onRetry && (
        <button type="button" className="btn btn-ghost !py-1.5" onClick={onRetry}>
          Retry
        </button>
      )}
    </div>
  )
}

export function EmptyState({ title, hint, action }) {
  return (
    <div className="flex flex-col items-center justify-center gap-2 rounded-2xl border border-dashed border-white/15 py-12 text-center">
      <p className="font-medium text-slate-200">{title}</p>
      {hint && <p className="max-w-md text-sm text-slate-400">{hint}</p>}
      {action}
    </div>
  )
}

export function Modal({ open, title, onClose, children, footer }) {
  if (!open) return null
  return (
    <div className="fixed inset-0 z-50 flex items-center justify-center p-4">
      <div className="absolute inset-0 bg-black/70 backdrop-blur-sm" onClick={onClose} />
      <div className="card fade-in relative z-10 max-h-[85vh] w-full max-w-xl overflow-y-auto p-6">
        <div className="mb-4 flex items-center justify-between">
          <h3 className="text-base font-semibold text-slate-100">{title}</h3>
          <button type="button" className="btn btn-ghost !px-2.5 !py-1" onClick={onClose} aria-label="Close">
            ✕
          </button>
        </div>
        {children}
        {footer && <div className="mt-5 flex justify-end gap-2">{footer}</div>}
      </div>
    </div>
  )
}

export function Avatar({ name, size = 40 }) {
  const text = (name || '?')
    .split(/\s+/)
    .filter(Boolean)
    .slice(0, 2)
    .map((p) => p[0].toUpperCase())
    .join('')
  return (
    <span
      className="inline-flex shrink-0 items-center justify-center rounded-full border border-white/10 bg-gradient-to-br from-indigo-500/30 to-teal-500/20 font-semibold text-slate-100"
      style={{ width: size, height: size, fontSize: size * 0.36 }}
    >
      {text}
    </span>
  )
}
