package com.interviewiq.web.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import java.util.List;

/** Create/update payload for a recruiter-hosted assessment (OA). */
public record AssessmentRequest(
        @NotBlank @Size(max = 160) String title,
        @Size(max = 120) String company,
        @Size(max = 2000) String description,
        @NotBlank @Size(max = 120) String role,
        List<@Size(max = 60) String> skills,
        String difficulty,
        String level,
        @Min(3) @Max(30) Integer questionCount,
        @Min(5) @Max(240) Integer durationMinutes,
        @Min(0) @Max(100) Integer passingScore,
        /** Optional ISO-8601 instant; no new attempts are accepted after it. */
        Instant deadline
) {
}
