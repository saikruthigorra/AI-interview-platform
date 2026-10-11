package com.interviewiq.web.dto;

import com.interviewiq.domain.Question;
import java.time.Instant;

public record QuestionResponse(
        Long id,
        String text,
        String role,
        String topic,
        String difficulty,
        String source,
        String expectedKeywords,
        String idealAnswer,
        String skillTags,
        Instant createdAt
) {
    public static QuestionResponse from(Question q) {
        return new QuestionResponse(q.getId(), q.getText(), q.getRole(), q.getTopic(),
                q.getDifficulty().name(), q.getSource().name(), q.getExpectedKeywords(),
                q.getIdealAnswer(), q.getSkillTags(), q.getCreatedAt());
    }
}
