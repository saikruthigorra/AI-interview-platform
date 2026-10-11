import { useCallback, useEffect, useState } from 'react'
import { api } from '../lib/api.js'
import { Card, DiffBadge, EmptyState, ErrorNote, Modal, SectionHeader, Spinner } from '../components/ui.jsx'

const EMPTY_FORM = { text: '', role: '', topic: '', difficulty: 'MEDIUM', expectedKeywords: '', idealAnswer: '', skillTags: '' }

export default function QuestionBank() {
  const [questions, setQuestions] = useState([])
  const [roles, setRoles] = useState([])
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState('')

  const [role, setRole] = useState('')
  const [difficulty, setDifficulty] = useState('')
  const [search, setSearch] = useState('')

  const [modalOpen, setModalOpen] = useState(false)
  const [form, setForm] = useState(EMPTY_FORM)
  const [saving, setSaving] = useState(false)
  const [formError, setFormError] = useState('')
  const [expanded, setExpanded] = useState({})

  const load = useCallback(() => {
    setLoading(true)
    setError('')
    Promise.all([
      api.questions.search({ role, difficulty, search }),
      roles.length ? Promise.resolve(roles) : api.questions.roles(),
    ])
      .then(([qs, rs]) => {
        setQuestions(qs)
        setRoles(rs)
      })
      .catch((e) => setError(e.message))
      .finally(() => setLoading(false))
  }, [role, difficulty, search, roles])

  useEffect(() => {
    const timer = setTimeout(load, search ? 250 : 0)
    return () => clearTimeout(timer)
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [role, difficulty, search])

  const create = async (event) => {
    event.preventDefault()
    setSaving(true)
    setFormError('')
    try {
      await api.questions.create({
        ...form,
        role: form.role.trim() || role || 'General',
        topic: form.topic.trim() || null,
        expectedKeywords: form.expectedKeywords.trim() || null,
        idealAnswer: form.idealAnswer.trim() || null,
        skillTags: form.skillTags.trim() || null,
      })
      setModalOpen(false)
      setForm(EMPTY_FORM)
      load()
    } catch (e) {
      setFormError(e.message)
    } finally {
      setSaving(false)
    }
  }

  const remove = async (question) => {
    if (!window.confirm('Delete this question?')) return
    try {
      await api.questions.remove(question.id)
      load()
    } catch (e) {
      setError(e.message)
    }
  }

  return (
    <div className="fade-in space-y-6">
      <SectionHeader
        title="Question bank"
        subtitle={`${questions.length} question(s) matching the filters — used for sessions and as rubric ground truth`}
        action={
          <button type="button" className="btn btn-primary" onClick={() => setModalOpen(true)}>
            + Add question
          </button>
        }
      />

      <ErrorNote message={error} onRetry={load} />

      <div className="grid gap-3 sm:grid-cols-3">
        <div>
          <label className="label" htmlFor="f-role">
            Role
          </label>
          <select id="f-role" className="input" value={role} onChange={(e) => setRole(e.target.value)}>
            <option value="">All roles</option>
            {roles.map((r) => (
              <option key={r} value={r}>
                {r}
              </option>
            ))}
          </select>
        </div>
        <div>
          <label className="label" htmlFor="f-diff">
            Difficulty
          </label>
          <select id="f-diff" className="input" value={difficulty} onChange={(e) => setDifficulty(e.target.value)}>
            <option value="">Any difficulty</option>
            <option value="EASY">Easy</option>
            <option value="MEDIUM">Medium</option>
            <option value="HARD">Hard</option>
          </select>
        </div>
        <div>
          <label className="label" htmlFor="f-search">
            Search
          </label>
          <input
            id="f-search"
            className="input"
            placeholder="e.g. caching, index, Kafka…"
            value={search}
            onChange={(e) => setSearch(e.target.value)}
          />
        </div>
      </div>

      {loading ? (
        <Spinner label="Loading questions…" />
      ) : questions.length === 0 ? (
        <EmptyState title="No questions match" hint="Try clearing the filters or add a new question to the bank." />
      ) : (
        <div className="space-y-3">
          {questions.map((q) => {
            const isOpen = !!expanded[q.id]
            return (
              <Card key={q.id} className="!p-0 overflow-hidden">
                <div className="flex items-start gap-4 px-5 py-4">
                  <div className="min-w-0 flex-1">
                    <div className="mb-2 flex flex-wrap items-center gap-2">
                      <DiffBadge difficulty={q.difficulty} />
                      <span className="chip">{q.role}</span>
                      {q.topic && <span className="chip">{q.topic}</span>}
                      <span
                        className={`chip ${
                          q.source === 'AI' ? 'border-indigo-400/30 bg-indigo-400/10 text-indigo-300' : ''
                        }`}
                      >
                        {q.source === 'AI' ? 'AI-generated' : 'bank'}
                      </span>
                      {q.skillTags &&
                        q.skillTags.split(',').slice(0, 3).map((tag) => (
                          <span key={tag} className="chip border-teal-400/25 bg-teal-400/10 text-teal-200">
                            {tag.trim()}
                          </span>
                        ))}
                    </div>
                    <p className="text-sm font-medium leading-relaxed text-slate-100">{q.text}</p>
                    <button
                      type="button"
                      className="mt-2 text-xs font-semibold text-indigo-300 hover:text-indigo-200"
                      onClick={() => setExpanded((prev) => ({ ...prev, [q.id]: !prev[q.id] }))}
                    >
                      {isOpen ? '▾ Hide rubric' : '▸ Show rubric & model answer'}
                    </button>
                  </div>
                  <button type="button" className="btn btn-danger !px-3 !py-1.5 text-xs" onClick={() => remove(q)}>
                    Delete
                  </button>
                </div>
                {isOpen && (
                  <div className="space-y-3 border-t border-white/5 bg-white/[0.02] px-5 py-4">
                    {q.expectedKeywords && (
                      <div>
                        <p className="text-xs font-semibold uppercase tracking-wider text-slate-400">Graded concepts</p>
                        <div className="mt-1.5 flex flex-wrap gap-2">
                          {q.expectedKeywords.split(',').map((k) => (
                            <span key={k} className="chip">
                              {k.trim()}
                            </span>
                          ))}
                        </div>
                      </div>
                    )}
                    {q.idealAnswer && (
                      <div>
                        <p className="text-xs font-semibold uppercase tracking-wider text-slate-400">Model answer</p>
                        <p className="mt-1 text-sm leading-relaxed text-slate-300">{q.idealAnswer}</p>
                      </div>
                    )}
                  </div>
                )}
              </Card>
            )
          })}
        </div>
      )}

      <Modal
        open={modalOpen}
        title="Add a question"
        onClose={() => setModalOpen(false)}
        footer={
          <>
            <button type="button" className="btn btn-ghost" onClick={() => setModalOpen(false)}>
              Cancel
            </button>
            <button type="submit" form="question-form" className="btn btn-primary" disabled={saving}>
              {saving ? 'Saving…' : 'Add question'}
            </button>
          </>
        }
      >
        <form id="question-form" onSubmit={create} className="space-y-4">
          <div>
            <label className="label" htmlFor="q-text">
              Question
            </label>
            <textarea
              id="q-text"
              className="input min-h-[90px]"
              required
              value={form.text}
              onChange={(e) => setForm({ ...form, text: e.target.value })}
              placeholder="Explain how you would design…"
            />
          </div>
          <div className="grid gap-4 sm:grid-cols-2">
            <div>
              <label className="label" htmlFor="q-role">
                Role
              </label>
              <input
                id="q-role"
                className="input"
                value={form.role}
                onChange={(e) => setForm({ ...form, role: e.target.value })}
                placeholder={role || 'Backend Engineer'}
              />
            </div>
            <div>
              <label className="label" htmlFor="q-topic">
                Topic
              </label>
              <input
                id="q-topic"
                className="input"
                value={form.topic}
                onChange={(e) => setForm({ ...form, topic: e.target.value })}
                placeholder="Databases"
              />
            </div>
          </div>
          <div>
            <label className="label" htmlFor="q-diff">
              Difficulty
            </label>
            <select
              id="q-diff"
              className="input"
              value={form.difficulty}
              onChange={(e) => setForm({ ...form, difficulty: e.target.value })}
            >
              <option value="EASY">Easy</option>
              <option value="MEDIUM">Medium</option>
              <option value="HARD">Hard</option>
            </select>
          </div>
          <div>
            <label className="label" htmlFor="q-skills">
              Skill tags (comma-separated)
            </label>
            <input
              id="q-skills"
              className="input"
              value={form.skillTags}
              onChange={(e) => setForm({ ...form, skillTags: e.target.value })}
              placeholder="React, JavaScript"
            />
            <p className="mt-1 text-xs text-slate-500">
              Used to match this question to students whose skills overlap — drives skill-based selection.
            </p>
          </div>
          <div>
            <label className="label" htmlFor="q-kw">
              Graded concepts (comma-separated)
            </label>
            <input
              id="q-kw"
              className="input"
              value={form.expectedKeywords}
              onChange={(e) => setForm({ ...form, expectedKeywords: e.target.value })}
              placeholder="index, selectivity, write amplification"
            />
            <p className="mt-1 text-xs text-slate-500">
              The scorer checks how many of these a candidate mentions — it&apos;s also the rubric an LLM grades against.
            </p>
          </div>
          <div>
            <label className="label" htmlFor="q-ideal">
              Model answer
            </label>
            <textarea
              id="q-ideal"
              className="input min-h-[80px]"
              value={form.idealAnswer}
              onChange={(e) => setForm({ ...form, idealAnswer: e.target.value })}
              placeholder="What a strong answer covers…"
            />
          </div>
          {formError && <p className="text-sm text-rose-300">{formError}</p>}
        </form>
      </Modal>
    </div>
  )
}
