package com.interviewiq.web.dto;

import com.interviewiq.domain.InterviewAnswer;
import java.time.Instant;
import java.util.Arrays;
import java.util.List;

public record AnswerView(
        Long id,
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
        Integer timeTakenSeconds,
        Instant answeredAt,
        String idealAnswer,
        boolean sessionCompleted
) {
    public static AnswerView from(InterviewAnswer a, boolean sessionCompleted) {
        return new AnswerView(
                a.getId(),
                a.getQuestion().getId(),
                a.getQuestion().getText(),
                a.getQuestion().getTopic(),
                a.getQuestion().getDifficulty().name(),
                a.getAnswerText(),
                a.getScore(),
                a.getTechnicalScore(),
                a.getDepthScore(),
                a.getCommunicationScore(),
                a.getClarityScore(),
                a.getFeedback(),
                lines(a.getStrengths()),
                lines(a.getImprovements()),
                lines(a.getKeywordsMatched()),
                lines(a.getMissingPoints()),
                a.getTimeTakenSeconds(),
                a.getAnsweredAt(),
                a.getQuestion().getIdealAnswer(),
                sessionCompleted);
    }

    static List<String> lines(String value) {
        if (value == null || value.isBlank()) {
            return List.of();
        }
        return Arrays.stream(value.split("\n")).map(String::trim).filter(v -> !v.isEmpty()).toList();
    }
}
