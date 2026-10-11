package com.interviewiq.web.dto;

import com.interviewiq.domain.SessionQuestion;

/** A question as served inside an interview (no rubric leaks before answering). */
public record QuestionView(
        Long sessionQuestionId,
        int position,
        Long questionId,
        String text,
        String topic,
        String difficulty,
        boolean answered
) {
    public static QuestionView from(SessionQuestion sq) {
        return new QuestionView(
                sq.getId(),
                sq.getPosition(),
                sq.getQuestion().getId(),
                sq.getQuestion().getText(),
                sq.getQuestion().getTopic(),
                sq.getQuestion().getDifficulty().name(),
                sq.isAnswered());
    }
}
