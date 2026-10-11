package com.interviewiq.web.dto;

import com.interviewiq.domain.AssessmentQuestion;
import java.util.Arrays;
import java.util.List;

/** A question inside an assessment, as the recruiting side sees it (rubric included). */
public record AssessmentQuestionResponse(
        Long assessmentQuestionId,
        Long questionId,
        int position,
        String text,
        String topic,
        String difficulty,
        String source,
        List<String> skillTags,
        String expectedKeywords,
        String idealAnswer
) {
    public static AssessmentQuestionResponse from(AssessmentQuestion aq) {
        var q = aq.getQuestion();
        return new AssessmentQuestionResponse(
                aq.getId(),
                q.getId(),
                aq.getPosition(),
                q.getText(),
                q.getTopic(),
                q.getDifficulty().name(),
                q.getSource().name(),
                q.getSkillTags() == null ? List.of()
                        : Arrays.stream(q.getSkillTags().split(",")).map(String::trim)
                                .filter(s -> !s.isEmpty()).toList(),
                q.getExpectedKeywords(),
                q.getIdealAnswer());
    }
}
