import { useEffect, useState } from 'react'
import { useNavigate, useSearchParams } from 'react-router-dom'
import { useAuth } from '../auth/AuthContext.jsx'
import { api } from '../lib/api.js'
import { Avatar, Card, ErrorNote, SectionHeader, Spinner } from '../components/ui.jsx'

const DIFFICULTIES = [
  { value: 'EASY', label: 'Easy', hint: 'Fundamentals and definitions' },
  { value: 'MEDIUM', label: 'Medium', hint: 'Applied reasoning and trade-offs' },
  { value: 'HARD', label: 'Hard', hint: 'System design and edge cases' },
]

export default function InterviewSetup() {
  const [params] = useSearchParams()
  const navigate = useNavigate()
  const { user } = useAuth()
  // Students practise as themselves — no candidate roster involved.
  const personalMode = !!user && user.role === 'STUDENT'

  const [candidates, setCandidates] = useState([])
  const [roles, setRoles] = useState([])
  const [meta, setMeta] = useState(null)
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState('')
  const [starting, setStarting] = useState(false)

  const [candidateId, setCandidateId] = useState(params.get('candidateId') || '')
  const [role, setRole] = useState('Backend Engineer')
  const [customRole, setCustomRole] = useState('')
  const [difficulty, setDifficulty] = useState('MEDIUM')
  const [questionCount, setQuestionCount] = useState(5)
  const [skills, setSkills] = useState('')
  const [level, setLevel] = useState('STUDENT')

  useEffect(() => {
    Promise.all([api.candidates.list(), api.questions.roles(), api.meta()])
      .then(async ([c, r, m]) => {
        setRoles(r)
        setMeta(m)

        if (personalMode) {
          // Reuse (or quietly create once) a private practice profile for this account.
          let list = c
          let mine = list.find((x) => (x.email || '').toLowerCase() === (user.email || '').toLowerCase())
          if (!mine) {
            mine = await api.candidates.create({
              name: user.name,
              email: user.email,
              headline: 'Practice profile',
              level: 'STUDENT',
            })
            list = [...list, mine]
          }
          setCandidates(list)
          setCandidateId(String(mine.id))
          if (mine.targetRole && r.includes(mine.targetRole)) setRole(mine.targetRole)
          return
        }

        setCandidates(c)
        const preselected = params.get('candidateId')
        if (preselected) {
          // Prefer the role this candidate is actually targeting.
          const person = c.find((x) => String(x.id) === String(preselected))
          if (person?.targetRole && r.includes(person.targetRole)) {
            setRole(person.targetRole)
          }
        } else if (c.length) {
          setCandidateId(String(c[0].id))
        }
      })
      .catch((e) => setError(e.message))
      .finally(() => setLoading(false))
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [])

  const selectedCandidate = candidates.find((c) => String(c.id) === String(candidateId))
  const effectiveRole = role === '__custom' ? customRole : role
  const skillList = skills.split(',').map((s) => s.trim()).filter(Boolean)

  useEffect(() => {
    if (!selectedCandidate) return
    setSkills((selectedCandidate.skills || []).join(', '))
    setLevel(selectedCandidate.level || 'STUDENT')
  }, [selectedCandidate?.id])


  const start = async () => {
    if (!candidateId) {
      setError('Pick a candidate first')
      return
    }
    if (!effectiveRole.trim()) {
      setError('Choose or type a role')
      return
    }
    setStarting(true)
    setError('')
    try {
      const detail = await api.interviews.start({
        candidateId: Number(candidateId),
        role: effectiveRole.trim(),
        difficulty,
        questionCount: Number(questionCount),
        skills: skills.trim() || null,
        level,
      })
      navigate(`/interview/${detail.session.id}`)
    } catch (e) {
      setError(e.message)
      setStarting(false)
    }
  }

  if (loading) return <Spinner label="Preparing setup…" />

  return (
    <div className="fade-in mx-auto max-w-4xl space-y-6">
      <SectionHeader
        title={personalMode ? 'Practice interview' : 'Start an interview'}
        subtitle={
          personalMode
            ? 'Questions are generated for the role you choose the moment you hit start'
            : 'Questions are generated for the role and difficulty the moment you hit start'
        }
      />

      <ErrorNote message={error} />

      {candidates.length === 0 && !personalMode ? (
        <Card className="text-center">
          <p className="font-medium text-slate-100">You need a candidate first</p>
          <p className="mt-1 text-sm text-slate-400">Add someone to the pipeline, then come back here.</p>
          <button type="button" className="btn btn-primary mt-4" onClick={() => navigate('/candidates')}>
            Go to candidates
          </button>
        </Card>
      ) : (
        <div className="grid gap-6 lg:grid-cols-[1.4fr_1fr]">
          <Card className="space-y-5">
            {personalMode ? (
              <div className="flex items-center gap-3 rounded-xl border border-teal-400/20 bg-teal-400/[0.06] px-4 py-3">
                <Avatar name={user?.name} size={36} />
                <div>
                  <p className="text-sm font-semibold text-slate-100">Practising as {user?.name}</p>
                  <p className="text-xs text-slate-400">
                    {selectedCandidate?.sessionCount || 0} practice interview(s) so far
                    {selectedCandidate?.education ? ` · ${selectedCandidate.education}` : ''}
                  </p>
                </div>
              </div>
            ) : (
              <div>
                <label className="label" htmlFor="candidate">
                  Candidate
                </label>
                <select
                  id="candidate"
                  className="input"
                  value={candidateId}
                  onChange={(e) => setCandidateId(e.target.value)}
                >
                  <option value="">Select a candidate…</option>
                  {candidates.map((c) => (
                    <option key={c.id} value={c.id}>
                      {c.name} — {c.email}
                    </option>
                  ))}
                </select>
                {selectedCandidate && (
                  <div className="mt-2 space-y-2">
                    <p className="text-xs text-slate-400">
                      {selectedCandidate.sessionCount} interview(s) so far
                      {selectedCandidate.education ? ` · ${selectedCandidate.education}` : ''}
                      {' · '}
                      <button
                        type="button"
                        className="text-indigo-300 underline-offset-2 hover:underline"
                        onClick={() => navigate('/candidates')}
                      >
                        edit profile
                      </button>
                    </p>
                    {(selectedCandidate.skills || []).length > 0 && (
                      <div className="flex flex-wrap gap-1.5">
                        {selectedCandidate.skills.map((skill) => (
                          <span key={skill} className="chip border-indigo-400/25 bg-indigo-400/10 text-indigo-200">
                            {skill}
                          </span>
                        ))}
                      </div>
                    )}
                  </div>
                )}
              </div>
            )}

            <div>
              <label className="label" htmlFor="role">
                Role
              </label>
              <select id="role" className="input" value={role} onChange={(e) => setRole(e.target.value)}>
                {roles.map((r) => (
                  <option key={r} value={r}>
                    {r}
                  </option>
                ))}
                <option value="__custom">Custom role…</option>
              </select>
              {role === '__custom' && (
                <input
                  className="input mt-2"
                  value={customRole}
                  onChange={(e) => setCustomRole(e.target.value)}
                  placeholder="e.g. Site Reliability Engineer"
                />
              )}
            </div>

            <div className="grid gap-4 sm:grid-cols-[1.6fr_1fr]">
              <div>
                <label className="label" htmlFor="session-skills">
                  Skills for this interview
                </label>
                <input
                  id="session-skills"
                  className="input"
                  value={skills}
                  onChange={(e) => setSkills(e.target.value)}
                  placeholder="React, JavaScript, CSS"
                />
                <p className="mt-1 text-xs text-slate-500">
                  Prefilled from the student&apos;s profile — tweak it to focus this session.
                </p>
              </div>
              <div>
                <label className="label" htmlFor="session-level">
                  Level
                </label>
                <select
                  id="session-level"
                  className="input"
                  value={level}
                  onChange={(e) => setLevel(e.target.value)}
                >
                  <option value="STUDENT">Student</option>
                  <option value="GRADUATE">Graduate</option>
                  <option value="PROFESSIONAL">Professional</option>
                </select>
              </div>
            </div>

            <div>
              <span className="label">Difficulty</span>
              <div className="grid gap-2 sm:grid-cols-3">
                {DIFFICULTIES.map((d) => (
                  <button
                    key={d.value}
                    type="button"
                    onClick={() => setDifficulty(d.value)}
                    className={`rounded-xl border px-3 py-2.5 text-left transition ${
                      difficulty === d.value
                        ? 'border-indigo-400/60 bg-indigo-500/15 text-slate-100'
                        : 'border-white/10 bg-white/[0.03] text-slate-300 hover:border-white/25'
                    }`}
                  >
                    <span className="block text-sm font-semibold">{d.label}</span>
                    <span className="block text-[11px] text-slate-400">{d.hint}</span>
                  </button>
                ))}
              </div>
            </div>

            <div>
              <div className="flex items-center justify-between">
                <span className="label !mb-0">Questions</span>
                <span className="text-sm font-semibold text-slate-200">{questionCount}</span>
              </div>
              <input
                type="range"
                min={3}
                max={12}
                value={questionCount}
                onChange={(e) => setQuestionCount(Number(e.target.value))}
                className="mt-2 w-full accent-indigo-400"
              />
              <p className="mt-1 text-xs text-slate-500">3–12 questions · about 3 minutes each</p>
            </div>

            <button type="button" className="btn btn-primary w-full" onClick={start} disabled={starting}>
              {starting ? 'Generating questions…' : '▶ Start interview'}
            </button>
          </Card>

          <div className="space-y-4">
            <Card>
              <h3 className="text-sm font-semibold text-slate-100">How it runs</h3>
              <ol className="mt-3 space-y-3 text-xs text-slate-400">
                <li>
                  <span className="font-semibold text-slate-200">1 · Questions</span>
                  <br />
                  Generated for {effectiveRole || 'the role'} at {difficulty.toLowerCase()} level, weighted towards{' '}
                  {skillList.length ? `their skills (${skillList.slice(0, 3).join(', ')}${skillList.length > 3 ? '…' : ''})` : 'the role'},
                  and avoiding anything already answered before.
                </li>
                <li>
                  <span className="font-semibold text-slate-200">2 · Answers</span>
                  <br />
                  The candidate answers in the browser; each answer is graded the moment it is submitted.
                </li>
                <li>
                  <span className="font-semibold text-slate-200">3 · Report</span>
                  <br />
                  Scores per dimension, a written debrief, strengths, gaps and a model answer per question.
                </li>
              </ol>
            </Card>

            <Card>
              <h3 className="text-sm font-semibold text-slate-100">Scoring engine</h3>
              <p className="mt-2 text-xs text-slate-400">
                {meta?.llmEnabled ? (
                  <>
                    LLM grading active — <span className="font-mono text-slate-200">{meta.llmModel}</span>. If the model
                    errors, the built-in heuristic grader takes over automatically.
                  </>
                ) : (
                  <>
                    Running the built-in heuristic grader: concept coverage against each question&apos;s rubric, plus
                    depth, structure and clarity signals. Set <code className="text-slate-200">APP_AI_API_KEY</code> to
                    switch to LLM grading.
                  </>
                )}
              </p>
            </Card>
          </div>
        </div>
      )}
    </div>
  )
}
