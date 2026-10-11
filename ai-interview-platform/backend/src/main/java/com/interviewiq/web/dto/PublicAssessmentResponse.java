package com.interviewiq.web.dto;

import java.time.Instant;
import java.util.List;

/** What a candidate sees on the OA landing page — no questions, no rubric. */
public record PublicAssessmentResponse(
        String code,
        String title,
        String company,
        String description,
        String role,
        List<String> skills,
        String difficulty,
        int questionCount,
        int durationMinutes,
        int passingScore,
        Instant deadline,
        /** OPEN | CLOSED | NOT_FOUND handled via 404; this is the takeable flag. */
        boolean openToAttempts,
        String reason
) {
}
