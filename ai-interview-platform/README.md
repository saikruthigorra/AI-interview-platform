# InterviewIQ — AI Interview Platform

Run structured technical interviews, let AI generate the questions **from each student's skills and
target role**, grade every answer the moment it's submitted, and analyse the reviews and feedback
students leave behind. Recruiters can also **sign in and host their own online assessment (OA)** —
the same AI writes the paper, grades every candidate and analyses the whole cohort.

**Stack:** React 19 + Vite + Tailwind CSS v4 · Spring Boot 3 + Spring Security (JWT) · TiDB
(MySQL-compatible distributed SQL). Zero-setup local mode runs on an embedded MySQL-dialect database,
so you can have the whole thing running before you've even created a TiDB cluster.

---

## What it does

| Area | Details |
| --- | --- |
| **Students** | Each student carries a profile: skills, education, level (student/graduate/professional) and target role. |
| **Skill-based question generation** | Questions are ranked by overlap with the student's skills, then the target role and difficulty. Questions already answered are reused last, so repeat sessions stay fresh. |
| **AI generation** | From the curated 36-question bank offline, or generated live by an LLM when a key is configured — calibrated to the student's level. |
| **Instant grading** | Every answer is scored on submit: overall plus technical coverage, depth, communication and clarity. |
| **Coaching feedback** | Per-answer feedback, matched/missing rubric concepts, strengths, improvements, and a model answer. |
| **Debrief report** | End-of-session summary with readiness band, strengths, gaps and per-question breakdown. Printable to PDF. |
| **Reviews & feedback** | Students rate completed interviews 1–5★ and comment; the platform derives a sentiment label and 0–100 score from rating + wording. |
| **Analysis dashboard** | Score trends, dimension averages, weakest/strongest concepts, per-skill and per-role performance, question calibration, sentiment split and the reviews feed — all from real graded data. |
| **Recruiter console** | Recruiters sign up / sign in and create assessments (role, skills, difficulty, level, question count, duration, pass mark), generate the paper with AI, publish it and share a link. Own assessments only; admins see everything. |
| **Two consoles, one app** | Signing in as a **student** shows a personal practice hub (run an interview, jump into a hosted OA by pasting its code, how-grading-works tips) with a student-only sidebar. **Recruiters/admins** get the recruiting console: candidate roster, cohort analytics, question-bank curation, OA hosting and a pipeline dashboard. Route-guarded on both sides; the API enforces the same split server-side for OAs and admin areas. |
| **Hosted OA (no candidate account)** | Candidates open `/oa/{CODE}`, give a name and email, and take a timed, auto-graded assessment. Scores and model answers stay hidden until they submit. |
| **Cohort analytics** | Per-attempt AI analysis for the recruiter, plus leaderboard, score distribution, per-question difficulty verdicts, weakest/strongest concepts, skill breakdown and an AI cohort read-out. |
| **Security** | Spring Security: BCrypt(12) passwords, stateless JWTs, strict password policy, login rate-limiting with lockout, method-level `ADMIN` enforcement, JSON 401/403, no self-assigned admins. |

---

## Architecture

```
┌──────────────────────────┐        ┌────────────────────────────────────────────┐
│  React 19 + Vite         │        │  Spring Boot 3  (Java 17+)                 │
│  Tailwind CSS v4         │  /api  │                                            │
│  react-router v7         ├───────▶│  SecurityFilterChain → JwtAuthFilter       │
│  (dev server proxies     │ Bearer │  Controller → Service → Repository         │
│   /api → :8080)          │  JWT   │                                            │
└──────────────────────────┘        │  AiService ─▶ bank/skill ranker            │
                                    │            ─▶ heuristic scorer             │
                                    │            ─▶ LLM (optional) + sentiment   │
                                    │  Hibernate / JPA                           │
                                    └──────────────┬─────────────────────────────┘
                                                   │ MySQL protocol
                                    ┌──────────────▼─────────────────────────────┐
                                    │  TiDB (MySQL-wire compatible)              │
                                    │  local dev: H2 in MySQL mode               │
                                    └────────────────────────────────────────────┘
```

### Layout

```
ai-interview-platform/
├── backend/src/main/java/com/interviewiq/
│   ├── ai/          # question bank, skill-aware generators, evaluators, sentiment, AiService
│   ├── config/      # properties, CORS, admin bootstrap, demo seed
│   ├── domain/      # JPA entities (student, question, session, answer, review, user)
│   ├── repo/        # Spring Data repositories
│   ├── security/    # JWT service + filter, auth service, password policy, rate limiter, config
│   ├── service/     # interview engine, students, questions, feedback, analytics, stats
│   └── web/         # REST controllers + DTOs + error handling
├── frontend/src/
│   ├── auth/        # AuthContext (token storage, 401 handling)
│   ├── components/  # layout, UI primitives, review widget
│   ├── lib/         # API client (bearer token, error mapping), formatting
│   └── pages/       # Login, Register, Dashboard, Analytics, Students, Setup, Runner, Report, Bank, Team
├── scripts/         # dev servers, TiDB playground, DB reset, end-to-end connectivity suite
└── .env.example     # every knob, documented
```

---

## Demo accounts (seeded)

`APP_SEED=true` (the default in local mode) creates three ready-to-use logins, one per role, so you can
try every side of the platform without registering:

| Role | Email | Password | What it shows |
| --- | --- | --- | --- |
| **Recruiter** | `recruiter@interviewiq.dev` | `Demo!Access2026` | **Assessments (OA)** console, owning the seeded published OA `DEMO26` with its question set, 3 attempts, per-attempt AI debriefs and cohort analytics. |
| **Student** | `student@interviewiq.dev` | `Demo!Access2026` | The candidate-facing app: dashboard, analytics, student list, question bank, skill-driven interviews, reports and reviews. |
| **Admin** | `admin@interviewiq.dev` | from `.env.local` (`APP_ADMIN_PASSWORD`) | Everything the recruiter sees (all recruiters' OAs, not just their own) plus **Team & access** account administration. |

The **sign-in screen lists the recruiter and student logins itself** (one click fills the form), served
by `GET /api/public/demo-accounts`. That endpoint is public but self-limiting: it stays empty unless
seeding is enabled, and it only reports an account when the printed password actually matches the
stored BCrypt hash — so it can never advertise a credential that fails to sign in.

Passwords are BCrypt-hashed like any other account; these are dev credentials created only when
seeding is enabled. Change them via `POST /api/auth/password`, override the shared demo password with
`APP_DEMO_PASSWORD`, or set `APP_SEED=false` to skip the demo accounts entirely.

A candidate taking a hosted OA needs **no** account at all — share `/oa/{CODE}` (the seeded demo one is
**`DEMO26`**) and they just enter a name and email.

---

## Quick start (zero setup)

Requires **JDK 17+**, **Maven 3.9+**, **Node 18+**.

```bash
scripts/dev-backend.sh    # http://localhost:8080 — seeds bank + demo data on first run
scripts/dev-frontend.sh   # http://localhost:5173 (second shell)

# Windows, no typing: double-click scripts\run-backend.bat, then scripts\run-frontend.bat
# Something off?  bash scripts/doctor.sh  → prints tools, config and connectivity
```

Open <http://localhost:5173>. **You'll land on the login page:**

- **Register an account** (takes 10 seconds — you'll see the password policy and strength meter live),
  or
- **Sign in as the bootstrap admin.** On first boot the admin account is created from
  `APP_ADMIN_EMAIL` (default `admin@interviewiq.dev`) and `APP_ADMIN_PASSWORD`; if that variable is
  blank a strong password is generated and printed **once** in the backend log. Copy it, sign in, then
  change it.

> `scripts/dev-backend.sh` automatically sources `.env.local` (or `.env`) from the project root, so
> put secrets there — the file is gitignored.

Demo data seeded on first boot: 5 students with skills, 36 bank questions, 4 completed interviews with
reviews (positive, neutral and mixed sentiment) and 1 interview still in progress so you can try the
resume flow.

---

## Security model

| Control | Implementation |
| --- | --- |
| Password storage | BCrypt, strength 12. Hashes are never returned by any API. |
| Password policy | ≥10 chars, upper + lower + digit + symbol, no 5-character runs, common-password blocklist — enforced server-side at registration *and* password change. |
| Sessions | Stateless JWT (HMAC-SHA256, 2 h TTL). No server session, no auth cookie, therefore no CSRF surface. |
| Secret management | `APP_JWT_SECRET` (32+ chars). If unset, a random key is generated per boot and the app warns loudly — no default secret ever ships in the repo. |
| Brute-force protection | 5 failed logins per email+IP → 15-minute lockout; 30 attempts per IP per hour. Responses are `429` with `Retry-After`. |
| User enumeration | Login failures are always the vague "Invalid email or password"; the response time is equalised by hashing a dummy value for unknown accounts. |
| Role escalation | Signup can request `STUDENT` or `RECRUITER`; `ADMIN` is silently downgraded. Promotion happens only via `/api/users` (ADMIN-only, enforced by method security). |
| Transport / CORS | `APP_CORS_ORIGINS` locks browsers to your origin; wildcard is dev-only. TiDB profile forces TLS with `VERIFY_IDENTITY`. |
| Error responses | JSON `401`/`403` (no redirects), so SPAs behave and no login page HTML leaks into API calls. |

Change a password: `POST /api/auth/password` with `currentPassword`/`newPassword`/`confirmPassword`.

---

## The analysis dashboard

Everything on the Analytics page is derived from real data, in one request (`GET /api/analytics`):

- **Summary band** — average score, average rating, completion rate, positive-sentiment share,
  average seconds per answer.
- **Score trend** — average interview score per day (chronological).
- **Dimension averages** — technical coverage, depth, communication, clarity across every graded answer.
- **Weakest concepts** — rubric concepts students most often miss, counted across all answers; this is
  effectively a revision syllabus generated from your own interviews.
- **Concepts students nail** — recurring matched keywords.
- **Performance by skill / by role** — sessions, average score and average rating per skill and role.
- **Question calibration** — every question sorted by average score with a verdict
  (`Too hard — consider rewording` / `Well calibrated` / `Too easy — add depth` / `Need more data`),
  so the bank improves itself over time.
- **Sentiment split & reviews feed** — rating distribution plus recent comments with their derived
  sentiment and score.

Sentiment comes from a transparent lexical analyser (rating prior ± comment terms, negation-aware —
"not helpful" flips) and switches to the LLM when one is configured.

---

## Hosting an OA (recruiter phase)

A recruiter signs in (or creates an account — signup accepts `STUDENT` or `RECRUITER`), opens
**Assessments (OA)** and runs the whole loop:

```
create draft ─▶ AI generates the question set ─▶ publish ─▶ share /oa/{CODE}
      │                                              │
      │                                    candidate opens the link — no account
      │                                              │
      │                                   answers one question at a time
      │                               (AI grades each answer on submit, score hidden)
      │                                              │
      └────────── recruiter sees ◀── submit ─────────┘
            per-attempt AI analysis  +  cohort analytics & leaderboard
```

**Same AI pipeline, both flows.** The OA question set comes from the very same
`ai.generateQuestions(role, difficulty, count, skills, level)` call the live interviews use, each answer
goes through the same evaluator, and the cohort read-out reuses the same summariser — a hosted OA is a
different door into the identical AI engine, never a shortcut.

| Decision | Why |
| --- | --- |
| The attempt token is the credential | Candidates have no account. `POST /api/public/assessments/{code}/attempts` returns a 32-char opaque token; everything after that is keyed on it. A wrong or expired token is a 404. |
| Scores are hidden mid-attempt | Each answer is graded server-side on submit, but the acknowledgement carries only a receipt (`answeredCount`, `completed`). Revealing the rubric or scores mid-paper would let candidates game the remaining questions. |
| One attempt per candidate per OA | The email is matched to a candidate record (created on the fly); a finished attempt is a 409, an unfinished one resumes, and time runs out against `expires_at = start + duration`. |
| Published papers are frozen | Editing a published OA or its questions is a 409 — "close it and create a new one". An OA with attempts can't be deleted either. |
| Closing is a first-class action | Closing stops new attempts (409 on start) while leaving existing analyses readable. |
| Ownership | Recruiters only reach their own assessments (403 otherwise); `ADMIN` sees every one. |

A demo OA ships with the seed so you can try it immediately: **code `DEMO26`** — "Campus Placement OA —
Frontend Engineer", 4 AI-generated questions, owned by the seeded admin, already carrying three
completed attempts (strong / borderline / weak) so the cohort analytics have something to show.
Open <http://localhost:5173/oa/DEMO26> to take it as a candidate, or sign in as the admin and
open **Assessments (OA)** to read the analysis.

---

## Switching on TiDB

### Option A — TiDB Cloud Serverless (recommended)

1. Create a free cluster at <https://tidbcloud.com>.
2. Console → **Connect → JDBC / MySQL**: note host, port (`4000`), user (`2Txxxxxx.root`), password.
3. Create the database once (the app creates *tables*, not the database):

   ```bash
   mysql --host <TIDB_HOST> --port 4000 --user <TIDB_USER> -p --ssl-mode=VERIFY_IDENTITY \
     -e "CREATE DATABASE IF NOT EXISTS interviewiq;"
   ```

4. Start the API against TiDB:

   ```bash
   export SPRING_PROFILES_ACTIVE=tidb
   export TIDB_HOST='gateway01.<region>.prod.aws.tidbcloud.com'
   export TIDB_PORT=4000
   export TIDB_USER='2Txxxxxx.root'
   export TIDB_PASSWORD='••••••'
   export TIDB_DATABASE=interviewiq
   export TIDB_SSL_MODE=VERIFY_IDENTITY
   export APP_JWT_SECRET="$(openssl rand -hex 32)"
   scripts/dev-backend.sh
   ```

### Option B — local TiDB cluster

```bash
scripts/run-tidb.sh     # TiUP playground on :4000 (see the script header for the env block)
```

### What's TiDB-specific here

- **Same driver as MySQL** — TiDB speaks the MySQL wire protocol, so `mysql-connector-j` is the connector.
- **TLS from day one** — `sslMode=VERIFY_IDENTITY` + TLS 1.2/1.3, as TiDB Cloud Serverless requires.
- **Connection lifetime** — TiDB reaps idle connections around 5–10 min, so Hikari uses
  `max-lifetime=240s` / `keepalive-time=120s`.
- **Foreign keys are not enforced by TiDB.** Nothing relies on `ON DELETE CASCADE` for correctness:
  deleting a student removes reviews → sessions → answers/question links in the service layer, and a
  question that has been served in an interview can't be deleted (guard rails, not FK errors).
- **`LONGVARCHAR`** for answers, feedback and debriefs so long text isn't truncated.
- **`rewriteBatchedStatements=true`** for faster seeding.

---

## Configuring real AI grading

Out of the box the app uses a **deterministic heuristic engine**: question selection by skill overlap,
scoring by rubric-concept coverage plus depth/structure/example/quantification signals. No key, no
rate limits, fully explainable — and it stays as the automatic fallback whenever a remote model fails.

To switch to LLM mode:

```bash
export APP_AI_PROVIDER=openai-compatible
export APP_AI_API_KEY=sk-…
export APP_AI_MODEL=gpt-4o-mini
# export APP_AI_BASE_URL=https://api.openai.com/v1   # default; any OpenAI-compatible endpoint works
```

The model then generates questions (prompted with the student's skills, level and role), grades
answers against the same JSON contract, writes the end-of-session debrief, and scores review sentiment.
`/api/meta` reports which engine is live and the sidebar shows it.

---

## Verifying every connection

`scripts/e2e-connections.sh` drives the running stack through curl the way a browser would — through
the Vite proxy, with real JWTs, including the failure paths:

```bash
set -a; . ./.env.local; set +a      # so the admin section can run
scripts/e2e-connections.sh          # API_BASE / WEB_BASE env vars override the defaults
```

It checks reachability (actuator + proxy), registration and the password policy, duplicate emails,
admin-role downgrade, login, 401 with `attemptsRemaining`, 429 lockout, token enforcement (401/403),
student CRUD with skills, the skills catalogue, question CRUD, the full interview lifecycle (skill-
matched questions, idempotent resume, grading, auto-completion, report), reviews with sentiment,
all ten analytics blocks, stats/meta, admin login and account listing, and the cleanup cascades.

Section 12 covers the recruiter phase end to end: recruiter signup, students blocked from the console
(403), draft → publish-before-questions (409) → AI generation → publish → immutable-after-publish
(409) → candidate landing page with **no** auth → anonymous start → rubric never leaked before
submission → resume instead of duplicate → per-answer acks with no score leakage → auto-submit on the
last answer → analysis revealed → repeat attempt (409) → recruiter's per-attempt AI analysis → cohort
analytics key set → delete-with-attempts (409) → close → closed OA refuses attempts (409) → clean draft
delete (204), plus the public and console routes through the Vite proxy.

Exit code is non-zero if anything fails.

**Current status: 120 passed, 0 failed.**

---

## API reference

| Method | Path | Purpose |
| --- | --- | --- |
| `POST` | `/api/auth/register` | Create account (student/recruiter), returns JWT |
| `POST` | `/api/auth/login` | Sign in (rate-limited; 401 with `attemptsRemaining`, 429 on lockout) |
| `GET` | `/api/auth/me` | Current profile |
| `POST` | `/api/auth/password` | Change own password (policy enforced) |
| `POST` | `/api/auth/logout` | Client-side token discard |
| `GET` | `/api/users` · `PATCH /{id}/role` · `PATCH /{id}/enabled` | Account administration (**ADMIN only**) |
| `GET` | `/api/candidates` | Students with skills/level/session counts |
| `POST` `PUT` `DELETE` | `/api/candidates[/{id}]` | CRUD (delete cascades reviews → sessions → answers) |
| `GET` | `/api/questions?role=&difficulty=&search=` | Search the bank |
| `GET` | `/api/questions/roles` · `/api/questions/skills` | Role list · skill-tag catalogue |
| `POST` `DELETE` | `/api/questions[/{id}]` | Add / remove a question (used questions refuse deletion) |
| `POST` | `/api/interviews` | **Start or resume** `{candidateId, role, difficulty, questionCount, skills?, level?}` |
| `GET` | `/api/interviews` · `/{id}` · `/{id}/report` | Sessions, detail, scored report |
| `POST` | `/api/interviews/{id}/answers` | Submit answer → graded immediately |
| `POST` | `/api/interviews/{id}/finish` | Finalise (idempotent, auto-fires on last answer) |
| `POST` `GET` `DELETE` | `/api/interviews/{id}/feedback` | Review a completed interview (upsert) with derived sentiment |
| `GET` | `/api/feedback` | Every review, newest first |
| `GET` | `/api/analytics` | The whole analysis dashboard in one payload |
| `GET` `POST` | `/api/assessments` | List / create OAs (**RECRUITER** or **ADMIN**) |
| `GET` `PUT` `DELETE` | `/api/assessments/{id}` | Read / edit (drafts only) / delete (only without attempts) |
| `POST` | `/api/assessments/{id}/generate` | AI-generate the question set (replaces the draft set) |
| `GET` `DELETE` | `/api/assessments/{id}/questions[/{aqId}]` | The paper · drop a question while it's a draft |
| `POST` | `/api/assessments/{id}/publish` · `/close` | Make it shareable (≥3 questions) · stop accepting attempts |
| `GET` | `/api/assessments/{id}/attempts` · `/{sessionId}` | Candidate attempts · one attempt's full AI analysis |
| `GET` | `/api/assessments/{id}/analytics` | Cohort analytics: distribution, per-question verdicts, concepts, skills, leaderboard, AI read-out |
| `GET` | `/api/public/demo-accounts` | **No auth** — seeded demo logins for the sign-in screen (empty unless seeding is on) |
| `GET` | `/api/public/assessments/{code}` | **No auth** — landing data for a shared link (never the rubric) |
| `POST` | `/api/public/assessments/{code}/attempts` | **No auth** — start/resume an attempt with name + email |
| `GET` | `/api/public/attempts/{token}` | **No auth** — attempt state; analysis only once completed |
| `POST` | `/api/public/attempts/{token}/answers` · `/submit` | **No auth** — record an answer (graded, hidden) · submit early/on time-up |
| `GET` | `/api/stats` · `/api/meta` | Dashboard aggregates · runtime engine/profile info |

Errors: `{timestamp, status, error, fields?}` — 400 validation, 401 unauthenticated, 403 forbidden,
404 unknown, 409 conflict, 429 rate-limited.

### The interview lifecycle

```
POST /api/interviews ─▶ questions ranked by skill overlap → frozen onto the session
        │
        ├─ POST /answers  → grade → save → mark answered   (repeat)
        │                    └─ last answer → session auto-completes + debrief written
        ├─ GET  /report   → overall score, strengths, gaps, per-question detail
        └─ POST /feedback → 1–5★ + comment → sentiment derived → appears in analytics
```

### Data model

```
app_user (security) ──1──n assessment (recruiter-hosted OA)
                              │ 1──n assessment_question n──1 question
                              │
candidate (= student)         │
     1──n interview_session ──┘   (assessment_id NULL for a live interview)
              │  ▲
              │  └──n interview_answer ──┘
              └──1 interview_feedback (rating, comment, sentiment)
```

`session_question` freezes the ordered question set per session (essential when questions are
generated live), while `interview_answer` stores the submission *and* its evaluation.

One session table serves both flows: a **live interview** is a session with no assessment, an **OA
attempt** is a session pointing at an `assessment` and carrying an `attempt_token` (the anonymous
candidate's capability) plus `expires_at` (start + the OA's duration). That's why grading, reporting
and analytics only ever had to be written once.

---

## Scoring model

For a hosted OA the same per-answer scores roll up into the attempt's overall score, which is then
compared against the assessment's pass mark (default 60%) to give the recruiter a pass/fail — and the
cohort view ranks every attempt, flags questions most candidates fail, and lists the concepts the
cohort missed most.

Answers score 0–100 across four dimensions:

- **Technical coverage** — how many rubric concepts the answer touches.
- **Depth** — substance relative to expected length, modulated by coverage.
- **Communication** — signposting, sentence construction, paragraphing, absence of filler.
- **Clarity** — blend of communication and technical, penalised for hedging and run-ons.

Session score = mean of per-answer scores; the debrief names the strongest dimension and the recurring
gaps. The LLM grader returns the identical JSON contract, so storage, reports and analytics are
unchanged when you flip engines.

---

## Production notes

- Build the frontend (`npm run build`) and serve `dist/` from nginx/Caddy — or copy it into
  `backend/src/main/resources/static/` for a single artifact.
- Set `APP_JWT_SECRET`, `APP_CORS_ORIGINS`, `APP_ADMIN_PASSWORD`; never rely on generated values in a
  shared environment.
- Add Flyway/Liquibase before real deployments; `DDL_AUTO=validate` is already wired.
- For horizontal scaling, move the rate limiter to Redis (the interface is isolated in
  `LoginRateLimiter`) — tokens are already stateless.
- Disable demo seeding with `APP_SEED=false`.

## Roadmap ideas

- Voice answers with speech-to-text (grading already takes plain text)
- Interview templates and scheduled invites
- Live coding questions in a sandboxed editor
- Human reviewer comments alongside AI scores on reports
- Per-student progress tracking across sessions over time

---

*Full-stack reference: React 19, Spring Boot 3 + Spring Security, TiDB. If you only have five minutes:
start both servers, register an account, run an interview, leave a review, then open Analytics.*

## Deploy it online (optional)

The project deploys as **one service**: the React app is baked into the Spring Boot jar, so a
single URL serves the website *and* the API, with TiDB Cloud as the external database.

- **Render (recommended, free tier):** the repo ships a `Dockerfile` (multi-stage: Vite build →
  Maven build → JRE runtime) and a `render.yaml` blueprint — Render prompts for your TiDB
  host/user/password and admin password, generates a JWT secret, and deploys. Full walkthrough:
  **RENDER-DEPLOY.md**.
- **Anywhere else that runs a container or a jar:** build the image with `docker build -t interviewiq .`
  (or the jar alone with `scripts/build-app.sh` / `.bat`) and set the same environment variables.
  The app binds to `SERVER_PORT`, else the PaaS `PORT`, else 8080 — no port config needed.

Your local demo flow is untouched: nothing changes unless you set `SPRING_PROFILES_ACTIVE=tidb`
and run the packaged jar.
