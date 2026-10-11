package com.interviewiq.config;

import com.interviewiq.ai.AiService;
import com.interviewiq.ai.EvaluationResult;
import com.interviewiq.ai.QuestionBank;
import com.interviewiq.ai.SessionSummary;
import com.interviewiq.domain.Assessment;
import com.interviewiq.domain.AssessmentQuestion;
import com.interviewiq.domain.AssessmentStatus;
import com.interviewiq.domain.AppUser;
import com.interviewiq.domain.Candidate;
import com.interviewiq.domain.Role;
import com.interviewiq.domain.Difficulty;
import com.interviewiq.domain.InterviewAnswer;
import com.interviewiq.domain.InterviewFeedback;
import com.interviewiq.domain.InterviewSession;
import com.interviewiq.domain.Level;
import com.interviewiq.domain.Question;
import com.interviewiq.domain.SessionQuestion;
import com.interviewiq.domain.SessionStatus;
import com.interviewiq.repo.AppUserRepository;
import com.interviewiq.repo.AssessmentQuestionRepository;
import com.interviewiq.repo.AssessmentRepository;
import com.interviewiq.repo.CandidateRepository;
import com.interviewiq.repo.InterviewAnswerRepository;
import com.interviewiq.repo.InterviewFeedbackRepository;
import com.interviewiq.repo.InterviewSessionRepository;
import com.interviewiq.repo.QuestionRepository;
import com.interviewiq.repo.SessionQuestionRepository;
import com.interviewiq.security.PasswordPolicy;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import com.interviewiq.config.AppProperties;
import org.springframework.core.annotation.Order;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * First-boot bootstrap: loads the curated question bank and a small set of demo
 * interviews so the dashboard, question library and reports have content
 * immediately. Runs only when the tables are empty, so restarts are safe.
 */
@Component
@Order(20)   // after AdminBootstrap (@Order(10)) so the demo OA can be owned by the admin
@ConditionalOnProperty(prefix = "app.seed", name = "enabled", havingValue = "true", matchIfMissing = true)
public class SeedDataInitializer implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(SeedDataInitializer.class);

    private final QuestionRepository questions;
    private final CandidateRepository candidates;
    private final InterviewSessionRepository sessions;
    private final InterviewAnswerRepository answers;
    private final InterviewFeedbackRepository feedback;
    private final SessionQuestionRepository sessionQuestions;
    private final AssessmentRepository assessments;
    private final AssessmentQuestionRepository assessmentQuestions;
    private final AppUserRepository users;
    private final PasswordEncoder passwordEncoder;
    private final AiService ai;

    /** Demo-account credentials live in AppProperties (app.demo.password). */
    private final AppProperties props;

    public SeedDataInitializer(QuestionRepository questions,
                               CandidateRepository candidates,
                               InterviewSessionRepository sessions,
                               InterviewAnswerRepository answers,
                               InterviewFeedbackRepository feedback,
                               SessionQuestionRepository sessionQuestions,
                               AssessmentRepository assessments,
                               AssessmentQuestionRepository assessmentQuestions,
                               AppUserRepository users,
                               PasswordEncoder passwordEncoder,
                               AppProperties props,
                               AiService ai) {
        this.questions = questions;
        this.candidates = candidates;
        this.sessions = sessions;
        this.answers = answers;
        this.feedback = feedback;
        this.sessionQuestions = sessionQuestions;
        this.assessments = assessments;
        this.assessmentQuestions = assessmentQuestions;
        this.users = users;
        this.passwordEncoder = passwordEncoder;
        this.props = props;
        this.ai = ai;
    }

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        if (questions.count() == 0) {
            questions.saveAll(QuestionBank.seed());
            log.info("Seeded question bank ({} questions)", questions.count());
        }
        if (candidates.count() == 0) {
            seedDemoInterviews();
        }
        seedDemoAccounts();
        if (assessments.count() == 0) {
            seedDemoAssessment();
        }
    }

    // ── Demo logins (one per role, so both sides can be tried immediately) ───

    private void seedDemoAccounts() {
        String password = props.getDemo().getPassword() == null ? "" : props.getDemo().getPassword().trim();
        String policyError = PasswordPolicy.validate(password);
        if (policyError != null) {
            log.error("APP_DEMO_PASSWORD rejected by the password policy ({}); falling back to the built-in demo password", policyError);
            password = "Demo!Access2026";
        }
        createAccountIfMissing("Riya Recruiter", "recruiter@interviewiq.dev", password, Role.RECRUITER);
        createAccountIfMissing("Dev Student", "student@interviewiq.dev", password, Role.STUDENT);
    }

    private void createAccountIfMissing(String name, String email, String password, Role role) {
        if (users.findByEmailIgnoreCase(email).isPresent()) {
            return;
        }
        users.save(new AppUser(name, email, passwordEncoder.encode(password), role));
        log.info("Seeded demo {} account {} (dev credentials — see README)", role, email);
    }

    // ── Demo online assessment (recruiter-hosted OA) ─────────────────────────

    private void seedDemoAssessment() {
        // Owned by the demo recruiter so the recruiter console shows a live OA;
        // falls back to the admin when the demo recruiter is switched off.
        AppUser owner = users.findByEmailIgnoreCase("recruiter@interviewiq.dev")
                .or(() -> users.findByEmailIgnoreCase("admin@interviewiq.dev"))
                .orElse(null);
        if (owner == null) {
            log.info("No recruiter/admin account available — skipping the demo OA seed");
            return;
        }

        Assessment assessment = new Assessment(owner,
                "Campus Placement OA — Frontend Engineer", "Frontend Engineer", 4);
        assessment.setCompany("InterviewIQ Labs");
        assessment.setDescription("""
                Timed online assessment for final-year students targeting frontend roles.
                4 questions · 30 minutes · auto-graded with AI feedback on every answer.
                """);
        assessment.setSkills("React,JavaScript,CSS,Testing");
        assessment.setDifficulty(Difficulty.MEDIUM);
        assessment.setLevel(Level.STUDENT);
        assessment.setDurationMinutes(30);
        assessment.setPassingScore(60);
        assessments.save(assessment);

        List<Question> generated = ai.generateQuestions(
                assessment.getRole(), assessment.getDifficulty(), assessment.getQuestionCount(),
                Set.of(), AssessmentResponseSkills(assessment.getSkills()), assessment.getLevel());
        questions.saveAll(generated);
        List<AssessmentQuestion> rows = new ArrayList<>();
        for (int i = 0; i < generated.size(); i++) {
            rows.add(new AssessmentQuestion(assessment, generated.get(i), i));
        }
        assessmentQuestions.saveAll(rows);

        assessment.setAccessCode("DEMO26");
        assessment.setStatus(AssessmentStatus.PUBLISHED);
        assessment.setPublishedAt(Instant.now().minus(Duration.ofDays(3)));
        assessments.save(assessment);
        log.info("Seeded demo OA '{}' with code {} ({} AI-generated questions)",
                assessment.getTitle(), assessment.getAccessCode(), rows.size());

        seedDemoAttempts(assessment, rows);
    }

    /** Three representative attempts (strong / borderline / weak) so analysis has data on day one. */
    private void seedDemoAttempts(Assessment assessment, List<AssessmentQuestion> rows) {
        record Attempt(String name, String email, double coverage, Duration age) {
        }
        List<Attempt> attempts = List.of(
                new Attempt("Nikhil Sharma", "nikhil.sharma@example.com", 0.95, Duration.ofDays(2)),
                new Attempt("Priya Desai", "priya.desai@example.com", 0.55, Duration.ofDays(2).minusHours(3)),
                new Attempt("Arjun Reddy", "arjun.reddy@example.com", 0.18, Duration.ofDays(1)));

        for (Attempt spec : attempts) {
            Candidate candidate = candidates.save(new Candidate(spec.name(), spec.email(),
                    "Campus placement candidate", assessment.getRole(), assessment.getSkills(),
                    "Final year", Level.STUDENT));

            Instant started = Instant.now().minus(spec.age());
            InterviewSession session = new InterviewSession(candidate, assessment.getRole(),
                    assessment.getDifficulty(), rows.size());
            session.setAssessment(assessment);
            session.setAttemptToken(java.util.UUID.randomUUID().toString().replace("-", ""));
            session.setExpiresAt(started.plusSeconds(60L * assessment.getDurationMinutes()));
            session.setStartedAt(started);
            session.setEngine("heuristic");
            session.setLevel(assessment.getLevel());
            session.setFocusSkills(assessment.getSkills());
            sessions.save(session);

            List<InterviewAnswer> graded = new ArrayList<>();
            for (int i = 0; i < rows.size(); i++) {
                Question q = rows.get(i).getQuestion();
                sessionQuestions.save(new SessionQuestion(session, q, i));

                InterviewAnswer answer = new InterviewAnswer();
                answer.setSession(session);
                answer.setQuestion(q);
                answer.setAnswerText(syntheticAnswer(q, spec.coverage()));
                answer.setTimeTakenSeconds(120 + 35 * i);
                answer.setAnsweredAt(started.plus(Duration.ofMinutes(4L * (i + 1))));

                EvaluationResult ev = ai.evaluate(q, answer.getAnswerText());
                answer.setScore(ev.score());
                answer.setTechnicalScore(ev.technicalScore());
                answer.setDepthScore(ev.depthScore());
                answer.setCommunicationScore(ev.communicationScore());
                answer.setClarityScore(ev.clarityScore());
                answer.setFeedback(ev.feedback());
                answer.setStrengths(join(ev.strengths()));
                answer.setImprovements(join(ev.improvements()));
                answer.setKeywordsMatched(join(ev.keywordsMatched()));
                answer.setMissingPoints(join(ev.missingPoints()));
                answers.save(answer);
                graded.add(answer);
            }

            double average = graded.stream().mapToInt(InterviewAnswer::getScore).average().orElse(0);
            double rounded = Math.round(average * 10) / 10.0;
            SessionSummary summary = ai.summarize(assessment.getRole(), graded, rounded);
            session.setOverallScore(rounded);
            session.setAiSummary(summary.summary());
            session.setStrengths(summary.strengthsText());
            session.setGaps(summary.gapsText());
            session.setStatus(SessionStatus.COMPLETED);
            session.setCompletedAt(started.plus(Duration.ofMinutes(26)));
            sessions.save(session);
        }
        log.info("Seeded {} demo OA attempt(s) for '{}'", attempts.size(), assessment.getTitle());
    }

    /**
     * Builds an answer with roughly {@code coverage} of the question's rubric
     * concepts so the seeded attempts span strong, borderline and weak profiles.
     */
    private static String syntheticAnswer(Question question, double coverage) {
        List<String> keywords = new ArrayList<>();
        if (question.getExpectedKeywords() != null) {
            for (String part : question.getExpectedKeywords().split(",")) {
                String value = part.trim();
                if (!value.isEmpty()) {
                    keywords.add(value);
                }
            }
        }
        int mention = (int) Math.round(keywords.size() * coverage);
        List<String> used = keywords.subList(0, Math.min(mention, keywords.size()));

        StringBuilder sb = new StringBuilder();
        if (coverage >= 0.8) {
            sb.append("My approach: first I clarify the goal, then walk through the reasoning step by step. ");
            sb.append("The key ideas here are ").append(String.join(", ", used)).append(". ");
            sb.append("For example, on a recent project I applied exactly this and measured the result — ");
            sb.append("latency dropped by about 30% and the team adopted the pattern. ");
            sb.append("The trade-off is that it costs some implementation effort, but the numbers justified it.");
        } else if (coverage >= 0.4) {
            sb.append("I would start by breaking the problem down and then explaining my reasoning. ");
            sb.append("I know about ").append(String.join(", ", used)).append(" and would use them here. ");
            sb.append("I think the main trade-off is between speed and correctness, though I have not measured it myself.");
        } else {
            sb.append("I would look at it and try to fix whatever seems slow. ");
            if (!used.isEmpty()) {
                sb.append("Maybe ").append(used.get(0)).append(" is involved. ");
            }
            sb.append("I have not worked on this much yet.");
        }
        return sb.toString().trim();
    }

    private static List<String> AssessmentResponseSkills(String raw) {
        if (raw == null || raw.isBlank()) {
            return List.of();
        }
        return java.util.Arrays.stream(raw.split(",")).map(String::trim).filter(v -> !v.isEmpty()).toList();
    }

    // ── Demo transcripts ─────────────────────────────────────────────────────

    private record DemoAnswer(String questionMarker, String answer) {
    }

    private record Review(int rating, String comment) {
    }

    private record DemoInterview(String name, String email, String headline, String targetRole,
                                 String skills, String education, Level level,
                                 String role, Difficulty difficulty, int totalQuestions,
                                 Duration age, List<DemoAnswer> answers, List<Review> reviews) {
    }

    private void seedDemoInterviews() {
        List<DemoInterview> demos = List.of(
                new DemoInterview("Aarav Mehta", "aarav.mehta@example.com",
                        "Backend engineer · 4 yrs · payments", "Backend Engineer",
                        "Java,Spring Boot,SQL,Caching,Concurrency", "B.Tech CSE · 2021", Level.PROFESSIONAL,
                        "Backend Engineer", Difficulty.MEDIUM, 4, Duration.ofDays(2), List.of(
                        new DemoAnswer("When would you add an index",
                                "I add an index when a query pattern repeatedly filters or joins on a column and the plan in EXPLAIN shows a full scan over a large table. "
                                        + "The trade-off is write amplification — every insert, update and delete maintains the B-tree, and each index costs storage — so I skip low-selectivity columns like booleans. "
                                        + "For hot read paths I'll sometimes build a covering index so the query is answered from the index alone. "
                                        + "Either way I confirm the win with the query plan and p95 latency before and after, rather than assuming."),
                        new DemoAnswer("Explain how HashMap works internally",
                                "I describe it as an array of buckets addressed by hashing the key. Two keys can collide, so each bucket holds a linked list that treeifies into a red-black tree once it grows long, which turns worst-case lookups from O(n) into O(log n). "
                                        + "The table doubles when the load factor of 0.75 is exceeded, rehashing every entry. "
                                        + "Lookups degrade when the hash function is poor — say equals is overridden without hashCode, or the key type is mutable — because everything lands in one bucket and every get becomes a linear scan."),
                        new DemoAnswer("Describe cache invalidation strategies",
                                "It depends on how much correctness I need. For mostly-static data a TTL with bounded staleness is the cheapest option. "
                                        + "When writes must be visible quickly I prefer delete-on-write plus read-through rebuild, so the database stays the source of truth and the cache can't drift far. "
                                        + "In Redis I set a maxmemory policy of allkeys-lru so eviction behaves under pressure instead of failing writes. "
                                        + "The classic bug is a stale negative cache, so I never cache misses without a very short TTL."),
                        new DemoAnswer("How does Spring Boot auto-configuration decide",
                                "Starters ship auto-configuration classes, and each one is guarded with conditions like @ConditionalOnClass, @ConditionalOnBean and @ConditionalOnMissingBean. "
                                        + "At startup Boot evaluates the configurations listed in each jar against the classpath and the beans you've already defined. "
                                        + "Your own beans win — that's the @ConditionalOnMissingBean escape hatch — so defining your own DataSource or SecurityFilterChain quietly replaces the default. "
                                        + "It's convention over configuration with a clean override, which is why a starter jar can feel magical until you read the conditions.")),
                        List.of(new Review(5, "Very fair and thorough interview. The questions matched my skills exactly and the list of missing topics was spot on — I revised two of them the same evening."))),

                new DemoInterview("Sara Khan", "sara.khan@example.com",
                        "Frontend engineer · 3 yrs · design systems", "Frontend Engineer",
                        "React,JavaScript,CSS,Testing", "B.Sc Computer Science · 2022", Level.PROFESSIONAL,
                        "Frontend Engineer", Difficulty.MEDIUM, 4, Duration.ofDays(1), List.of(
                        new DemoAnswer("difference between let, const, and var",
                                "var is function-scoped and hoisted, so it's readable before its declaration as undefined, which hides bugs. "
                                        + "let and const are block-scoped; they're hoisted too but sit in a temporal dead zone, so touching them early throws a ReferenceError. "
                                        + "const prevents rebinding but doesn't freeze the object it points at — you can still mutate properties. "
                                        + "In our codebase we default to const, use let when a binding genuinely changes, and ban var in new files via lint."),
                        new DemoAnswer("problem do React hooks solve",
                                "Hooks let function components own state and effects without classes. "
                                        + "The bigger win is reuse: sharing stateful logic used to mean higher-order components or render props, which nested the tree and obscured where data came from. "
                                        + "A custom hook extracts that logic without changing the component hierarchy. "
                                        + "The mental model also got simpler — derived values are computed during render rather than synced by hand in lifecycle methods."),
                        new DemoAnswer("CSS box model",
                                "Every element is a box: content, padding, border, margin, from the inside out. "
                                        + "With content-box, width applies to content only, so padding and border make the rendered box wider than declared — that's the classic layout surprise. "
                                        + "box-sizing: border-box makes width include padding and border; margin always sits outside. "
                                        + "We set border-box globally with a reset so measurements match what designers hand over, and remember margins can collapse between siblings while padding never does."),
                        new DemoAnswer("typing a URL to seeing pixels",
                                "A lot happens: DNS resolution, TCP handshake, TLS negotiation, then the HTTP request. "
                                        + "The response is parsed — HTML into the DOM, CSS into the CSSOM — and render-blocking resources hold up first paint. "
                                        + "Then layout computes geometry, paint rasterises, and compositing merges layers onto the screen. "
                                        + "Fonts and images keep arriving afterwards and can trigger reflow, which is why the critical path is HTML, CSS and above-the-fold assets.")),
                        List.of(new Review(4, "Loved the instant feedback after each answer — much more useful than waiting a week for a human review. One question felt harder than my level but the model answer helped."))),

                new DemoInterview("Dev Patel", "dev.patel@example.com",
                        "Data engineer · 5 yrs · streaming platforms", "Data Engineer",
                        "Spark,SQL,Kafka,ETL,Big Data", "M.Tech Data Science · 2019", Level.PROFESSIONAL,
                        "Data Engineer", Difficulty.HARD, 4, Duration.ofHours(5), List.of(
                        new DemoAnswer("What causes skew in Spark jobs",
                                "Skew shows up as a few tasks dragging far behind their peers — one partition keys holding most of the rows. "
                                        + "The fix depends on the cause: if a join touches a small table, I broadcast it and skip the shuffle entirely. "
                                        + "If a hot key is unavoidable, I salt the key with a random suffix, replicate the other side, then aggregate the extra rows back. "
                                        + "Adaptive query execution now helps by splitting and coalescing partitions at runtime, and I always verify the change in the Spark UI task distribution instead of trusting the theory."),
                        new DemoAnswer("daily ETL job idempotent",
                                "Each run writes to a date partition and overwrites that partition on rerun, so replaying a day can't duplicate rows. "
                                        + "For streaming I rely on checkpointing plus a watermark so state and progress are deterministic across restarts. "
                                        + "At load time I dedupe on business keys, keeping the latest version by event time. "
                                        + "The payoff is that backfills become routine reruns of isolated partitions, so recovering from a bad deploy is boring instead of scary.")),
                        List.of()),

                new DemoInterview("Rahul Verma", "rahul.verma@example.com",
                        "Final-year CS student · ML interest", "ML Engineer",
                        "Python,Machine Learning,Statistics", "B.Tech CSE · 2026", Level.STUDENT,
                        "ML Engineer", Difficulty.MEDIUM, 3, Duration.ofDays(3), List.of(
                        new DemoAnswer("overfitting",
                                "Overfitting is when the model learns the training data too well, including its noise, so it performs great on training and poorly on new data. "
                                        + "I counter it with regularisation like dropout, by using cross validation to catch it early and stop training when validation stops improving, and by getting more data or augmenting what I have. "
                                        + "In a course project on image classification I had 98% train and 62% validation accuracy, and adding dropout plus early stopping brought validation up to around 80%."),
                        new DemoAnswer("class imbalance",
                                "If one class is rare, a model can get high accuracy by predicting the common class. "
                                        + "I would use class weights or resample the data, and judge the model with precision-recall AUC and F1 instead of accuracy. "
                                        + "Then I would tune the decision threshold to match the cost of false negatives versus false positives."),
                        new DemoAnswer("fine-tuning better than prompt engineering",
                                "I think fine-tuning is better when you have lots of labelled data and a very specific task. "
                                        + "For most things I would try prompting first because it is cheaper.")),
                        List.of(new Review(3, "Helpful feedback on my ML basics and the keywords list made revision easy, though a couple of comments felt a bit generic. I would like more maths-heavy questions at this difficulty."))),

                new DemoInterview("Ananya Rao", "ananya.rao@example.com",
                        "Graduate · analytics bootcamp", "Data Engineer",
                        "SQL,Python,Analytics", "B.E Information Science · 2025", Level.GRADUATE,
                        "Data Engineer", Difficulty.EASY, 3, Duration.ofDays(1).plusHours(2), List.of(
                        new DemoAnswer("window functions",
                                "Window functions calculate values across a set of rows related to the current row without collapsing them the way GROUP BY does. "
                                        + "I used ROW_NUMBER to remove duplicate events keeping the newest, and SUM OVER with a partition for running totals in a dashboard query. "
                                        + "The work is scoped by PARTITION BY and ordered with ORDER BY, and the frame decides how many rows each calculation sees."),
                        new DemoAnswer("Star schema",
                                "For analytics I would choose a star schema: a central fact table with measures and dimension tables around it for the descriptive attributes. "
                                        + "It means fewer joins and simpler, faster queries for BI tools, and I accept some duplication because analytical reads dominate. "
                                        + "Normalised models are better when write integrity matters more, like transactional systems."),
                        new DemoAnswer("Parquet versus CSV",
                                "Parquet is a columnar format, so a query that needs three columns reads only those columns instead of the whole file, which is a big I/O saving. "
                                        + "It also compresses much better and carries a schema, so types do not get guessed wrong. "
                                        + "CSV is fine for small files and for humans to read.")),
                        List.of(new Review(4, "Clear, well-structured questions at a level I could actually handle as a fresher. The scoring felt fair and the model answers were useful. Slightly repetitive between my two attempts."))));

        for (DemoInterview demo : demos) {
            candidateAndSession(demo);
        }
        log.info("Seeded {} demo candidate(s) with interview history", candidates.count());
    }

    private void candidateAndSession(DemoInterview demo) {
        Candidate candidate = candidates.save(new Candidate(demo.name(), demo.email(), demo.headline(),
                demo.targetRole(), demo.skills(), demo.education(), demo.level()));

        List<Question> chosen = new ArrayList<>();
        for (DemoAnswer da : demo.answers()) {
            byMarker(da.questionMarker()).ifPresentOrElse(chosen::add, () ->
                    log.warn("Demo question matching '{}' was not found in the bank", da.questionMarker()));
        }
        // Fill up to the session length with other questions for the same role.
        if (chosen.size() < demo.totalQuestions()) {
            List<Question> pool = questions.findByRole(demo.role());
            for (Question q : pool) {
                if (chosen.size() >= demo.totalQuestions()) {
                    break;
                }
                if (chosen.stream().noneMatch(c -> c.getId().equals(q.getId()))) {
                    chosen.add(q);
                }
            }
        }
        if (chosen.isEmpty()) {
            return;
        }

        Instant started = Instant.now().minus(demo.age());
        InterviewSession session = new InterviewSession(candidate, demo.role(), demo.difficulty(), chosen.size());
        session.setEngine("heuristic");
        session.setLevel(demo.level());
        session.setFocusSkills(demo.skills());
        session.setStartedAt(started);
        sessions.save(session);

        List<SessionQuestion> rows = new ArrayList<>();
        for (int i = 0; i < chosen.size(); i++) {
            rows.add(new SessionQuestion(session, chosen.get(i), i));
        }
        sessionQuestions.saveAll(rows);

        List<InterviewAnswer> graded = new ArrayList<>();
        for (int i = 0; i < demo.answers().size() && i < chosen.size(); i++) {
            SessionQuestion sq = rows.get(i);
            InterviewAnswer answer = new InterviewAnswer();
            answer.setSession(session);
            answer.setQuestion(sq.getQuestion());
            answer.setAnswerText(demo.answers().get(i).answer());
            answer.setAnsweredAt(started.plus(Duration.ofMinutes(6L * (i + 1))));
            answer.setTimeTakenSeconds(180 + 45 * i);

            EvaluationResult ev = ai.evaluate(sq.getQuestion(), answer.getAnswerText());
            answer.setScore(ev.score());
            answer.setTechnicalScore(ev.technicalScore());
            answer.setDepthScore(ev.depthScore());
            answer.setCommunicationScore(ev.communicationScore());
            answer.setClarityScore(ev.clarityScore());
            answer.setFeedback(ev.feedback());
            answer.setStrengths(join(ev.strengths()));
            answer.setImprovements(join(ev.improvements()));
            answer.setKeywordsMatched(join(ev.keywordsMatched()));
            answer.setMissingPoints(join(ev.missingPoints()));
            answers.save(answer);
            graded.add(answer);

            sq.setAnswered(true);
            sessionQuestions.save(sq);
        }

        if (graded.size() == chosen.size()) {
            double average = graded.stream().mapToInt(InterviewAnswer::getScore).average().orElse(0);
            double rounded = Math.round(average * 10) / 10.0;
            SessionSummary summary = ai.summarize(session.getRole(), graded, rounded);
            session.setOverallScore(rounded);
            session.setAiSummary(summary.summary());
            session.setStrengths(summary.strengthsText());
            session.setGaps(summary.gapsText());
            session.setStatus(SessionStatus.COMPLETED);
            session.setCompletedAt(started.plus(Duration.ofMinutes(40)));
            sessions.save(session);

            for (Review review : demo.reviews()) {
                InterviewFeedback entity = new InterviewFeedback(session, review.rating(), review.comment());
                var sentiment = ai.sentiment(review.rating(), review.comment());
                entity.setSentiment(sentiment.label());
                entity.setSentimentScore(sentiment.score());
                entity.setSubmittedAt(started.plus(Duration.ofHours(20)));
                feedback.save(entity);
            }
        } else {
            sessions.save(session);
        }
    }

    private java.util.Optional<Question> byMarker(String marker) {
        return questions.findAll().stream()
                .filter(q -> q.getText().contains(marker))
                .findFirst();
    }

    private static String join(List<String> values) {
        return values == null || values.isEmpty() ? null : String.join("\n", values);
    }
}
