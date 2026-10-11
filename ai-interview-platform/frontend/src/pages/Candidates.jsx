import { useEffect, useState } from 'react'
import { Link, useNavigate } from 'react-router-dom'
import { api } from '../lib/api.js'
import { relativeTime } from '../lib/format.js'
import { Avatar, Card, EmptyState, ErrorNote, Modal, SectionHeader, Spinner } from '../components/ui.jsx'

const EMPTY = { name: '', email: '', headline: '', targetRole: '', skills: '', education: '', level: 'STUDENT' }

const LEVELS = [
  { value: 'STUDENT', label: 'Student' },
  { value: 'GRADUATE', label: 'Graduate' },
  { value: 'PROFESSIONAL', label: 'Professional' },
]

export default function Candidates() {
  const [candidates, setCandidates] = useState([])
  const [sessions, setSessions] = useState([])
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState('')
  const [modalOpen, setModalOpen] = useState(false)
  const [editing, setEditing] = useState(null)
  const [form, setForm] = useState(EMPTY)
  const [saving, setSaving] = useState(false)
  const [formError, setFormError] = useState('')
  const navigate = useNavigate()

  const load = () => {
    setLoading(true)
    setError('')
    Promise.all([api.candidates.list(), api.interviews.list()])
      .then(([c, s]) => {
        setCandidates(c)
        setSessions(s)
      })
      .catch((e) => setError(e.message))
      .finally(() => setLoading(false))
  }

  useEffect(load, [])

  const openCreate = () => {
    setEditing(null)
    setForm(EMPTY)
    setFormError('')
    setModalOpen(true)
  }

  const openEdit = (candidate) => {
    setEditing(candidate)
    setForm({
      name: candidate.name,
      email: candidate.email,
      headline: candidate.headline || '',
      targetRole: candidate.targetRole || '',
      skills: (candidate.skills || []).join(', '),
      education: candidate.education || '',
      level: candidate.level || 'STUDENT',
    })
    setFormError('')
    setModalOpen(true)
  }

  const save = async (event) => {
    event.preventDefault()
    setSaving(true)
    setFormError('')
    try {
      if (editing) {
        await api.candidates.update(editing.id, form)
      } else {
        await api.candidates.create(form)
      }
      setModalOpen(false)
      load()
    } catch (e) {
      setFormError(e.message)
    } finally {
      setSaving(false)
    }
  }

  const remove = async (candidate) => {
    if (!window.confirm(`Delete ${candidate.name} and all of their interviews?`)) return
    try {
      await api.candidates.remove(candidate.id)
      load()
    } catch (e) {
      setError(e.message)
    }
  }

  const sessionsFor = (id) => sessions.filter((s) => s.candidateId === id)

  return (
    <div className="fade-in space-y-6">
      <SectionHeader
        title="Students"
        subtitle="Everyone in the pipeline, with their interview history"
        action={
          <button type="button" className="btn btn-primary" onClick={openCreate}>
            + Add candidate
          </button>
        }
      />

      <ErrorNote message={error} onRetry={load} />

      {loading ? (
        <Spinner label="Loading candidates…" />
      ) : candidates.length === 0 ? (
        <EmptyState
          title="No candidates yet"
          hint="Add your first candidate to run an AI interview against them."
          action={
            <button type="button" className="btn btn-primary mt-2" onClick={openCreate}>
              + Add candidate
            </button>
          }
        />
      ) : (
        <div className="grid gap-4 xl:grid-cols-2">
          {candidates.map((candidate) => {
            const history = sessionsFor(candidate.id)
            const completed = history.filter((s) => s.status === 'COMPLETED')
            const best = completed.length ? Math.max(...completed.map((s) => s.overallScore ?? 0)) : null
            const inProgress = history.find((s) => s.status === 'IN_PROGRESS')
            return (
              <Card key={candidate.id} className="card-hover">
                <div className="flex items-start gap-4">
                  <Avatar name={candidate.name} size={48} />
                  <div className="min-w-0 flex-1">
                    <div className="flex flex-wrap items-center gap-2">
                      <p className="font-semibold text-slate-100">{candidate.name}</p>
                      {candidate.targetRole && <span className="chip">{candidate.targetRole}</span>}
                      {candidate.level && <span className="chip">{candidate.level.toLowerCase()}</span>}
                      {inProgress && (
                        <span className="chip border-indigo-400/30 bg-indigo-400/10 text-indigo-300">
                          interview in progress
                        </span>
                      )}
                    </div>
                    <p className="truncate text-xs text-slate-400">{candidate.email}</p>
                    {candidate.headline && <p className="mt-1 text-xs text-slate-400">{candidate.headline}</p>}
                    {candidate.education && <p className="mt-0.5 text-xs text-slate-500">{candidate.education}</p>}
                    {candidate.skills?.length > 0 && (
                      <div className="mt-2 flex flex-wrap gap-1.5">
                        {candidate.skills.slice(0, 6).map((skill) => (
                          <span key={skill} className="chip border-indigo-400/25 bg-indigo-400/10 text-indigo-200">
                            {skill}
                          </span>
                        ))}
                        {candidate.skills.length > 6 && (
                          <span className="chip">+{candidate.skills.length - 6} more</span>
                        )}
                      </div>
                    )}
                    <div className="mt-3 flex flex-wrap items-center gap-3 text-xs text-slate-400">
                      <span>{history.length} interview(s)</span>
                      <span>·</span>
                      <span>best {best == null ? '—' : `${best}/100`}</span>
                      <span>·</span>
                      <span>added {relativeTime(candidate.createdAt)}</span>
                    </div>
                  </div>
                </div>

                {history.length > 0 && (
                  <div className="mt-4 space-y-1.5 border-t border-white/5 pt-3">
                    {history.slice(0, 3).map((session) => (
                      <div key={session.id} className="flex items-center justify-between gap-3 text-xs">
                        <span className="min-w-0 truncate text-slate-400">
                          {session.role} · {session.difficulty.toLowerCase()} · {session.answeredCount}/{session.questionCount} answered
                        </span>
                        <div className="flex items-center gap-2">
                          <span className="font-semibold text-slate-200">
                            {session.overallScore == null ? '—' : session.overallScore}
                          </span>
                          <Link
                            to={
                              session.status === 'COMPLETED'
                                ? `/interview/${session.id}/report`
                                : `/interview/${session.id}`
                            }
                            className="text-indigo-300 hover:text-indigo-200"
                          >
                            {session.status === 'COMPLETED' ? 'report' : 'resume'}
                          </Link>
                        </div>
                      </div>
                    ))}
                  </div>
                )}

                <div className="mt-4 flex flex-wrap gap-2">
                  <button
                    type="button"
                    className="btn btn-primary !py-2 text-xs"
                    onClick={() => navigate(`/interview/new?candidateId=${candidate.id}`)}
                  >
                    {inProgress ? 'Resume interview' : 'Start interview'}
                  </button>
                  <button type="button" className="btn btn-ghost !py-2 text-xs" onClick={() => openEdit(candidate)}>
                    Edit
                  </button>
                  <button type="button" className="btn btn-danger !py-2 text-xs" onClick={() => remove(candidate)}>
                    Delete
                  </button>
                </div>
              </Card>
            )
          })}
        </div>
      )}

      <Modal
        open={modalOpen}
        title={editing ? `Edit ${editing.name}` : 'Add candidate'}
        onClose={() => setModalOpen(false)}
        footer={
          <>
            <button type="button" className="btn btn-ghost" onClick={() => setModalOpen(false)}>
              Cancel
            </button>
            <button type="submit" form="candidate-form" className="btn btn-primary" disabled={saving}>
              {saving ? 'Saving…' : editing ? 'Save changes' : 'Add candidate'}
            </button>
          </>
        }
      >
        <form id="candidate-form" onSubmit={save} className="space-y-4">
          <div>
            <label className="label" htmlFor="c-name">
              Full name
            </label>
            <input
              id="c-name"
              className="input"
              required
              value={form.name}
              onChange={(e) => setForm({ ...form, name: e.target.value })}
              placeholder="Aarav Mehta"
            />
          </div>
          <div>
            <label className="label" htmlFor="c-email">
              Email
            </label>
            <input
              id="c-email"
              type="email"
              className="input"
              required
              value={form.email}
              onChange={(e) => setForm({ ...form, email: e.target.value })}
              placeholder="aarav@example.com"
            />
          </div>
          <div className="grid gap-4 sm:grid-cols-2">
            <div>
              <label className="label" htmlFor="c-headline">
                Headline
              </label>
              <input
                id="c-headline"
                className="input"
                value={form.headline}
                onChange={(e) => setForm({ ...form, headline: e.target.value })}
                placeholder="Backend engineer · 4 yrs"
              />
            </div>
            <div>
              <label className="label" htmlFor="c-role">
                Target role
              </label>
              <input
                id="c-role"
                className="input"
                value={form.targetRole}
                onChange={(e) => setForm({ ...form, targetRole: e.target.value })}
                placeholder="Backend Engineer"
              />
            </div>
          </div>
          <div className="grid gap-4 sm:grid-cols-2">
            <div>
              <label className="label" htmlFor="c-education">
                Education
              </label>
              <input
                id="c-education"
                className="input"
                value={form.education}
                onChange={(e) => setForm({ ...form, education: e.target.value })}
                placeholder="B.Tech CSE · 2026"
              />
            </div>
            <div>
              <label className="label" htmlFor="c-level">
                Level
              </label>
              <select
                id="c-level"
                className="input"
                value={form.level}
                onChange={(e) => setForm({ ...form, level: e.target.value })}
              >
                {LEVELS.map((l) => (
                  <option key={l.value} value={l.value}>
                    {l.label}
                  </option>
                ))}
              </select>
            </div>
          </div>
          <div>
            <label className="label" htmlFor="c-skills">
              Skills (comma-separated)
            </label>
            <input
              id="c-skills"
              className="input"
              value={form.skills}
              onChange={(e) => setForm({ ...form, skills: e.target.value })}
              placeholder="React, JavaScript, CSS"
            />
            <p className="mt-1 text-xs text-slate-500">
              Interviews generate questions that match these skills first, then fill from the target role.
            </p>
          </div>
          {formError && <p className="text-sm text-rose-300">{formError}</p>}
        </form>
      </Modal>
    </div>
  )
}
