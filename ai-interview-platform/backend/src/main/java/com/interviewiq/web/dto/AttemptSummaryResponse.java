package com.interviewiq.web.dto;

import com.interviewiq.domain.InterviewSession;
import java.time.Instant;

/** One candidate's attempt at an assessment, as listed for the recruiter. */
public record AttemptSummaryResponse(
        Long sessionId,
        Long candidateId,
        String candidateName,
        String candidateEmail,
        String status,
        int answered,
        int total,
        Double score,
        Boolean passed,
        Instant startedAt,
        Instant completedAt,
        Instant expiresAt,
        boolean timedOut
) {
    public static AttemptSummaryResponse from(InterviewSession s, long answered, int passingScore) {
        Double score = s.getOverallScore();
        boolean completed = score != null && s.getCompletedAt() != null;
        boolean timedOut = !completed && s.getExpiresAt() != null && Instant.now().isAfter(s.getExpiresAt());
        return new AttemptSummaryResponse(
                s.getId(),
                s.getCandidate().getId(),
                s.getCandidate().getName(),
                s.getCandidate().getEmail(),
                s.getStatus().name(),
                (int) answered,
                s.getQuestionCount(),
                score,
                completed ? score >= passingScore : null,
                s.getStartedAt(),
                s.getCompletedAt(),
                s.getExpiresAt(),
                timedOut);
    }
}
