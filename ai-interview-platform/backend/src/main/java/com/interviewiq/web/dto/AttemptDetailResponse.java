package com.interviewiq.web.dto;

import java.util.List;

/** Full attempt analysis: the summary, every graded answer, and the AI debrief. */
public record AttemptDetailResponse(
        AttemptSummaryResponse attempt,
        String assessmentTitle,
        String company,
        String role,
        int passingScore,
        String engine,
        String aiSummary,
        List<String> strengths,
        List<String> gaps,
        List<AttemptAnswerResponse> answers
) {
}
