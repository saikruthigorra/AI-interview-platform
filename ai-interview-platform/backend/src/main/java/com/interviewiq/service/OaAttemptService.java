package com.interviewiq.service;

import com.interviewiq.ai.AiService;
import com.interviewiq.ai.EvaluationResult;
import com.interviewiq.ai.SessionSummary;
import com.interviewiq.domain.Assessment;
import com.interviewiq.domain.AssessmentQuestion;
import com.interviewiq.domain.AssessmentStatus;
import com.interviewiq.domain.Candidate;
import com.interviewiq.domain.InterviewAnswer;
import com.interviewiq.domain.InterviewSession;
import com.interviewiq.domain.SessionQuestion;
import com.interviewiq.domain.SessionStatus;
import com.interviewiq.repo.AssessmentQuestionRepository;
import com.interviewiq.repo.AssessmentRepository;
import com.interviewiq.repo.CandidateRepository;
import com.interviewiq.repo.InterviewAnswerRepository;
import com.interviewiq.repo.InterviewSessionRepository;
import com.interviewiq.repo.SessionQuestionRepository;
import com.interviewiq.web.NotFoundException;
import com.interviewiq.web.dto.AttemptAnswerResponse;
import com.interviewiq.web.dto.AttemptStartRequest;
import com.interviewiq.web.dto.OaAnswerAckResponse;
import com.interviewiq.web.dto.OaAnswerRequest;
import com.interviewiq.web.dto.PublicAssessmentResponse;
import com.interviewiq.web.dto.PublicAttemptResponse;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Candidate-facing side of the OA module — no account required.
 *
 * The candidate holds an opaque attempt token (a capability). The AI grades each
 * answer the moment it is submitted, but scores, feedback and model answers stay
 * hidden until the attempt is submitted, so the analysis can't be used to game
 * the rest of the paper.
 */
@Service
public class OaAttemptService {

    private static final Logger log = LoggerFactory.getLogger(OaAttemptService.class);

    private final AssessmentRepository assessments;
    private final AssessmentQuestionRepository assessmentQuestions;
    private final CandidateRepository candidates;
    private final InterviewSessionRepository sessions;
    private final SessionQuestionRepository sessionQuestions;
    private final InterviewAnswerRepository answers;
    private final AiService ai;
    private final AssessmentService assessmentService;

    public OaAttemptService(AssessmentRepository assessments,
                            AssessmentQuestionRepository assessmentQuestions,
                            CandidateRepository candidates,
                            InterviewSessionRepository sessions,
                            SessionQuestionRepository sessionQuestions,
                            InterviewAnswerRepository answers,
                            AiService ai,
                            AssessmentService assessmentService) {
        this.assessments = assessments;
        this.assessmentQuestions = assessmentQuestions;
        this.candidates = candidates;
        this.sessions = sessions;
        this.sessionQuestions = sessionQuestions;
        this.answers = answers;
        this.ai = ai;
        this.assessmentService = assessmentService;
    }

    // ── Landing page ─────────────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public PublicAssessmentResponse publicInfo(String code) {
        Assessment assessment = requireByCode(code);
        boolean open = assessment.getStatus() == AssessmentStatus.PUBLISHED
                && (assessment.getDeadline() == null || Instant.now().isBefore(assessment.getDeadline()));
        String reason = assessment.getStatus() == AssessmentStatus.DRAFT ? "This assessment hasn't been published yet"
                : assessment.getStatus() == AssessmentStatus.CLOSED ? "This assessment is closed and no longer accepting attempts"
                : !open ? "The deadline for this assessment has passed"
                : null;
        return new PublicAssessmentResponse(
                assessment.getAccessCode(),
                assessment.getTitle(),
                assessment.getCompany(),
                assessment.getDescription(),
                assessment.getRole(),
                com.interviewiq.web.dto.AssessmentResponse.splitSkills(assessment.getSkills()),
                assessment.getDifficulty().name(),
                assessment.getQuestionCount(),
                assessment.getDurationMinutes(),
                assessment.getPassingScore(),
                assessment.getDeadline(),
                open,
                reason);
    }

    // ── Attempt lifecycle ────────────────────────────────────────────────────

    /** Starts an attempt, or resumes the candidate's unfinished one for this OA. */
    @Transactional
    public PublicAttemptResponse start(String code, AttemptStartRequest request) {
        Assessment assessment = requireByCode(code);
        if (assessment.getStatus() != AssessmentStatus.PUBLISHED) {
            throw new IllegalStateException("This assessment is not accepting attempts right now");
        }
        if (assessment.getDeadline() != null && Instant.now().isAfter(assessment.getDeadline())) {
            throw new IllegalStateException("The deadline for this assessment has passed");
        }

        String email = request.email().trim().toLowerCase(Locale.ROOT);
        Candidate candidate = candidates.findByEmailIgnoreCase(email).orElseGet(() -> {
            Candidate fresh = new Candidate(request.name().trim(), email, null, assessment.getRole());
            fresh.setSkills(assessment.getSkills());
            fresh.setLevel(assessment.getLevel());
            return candidates.save(fresh);
        });

        // One finished attempt per candidate per OA; incomplete ones resume.
        var existing = sessions.findFirstByAssessmentIdAndCandidateIdOrderByStartedAtDesc(assessment.getId(), candidate.getId());
        if (existing.isPresent()) {
            InterviewSession session = existing.get();
            if (session.getStatus() == SessionStatus.COMPLETED) {
                throw new IllegalStateException("You have already submitted this assessment");
            }
            if (session.getExpiresAt() != null && Instant.now().isAfter(session.getExpiresAt())) {
                finishSession(session, true);
                throw new IllegalStateException("Your time for this assessment is up — the attempt has been submitted");
            }
            log.info("Resuming OA attempt {} for {} on assessment {}", session.getId(), mask(email), assessment.getId());
            return toPublicAttempt(session, assessment);
        }

        List<AssessmentQuestion> set = assessmentQuestions.findByAssessmentIdOrderByPositionAsc(assessment.getId());
        if (set.isEmpty()) {
            throw new IllegalStateException("This assessment has no questions yet");
        }

        Instant now = Instant.now();
        InterviewSession session = new InterviewSession(candidate, assessment.getRole(), assessment.getDifficulty(), set.size());
        session.setAssessment(assessment);
        session.setAttemptToken(UUID.randomUUID().toString().replace("-", ""));
        session.setExpiresAt(now.plusSeconds(60L * assessment.getDurationMinutes()));
        session.setEngine(ai.engineName());
        session.setLevel(assessment.getLevel());
        session.setFocusSkills(assessment.getSkills());
        sessions.save(session);

        List<SessionQuestion> rows = new ArrayList<>();
        for (AssessmentQuestion aq : set) {
            rows.add(new SessionQuestion(session, aq.getQuestion(), aq.getPosition()));
        }
        sessionQuestions.saveAll(rows);

        log.info("OA attempt {} started for {} on assessment {} ({} questions, {} min)",
                session.getId(), mask(email), assessment.getId(), set.size(), assessment.getDurationMinutes());
        return toPublicAttempt(session, assessment);
    }

    @Transactional(readOnly = true)
    public PublicAttemptResponse detail(String token) {
        InterviewSession session = requireByToken(token);
        return toPublicAttempt(session, session.getAssessment());
    }

    /** Grades the answer server-side; the candidate only gets an acknowledgement for now. */
    @Transactional
    public OaAnswerAckResponse submitAnswer(String token, OaAnswerRequest request) {
        InterviewSession session = requireByToken(token);
        if (session.getStatus() == SessionStatus.COMPLETED) {
            throw new IllegalStateException("This attempt has already been submitted");
        }
        if (session.getExpiresAt() != null && Instant.now().isAfter(session.getExpiresAt())) {
            finishSession(session, true);
            throw new IllegalStateException("Time is up — your attempt has been submitted with the answers you gave");
        }

        SessionQuestion next = sessionQuestions.findFirstBySessionIdAndAnsweredFalseOrderByPositionAsc(session.getId())
                .orElseThrow(() -> new IllegalStateException("All questions are answered — submit the attempt"));

        EvaluationResult evaluation = ai.evaluate(next.getQuestion(), request.answerText());

        InterviewAnswer answer = new InterviewAnswer();
        answer.setSession(session);
        answer.setQuestion(next.getQuestion());
        answer.setAnswerText(request.answerText().trim());
        answer.setScore(evaluation.score());
        answer.setTechnicalScore(evaluation.technicalScore());
        answer.setDepthScore(evaluation.depthScore());
        answer.setCommunicationScore(evaluation.communicationScore());
        answer.setClarityScore(evaluation.clarityScore());
        answer.setFeedback(evaluation.feedback());
        answer.setStrengths(join(evaluation.strengths()));
        answer.setImprovements(join(evaluation.improvements()));
        answer.setKeywordsMatched(join(evaluation.keywordsMatched()));
        answer.setMissingPoints(join(evaluation.missingPoints()));
        answer.setTimeTakenSeconds(request.timeTakenSeconds());
        answer.setAnsweredAt(Instant.now());
        answers.save(answer);

        next.setAnswered(true);
        sessionQuestions.save(next);

        int answeredTotal = (int) sessionQuestions.countBySessionIdAndAnsweredTrue(session.getId());
        boolean complete = answeredTotal >= session.getQuestionCount();
        if (complete) {
            finishSession(session, false);
        }
        return new OaAnswerAckResponse(true, answeredTotal, session.getQuestionCount(), complete,
                session.getExpiresAt(),
                complete ? "Attempt complete — your analysis is ready"
                        : "Answer recorded. You'll see the AI analysis once you submit.");
    }

    /** Explicit submit (also used when time runs out or the candidate finishes early). */
    @Transactional
    public PublicAttemptResponse submit(String token) {
        InterviewSession session = requireByToken(token);
        if (session.getStatus() != SessionStatus.COMPLETED) {
            finishSession(session, true);
        }
        return toPublicAttempt(session, session.getAssessment());
    }

    // ── internals ────────────────────────────────────────────────────────────

    /** Grades nothing extra here — completeSession already ran the AI analysis on each answer. */
    private void finishSession(InterviewSession session, boolean early) {
        List<InterviewAnswer> graded = answers.findBySessionIdOrderByIdAsc(session.getId());
        double average = graded.stream().mapToInt(InterviewAnswer::getScore).average().orElse(0);
        double rounded = Math.round(average * 10) / 10.0;
        SessionSummary summary = ai.summarize(session.getRole(), graded, rounded);

        session.setOverallScore(graded.isEmpty() ? 0.0 : rounded);
        session.setAiSummary(summary.summary());
        session.setStrengths(summary.strengthsText());
        session.setGaps(summary.gapsText());
        session.setStatus(SessionStatus.COMPLETED);
        session.setCompletedAt(Instant.now());
        sessions.save(session);
        log.info("OA attempt {} submitted{} — score {} ({} answers)",
                session.getId(), early ? " (time up / early)" : "", rounded, graded.size());
    }

    private PublicAttemptResponse toPublicAttempt(InterviewSession session, Assessment assessment) {
        boolean completed = session.getStatus() == SessionStatus.COMPLETED;
        List<SessionQuestion> ordered = sessionQuestions.findBySessionIdOrderByPositionAsc(session.getId());
        List<PublicAttemptResponse.QuestionView> questionViews = ordered.stream()
                .map(sq -> new PublicAttemptResponse.QuestionView(sq.getId(), sq.getPosition(),
                        sq.getQuestion().getId(), sq.getQuestion().getText(), sq.getQuestion().getTopic(),
                        sq.getQuestion().getDifficulty().name(), sq.isAnswered()))
                .toList();

        List<InterviewAnswer> graded = answers.findBySessionIdOrderByIdAsc(session.getId());
        List<PublicAttemptResponse.PublicAnswer> answerViews = new ArrayList<>();
        for (InterviewAnswer answer : graded) {
            Integer position = ordered.stream()
                    .filter(sq -> sq.getQuestion().getId().equals(answer.getQuestion().getId()))
                    .map(SessionQuestion::getPosition)
                    .findFirst().orElse(0);
            // Scores, feedback and model answers are revealed only after submission.
            AttemptAnswerResponse analysis = completed ? AttemptAnswerResponse.from(answer, position) : null;
            answerViews.add(new PublicAttemptResponse.PublicAnswer(
                    position,
                    answer.getQuestion().getId(),
                    answer.getQuestion().getText(),
                    answer.getQuestion().getTopic(),
                    answer.getQuestion().getDifficulty().name(),
                    answer.getAnswerText(),
                    answer.getAnsweredAt(),
                    analysis));
        }

        Double score = completed ? session.getOverallScore() : null;
        Boolean passed = completed && score != null ? score >= assessment.getPassingScore() : null;
        long minutes = assessment.getDurationMinutes();

        return new PublicAttemptResponse(
                session.getAttemptToken(),
                session.getId(),
                assessment.getTitle(),
                assessment.getCompany(),
                session.getCandidate().getName(),
                session.getStatus().name(),
                completed,
                session.getStartedAt(),
                session.getExpiresAt(),
                (int) minutes,
                assessment.getPassingScore(),
                graded.size(),
                session.getQuestionCount(),
                questionViews,
                answerViews,
                score,
                passed,
                completed ? session.getAiSummary() : null,
                completed ? attemptServiceLines(session.getStrengths()) : List.of(),
                completed ? attemptServiceLines(session.getGaps()) : List.of(),
                session.getEngine());
    }

    private Assessment requireByCode(String code) {
        return assessments.findByAccessCodeIgnoreCase(code == null ? "" : code.trim())
                .orElseThrow(() -> new NotFoundException("No assessment found for code " + code));
    }

    private InterviewSession requireByToken(String token) {
        return sessions.findByAttemptToken(token)
                .orElseThrow(() -> new NotFoundException("This attempt link is not valid"));
    }

    private static List<String> attemptServiceLines(String raw) {
        if (raw == null || raw.isBlank()) {
            return List.of();
        }
        return java.util.Arrays.stream(raw.split("\n")).map(String::trim).filter(v -> !v.isEmpty()).toList();
    }

    private static String join(List<String> values) {
        return values == null || values.isEmpty() ? null : String.join("\n", values);
    }

    private static String mask(String email) {
        int at = email.indexOf('@');
        return at <= 1 ? "***" : email.charAt(0) + "***" + email.substring(at);
    }
}
