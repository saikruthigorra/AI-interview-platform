package com.interviewiq.web.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record SessionStartRequest(
        @NotNull Long candidateId,
        @NotBlank @Size(max = 120) String role,
        String difficulty,
        Integer questionCount,
        /** Optional override of the student's stored skills for this session. */
        @Size(max = 500) String skills,
        /** Optional override: STUDENT | GRADUATE | PROFESSIONAL */
        String level
) {
}
