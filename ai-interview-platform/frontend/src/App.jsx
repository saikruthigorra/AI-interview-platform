import { Navigate, Route, Routes, useLocation } from 'react-router-dom'
import { AuthProvider, useAuth } from './auth/AuthContext.jsx'
import Layout from './components/Layout.jsx'
import { Spinner } from './components/ui.jsx'
import Analytics from './pages/Analytics.jsx'
import Assessments from './pages/Assessments.jsx'
import Candidates from './pages/Candidates.jsx'
import Dashboard from './pages/Dashboard.jsx'
import InterviewRunner from './pages/InterviewRunner.jsx'
import InterviewSetup from './pages/InterviewSetup.jsx'
import Login from './pages/Login.jsx'
import QuestionBank from './pages/QuestionBank.jsx'
import PublicAssessment from './pages/PublicAssessment.jsx'
import Register from './pages/Register.jsx'
import Report from './pages/Report.jsx'
import Team from './pages/Team.jsx'

function RequireAuth({ children }) {
  const { user, loading } = useAuth()
  const location = useLocation()

  if (loading) {
    return (
      <div className="flex min-h-full items-center justify-center">
        <Spinner label="Checking your session…" />
      </div>
    )
  }
  if (!user) {
    return <Navigate to="/login" state={{ from: location.pathname }} replace />
  }
  return children
}

function AdminOnly({ children }) {
  const { isAdmin } = useAuth()
  return isAdmin ? children : <Navigate to="/" replace />
}

/** Recruiters host OAs; admins can do everything. */
function RecruiterOnly({ children }) {
  const { user } = useAuth()
  const allowed = user && (user.role === 'RECRUITER' || user.role === 'ADMIN')
  return allowed ? children : <Navigate to="/" replace />
}

/** Recruiting-side screens (roster, cohort analytics, question-bank curation):
 *  students are bounced to their own dashboard instead. */
function RecruitingOnly({ children }) {
  const { isRecruiter } = useAuth()
  return isRecruiter ? children : <Navigate to="/" replace />
}

export default function App() {
  return (
    <AuthProvider>
      <Routes>
        <Route path="/login" element={<Login />} />
        <Route path="/register" element={<Register />} />
        {/* Candidate side of a hosted OA — deliberately outside the app shell:
            candidates have no account, the attempt token is their credential. */}
        <Route path="/oa/:code" element={<PublicAssessment />} />
        <Route
          element={
            <RequireAuth>
              <Layout />
            </RequireAuth>
          }
        >
          <Route path="/" element={<Dashboard />} />
          <Route
            path="/analytics"
            element={
              <RecruitingOnly>
                <Analytics />
              </RecruitingOnly>
            }
          />
          <Route
            path="/candidates"
            element={
              <RecruitingOnly>
                <Candidates />
              </RecruitingOnly>
            }
          />
          <Route path="/interview/new" element={<InterviewSetup />} />
          <Route path="/interview/:id" element={<InterviewRunner />} />
          <Route path="/interview/:id/report" element={<Report />} />
          <Route
            path="/questions"
            element={
              <RecruitingOnly>
                <QuestionBank />
              </RecruitingOnly>
            }
          />
          <Route
            path="/assessments"
            element={
              <RecruiterOnly>
                <Assessments />
              </RecruiterOnly>
            }
          />
          <Route
            path="/team"
            element={
              <AdminOnly>
                <Team />
              </AdminOnly>
            }
          />
          <Route
            path="*"
            element={
              <div className="card p-10 text-center">
                <p className="text-lg font-semibold">Page not found</p>
                <p className="mt-1 text-sm text-slate-400">Check the URL or head back to the dashboard.</p>
              </div>
            }
          />
        </Route>
      </Routes>
    </AuthProvider>
  )
}
