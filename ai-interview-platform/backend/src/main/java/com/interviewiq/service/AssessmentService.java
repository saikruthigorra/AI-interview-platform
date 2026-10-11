package com.interviewiq.service;

import com.interviewiq.ai.AiService;
import com.interviewiq.domain.Assessment;
import com.interviewiq.domain.AssessmentQuestion;
import com.interviewiq.domain.AssessmentStatus;
import com.interviewiq.domain.AppUser;
import com.interviewiq.domain.Difficulty;
import com.interviewiq.domain.InterviewAnswer;
import com.interviewiq.domain.InterviewSession;
import com.interviewiq.domain.Level;
import com.interviewiq.domain.Question;
import com.interviewiq.domain.Role;
import com.interviewiq.domain.SessionQuestion;
import com.interviewiq.domain.SessionStatus;
import com.interviewiq.repo.AppUserRepository;
import com.interviewiq.repo.AssessmentQuestionRepository;
import com.interviewiq.repo.AssessmentRepository;
import com.interviewiq.repo.InterviewAnswerRepository;
import com.interviewiq.repo.InterviewSessionRepository;
import com.interviewiq.repo.QuestionRepository;
import com.interviewiq.repo.SessionQuestionRepository;
import com.interviewiq.web.NotFoundException;
import com.interviewiq.web.dto.AssessmentAnalyticsResponse;
import com.interviewiq.web.dto.AssessmentQuestionResponse;
import com.interviewiq.web.dto.AssessmentRequest;
import com.interviewiq.web.dto.AssessmentResponse;
import com.interviewiq.web.dto.AttemptAnswerResponse;
import com.interviewiq.web.dto.AttemptDetailResponse;
import com.interviewiq.web.dto.AttemptSummaryResponse;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Recruiter-facing side of the OA module: build an assessment, have AI generate
 * its question set, publish it behind a share code, then review attempts with
 * cohort-level analysis of every graded answer.
 */
@Service
public class AssessmentService {

    private static final Logger log = LoggerFactory.getLogger(AssessmentService.class);

    /** Unambiguous alphabet — no O/0, I/1 — so codes survive being read aloud. */
    private static final char[] CODE_ALPHABET = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789".toCharArray();

    private final AssessmentRepository assessments;
    private final AssessmentQuestionRepository assessmentQuestions;
    private final InterviewSessionRepository sessions;
    private final InterviewAnswerRepository answers;
    private final SessionQuestionRepository sessionQuestions;
    private final QuestionRepository questions;
    private final AppUserRepository users;
    private final AiService ai;

    public AssessmentService(AssessmentRepository assessments,
                             AssessmentQuestionRepository assessmentQuestions,
                             InterviewSessionRepository sessions,
                             InterviewAnswerRepository answers,
                             SessionQuestionRepository sessionQuestions,
                             QuestionRepository questions,
                             AppUserRepository users,
                             AiService ai) {
        this.assessments = assessments;
        this.assessmentQuestions = assessmentQuestions;
        this.sessions = sessions;
        this.answers = answers;
        this.sessionQuestions = sessionQuestions;
        this.questions = questions;
        this.users = users;
        this.ai = ai;
    }

    // ── CRUD ─────────────────────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public List<AssessmentResponse> list(String requesterEmail) {
        AppUser user = requireUser(requesterEmail);
        List<Assessment> list = user.getRole() == Role.ADMIN
                ? assessments.findAllByOrderByCreatedAtDesc()
                : assessments.findByOwnerIdOrderByCreatedAtDesc(user.getId());
        return list.stream().map(this::toResponse).toList();
    }

    @Transactional(readOnly = true)
    public AssessmentResponse get(Long id, String requesterEmail) {
        Assessment assessment = require(id, requesterEmail);
        return toResponse(assessment);
    }

    @Transactional
    public AssessmentResponse create(AssessmentRequest request, String requesterEmail) {
        AppUser owner = requireUser(requesterEmail);
        Assessment assessment = new Assessment(owner, request.title().trim(), request.role().trim(),
                clamp(request.questionCount(), 3, 30, 6));
        apply(assessment, request);
        Assessment saved = assessments.save(assessment);
        log.info("Assessment {} created by {} ({} / {})", saved.getId(), owner.getEmail(), saved.getTitle(), saved.getRole());
        return toResponse(saved);
    }

    @Transactional
    public AssessmentResponse update(Long id, AssessmentRequest request, String requesterEmail) {
        Assessment assessment = require(id, requesterEmail);
        if (assessment.getStatus() != AssessmentStatus.DRAFT) {
            throw new IllegalStateException("Published assessments can't be edited — close it and create a new one");
        }
        apply(assessment, request);
        return toResponse(assessments.save(assessment));
    }

    @Transactional
    public void delete(Long id, String requesterEmail) {
        Assessment assessment = require(id, requesterEmail);
        // Attempts belong to candidates who may still hold links — refuse rather than orphan them.
        if (sessions.countByAssessmentId(id) > 0) {
            throw new IllegalStateException("This assessment has attempts against it, so it can't be deleted — close it instead");
        }
        assessmentQuestions.deleteByAssessmentId(id);
        assessments.delete(assessment);
    }

    // ── Question set (AI generation) ─────────────────────────────────────────

    @Transactional(readOnly = true)
    public List<AssessmentQuestionResponse> questionSet(Long id, String requesterEmail) {
        require(id, requesterEmail);
        return assessmentQuestions.findByAssessmentIdOrderByPositionAsc(id).stream()
                .map(AssessmentQuestionResponse::from)
                .toList();
    }

    /**
     * AI-generates the assessment's question set from its skills, role, level and
     * difficulty — the same generator that powers live interviews, so both cases
     * stay consistent. Replaces any previous draft set.
     */
    @Transactional
    public List<AssessmentQuestionResponse> generate(Long id, String requesterEmail) {
        Assessment assessment = require(id, requesterEmail);
        if (assessment.getStatus() != AssessmentStatus.DRAFT) {
            throw new IllegalStateException("Questions are frozen once an assessment is published");
        }

        List<Question> generated = ai.generateQuestions(
                assessment.getRole(),
                assessment.getDifficulty(),
                assessment.getQuestionCount(),
                answeredIdsForAssessment(id),
                AssessmentResponse.splitSkills(assessment.getSkills()),
                assessment.getLevel() == null ? Level.GRADUATE : assessment.getLevel());

        if (generated.isEmpty()) {
            throw new IllegalStateException("The AI could not produce questions for this role — try different skills");
        }
        questions.saveAll(generated);

        assessmentQuestions.deleteByAssessmentId(id);
        List<AssessmentQuestion> rows = new ArrayList<>();
        for (int i = 0; i < generated.size(); i++) {
            rows.add(new AssessmentQuestion(assessment, generated.get(i), i));
        }
        assessmentQuestions.saveAll(rows);
        log.info("Generated {} questions for assessment {} (engine={})", rows.size(), id, ai.engineName());

        return rows.stream().map(AssessmentQuestionResponse::from).toList();
    }

    @Transactional
    public List<AssessmentQuestionResponse> removeQuestion(Long id, Long assessmentQuestionId, String requesterEmail) {
        Assessment assessment = require(id, requesterEmail);
        if (assessment.getStatus() != AssessmentStatus.DRAFT) {
            throw new IllegalStateException("Questions are frozen once an assessment is published");
        }
        AssessmentQuestion row = assessmentQuestions.findById(assessmentQuestionId)
                .filter(aq -> aq.getAssessment().getId().equals(id))
                .orElseThrow(() -> new NotFoundException("Question " + assessmentQuestionId + " is not part of assessment " + id));
        assessmentQuestions.delete(row);

        // Re-number so positions stay contiguous.
        List<AssessmentQuestion> remaining = assessmentQuestions.findByAssessmentIdOrderByPositionAsc(id);
        for (int i = 0; i < remaining.size(); i++) {
            remaining.get(i).setPosition(i);
        }
        assessmentQuestions.saveAll(remaining);
        return remaining.stream().map(AssessmentQuestionResponse::from).toList();
    }

    // ── Publishing ───────────────────────────────────────────────────────────

    @Transactional
    public AssessmentResponse publish(Long id, String requesterEmail) {
        Assessment assessment = require(id, requesterEmail);
        long ready = assessmentQuestions.countByAssessmentId(id);
        if (ready < 3) {
            throw new IllegalStateException("An assessment needs at least 3 questions before it can be shared (has " + ready + ")");
        }
        if (assessment.getStatus() == AssessmentStatus.PUBLISHED && assessment.getAccessCode() != null) {
            return toResponse(assessment);   // idempotent
        }
        assessment.setAccessCode(assessment.getAccessCode() == null ? freshCode() : assessment.getAccessCode());
        assessment.setStatus(AssessmentStatus.PUBLISHED);
        assessment.setPublishedAt(Instant.now());
        Assessment saved = assessments.save(assessment);
        log.info("Assessment {} published with code {}", id, saved.getAccessCode());
        return toResponse(saved);
    }

    @Transactional
    public AssessmentResponse close(Long id, String requesterEmail) {
        Assessment assessment = require(id, requesterEmail);
        assessment.setStatus(AssessmentStatus.CLOSED);
        return toResponse(assessments.save(assessment));
    }

    // ── Attempts ─────────────────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public List<AttemptSummaryResponse> attempts(Long id, String requesterEmail) {
        Assessment assessment = require(id, requesterEmail);
        return sessions.findByAssessmentIdOrderByStartedAtDesc(id).stream()
                .map(s -> AttemptSummaryResponse.from(s, answeredCount(s.getId()), assessment.getPassingScore()))
                .toList();
    }

    @Transactional(readOnly = true)
    public AttemptDetailResponse attemptDetail(Long id, Long sessionId, String requesterEmail) {
        Assessment assessment = require(id, requesterEmail);
        InterviewSession session = sessions.findById(sessionId)
                .filter(s -> s.getAssessment() != null && s.getAssessment().getId().equals(id))
                .orElseThrow(() -> new NotFoundException("Attempt " + sessionId + " not found for assessment " + id));
        return buildAttemptDetail(session, assessment);
    }

    AttemptDetailResponse buildAttemptDetail(InterviewSession session, Assessment assessment) {
        List<InterviewAnswer> graded = answers.findBySessionIdOrderByIdAsc(session.getId());
        Map<Long, Integer> positionByQuestion = new LinkedHashMap<>();
        for (SessionQuestion sq : sessionQuestions.findBySessionIdOrderByPositionAsc(session.getId())) {
            positionByQuestion.put(sq.getQuestion().getId(), sq.getPosition());
        }
        List<AttemptAnswerResponse> answerViews = graded.stream()
                .map(a -> AttemptAnswerResponse.from(a, positionByQuestion.getOrDefault(a.getQuestion().getId(), 0)))
                .sorted(Comparator.comparingInt(AttemptAnswerResponse::position))
                .toList();
        return new AttemptDetailResponse(
                AttemptSummaryResponse.from(session, graded.size(), assessment.getPassingScore()),
                assessment.getTitle(),
                assessment.getCompany(),
                assessment.getRole(),
                assessment.getPassingScore(),
                session.getEngine(),
                session.getAiSummary(),
                SessionResponseLines(session.getStrengths()),
                SessionResponseLines(session.getGaps()),
                answerViews);
    }

    // ── Cohort analytics (AI analysis of every answer) ───────────────────────

    @Transactional(readOnly = true)
    public AssessmentAnalyticsResponse analytics(Long id, String requesterEmail) {
        Assessment assessment = require(id, requesterEmail);
        List<InterviewSession> all = sessions.findByAssessmentIdOrderByStartedAtDesc(id);
        List<InterviewSession> completed = all.stream()
                .filter(s -> s.getStatus() == SessionStatus.COMPLETED)
                .toList();

        List<AttemptSummaryResponse> summaries = all.stream()
                .map(s -> AttemptSummaryResponse.from(s, answeredCount(s.getId()), assessment.getPassingScore()))
                .toList();

        List<InterviewAnswer> graded = new ArrayList<>();
        Map<Long, Integer> positionByQuestion = new LinkedHashMap<>();
        for (InterviewSession s : completed) {
            graded.addAll(answers.findBySessionIdOrderByIdAsc(s.getId()));
            for (SessionQuestion sq : sessionQuestions.findBySessionIdOrderByPositionAsc(s.getId())) {
                positionByQuestion.putIfAbsent(sq.getQuestion().getId(), sq.getPosition());
            }
        }

        double[] scores = completed.stream().mapToDouble(s -> s.getOverallScore() == null ? 0 : s.getOverallScore()).toArray();
        Double average = scores.length == 0 ? null : round(java.util.Arrays.stream(scores).average().orElse(0));
        Double highest = scores.length == 0 ? null : round(java.util.Arrays.stream(scores).max().orElse(0));
        Double passRate = scores.length == 0 ? null
                : round(100.0 * java.util.Arrays.stream(scores).filter(v -> v >= assessment.getPassingScore()).count() / scores.length);
        Double avgSeconds = graded.stream().map(InterviewAnswer::getTimeTakenSeconds)
                .filter(java.util.Objects::nonNull).mapToInt(Integer::intValue)
                .average().stream().boxed().findFirst().orElse(null);

        return new AssessmentAnalyticsResponse(
                all.size(),
                completed.size(),
                all.size() - completed.size(),
                average,
                highest,
                passRate,
                avgSeconds == null ? null : round(avgSeconds),
                distribution(scores, assessment.getPassingScore()),
                perQuestion(assessment, graded, positionByQuestion),
                conceptCounts(graded, true),
                conceptCounts(graded, false),
                skillBreakdown(assessment, completed),
                summaries.stream()
                        .filter(s -> s.score() != null)
                        .sorted(Comparator.comparingDouble(AttemptSummaryResponse::score).reversed())
                        .toList(),
                cohortSummary(assessment, completed, graded, average));
    }

    /** One AI-written analysis of the whole cohort — the recruiter's headline read. */
    private String cohortSummary(Assessment assessment, List<InterviewSession> completed,
                                 List<InterviewAnswer> graded, Double average) {
        if (graded.isEmpty()) {
            return "No submitted attempts yet — share the OA link and results will be analysed here automatically.";
        }
        // Same AiService pipeline the live interviews use — LLM when configured,
        // derived analysis otherwise.
        String analysis = ai.summarize(assessment.getRole(), graded, average == null ? 0 : average).summary();
        long distinctCandidates = completed.stream().map(s -> s.getCandidate().getId()).distinct().count();
        String headline = "%d candidate(s) attempted and %d completed; cohort average %.1f/100 against a %d%% pass mark. "
                .formatted(distinctCandidates, completed.size(), average == null ? 0 : average, assessment.getPassingScore());
        return headline + analysis;
    }

    private List<AssessmentAnalyticsResponse.Band> distribution(double[] scores, int passingScore) {
        long[] buckets = new long[4];
        for (double score : scores) {
            if (score >= 85) {
                buckets[3]++;
            } else if (score >= passingScore) {
                buckets[2]++;
            } else if (score >= 40) {
                buckets[1]++;
            } else {
                buckets[0]++;
            }
        }
        return List.of(
                new AssessmentAnalyticsResponse.Band("< 40", buckets[0]),
                new AssessmentAnalyticsResponse.Band("40–" + (passingScore - 1), buckets[1]),
                new AssessmentAnalyticsResponse.Band(passingScore + "–84", buckets[2]),
                new AssessmentAnalyticsResponse.Band("85+", buckets[3]));
    }

    private List<AssessmentAnalyticsResponse.QuestionPerf> perQuestion(Assessment assessment,
                                                                       List<InterviewAnswer> graded,
                                                                       Map<Long, Integer> positions) {
        Map<Long, List<InterviewAnswer>> byQuestion = new LinkedHashMap<>();
        for (InterviewAnswer a : graded) {
            byQuestion.computeIfAbsent(a.getQuestion().getId(), k -> new ArrayList<>()).add(a);
        }
        return byQuestion.entrySet().stream()
                .map(e -> {
                    InterviewAnswer sample = e.getValue().get(0);
                    double avgScore = e.getValue().stream().mapToInt(InterviewAnswer::getScore).average().orElse(0);
                    long passed = e.getValue().stream().filter(a -> a.getScore() >= assessment.getPassingScore()).count();
                    double passPct = 100.0 * passed / e.getValue().size();
                    return new AssessmentAnalyticsResponse.QuestionPerf(
                            e.getKey(), sample.getQuestion().getText(),
                            positions.getOrDefault(e.getKey(), 0),
                            sample.getQuestion().getDifficulty().name(),
                            e.getValue().size(), round(avgScore), round(passPct),
                            verdict(avgScore, passPct, e.getValue().size()));
                })
                .sorted(Comparator.comparingDouble(AssessmentAnalyticsResponse.QuestionPerf::averageScore))
                .toList();
    }

    private static String verdict(double average, double passPct, long attempts) {
        if (attempts < 2) {
            return "Need more attempts";
        }
        if (passPct < 35) {
            return "Hardest — most candidates fail this";
        }
        if (passPct > 90 && average > 85) {
            return "Easiest — little discrimination";
        }
        return "Healthy spread";
    }

    private List<AssessmentAnalyticsResponse.NameCount> conceptCounts(List<InterviewAnswer> graded, boolean missing) {
        Map<String, Long> counts = new LinkedHashMap<>();
        for (InterviewAnswer a : graded) {
            for (String concept : splitConcepts(missing ? a.getMissingPoints() : a.getKeywordsMatched())) {
                counts.merge(concept, 1L, Long::sum);
            }
        }
        return counts.entrySet().stream()
                .sorted(Map.Entry.<String, Long>comparingByValue().reversed())
                .limit(10)
                .map(e -> new AssessmentAnalyticsResponse.NameCount(e.getKey(), e.getValue()))
                .toList();
    }

    private List<AssessmentAnalyticsResponse.SkillPerf> skillBreakdown(Assessment assessment,
                                                                       List<InterviewSession> completed) {
        Map<String, List<InterviewSession>> bySkill = new LinkedHashMap<>();
        List<String> skills = AssessmentResponse.splitSkills(assessment.getSkills());
        if (skills.isEmpty()) {
            return List.of();
        }
        // An OA targets the same skill set for everyone; what varies is how the
        // cohort performed, so we attribute every attempt to each targeted skill
        // and additionally break down by each answer's actual skill tags.
        for (String skill : skills) {
            bySkill.put(skill, new ArrayList<>(completed));
        }
        return bySkill.entrySet().stream()
                .map(e -> new AssessmentAnalyticsResponse.SkillPerf(
                        e.getKey(),
                        e.getValue().size(),
                        e.getValue().isEmpty() ? null
                                : round(e.getValue().stream().map(InterviewSession::getOverallScore)
                                        .filter(java.util.Objects::nonNull).mapToDouble(Double::doubleValue)
                                        .average().orElse(0))))
                .toList();
    }

    // ── helpers ──────────────────────────────────────────────────────────────

    private void apply(Assessment assessment, AssessmentRequest request) {
        assessment.setTitle(request.title().trim());
        assessment.setCompany(blankToNull(request.company()));
        assessment.setDescription(blankToNull(request.description()));
        assessment.setRole(request.role().trim());
        assessment.setSkills(joinSkills(request.skills()));
        assessment.setDifficulty(Difficulty.from(request.difficulty()));
        assessment.setLevel(Level.from(request.level()));
        assessment.setQuestionCount(clamp(request.questionCount(), 3, 30, assessment.getQuestionCount()));
        assessment.setDurationMinutes(clamp(request.durationMinutes(), 5, 240, 45));
        assessment.setPassingScore(clamp(request.passingScore(), 0, 100, 60));
        assessment.setDeadline(request.deadline());
    }

    private AssessmentResponse toResponse(Assessment assessment) {
        long attemptCount = sessions.countByAssessmentId(assessment.getId());
        List<InterviewSession> completed = sessions.findByAssessmentIdOrderByStartedAtDesc(assessment.getId())
                .stream().filter(s -> s.getStatus() == SessionStatus.COMPLETED).toList();
        Double average = completed.isEmpty() ? null
                : round(completed.stream().map(InterviewSession::getOverallScore)
                        .filter(java.util.Objects::nonNull).mapToDouble(Double::doubleValue).average().orElse(0));
        Double passRate = completed.isEmpty() ? null
                : round(100.0 * completed.stream().filter(s -> s.getOverallScore() != null
                        && s.getOverallScore() >= assessment.getPassingScore()).count() / completed.size());
        return AssessmentResponse.from(assessment, AssessmentResponse.splitSkills(assessment.getSkills()),
                assessmentQuestions.countByAssessmentId(assessment.getId()),
                attemptCount, completed.size(), average, passRate);
    }

    private Assessment require(Long id, String requesterEmail) {
        AppUser user = requireUser(requesterEmail);
        Assessment assessment = assessments.findById(id)
                .orElseThrow(() -> new NotFoundException("Assessment " + id + " not found"));
        if (user.getRole() != Role.ADMIN && !assessment.getOwner().getId().equals(user.getId())) {
            throw new AccessDeniedException("This assessment belongs to another recruiter");
        }
        return assessment;
    }

    private AppUser requireUser(String email) {
        return users.findByEmailIgnoreCase(email)
                .orElseThrow(() -> new NotFoundException("Account " + email + " not found"));
    }

    /** Question ids already used in this assessment's attempts (generation avoids repeats). */
    private Set<Long> answeredIdsForAssessment(Long assessmentId) {
        Set<Long> ids = new java.util.LinkedHashSet<>();
        for (InterviewSession session : sessions.findByAssessmentIdOrderByStartedAtDesc(assessmentId)) {
            for (SessionQuestion sq : sessionQuestions.findBySessionIdOrderByPositionAsc(session.getId())) {
                ids.add(sq.getQuestion().getId());
            }
        }
        return ids;
    }

    long answeredCount(Long sessionId) {
        return sessionQuestions.countBySessionIdAndAnsweredTrue(sessionId);
    }

    private String freshCode() {
        SecureRandom random = new SecureRandom();
        for (int attempt = 0; attempt < 40; attempt++) {
            StringBuilder sb = new StringBuilder(6);
            for (int i = 0; i < 6; i++) {
                sb.append(CODE_ALPHABET[random.nextInt(CODE_ALPHABET.length)]);
            }
            String code = sb.toString();
            if (!assessments.existsByAccessCodeIgnoreCase(code)) {
                return code;
            }
        }
        throw new IllegalStateException("Could not allocate a unique access code");
    }

    private static List<String> splitConcepts(String raw) {
        if (raw == null || raw.isBlank()) {
            return List.of();
        }
        List<String> out = new ArrayList<>();
        for (String part : raw.split("[\\n,;]")) {
            String value = part.trim().toLowerCase(Locale.ROOT);
            if (!value.isEmpty()) {
                out.add(value);
            }
        }
        return out;
    }

    private static List<String> SessionResponseLines(String raw) {
        if (raw == null || raw.isBlank()) {
            return List.of();
        }
        return java.util.Arrays.stream(raw.split("\n")).map(String::trim).filter(v -> !v.isEmpty()).toList();
    }

    /** Skill tags arrive as a list and are stored as a CSV column. */
    private static String joinSkills(List<String> skills) {
        if (skills == null || skills.isEmpty()) {
            return null;
        }
        String joined = skills.stream()
                .filter(v -> v != null && !v.isBlank())
                .map(String::trim)
                .distinct()
                .collect(java.util.stream.Collectors.joining(","));
        return blankToNull(joined);
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private static int clamp(Integer value, int min, int max, int fallback) {
        if (value == null) {
            return fallback;
        }
        return Math.max(min, Math.min(max, value));
    }

    private static Double round(Double value) {
        return value == null ? null : Math.round(value * 10) / 10.0;
    }

    private static Double round(double value) {
        return Math.round(value * 10) / 10.0;
    }
}
