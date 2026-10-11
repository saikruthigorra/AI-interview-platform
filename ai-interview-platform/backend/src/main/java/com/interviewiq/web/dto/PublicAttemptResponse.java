package com.interviewiq.web.dto;

import java.time.Instant;
import java.util.List;

/**
 * State of a candidate's OA attempt. The {@code attemptToken} is their capability
 * to continue — they have no account. Per-answer scores, feedback and model
 * answers stay hidden until the attempt is submitted (see {@code completed}).
 */
public record PublicAttemptResponse(
        String attemptToken,
        Long sessionId,
        String assessmentTitle,
        String company,
        String candidateName,
        String status,
        boolean completed,
        Instant startedAt,
        Instant expiresAt,
        Integer durationMinutes,
        int passingScore,
        int answeredCount,
        int totalQuestions,
        /** Questions without rubric data; index matches answers' position. */
        List<QuestionView> questions,
        /** Answer bodies while in progress; full graded analysis once completed. */
        List<PublicAnswer> answers,
        /** Only present once completed. */
        Double overallScore,
        Boolean passed,
        String aiSummary,
        List<String> strengths,
        List<String> gaps,
        String engine
) {
    /** A question as the candidate may see it (no expected keywords / model answer). */
    public record QuestionView(Long sessionQuestionId, int position, Long questionId,
                               String text, String topic, String difficulty, boolean answered) {
    }

    /** An answer; {@code analysis} is null until the attempt is submitted. */
    public record PublicAnswer(
            int position,
            Long questionId,
            String questionText,
            String topic,
            String difficulty,
            String answerText,
            Instant answeredAt,
            AttemptAnswerResponse analysis
    ) {
    }
}
