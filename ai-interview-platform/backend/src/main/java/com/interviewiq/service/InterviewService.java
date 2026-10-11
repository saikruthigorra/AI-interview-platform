package com.interviewiq.service;

import com.interviewiq.ai.AiService;
import com.interviewiq.ai.EvaluationResult;
import com.interviewiq.ai.SessionSummary;
import com.interviewiq.domain.Candidate;
import com.interviewiq.domain.Difficulty;
import com.interviewiq.domain.InterviewAnswer;
import com.interviewiq.domain.InterviewSession;
import com.interviewiq.domain.Level;
import com.interviewiq.domain.Question;
import com.interviewiq.domain.SessionQuestion;
import com.interviewiq.domain.SessionStatus;
import com.interviewiq.repo.CandidateRepository;
import com.interviewiq.repo.InterviewAnswerRepository;
import com.interviewiq.repo.InterviewFeedbackRepository;
import com.interviewiq.repo.InterviewSessionRepository;
import com.interviewiq.repo.QuestionRepository;
import com.interviewiq.repo.SessionQuestionRepository;
import com.interviewiq.web.NotFoundException;
import com.interviewiq.web.dto.AnswerRequest;
import com.interviewiq.web.dto.AnswerView;
import com.interviewiq.web.dto.QuestionView;
import com.interviewiq.web.dto.SessionDetailResponse;
import com.interviewiq.web.dto.SessionResponse;
import com.interviewiq.web.dto.SessionStartRequest;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * The interview engine: start a session (question set generated from the
 * student's skills + target role), grade each answer as it is submitted, then
 * roll everything up into a scored report.
 */
@Service
public class InterviewService {

    private static final Logger log = LoggerFactory.getLogger(InterviewService.class);

    public static final int MIN_QUESTIONS = 3;
    public static final int MAX_QUESTIONS = 12;
    public static final int DEFAULT_QUESTIONS = 5;

    private final InterviewSessionRepository sessions;
    private final InterviewAnswerRepository answers;
    private final InterviewFeedbackRepository feedback;
    private final SessionQuestionRepository sessionQuestions;
    private final CandidateRepository candidates;
    private final QuestionRepository questions;
    private final AiService ai;

    public InterviewService(InterviewSessionRepository sessions,
                            InterviewAnswerRepository answers,
                            InterviewFeedbackRepository feedback,
                            SessionQuestionRepository sessionQuestions,
                            CandidateRepository candidates,
                            QuestionRepository questions,
                            AiService ai) {
        this.sessions = sessions;
        this.answers = answers;
        this.feedback = feedback;
        this.sessionQuestions = sessionQuestions;
        this.candidates = candidates;
        this.questions = questions;
        this.ai = ai;
    }

    @Transactional(readOnly = true)
    public List<SessionResponse> list() {
        return sessions.findAllByOrderByStartedAtDesc().stream()
                .map(s -> SessionResponse.from(s, sessionQuestions.countBySessionIdAndAnsweredTrue(s.getId())))
                .toList();
    }

    @Transactional(readOnly = true)
    public SessionDetailResponse detail(Long id) {
        InterviewSession session = require(id);
        return buildDetail(session);
    }

    /** Starts a new session — or hands back the student's unfinished one. */
    @Transactional
    public SessionDetailResponse start(SessionStartRequest request) {
        Candidate candidate = candidates.findById(request.candidateId())
                .orElseThrow(() -> new NotFoundException("Candidate " + request.candidateId() + " not found"));

        Optional<InterviewSession> inProgress = sessions.findByCandidateIdOrderByStartedAtDesc(candidate.getId())
                .stream()
                .filter(s -> s.getStatus() == SessionStatus.IN_PROGRESS)
                .findFirst();
        if (inProgress.isPresent()) {
            log.info("Resuming in-progress session {} for candidate {}", inProgress.get().getId(), candidate.getId());
            return buildDetail(inProgress.get());
        }

        String role = request.role().trim();
        Difficulty difficulty = Difficulty.from(request.difficulty());
        // Skills: request override wins, otherwise the student's stored profile.
        List<String> skills = request.skills() != null && !request.skills().isBlank()
                ? splitCsv(request.skills())
                : splitCsv(candidate.getSkills());
        Level level = request.level() != null && !request.level().isBlank()
                ? Level.from(request.level())
                : (candidate.getLevel() == null ? Level.STUDENT : candidate.getLevel());
        int count = Math.max(MIN_QUESTIONS, Math.min(MAX_QUESTIONS,
                request.questionCount() == null ? DEFAULT_QUESTIONS : request.questionCount()));

        List<Question> generated = ai.generateQuestions(role, difficulty, count,
                previouslyAnswered(candidate.getId()), skills, level);
        if (generated.isEmpty()) {
            throw new IllegalStateException("No questions could be generated for role '" + role + "'");
        }
        questions.saveAll(generated);

        InterviewSession session = new InterviewSession(candidate, role, difficulty, generated.size());
        session.setEngine(ai.engineName());
        session.setLevel(level);
        session.setFocusSkills(skills.isEmpty() ? null : String.join(",", skills));
        sessions.save(session);

        List<SessionQuestion> rows = new ArrayList<>();
        for (int i = 0; i < generated.size(); i++) {
            rows.add(new SessionQuestion(session, generated.get(i), i));
        }
        sessionQuestions.saveAll(rows);

        log.info("Started session {} for {} ({} questions, engine={}, focus skills: {})",
                session.getId(), candidate.getEmail(), generated.size(), session.getEngine(),
                skills.isEmpty() ? "none declared" : String.join("/", skills));
        return buildDetail(session);
    }

    @Transactional
    public AnswerView submitAnswer(Long sessionId, AnswerRequest request) {
        InterviewSession session = require(sessionId);
        if (session.getStatus() == SessionStatus.COMPLETED) {
            throw new IllegalStateException("This interview has already been completed");
        }
        SessionQuestion next = sessionQuestions
                .findFirstBySessionIdAndAnsweredFalseOrderByPositionAsc(sessionId)
                .orElseThrow(() -> new IllegalStateException("All questions have been answered — finish the interview"));

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

        boolean complete = sessionQuestions.countBySessionIdAndAnsweredTrue(sessionId) >= session.getQuestionCount();
        if (complete) {
            completeSession(session);
        }
        return AnswerView.from(answer, complete);
    }

    @Transactional
    public SessionDetailResponse finish(Long sessionId) {
        InterviewSession session = require(sessionId);
        if (session.getStatus() == SessionStatus.IN_PROGRESS) {
            completeSession(session);
        }
        return buildDetail(session);
    }

    @Transactional
    public void delete(Long sessionId) {
        InterviewSession session = require(sessionId);
        feedback.findBySessionId(sessionId).ifPresent(feedback::delete);
        answers.deleteBySessionId(sessionId);
        sessionQuestions.deleteBySessionId(sessionId);
        sessions.delete(session);
    }

    // ── internals ────────────────────────────────────────────────────────────

    private void completeSession(InterviewSession session) {
        List<InterviewAnswer> graded = answers.findBySessionIdOrderByIdAsc(session.getId());
        double average = graded.stream().mapToInt(InterviewAnswer::getScore).average().orElse(0);
        double rounded = Math.round(average * 10) / 10.0;

        SessionSummary summary = ai.summarize(session.getRole(), graded, rounded);
        session.setOverallScore(rounded);
        session.setAiSummary(summary.summary());
        session.setStrengths(summary.strengthsText());
        session.setGaps(summary.gapsText());
        session.setStatus(SessionStatus.COMPLETED);
        session.setCompletedAt(Instant.now());
        sessions.save(session);
        log.info("Completed session {} with score {}", session.getId(), rounded);
    }

    private SessionDetailResponse buildDetail(InterviewSession session) {
        List<QuestionView> questionViews = sessionQuestions
                .findBySessionIdOrderByPositionAsc(session.getId()).stream()
                .map(QuestionView::from)
                .toList();
        boolean completed = session.getStatus() == SessionStatus.COMPLETED;
        List<AnswerView> answerViews = answers.findBySessionIdOrderByIdAsc(session.getId()).stream()
                .map(a -> AnswerView.from(a, completed))
                .toList();
        SessionResponse summary = SessionResponse.from(session,
                questionViews.stream().filter(QuestionView::answered).count());
        return new SessionDetailResponse(summary, questionViews, answerViews);
    }

    private InterviewSession require(Long id) {
        return sessions.findById(id)
                .orElseThrow(() -> new NotFoundException("Interview session " + id + " not found"));
    }

    /** Question ids the student has already seen — repeat sessions stay fresh. */
    private Set<Long> previouslyAnswered(Long candidateId) {
        Set<Long> seen = new HashSet<>();
        for (InterviewSession past : sessions.findByCandidateIdOrderByStartedAtDesc(candidateId)) {
            for (SessionQuestion sq : sessionQuestions.findBySessionIdOrderByPositionAsc(past.getId())) {
                if (sq.isAnswered()) {
                    seen.add(sq.getQuestion().getId());
                }
            }
        }
        return seen;
    }

    static List<String> splitCsv(String raw) {
        if (raw == null || raw.isBlank()) {
            return List.of();
        }
        return Arrays.stream(raw.split(",")).map(String::trim)
                .filter(s -> !s.isEmpty()).distinct().toList();
    }

    private static String join(List<String> values) {
        return values == null || values.isEmpty() ? null : String.join("\n", values);
    }
}
