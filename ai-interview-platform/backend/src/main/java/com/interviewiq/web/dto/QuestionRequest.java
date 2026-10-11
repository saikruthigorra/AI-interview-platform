package com.interviewiq.web.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record QuestionRequest(
        @NotBlank String text,
        @NotBlank @Size(max = 120) String role,
        @Size(max = 120) String topic,
        String difficulty,
        String expectedKeywords,
        String idealAnswer,
        /** Comma-separated skill tags, e.g. "React,JavaScript". */
        String skillTags
) {
}
