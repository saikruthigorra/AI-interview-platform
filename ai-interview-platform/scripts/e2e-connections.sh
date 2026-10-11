#!/usr/bin/env bash
# ─────────────────────────────────────────────────────────────────────────────
# InterviewIQ end-to-end connectivity check.
#
# Exercises every layer the way a browser would: through the Vite proxy, with
# real tokens, including the security failure paths (401 / 403 / 429).
#
#   Usage:  scripts/e2e-connections.sh            # expects API :8080, web :5173
#           API_BASE=http://localhost:8080 \
#           WEB_BASE=http://localhost:5173 scripts/e2e-connections.sh
# ─────────────────────────────────────────────────────────────────────────────
set -uo pipefail

API_BASE="${API_BASE:-http://127.0.0.1:8080}"
WEB_BASE="${WEB_BASE:-http://127.0.0.1:5173}"
PASS=0
FAIL=0
EMAIL="e2e.$(date +%s)@interviewiq.dev"
PASSWORD='E2e!Demo-Pass1'

ok()   { PASS=$((PASS+1)); printf '  \033[32m✓\033[0m %s\n' "$1"; }
bad()  { FAIL=$((FAIL+1)); printf '  \033[31m✗\033[0m %s\n' "$1"; }
check(){ # check <label> <expected> <actual>
  if [ "$2" = "$3" ]; then ok "$1 ($3)"; else bad "$1 — expected $2, got $3"; fi
}
jget(){ # jget '<python expr on d>'  (reads stdin)
  python3 -c "import json,sys
try:
    d=json.load(sys.stdin)
except Exception:
    d={}
print($1)" 2>/dev/null || echo ""
}
status() { # status <method> <url> [token] [json]
  local method="$1" url="$2" token="${3:-}" data="${4:-}"
  local args=(-s -o /tmp/e2e_body -w '%{http_code}' -X "$method" "$url" -H 'Content-Type: application/json')
  [ -n "$token" ] && args+=(-H "Authorization: Bearer $token")
  [ -n "$data" ] && args+=(-d "$data")
  curl "${args[@]}"
}
body() { cat /tmp/e2e_body; }

echo "═══════════════════════════════════════════════════════════════"
echo " InterviewIQ connectivity check"
echo " API: $API_BASE   Web: $WEB_BASE"
echo "═══════════════════════════════════════════════════════════════"

echo
echo "▸ 1. Reachability"
check "Spring Boot actuator health" 200 "$(status GET "$API_BASE/actuator/health")"
check "health reports UP" "UP" "$(body | jget "d.get('status','')")"
check "React app served by Vite" 200 "$(status GET "$WEB_BASE/")"
check "Vite proxies /api to the backend (meta route)" 401 "$(status GET "$WEB_BASE/api/meta")"

echo
echo "▸ 2. Auth: registration"
REG_STATUS="$(status POST "$WEB_BASE/api/auth/register" "" "{\"name\":\"E2E Bot\",\"email\":\"$EMAIL\",\"password\":\"$PASSWORD\",\"confirmPassword\":\"$PASSWORD\",\"role\":\"STUDENT\"}")"
check "register via proxy" 201 "$REG_STATUS"
TOKEN="$(body | jget "d.get('token','')")"
[ -n "$TOKEN" ] && ok "registration returned a JWT" || bad "no token in registration response"
check "token is a 3-part JWT" 3 "$(awk -F. '{print NF}' <<<"$TOKEN")"
check "role is STUDENT, never ADMIN" "STUDENT" "$(body | jget "d.get('user',{}).get('role','')")"

echo
echo "▸ 3. Auth: password policy + duplicate protection"
WEAK="$(status POST "$API_BASE/api/auth/register" "" "{\"name\":\"Weak\",\"email\":\"weak.$EMAIL\",\"password\":\"password\",\"confirmPassword\":\"password\",\"role\":\"STUDENT\"}")"
check "weak password rejected" 400 "$WEAK"
check "duplicate email rejected" 409 "$(status POST "$API_BASE/api/auth/register" "" "{\"name\":\"Dup\",\"email\":\"$EMAIL\",\"password\":\"$PASSWORD\",\"confirmPassword\":\"$PASSWORD\",\"role\":\"STUDENT\"}")"
check "admin role request collapses to RECRUITER" "RECRUITER" "$(status POST "$API_BASE/api/auth/register" "" "{\"name\":\"Sneaky\",\"email\":\"sneaky.$EMAIL\",\"password\":\"$PASSWORD\",\"confirmPassword\":\"$PASSWORD\",\"role\":\"ADMIN\"}" >/dev/null; body | jget "d.get('user',{}).get('role','')")"

echo
echo "▸ 4. Auth: login + brute-force protection"
check "login with correct credentials" 200 "$(status POST "$API_BASE/api/auth/login" "" "{\"email\":\"$EMAIL\",\"password\":\"$PASSWORD\"}")"
check "wrong password → 401 with attempts left" 401 "$(status POST "$API_BASE/api/auth/login" "" "{\"email\":\"$EMAIL\",\"password\":\"wrong-password-1\"}")"
check "attemptsRemaining reported" "4" "$(body | jget "d.get('attemptsRemaining','')")"
LOCKED=0
for i in 2 3 4 5 6 7; do
  CODE="$(status POST "$API_BASE/api/auth/login" "" "{\"email\":\"$EMAIL\",\"password\":\"wrong-password-$i\"}")"
  if [ "$CODE" = "429" ]; then LOCKED=1; break; fi
done
check "account locks with 429 after repeated failures" 1 "$LOCKED"

echo
echo "▸ 5. Auth: token enforcement"
check "/auth/me with token" 200 "$(status GET "$API_BASE/api/auth/me" "$TOKEN")"
check "/auth/me returns the right account" "$EMAIL" "$(body | jget "d.get('email','')")"
check "protected route without token → 401" 401 "$(status GET "$API_BASE/api/candidates")"
check "protected route with garbage token → 401" 401 "$(status GET "$API_BASE/api/candidates" "not.a.jwt")"
check "student hitting admin route → 403" 403 "$(status GET "$API_BASE/api/users" "$TOKEN")"

echo
echo "▸ 6. Students (skills profile)"
NEW_STUDENT="$(status POST "$API_BASE/api/candidates" "$TOKEN" "{\"name\":\"Riya Nair\",\"email\":\"riya.e2e@example.com\",\"headline\":\"Final-year CS\",\"targetRole\":\"Frontend Engineer\",\"skills\":\"React,CSS,Testing\",\"education\":\"B.Tech CSE 2026\",\"level\":\"STUDENT\"}")"
check "create student with skills" 201 "$NEW_STUDENT"
STUDENT_ID="$(body | jget "d.get('id','')")"
check "skills round-trip" "React,CSS,Testing" "$(body | jget "','.join(d.get('skills',[]))")"
check "level round-trip" "STUDENT" "$(body | jget "d.get('level','')")"
check "update student" 200 "$(status PUT "$API_BASE/api/candidates/$STUDENT_ID" "$TOKEN" "{\"name\":\"Riya Nair\",\"email\":\"riya.e2e@example.com\",\"targetRole\":\"Frontend Engineer\",\"skills\":\"React,CSS,Testing,JavaScript\",\"education\":\"B.Tech CSE 2026\",\"level\":\"GRADUATE\"}")"
check "list students" 200 "$(status GET "$API_BASE/api/candidates" "$TOKEN")"

echo
echo "▸ 7. Question bank + skills catalogue"
check "skill tags endpoint" 200 "$(status GET "$API_BASE/api/questions/skills" "$TOKEN")"
check "skills include React" "yes" "$(body | jget "'yes' if 'React' in d else 'no'")"
check "roles endpoint" 200 "$(status GET "$API_BASE/api/questions/roles" "$TOKEN")"
check "question search by role" 200 "$(status GET "$API_BASE/api/questions?role=Frontend%20Engineer" "$TOKEN")"
check "search returned questions" "yes" "$(body | jget "'yes' if len(d)>0 else 'no'")"
check "create bank question with rubric + skills" 201 "$(status POST "$API_BASE/api/questions" "$TOKEN" "{\"text\":\"How do you debug a flaky end-to-end test?\",\"role\":\"Frontend Engineer\",\"topic\":\"Testing\",\"difficulty\":\"MEDIUM\",\"expectedKeywords\":\"reproduce,isolation,trace,retry\",\"idealAnswer\":\"Isolate the flaky layer, capture traces, fix the root cause.\",\"skillTags\":\"Testing,JavaScript\"}")"
CUSTOM_Q="$(body | jget "d.get('id','')")"

echo
echo "▸ 8. Interview lifecycle (skill-driven questions)"
START="$(status POST "$WEB_BASE/api/interviews" "$TOKEN" "{\"candidateId\":$STUDENT_ID,\"role\":\"Frontend Engineer\",\"difficulty\":\"MEDIUM\",\"questionCount\":3,\"skills\":\"React,CSS,Testing\",\"level\":\"GRADUATE\"}")"
check "start interview via proxy" 201 "$START"
SESSION_ID="$(body | jget "d.get('session',{}).get('id','')")"
check "session records focus skills" "React,CSS,Testing" "$(body | jget "','.join(d.get('session',{}).get('focusSkills',[]))")"
check "session records level" "GRADUATE" "$(body | jget "d.get('session',{}).get('level','')")"
check "question set size honoured" 3 "$(body | jget "len(d.get('questions',[]))")"
MATCHED="$(body | jget "sum(1 for q in d.get('questions',[]) if any(s.lower() in (q.get('topic','')+' '+q.get('text','')).lower() for s in ['react','css','testing','javascript']))")"
[ "${MATCHED:-0}" -ge 1 ] && ok "questions tailored to the student's skills ($MATCHED/3 matched)" || bad "no skill-matched questions were served"
check "start is idempotent (resume, not duplicate)" "$SESSION_ID" "$(status POST "$API_BASE/api/interviews" "$TOKEN" "{\"candidateId\":$STUDENT_ID,\"role\":\"Frontend Engineer\",\"difficulty\":\"MEDIUM\",\"questionCount\":3}" >/dev/null; body | jget "d.get('session',{}).get('id','')")"

ANSWER="$(status POST "$API_BASE/api/interviews/$SESSION_ID/answers" "$TOKEN" "{\"answerText\":\"I keep state in a single useReducer so the component tree re-renders predictably, memoise the expensive rows and virtualise the list. For testing I assert on user-visible behaviour with Testing Library, and I profile with React DevTools when a list janks.\",\"timeTakenSeconds\":75}")"
check "submit answer → graded immediately" 200 "$ANSWER"
check "answer carries a score" "yes" "$(body | jget "'yes' if isinstance(d.get('score'),int) else 'no'")"
check "answer carries feedback" "yes" "$(body | jget "'yes' if d.get('feedback') else 'no'")"

for i in 2 3; do
  status POST "$API_BASE/api/interviews/$SESSION_ID/answers" "$TOKEN" "{\"answerText\":\"React hooks let function components own state and effects; I extract shared logic into custom hooks instead of higher-order components, which keeps the tree flat and the data flow visible.\",\"timeTakenSeconds\":60}" >/dev/null
done
check "final answer auto-completes the session" "yes" "$(body | jget "'yes' if d.get('sessionCompleted') else 'no'")"
check "report endpoint" 200 "$(status GET "$API_BASE/api/interviews/$SESSION_ID/report" "$TOKEN")"
check "report has an overall score" "yes" "$(body | jget "'yes' if isinstance(d.get('session',{}).get('overallScore'),(int,float)) else 'no'")"
check "report has an AI debrief" "yes" "$(body | jget "'yes' if (d.get('session',{}).get('aiSummary') or '') else 'no'")"
check "report lists strengths & gaps" "yes" "$(body | jget "'yes' if d.get('session',{}).get('strengths') or d.get('session',{}).get('gaps') else 'no'")"

echo
echo "▸ 9. Reviews & feedback"
FB="$(status POST "$API_BASE/api/interviews/$SESSION_ID/feedback" "$TOKEN" "{\"rating\":5,\"comment\":\"Excellent and fair interview — the questions matched my React skills and the feedback was genuinely useful.\"}")"
check "submit review" 200 "$FB"
check "sentiment derived as POSITIVE" "POSITIVE" "$(body | jget "d.get('sentiment','')")"
check "sentiment score computed" "yes" "$(body | jget "'yes' if isinstance(d.get('sentimentScore'),int) else 'no'")"
check "read review back" 200 "$(status GET "$API_BASE/api/interviews/$SESSION_ID/feedback" "$TOKEN")"
check "review is an upsert (still one review, new rating)" "4" "$(status POST "$API_BASE/api/interviews/$SESSION_ID/feedback" "$TOKEN" "{\"rating\":4,\"comment\":\"Good, though one question was too hard.\"}" >/dev/null; body | jget "d.get('rating','')")"

echo
echo "▸ 10. Analytics dashboard"
check "analytics endpoint via proxy" 200 "$(status GET "$WEB_BASE/api/analytics" "$TOKEN")"
check "summary present" "yes" "$(body | jget "'yes' if d.get('summary') else 'no'")"
check "average rating computed" "yes" "$(body | jget "'yes' if d.get('summary',{}).get('averageRating') is not None else 'no'")"
check "score trend present" "yes" "$(body | jget "'yes' if len(d.get('scoreTrend',[]))>0 else 'no'")"
check "dimension averages present" "yes" "$(body | jget "'yes' if len(d.get('dimensionAverages',[]))==4 else 'no'")"
check "weakest concepts present" "yes" "$(body | jget "'yes' if len(d.get('weakestConcepts',[]))>0 else 'no'")"
check "skill performance present" "yes" "$(body | jget "'yes' if len(d.get('skillPerformance',[]))>0 else 'no'")"
check "question calibration present" "yes" "$(body | jget "'yes' if len(d.get('questionCalibration',[]))>0 else 'no'")"
check "recent feedback feed present" "yes" "$(body | jget "'yes' if len(d.get('recentFeedback',[]))>0 else 'no'")"
check "sentiment split has 3 bands" 3 "$(body | jget "len(d.get('sentimentSplit',[]))")"
check "dashboard stats endpoint" 200 "$(status GET "$API_BASE/api/stats" "$TOKEN")"
check "runtime meta endpoint" 200 "$(status GET "$API_BASE/api/meta" "$TOKEN")"

echo
echo "▸ 11. Administration"
ADMIN_EMAIL="${APP_ADMIN_EMAIL:-admin@interviewiq.dev}"
ADMIN_PASSWORD="${APP_ADMIN_PASSWORD:-}"
if [ -n "$ADMIN_PASSWORD" ]; then
  check "admin login" 200 "$(status POST "$API_BASE/api/auth/login" "" "{\"email\":\"$ADMIN_EMAIL\",\"password\":\"$ADMIN_PASSWORD\"}")"
  ADMIN_TOKEN="$(body | jget "d.get('token','')")"
  check "admin can list accounts" 200 "$(status GET "$API_BASE/api/users" "$ADMIN_TOKEN")"
  check "admin sees the seeded OA (owned by another account)" "yes" "$(status GET "$API_BASE/api/assessments" "$ADMIN_TOKEN" >/dev/null; body | jget "'yes' if any(a.get('accessCode')=='DEMO26' for a in d) else 'no'")"
else
  echo "  (skipped: APP_ADMIN_PASSWORD not set in this shell)"
fi

echo
echo "▸ 12. Recruiter phase: host an OA (AI questions + AI analysis, no candidate account)"
REC_EMAIL="recruiter.$(date +%s)@interviewiq.dev"
REC_TOKEN="$(status POST "$API_BASE/api/auth/register" "" "{\"name\":\"E2E Recruiter\",\"email\":\"$REC_EMAIL\",\"password\":\"$PASSWORD\",\"confirmPassword\":\"$PASSWORD\",\"role\":\"RECRUITER\"}" >/dev/null; body | jget "d.get('token','')")"
check "recruiter signs up" "RECRUITER" "$(status GET "$API_BASE/api/auth/me" "$REC_TOKEN" >/dev/null; body | jget "d.get('role','')")"
check "student is blocked from the recruiter console (403)" 403 "$(status GET "$API_BASE/api/assessments" "$TOKEN")"
check "anonymous is blocked from the recruiter console (401)" 401 "$(status GET "$API_BASE/api/assessments")"

check "recruiter creates a draft OA" 201 "$(status POST "$API_BASE/api/assessments" "$REC_TOKEN" "{\"title\":\"E2E Backend OA\",\"company\":\"E2E Corp\",\"description\":\"Automated connectivity probe\",\"role\":\"Backend Engineer\",\"skills\":[\"Java\",\"SQL\",\"Testing\"],\"difficulty\":\"MEDIUM\",\"level\":\"JUNIOR\",\"questionCount\":3,\"durationMinutes\":15,\"passingScore\":50}")"
OA_ID="$(body | jget "d.get('id','')")"
check "draft starts in DRAFT status" "DRAFT" "$(body | jget "d.get('status','')")"
check "draft has no share code yet" "" "$(body | jget "d.get('accessCode') or ''" )"
check "publishing before the questions exist is rejected (409)" 409 "$(status POST "$API_BASE/api/assessments/$OA_ID/publish" "$REC_TOKEN")"

check "AI generates the question set" 3 "$(status POST "$API_BASE/api/assessments/$OA_ID/generate" "$REC_TOKEN" >/dev/null; body | jget "len(d) if isinstance(d,list) else 0")"
check "generated questions carry a rubric (keyword outline)" "yes" "$(body | jget "'yes' if d and d[0].get('expectedKeywords') else 'no'")"
check "recruiter reads the question set back" 3 "$(status GET "$API_BASE/api/assessments/$OA_ID/questions" "$REC_TOKEN" >/dev/null; body | jget "len(d)")"

check "publish returns a shareable code" "yes" "$(status POST "$API_BASE/api/assessments/$OA_ID/publish" "$REC_TOKEN" >/dev/null; body | jget "'yes' if d.get('accessCode') else 'no'")"
OA_CODE="$(body | jget "d.get('accessCode','')")"
OA_PATH="$(body | jget "d.get('sharePath','')")"
check "share path is /oa/{CODE}" "/oa/$OA_CODE" "$OA_PATH"
check "published OA is immutable (409 on edit)" 409 "$(status PUT "$API_BASE/api/assessments/$OA_ID" "$REC_TOKEN" "{\"title\":\"Renamed\",\"role\":\"Backend Engineer\",\"skills\":[\"Java\"],\"difficulty\":\"EASY\",\"level\":\"JUNIOR\",\"questionCount\":3,\"durationMinutes\":15,\"passingScore\":50}")"

check "candidate landing page needs no account" 200 "$(status GET "$API_BASE/api/public/assessments/$OA_CODE")"
check "landing page reports the paper is open" "yes" "$(body | jget "'yes' if d.get('openToAttempts') else 'no'")"
check "unknown OA code is a 404" 404 "$(status GET "$API_BASE/api/public/assessments/NOSUCH")"
check "malformed JSON body is a 400, not a 500" 400 "$(status POST "$API_BASE/api/auth/register" "" '{"name": }')"

OA_EMAIL="e2e.oa.$(date +%s)@interviewiq.dev"
check "candidate starts an attempt with just name + email" 201 "$(status POST "$API_BASE/api/public/assessments/$OA_CODE/attempts" "" "{\"name\":\"E2E Candidate\",\"email\":\"$OA_EMAIL\"}")"
OA_TOKEN="$(body | jget "d.get('attemptToken','')")"
OA_SESSION="$(body | jget "d.get('sessionId','')")"
check "attempt token is an opaque capability (32 hex chars)" 32 "${#OA_TOKEN}"
check "candidate sees every question up front" 3 "$(body | jget "len(d.get('questions',[]))")"
check "model answers are NOT exposed before submission" "yes" "$(body | jget "'yes' if all(('idealAnswer' not in q and 'expectedKeywords' not in q) for q in d.get('questions',[])) else 'no'")"
check "second start for the same email resumes instead of duplicating" "$OA_SESSION" "$(status POST "$API_BASE/api/public/assessments/$OA_CODE/attempts" "" "{\"name\":\"E2E Candidate\",\"email\":\"$OA_EMAIL\"}" >/dev/null; body | jget "d.get('sessionId','')")"
check "unknown attempt token is a 404" 404 "$(status GET "$API_BASE/api/public/attempts/deadbeefdeadbeefdeadbeefdeadbeef")"
check "resume by token returns the same attempt" "$OA_SESSION" "$(status GET "$API_BASE/api/public/attempts/$OA_TOKEN" >/dev/null; body | jget "d.get('sessionId','')")"

OA_ANSWER="A service layer separates HTTP concerns from domain rules. I keep transactions short, validate input at the edge, and cover the risky paths with tests so refactors stay safe. On one project this cut production incidents by half because the boundaries were explicit and measurable in review."
for i in 1 2 3; do
  ACK="$(status POST "$API_BASE/api/public/attempts/$OA_TOKEN/answers" "" "{\"answerText\":\"$OA_ANSWER\",\"timeTakenSeconds\":$(($i * 30))}")"
  if [ "$i" -lt 3 ]; then
    check "answer $i accepted ($(body | jget "d.get('answeredCount','?')")/3, score still hidden)" 200 "$ACK"
    check "no score leaks back in the acknowledgement" "yes" "$(body | jget "'yes' if 'score' not in d and not d.get('completed') else 'no'")"
  else
    check "last answer auto-submits the attempt" "yes" "$(body | jget "'yes' if d.get('completed') else 'no'")"
  fi
done
check "candidate result view reveals the AI analysis" 200 "$(status GET "$API_BASE/api/public/attempts/$OA_TOKEN")"
check "analysis hidden until submit, then scored" "yes" "$(body | jget "'yes' if d.get('completed') and d.get('answers',[{}])[0].get('analysis',{}).get('score') is not None else 'no'")"
check "overall AI summary written" "yes" "$(body | jget "'yes' if d.get('aiSummary') else 'no'")"
check "pass/fail is computed against the pass mark" "yes" "$(body | jget "'yes' if isinstance(d.get('passed'), bool) else 'no'")"
check "a fresh attempt after completing is refused (409)" 409 "$(status POST "$API_BASE/api/public/assessments/$OA_CODE/attempts" "" "{\"name\":\"E2E Candidate\",\"email\":\"$OA_EMAIL\"}")"

check "recruiter sees the attempt in the OA" "yes" "$(status GET "$API_BASE/api/assessments/$OA_ID/attempts" "$REC_TOKEN" >/dev/null; body | jget "'yes' if any(r.get('sessionId')==$OA_SESSION for r in d) else 'no'")"
check "per-attempt AI analysis is available to the recruiter" 200 "$(status GET "$API_BASE/api/assessments/$OA_ID/attempts/$OA_SESSION" "$REC_TOKEN")"
check "attempt detail carries per-answer scores + debrief" "yes" "$(body | jget "'yes' if d.get('aiSummary') and d.get('answers') and d['answers'][0].get('score') is not None else 'no'")"
check "cohort analytics available" 200 "$(status GET "$API_BASE/api/assessments/$OA_ID/analytics" "$REC_TOKEN")"
check "cohort analytics has the full key set" "yes" "$(body | jget "'yes' if all(k in d for k in ['attempts','completed','averageScore','passRate','distribution','perQuestion','weakestConcepts','strongestConcepts','skillBreakdown','leaderboard','aiCohortSummary']) else 'no'")"
check "cohort AI read-out written" "yes" "$(body | jget "'yes' if d.get('aiCohortSummary') else 'no'")"

check "deleting an OA with attempts is refused (409)" 409 "$(status DELETE "$API_BASE/api/assessments/$OA_ID" "$REC_TOKEN")"
check "recruiter closes the OA" "CLOSED" "$(status POST "$API_BASE/api/assessments/$OA_ID/close" "$REC_TOKEN" >/dev/null; body | jget "d.get('status','')")"
check "closed OA no longer accepts attempts (409)" 409 "$(status POST "$API_BASE/api/public/assessments/$OA_CODE/attempts" "" "{\"name\":\"Too Late\",\"email\":\"too.late@interviewiq.dev\"}")"
check "a clean draft can be deleted" 204 "$(status DELETE "$API_BASE/api/assessments/$(status POST "$API_BASE/api/assessments" "$REC_TOKEN" '{"title":"Throwaway","role":"QA Engineer","skills":["Testing"],"difficulty":"EASY","level":"JUNIOR","questionCount":3,"durationMinutes":10,"passingScore":50}' >/dev/null; body | jget "d.get('id','')")" "$REC_TOKEN")"

check "demo logins are advertised without auth" 200 "$(status GET "$API_BASE/api/public/demo-accounts")"
check "both a recruiter and a student login are offered" "yes" "$(body | jget "'yes' if {a.get('role') for a in d.get('accounts',[])} >= {'RECRUITER','STUDENT'} else 'no'")"
DEMO_REC_EMAIL="$(body | jget "[a.get('email') for a in d.get('accounts',[]) if a.get('role')=='RECRUITER'][0] if d.get('accounts') else ''")"
DEMO_REC_PW="$(body | jget "[a.get('password') for a in d.get('accounts',[]) if a.get('role')=='RECRUITER'][0] if d.get('accounts') else ''")"
DEMO_STU_EMAIL="$(body | jget "[a.get('email') for a in d.get('accounts',[]) if a.get('role')=='STUDENT'][0] if d.get('accounts') else ''")"
DEMO_STU_PW="$(body | jget "[a.get('password') for a in d.get('accounts',[]) if a.get('role')=='STUDENT'][0] if d.get('accounts') else ''")"
check "advertised recruiter login really signs in" 200 "$(status POST "$API_BASE/api/auth/login" "" "{\"email\":\"$DEMO_REC_EMAIL\",\"password\":\"$DEMO_REC_PW\"}")"
check "advertised student login really signs in" 200 "$(status POST "$API_BASE/api/auth/login" "" "{\"email\":\"$DEMO_STU_EMAIL\",\"password\":\"$DEMO_STU_PW\"}")"

check "react app serves the public OA route" 200 "$(status GET "$WEB_BASE/oa/$OA_CODE")"
check "react app serves the recruiter console route" 200 "$(status GET "$WEB_BASE/assessments")"
check "public OA API is reachable through the Vite proxy" 200 "$(status GET "$WEB_BASE/api/public/assessments/$OA_CODE")"

echo
echo "▸ 13. Cleanup + teardown paths"
check "delete the test student" 204 "$(status DELETE "$API_BASE/api/candidates/$STUDENT_ID" "$TOKEN")"
check "deleted student is gone (404)" 404 "$(status GET "$API_BASE/api/candidates/$STUDENT_ID" "$TOKEN")"
check "custom question deleted" 204 "$(status DELETE "$API_BASE/api/questions/$CUSTOM_Q" "$TOKEN")"
check "review for deleted session is gone" 404 "$(status GET "$API_BASE/api/interviews/$SESSION_ID/feedback" "$TOKEN")"

echo
echo "═══════════════════════════════════════════════════════════════"
printf ' RESULT:  \033[32m%d passed\033[0m, \033[31m%d failed\033[0m\n' "$PASS" "$FAIL"
echo "═══════════════════════════════════════════════════════════════"
[ "$FAIL" -eq 0 ] || exit 1
