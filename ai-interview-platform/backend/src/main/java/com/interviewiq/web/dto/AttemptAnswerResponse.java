package com.interviewiq.web.dto;

import com.interviewiq.domain.InterviewAnswer;
import java.time.Instant;
import java.util.Arrays;
import java.util.List;

/** A graded answer with full AI analysis — shown to recruiters, and to the candidate after submission. */
public record AttemptAnswerResponse(
        Long answerId,
        int position,
        Long questionId,
        String questionText,
        String topic,
        String difficulty,
        String answerText,
        int score,
        Integer technicalScore,
        Integer depthScore,
        Integer communicationScore,
        Integer clarityScore,
        String feedback,
        List<String> strengths,
        List<String> improvements,
        List<String> keywordsMatched,
        List<String> missingPoints,
        String idealAnswer,
        Integer timeTakenSeconds,
        Instant answeredAt
) {
    public static AttemptAnswerResponse from(InterviewAnswer a, int position) {
        var q = a.getQuestion();
        return new AttemptAnswerResponse(
                a.getId(), position, q.getId(), q.getText(), q.getTopic(), q.getDifficulty().name(),
                a.getAnswerText(), a.getScore(), a.getTechnicalScore(), a.getDepthScore(),
                a.getCommunicationScore(), a.getClarityScore(), a.getFeedback(),
                lines(a.getStrengths()), lines(a.getImprovements()),
                lines(a.getKeywordsMatched()), lines(a.getMissingPoints()),
                q.getIdealAnswer(), a.getTimeTakenSeconds(), a.getAnsweredAt());
    }

    static List<String> lines(String value) {
        if (value == null || value.isBlank()) {
            return List.of();
        }
        return Arrays.stream(value.split("\n")).map(String::trim).filter(v -> !v.isEmpty()).toList();
    }
}
