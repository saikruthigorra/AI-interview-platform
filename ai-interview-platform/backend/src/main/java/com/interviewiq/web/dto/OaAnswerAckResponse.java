package com.interviewiq.web.dto;

import java.time.Instant;

/**
 * Acknowledgement of a stored OA answer. Deliberately excludes the score and
 * feedback: the AI grades on submit (server-side), but the candidate only sees
 * the analysis once the attempt is finished.
 */
public record OaAnswerAckResponse(
        boolean accepted,
        int answeredCount,
        int totalQuestions,
        boolean completed,
        Instant expiresAt,
        String message
) {
}
