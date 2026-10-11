package com.interviewiq.web.dto;

import com.interviewiq.domain.InterviewFeedback;
import java.time.Instant;
import java.util.List;

public record FeedbackResponse(
        Long id,
        Long sessionId,
        String candidateName,
        String role,
        Integer overallScore,
        int rating,
        String comment,
        String sentiment,
        Integer sentimentScore,
        List<String> positiveTerms,
        List<String> negativeTerms,
        Instant submittedAt
) {
    public static FeedbackResponse from(InterviewFeedback f) {
        return new FeedbackResponse(
                f.getId(),
                f.getSession().getId(),
                f.getSession().getCandidate().getName(),
                f.getSession().getRole(),
                f.getSession().getOverallScore() == null ? null : f.getSession().getOverallScore().intValue(),
                f.getRating(),
                f.getComment(),
                f.getSentiment(),
                f.getSentimentScore(),
                List.of(),
                List.of(),
                f.getSubmittedAt());
    }

    /** Same payload plus the terms that drove the sentiment score (shown in the UI tooltip). */
    public static FeedbackResponse withTerms(InterviewFeedback f, List<String> positive, List<String> negative) {
        FeedbackResponse base = from(f);
        return new FeedbackResponse(base.id(), base.sessionId(), base.candidateName(), base.role(),
                base.overallScore(), base.rating(), base.comment(), base.sentiment(), base.sentimentScore(),
                positive, negative, base.submittedAt());
    }
}
