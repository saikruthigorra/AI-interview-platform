package com.interviewiq.web.dto;

import java.util.List;

/**
 * Cohort analysis for one assessment: how the group performed, which questions
 * and concepts separated candidates, and what the AI makes of the whole cohort.
 */
public record AssessmentAnalyticsResponse(
        long attempts,
        long completed,
        long inProgress,
        Double averageScore,
        Double highestScore,
        Double passRate,
        Double averageSecondsPerAnswer,
        List<Band> distribution,
        List<QuestionPerf> perQuestion,
        List<NameCount> weakestConcepts,
        List<NameCount> strongestConcepts,
        List<SkillPerf> skillBreakdown,
        List<AttemptSummaryResponse> leaderboard,
        String aiCohortSummary
) {
    public record Band(String label, long count) {
    }

    public record QuestionPerf(
            Long questionId,
            String text,
            int position,
            String difficulty,
            long answered,
            Double averageScore,
            Double passRate,
            String verdict
    ) {
    }

    public record NameCount(String name, long count) {
    }

    public record SkillPerf(String skill, long attempts, Double averageScore) {
    }
}
