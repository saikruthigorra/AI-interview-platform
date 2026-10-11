package com.interviewiq.web.dto;

import java.util.List;

public record StatsResponse(
        long totalCandidates,
        long totalSessions,
        long completedSessions,
        long inProgressSessions,
        long totalQuestions,
        long aiGeneratedQuestions,
        Double averageScore,
        List<RoleStat> byRole,
        List<ScoreBand> scoreBands,
        List<SessionResponse> recent
) {
    public record RoleStat(String role, long sessions, Double averageScore) {
    }

    public record ScoreBand(String label, long count) {
    }
}
