import { useEffect, useState } from 'react'
import { api } from '../lib/api.js'
import { formatDate } from '../lib/format.js'
import { Avatar, Card, ErrorNote, SectionHeader, Spinner } from '../components/ui.jsx'

const ROLES = ['ADMIN', 'RECRUITER', 'STUDENT']

export default function Team() {
  const [users, setUsers] = useState([])
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState('')
  const [notice, setNotice] = useState('')

  const load = () => {
    setLoading(true)
    api.users
      .list()
      .then(setUsers)
      .catch((e) => setError(e.message))
      .finally(() => setLoading(false))
  }

  useEffect(load, [])

  const changeRole = async (user, role) => {
    setError('')
    setNotice('')
    try {
      await api.users.setRole(user.id, role)
      setNotice(`${user.name} is now ${role.toLowerCase()}`)
      load()
    } catch (e) {
      setError(e.message)
    }
  }

  const toggleEnabled = async (user) => {
    setError('')
    setNotice('')
    try {
      await api.users.setEnabled(user.id, !user.enabled)
      setNotice(`${user.name} ${user.enabled ? 'disabled' : 'enabled'}`)
      load()
    } catch (e) {
      setError(e.message)
    }
  }

  return (
    <div className="fade-in space-y-6">
      <SectionHeader
        title="Team & access"
        subtitle="Who can sign in, and with what role. Admin-only area — enforced server-side."
      />

      <ErrorNote message={error} onRetry={load} />
      {notice && (
        <div className="rounded-xl border border-teal-500/30 bg-teal-500/10 px-4 py-3 text-sm text-teal-200">
          {notice}
        </div>
      )}

      {loading ? (
        <Spinner label="Loading accounts…" />
      ) : (
        <Card className="!p-0 overflow-hidden">
          <table className="w-full text-sm">
            <thead>
              <tr className="border-b border-white/10 text-left text-xs uppercase tracking-wider text-slate-400">
                <th className="px-5 py-3 font-medium">Account</th>
                <th className="px-5 py-3 font-medium">Role</th>
                <th className="hidden px-5 py-3 font-medium sm:table-cell">Last login</th>
                <th className="hidden px-5 py-3 font-medium md:table-cell">Joined</th>
                <th className="px-5 py-3 font-medium">Status</th>
              </tr>
            </thead>
            <tbody className="divide-y divide-white/5">
              {users.map((user) => (
                <tr key={user.id} className="transition hover:bg-white/[0.02]">
                  <td className="px-5 py-3">
                    <div className="flex items-center gap-3">
                      <Avatar name={user.name} size={34} />
                      <div className="min-w-0">
                        <p className="truncate font-medium text-slate-100">{user.name}</p>
                        <p className="truncate text-xs text-slate-500">{user.email}</p>
                      </div>
                    </div>
                  </td>
                  <td className="px-5 py-3">
                    <select
                      className="input !w-auto !py-1.5 text-xs"
                      value={user.role}
                      onChange={(e) => changeRole(user, e.target.value)}
                    >
                      {ROLES.map((role) => (
                        <option key={role} value={role}>
                          {role}
                        </option>
                      ))}
                    </select>
                  </td>
                  <td className="hidden px-5 py-3 text-xs text-slate-400 sm:table-cell">
                    {user.lastLoginAt ? formatDate(user.lastLoginAt) : 'never'}
                  </td>
                  <td className="hidden px-5 py-3 text-xs text-slate-400 md:table-cell">
                    {formatDate(user.createdAt)}
                  </td>
                  <td className="px-5 py-3">
                    <button
                      type="button"
                      className={`chip ${
                        user.enabled
                          ? 'border-teal-400/30 bg-teal-400/10 text-teal-300'
                          : 'border-rose-400/30 bg-rose-400/10 text-rose-300'
                      }`}
                      onClick={() => toggleEnabled(user)}
                    >
                      {user.enabled ? 'enabled — click to disable' : 'disabled — click to enable'}
                    </button>
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        </Card>
      )}

      <p className="text-xs text-slate-500">
        Passwords are stored as BCrypt hashes (strength 12) and can never be read back — only reset by the owner.
        Signup cannot create an ADMIN; promotion happens here.
      </p>
    </div>
  )
}
