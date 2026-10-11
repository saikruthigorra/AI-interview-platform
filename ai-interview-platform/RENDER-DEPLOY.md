# Deploy InterviewIQ on Render (free tier) + TiDB Cloud

This is the full walkthrough the README points to. You deploy **one service**:
the React app is baked into the Spring Boot jar, so a single Render URL serves
the website *and* the API. The database is an external **TiDB Cloud Serverless**
cluster (free tier). Nothing in this guide changes how the app behaves locally.

**Time:** ~20 minutes, most of it waiting for the first build.

---

## 0. What Render will ask you for

| # | Value | Where it comes from |
|---|-------|---------------------|
| 1 | `TIDB_HOST` / `TIDB_USER` / `TIDB_PASSWORD` | TiDB Cloud console (step 1) |
| 2 | `APP_ADMIN_PASSWORD` | You invent it (step 2) |
| 3 | Everything else | Already filled in `render.yaml` |

Keep the TiDB tab open while you do the Render step — you'll copy-paste three values.

---

## 1. Create the TiDB Cloud Serverless cluster (~5 min)

1. Sign up at <https://tidbcloud.com> (free, no card) and create a **Serverless** cluster.
   Pick the region closest to your users — for India that's **`ap-south-1 (Mumbai)`**
   if offered, otherwise Singapore.
2. Console → **Connect → JDBC / MySQL** (or *Connection Strings*). Note:
   - **Host** → `TIDB_HOST` (looks like `gateway01.ap-south-1.prod.aws.tidbcloud.com`)
   - **Port** → `4000` (already the default in `render.yaml`)
   - **User** → `TIDB_USER` (looks like `2Txxxxxx.root`)
   - **Password** → `TIDB_PASSWORD` (shown once — copy it now)
3. Create the database **once**. The app creates *tables*, not the database.
   Easiest: TiDB console → **SQL Editor**, connect, and run:

   ```sql
   CREATE DATABASE IF NOT EXISTS interviewiq;
   ```

   (Or from a shell: `mysql --host <TIDB_HOST> --port 4000 --user <TIDB_USER> -p
   --ssl-mode=VERIFY_IDENTITY -e "CREATE DATABASE IF NOT EXISTS interviewiq;"`)

> If the SQL Editor asks about traffic filters / IP allowlists, allow `0.0.0.0/0`
> (Render builds and runs on shared, rotating IPs, so allowlisting one IP won't work).

---

## 2. Deploy on Render (~10 min first build)

1. Push this repo to GitHub (Render deploys from the branch you select — usually `main`).
2. Render dashboard → **New → Blueprint** → select the repo.
   Render reads `render.yaml` and shows one service: **`interviewiq`** (Singapore region,
   free plan — closest Render region to India).
3. Fill in the prompted secrets:
   - `TIDB_HOST`, `TIDB_USER`, `TIDB_PASSWORD` — from step 1.
   - `APP_ADMIN_PASSWORD` — invent a strong one (10+ chars, upper + lower + digit +
     symbol, e.g. from a password manager). This is your admin login.
   - `APP_JWT_SECRET` is auto-generated — leave it.
   - Leave `APP_SEED=true` for your first deploy so demo data exists (see §5).
4. Click **Apply**. Then watch **Logs**:
   - `npm ci` + `npm run build` (React) — ~1 min.
   - `mvn … package` (jar) — a few minutes. If Maven Central ever rate-limits the
     shared build IP (HTTP 429), the build automatically retries through a mirror,
     so just let it finish.
   - `Started InterviewIqApplication` → `Bootstrap admin … created` → `Seeded …` —
     your app is up. Render then passes the `/actuator/health` check and gives you
     the URL: `https://interviewiq-xxxx.onrender.com`.

---

## 3. Verify the deployment (2 min)

Open your Render URL. You should land on the **login page**:

- [ ] The sign-in screen offers one-click demo logins. Sign in as
      `student@interviewiq.dev` / `Demo!Access2026`, run an interview, leave a review.
- [ ] Open `/oa/DEMO26` in an incognito window — the seeded demo OA should load with
      no login and be submittable.
- [ ] Sign in as `admin@interviewiq.dev` with the `APP_ADMIN_PASSWORD` you set,
      open **Assessments (OA)** — the `DEMO26` cohort analytics should have data.
- [ ] `https://<your-app>.onrender.com/actuator/health` returns `{"status":"UP"}`.

---

## 4. Environment variables (what `render.yaml` sets)

| Variable | Value | Why |
|----------|-------|-----|
| `SPRING_PROFILES_ACTIVE` | `tidb` | Talk to TiDB instead of the local file DB |
| `TIDB_HOST` / `TIDB_USER` / `TIDB_PASSWORD` | *(you supply)* | Cluster connection |
| `TIDB_PORT` | `4000` | TiDB default |
| `TIDB_DATABASE` | `interviewiq` | Must already exist (§1.3) |
| `TIDB_SSL_MODE` | `VERIFY_IDENTITY` | Required by TiDB Cloud Serverless |
| `APP_JWT_SECRET` | *(auto-generated)* | Token signing — never commit one |
| `APP_ADMIN_PASSWORD` | *(you supply)* | Bootstrap admin login |
| `APP_SEED` | `true` | Seed bank + demo accounts + demo OA on first boot |
| `APP_DEMO_PASSWORD` | `Demo!Access2026` | Password for the two demo logins |
| `APP_CORS_ORIGINS` | `*` | Harmless here — browser and API share one origin |

Everything else (`APP_AI_*`, `APP_JWT_TTL_MINUTES`, …) runs on built-in defaults.
To switch on real LLM grading later, add `APP_AI_PROVIDER=openai-compatible`,
`APP_AI_API_KEY`, `APP_AI_MODEL` in Render → Environment and redeploy — no code change.

---

## 5. Before you share the link (production hygiene)

- [ ] **Change the demo passwords.** The seeded logins are public knowledge (they're in
      this repo). Sign in as each and use `POST /api/auth/password`, or set your own
      `APP_DEMO_PASSWORD` and redeploy *before* anyone uses the seeded ones.
- [ ] **Real usage? Set `APP_SEED=false`.** Seeding is restart-safe (it only fills
      empty tables), but a production workspace shouldn't carry demo students, demo
      attempts, or advertised demo logins. Flip it in Render → Environment → redeploy.
- [ ] **Free-tier sleep is normal.** Render spins the service down after ~15 min idle;
      the next visit takes 60–120 s to wake (jar boot + first TiDB connection). If that
      bothers you, that's what the paid plan fixes — nothing to change in the app.

---

## 6. Troubleshooting

| Symptom | Cause → fix |
|---------|-------------|
| Build fails: `Could not transfer artifact … 429 Too Many Requests` | Maven Central throttling the shared build IP. The Dockerfile already retries through a mirror — **just redeploy**; if it persists, wait ~15 min and redeploy again. |
| Build fails otherwise | Open the failed step in Logs. `npm …` errors = frontend; `mvn …` errors = backend. Reproduce locally with `scripts/build-app.sh` (needs JDK 17+, Maven, Node 18+). |
| Deploy stays “In progress”, then unhealthy | First boot takes a few minutes (jar + TiDB connect + seed). Check Logs for `Started InterviewIqApplication`. If you see TiDB connection errors, re-check §1: wrong host/user/password, database not created, or IP allowlist blocking Render. |
| `Access denied` / `Unknown database 'interviewiq'` | The database wasn't created — run the `CREATE DATABASE` from §1.3 (table creation is automatic; database creation is not). |
| `SSLHandshakeException` / `unable to find valid certification path` | `TIDB_SSL_MODE` must be `VERIFY_IDENTITY` and the host must be the exact TiDB gateway hostname — a typo here breaks TLS verification. |
| Login page shows no demo logins | `APP_SEED` is `false`, or the seed hasn't run yet (watch Logs). The endpoint only advertises accounts whose password actually works. |
| Forgot `APP_ADMIN_PASSWORD` | Change the value in Render → Environment and redeploy — **but** the bootstrap only creates a *missing* admin, it never resets one. If the admin already exists, sign in with the old password and change it via `POST /api/auth/password`, or delete the admin row in TiDB's SQL Editor and redeploy. |
| App is slow from India | Render's nearest region is Singapore and TiDB should be in Mumbai/Singapore. Cross-region (e.g. TiDB in the US) adds ~250 ms per query — recreate the cluster closer if needed. |

---

## 7. Redeploys & updates

- `autoDeploy: true` — every push to the tracked branch rebuilds and redeploys.
- Restarts are safe: the admin bootstrap and the seeder only fill gaps, never reset
  or duplicate anything. Your real students, interviews and OAs survive every deploy.
- The Render disk is ephemeral, but nothing stateful lives there — all data is in TiDB.

## 8. Anywhere else (VPS, Fly.io, Railway, Cloud Run…)

```bash
scripts/build-app.sh        # needs JDK 17+, Maven, Node 18+
java -jar backend/target/interviewiq-api-*.jar
```

Set the same variables as §4 (notably `SPRING_PROFILES_ACTIVE=tidb` + the `TIDB_*`
set + `APP_JWT_SECRET` + `APP_ADMIN_PASSWORD`). The app listens on `SERVER_PORT`,
else `PORT`, else `8080` — no port config needed on any PaaS.
