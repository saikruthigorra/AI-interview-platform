package com.interviewiq.ai;

import com.interviewiq.domain.Question;

public interface AnswerEvaluator {

    EvaluationResult evaluate(Question question, String answerText);

    String engineName();
}
