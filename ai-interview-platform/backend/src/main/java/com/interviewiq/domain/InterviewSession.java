package com.interviewiq.domain;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/** One run of an interview for one candidate. */
@Entity
@Table(name = "interview_session")
public class InterviewSession {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "candidate_id", nullable = false)
    private Candidate candidate;

    @Column(name = "role_name", nullable = false, length = 120)
    private String role;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private Difficulty difficulty = Difficulty.MEDIUM;

    @Enumerated(EnumType.STRING)
    @Column(length = 16)
    private Level level = Level.STUDENT;

    /** Comma-separated skills this session was generated against. */
    @Column(name = "focus_skills", length = 500)
    private String focusSkills;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private SessionStatus status = SessionStatus.IN_PROGRESS;

    /** Set when this session is an attempt at a recruiter-hosted assessment. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "assessment_id")
    private Assessment assessment;

    /**
     * Opaque capability token for anonymous OA attempts: the candidate holds it
     * instead of an account. Null for normal (authenticated) interviews.
     */
    @Column(name = "attempt_token", unique = true, length = 64)
    private String attemptToken;

    /** Attempt deadline (started + the assessment's duration). */
    @Column(name = "expires_at")
    private Instant expiresAt;

    @Column(name = "question_count", nullable = false)
    private int questionCount;

    @Column(name = "overall_score")
    private Double overallScore;

    /** Which engine produced the evaluations: "heuristic" or the LLM model name. */
    @Column(length = 64)
    private String engine;

    @JdbcTypeCode(SqlTypes.LONGVARCHAR)
    @Column(name = "ai_summary")
    private String aiSummary;

    @JdbcTypeCode(SqlTypes.LONGVARCHAR)
    private String strengths;

    @JdbcTypeCode(SqlTypes.LONGVARCHAR)
    private String gaps;

    @Column(name = "started_at", nullable = false)
    private Instant startedAt = Instant.now();

    @Column(name = "completed_at")
    private Instant completedAt;

    @OneToMany(mappedBy = "session", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    private List<InterviewAnswer> answers = new ArrayList<>();

    @OneToMany(mappedBy = "session", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    private List<SessionQuestion> questionSet = new ArrayList<>();

    public InterviewSession() {
    }

    public InterviewSession(Candidate candidate, String role, Difficulty difficulty, int questionCount) {
        this.candidate = candidate;
        this.role = role;
        this.difficulty = difficulty;
        this.questionCount = questionCount;
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Candidate getCandidate() {
        return candidate;
    }

    public void setCandidate(Candidate candidate) {
        this.candidate = candidate;
    }

    public String getRole() {
        return role;
    }

    public void setRole(String role) {
        this.role = role;
    }

    public Difficulty getDifficulty() {
        return difficulty;
    }

    public void setDifficulty(Difficulty difficulty) {
        this.difficulty = difficulty;
    }

    public Assessment getAssessment() {
        return assessment;
    }

    public void setAssessment(Assessment assessment) {
        this.assessment = assessment;
    }

    public String getAttemptToken() {
        return attemptToken;
    }

    public void setAttemptToken(String attemptToken) {
        this.attemptToken = attemptToken;
    }

    public Instant getExpiresAt() {
        return expiresAt;
    }

    public void setExpiresAt(Instant expiresAt) {
        this.expiresAt = expiresAt;
    }

    public SessionStatus getStatus() {
        return status;
    }

    public void setStatus(SessionStatus status) {
        this.status = status;
    }

    public int getQuestionCount() {
        return questionCount;
    }

    public void setQuestionCount(int questionCount) {
        this.questionCount = questionCount;
    }

    public Level getLevel() {
        return level;
    }

    public void setLevel(Level level) {
        this.level = level;
    }

    public String getFocusSkills() {
        return focusSkills;
    }

    public void setFocusSkills(String focusSkills) {
        this.focusSkills = focusSkills;
    }

    public Double getOverallScore() {
        return overallScore;
    }

    public void setOverallScore(Double overallScore) {
        this.overallScore = overallScore;
    }

    public String getEngine() {
        return engine;
    }

    public void setEngine(String engine) {
        this.engine = engine;
    }

    public String getAiSummary() {
        return aiSummary;
    }

    public void setAiSummary(String aiSummary) {
        this.aiSummary = aiSummary;
    }

    public String getStrengths() {
        return strengths;
    }

    public void setStrengths(String strengths) {
        this.strengths = strengths;
    }

    public String getGaps() {
        return gaps;
    }

    public void setGaps(String gaps) {
        this.gaps = gaps;
    }

    public Instant getStartedAt() {
        return startedAt;
    }

    public void setStartedAt(Instant startedAt) {
        this.startedAt = startedAt;
    }

    public Instant getCompletedAt() {
        return completedAt;
    }

    public void setCompletedAt(Instant completedAt) {
        this.completedAt = completedAt;
    }

    public List<InterviewAnswer> getAnswers() {
        return answers;
    }

    public void setAnswers(List<InterviewAnswer> answers) {
        this.answers = answers;
    }

    public void addAnswer(InterviewAnswer answer) {
        answers.add(answer);
        answer.setSession(this);
    }
}
