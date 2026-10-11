import { createContext, useCallback, useContext, useEffect, useState } from 'react'
import { api, getToken, setToken } from '../lib/api.js'

const AuthContext = createContext(null)

export function AuthProvider({ children }) {
  const [user, setUser] = useState(null)
  const [loading, setLoading] = useState(true)

  // On boot: if a token exists, verify it and load the profile.
  useEffect(() => {
    let cancelled = false
    async function boot() {
      if (!getToken()) {
        setLoading(false)
        return
      }
      try {
        const me = await api.auth.me()
        if (!cancelled) setUser(me)
      } catch {
        if (!cancelled) {
          setToken(null)
          setUser(null)
        }
      } finally {
        if (!cancelled) setLoading(false)
      }
    }
    boot()
    return () => {
      cancelled = true
    }
  }, [])

  // Any 401 anywhere in the app drops us back to a signed-out state.
  useEffect(() => {
    const onUnauthorized = () => setUser(null)
    window.addEventListener('iiq:unauthorized', onUnauthorized)
    return () => window.removeEventListener('iiq:unauthorized', onUnauthorized)
  }, [])

  const login = useCallback(async (email, password) => {
    const auth = await api.auth.login({ email, password })
    setToken(auth.token)
    setUser(auth.user)
    return auth.user
  }, [])

  const register = useCallback(async (payload) => {
    const auth = await api.auth.register(payload)
    setToken(auth.token)
    setUser(auth.user)
    return auth.user
  }, [])

  const logout = useCallback(async () => {
    try {
      await api.auth.logout()
    } catch {
      /* token is discarded client-side regardless */
    }
    setToken(null)
    setUser(null)
  }, [])

  const isRecruiter = user?.role === 'RECRUITER' || user?.role === 'ADMIN'
  const value = { user, loading, login, register, logout, isRecruiter, isAdmin: user?.role === 'ADMIN' }
  return <AuthContext.Provider value={value}>{children}</AuthContext.Provider>
}

export function useAuth() {
  const context = useContext(AuthContext)
  if (!context) {
    throw new Error('useAuth must be used inside <AuthProvider>')
  }
  return context
}
