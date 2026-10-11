package com.interviewiq.web.dto;

import java.util.List;

/**
 * Everything the analytics dashboard renders, computed in one pass so the UI
 * makes a single request.
 */
public record AnalyticsResponse(
        Summary summary,
        List<TrendPoint> scoreTrend,
        List<NamedValue> dimensionAverages,
        List<NamedValue> weakestConcepts,
        List<NamedValue> strongestConcepts,
        List<NamedValue> sentimentSplit,
        List<SkillStat> skillPerformance,
        List<RoleStat> rolePerformance,
        List<QuestionStat> questionCalibration,
        List<FeedbackResponse> recentFeedback
) {
    public record Summary(
            long completedInterviews,
            long totalInterviews,
            long feedbackCount,
            Double averageScore,
            Double averageRating,
            Double completionRate,
            Double averageSecondsPerAnswer,
            Double positiveShare
    ) {
    }

    public record TrendPoint(String label, Double averageScore, long count) {
    }

    public record NamedValue(String name, double value, long count) {
    }

    public record SkillStat(String skill, long sessions, Double averageScore, Double averageRating) {
    }

    public record RoleStat(String role, long sessions, Double averageScore, Double averageRating) {
    }

    public record QuestionStat(
            Long questionId,
            String text,
            String role,
            String topic,
            String difficulty,
            long timesAsked,
            Double averageScore,
            String verdict
    ) {
    }
}
