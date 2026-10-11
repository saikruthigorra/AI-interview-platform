package com.interviewiq.web.dto;

import jakarta.validation.constraints.NotBlank;

public record AnswerRequest(
        @NotBlank String answerText,
        Integer timeTakenSeconds
) {
}
