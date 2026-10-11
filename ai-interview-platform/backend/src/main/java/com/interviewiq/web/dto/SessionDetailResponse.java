package com.interviewiq.web.dto;

import java.util.List;

public record SessionDetailResponse(
        SessionResponse session,
        List<QuestionView> questions,
        List<AnswerView> answers
) {
}
