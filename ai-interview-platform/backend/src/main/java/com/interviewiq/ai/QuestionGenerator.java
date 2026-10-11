package com.interviewiq.ai;

import com.interviewiq.domain.Difficulty;
import com.interviewiq.domain.Level;
import com.interviewiq.domain.Question;
import java.util.List;
import java.util.Set;

public interface QuestionGenerator {

    /**
     * Produce up to {@code count} questions for a role/difficulty, tailored to the
     * student's {@code skills} and {@code level}, never reusing any id in
     * {@code excludeQuestionIds} (questions already answered by this student).
     */
    List<Question> generate(String role, Difficulty difficulty, int count, Set<Long> excludeQuestionIds,
                            List<String> skills, Level level);

    String engineName();
}
