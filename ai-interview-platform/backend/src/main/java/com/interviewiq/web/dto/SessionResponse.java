package com.interviewiq.web.dto;

import com.interviewiq.domain.InterviewSession;
import java.time.Instant;
import java.util.Arrays;
import java.util.List;

public record SessionResponse(
        Long id,
        Long candidateId,
        String candidateName,
        String role,
        String difficulty,
        String level,
        List<String> focusSkills,
        Long assessmentId,
        String assessmentTitle,
        String status,
        int questionCount,
        long answeredCount,
        Double overallScore,
        String engine,
        String aiSummary,
        List<String> strengths,
        List<String> gaps,
        Instant startedAt,
        Instant completedAt
) {
    public static SessionResponse from(InterviewSession s, long answeredCount) {
        return new SessionResponse(
                s.getId(),
                s.getCandidate().getId(),
                s.getCandidate().getName(),
                s.getRole(),
                s.getDifficulty().name(),
                s.getLevel() == null ? null : s.getLevel().name(),
                splitSkills(s.getFocusSkills()),
                s.getAssessment() == null ? null : s.getAssessment().getId(),
                s.getAssessment() == null ? null : s.getAssessment().getTitle(),
                s.getStatus().name(),
                s.getQuestionCount(),
                answeredCount,
                s.getOverallScore(),
                s.getEngine(),
                s.getAiSummary(),
                lines(s.getStrengths()),
                lines(s.getGaps()),
                s.getStartedAt(),
                s.getCompletedAt());
    }

    static List<String> splitSkills(String raw) {
        if (raw == null || raw.isBlank()) {
            return List.of();
        }
        return Arrays.stream(raw.split(",")).map(String::trim).filter(v -> !v.isEmpty()).toList();
    }

    static List<String> lines(String value) {
        if (value == null || value.isBlank()) {
            return List.of();
        }
        return Arrays.stream(value.split("\n")).map(String::trim).filter(v -> !v.isEmpty()).toList();
    }
}
