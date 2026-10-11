// Thin fetch wrapper around the Spring Boot API.
// - relative paths, so the Vite dev server proxy (and any reverse proxy in
//   production) routes /api to the backend without CORS gymnastics;
// - attaches the bearer token automatically;
// - turns an expired/invalid token into a global "unauthorized" event that the
//   auth context listens for, so the app bounces to /login exactly once.

const BASE = '/api'
const TOKEN_KEY = 'iiq_token'

export function getToken() {
  return localStorage.getItem(TOKEN_KEY)
}

export function setToken(token) {
  if (token) {
    localStorage.setItem(TOKEN_KEY, token)
  } else {
    localStorage.removeItem(TOKEN_KEY)
  }
}

async function request(path, { method = 'GET', body, auth = true } = {}) {
  const headers = { 'Content-Type': 'application/json' }
  const token = getToken()
  if (auth && token) {
    headers.Authorization = `Bearer ${token}`
  }

  const response = await fetch(`${BASE}${path}`, {
    method,
    headers,
    body: body === undefined ? undefined : JSON.stringify(body),
  })

  if (response.status === 401 && auth && !path.startsWith('/auth/login')) {
    setToken(null)
    window.dispatchEvent(new CustomEvent('iiq:unauthorized'))
  }

  if (!response.ok) {
    let message = `Request failed (${response.status})`
    let payload = null
    try {
      payload = await response.json()
      if (payload?.error) message = payload.error
      if (payload?.fields) {
        message = Object.entries(payload.fields)
          .map(([field, detail]) => `${field} ${detail}`)
          .join(', ')
      }
    } catch {
      /* keep the generic message */
    }
    const error = new Error(message)
    error.status = response.status
    error.payload = payload
    throw error
  }

  if (response.status === 204) return null
  return response.json()
}

export const api = {
  meta: () => request('/meta'),
  stats: () => request('/stats'),

  auth: {
    register: (body) => request('/auth/register', { method: 'POST', body, auth: false }),
    login: (body) => request('/auth/login', { method: 'POST', body, auth: false }),
    me: () => request('/auth/me'),
    changePassword: (body) => request('/auth/password', { method: 'POST', body }),
    logout: () => request('/auth/logout', { method: 'POST' }),
  },

  users: {
    list: () => request('/users'),
    setRole: (id, role) => request(`/users/${id}/role`, { method: 'PATCH', body: { role } }),
    setEnabled: (id, enabled) => request(`/users/${id}/enabled`, { method: 'PATCH', body: { enabled } }),
  },

  candidates: {
    list: () => request('/candidates'),
    create: (body) => request('/candidates', { method: 'POST', body }),
    update: (id, body) => request(`/candidates/${id}`, { method: 'PUT', body }),
    remove: (id) => request(`/candidates/${id}`, { method: 'DELETE' }),
  },

  questions: {
    search: ({ role, difficulty, search } = {}) => {
      const params = new URLSearchParams()
      if (role) params.set('role', role)
      if (difficulty) params.set('difficulty', difficulty)
      if (search) params.set('search', search)
      const qs = params.toString()
      return request(`/questions${qs ? `?${qs}` : ''}`)
    },
    roles: () => request('/questions/roles'),
    skills: () => request('/questions/skills'),
    create: (body) => request('/questions', { method: 'POST', body }),
    remove: (id) => request(`/questions/${id}`, { method: 'DELETE' }),
  },

  interviews: {
    list: () => request('/interviews'),
    start: (body) => request('/interviews', { method: 'POST', body }),
    detail: (id) => request(`/interviews/${id}`),
    report: (id) => request(`/interviews/${id}/report`),
    submitAnswer: (id, body) => request(`/interviews/${id}/answers`, { method: 'POST', body }),
    finish: (id) => request(`/interviews/${id}/finish`, { method: 'POST' }),
    remove: (id) => request(`/interviews/${id}`, { method: 'DELETE' }),
  },

  feedback: {
    forSession: (id) => request(`/interviews/${id}/feedback`),
    submit: (id, body) => request(`/interviews/${id}/feedback`, { method: 'POST', body }),
    remove: (id) => request(`/interviews/${id}/feedback`, { method: 'DELETE' }),
    all: () => request('/feedback'),
  },

  analytics: {
    dashboard: () => request('/analytics'),
  },

  // Recruiter console: host online assessments (OA) and analyse the cohort.
  assessments: {
    list: () => request('/assessments'),
    get: (id) => request(`/assessments/${id}`),
    create: (body) => request('/assessments', { method: 'POST', body }),
    update: (id, body) => request(`/assessments/${id}`, { method: 'PUT', body }),
    remove: (id) => request(`/assessments/${id}`, { method: 'DELETE' }),
    generate: (id) => request(`/assessments/${id}/generate`, { method: 'POST' }),
    questions: (id) => request(`/assessments/${id}/questions`),
    removeQuestion: (id, aqId) => request(`/assessments/${id}/questions/${aqId}`, { method: 'DELETE' }),
    publish: (id) => request(`/assessments/${id}/publish`, { method: 'POST' }),
    close: (id) => request(`/assessments/${id}/close`, { method: 'POST' }),
    attempts: (id) => request(`/assessments/${id}/attempts`),
    attempt: (id, sessionId) => request(`/assessments/${id}/attempts/${sessionId}`),
    analytics: (id) => request(`/assessments/${id}/analytics`),
  },

  /** Seeded demo logins for the sign-in screen (no auth; empty when seeding is off). */
  demoAccounts: () => request('/public/demo-accounts', { auth: false }),

  // Candidate side of an OA — no account, the attempt token is the credential.
  publicOa: {
    info: (code) => request(`/public/assessments/${encodeURIComponent(code)}`, { auth: false }),
    start: (code, body) =>
      request(`/public/assessments/${encodeURIComponent(code)}/attempts`, {
        method: 'POST',
        body,
        auth: false,
      }),
    attempt: (token) => request(`/public/attempts/${encodeURIComponent(token)}`, { auth: false }),
    answer: (token, body) =>
      request(`/public/attempts/${encodeURIComponent(token)}/answers`, {
        method: 'POST',
        body,
        auth: false,
      }),
    submit: (token) =>
      request(`/public/attempts/${encodeURIComponent(token)}/submit`, { method: 'POST', auth: false }),
  },
}
