package com.interviewiq.web.dto;

import jakarta.validation.constraints.NotBlank;

/** An answer submitted against an OA attempt. */
public record OaAnswerRequest(
        @NotBlank String answerText,
        Integer timeTakenSeconds
) {
}
