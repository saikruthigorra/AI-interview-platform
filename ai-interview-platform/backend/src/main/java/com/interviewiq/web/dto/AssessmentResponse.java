package com.interviewiq.web.dto;

import com.interviewiq.domain.Assessment;
import java.time.Instant;
import java.util.Arrays;
import java.util.List;

public record AssessmentResponse(
        Long id,
        String title,
        String company,
        String description,
        String role,
        List<String> skills,
        String difficulty,
        String level,
        int questionCount,
        int durationMinutes,
        int passingScore,
        String status,
        String accessCode,
        /** Share path the recruiter can copy, e.g. /oa/K7P4QZ. */
        String sharePath,
        Instant deadline,
        Instant createdAt,
        Instant publishedAt,
        String ownerName,
        long questionsReady,
        long attemptCount,
        long completedAttempts,
        Double averageScore,
        Double passRate
) {
    public static AssessmentResponse from(Assessment a, List<String> skills, long questionsReady,
                                          long attemptCount, long completedAttempts,
                                          Double averageScore, Double passRate) {
        return new AssessmentResponse(
                a.getId(), a.getTitle(), a.getCompany(), a.getDescription(), a.getRole(), skills,
                a.getDifficulty().name(), a.getLevel() == null ? null : a.getLevel().name(),
                a.getQuestionCount(), a.getDurationMinutes(), a.getPassingScore(),
                a.getStatus().name(), a.getAccessCode(),
                a.getAccessCode() == null ? null : "/oa/" + a.getAccessCode(),
                a.getDeadline(), a.getCreatedAt(), a.getPublishedAt(),
                a.getOwner().getName(), questionsReady, attemptCount, completedAttempts,
                averageScore, passRate);
    }

    public static List<String> splitSkills(String raw) {
        if (raw == null || raw.isBlank()) {
            return List.of();
        }
        return Arrays.stream(raw.split(",")).map(String::trim).filter(s -> !s.isEmpty()).distinct().toList();
    }
}
