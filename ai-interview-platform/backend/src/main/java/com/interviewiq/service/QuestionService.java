package com.interviewiq.service;

import com.interviewiq.domain.Difficulty;
import com.interviewiq.domain.Question;
import com.interviewiq.domain.QuestionSource;
import com.interviewiq.repo.QuestionRepository;
import com.interviewiq.repo.SessionQuestionRepository;
import com.interviewiq.web.NotFoundException;
import com.interviewiq.web.dto.QuestionRequest;
import com.interviewiq.web.dto.QuestionResponse;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class QuestionService {

    private final QuestionRepository questions;
    private final SessionQuestionRepository sessionQuestions;

    public QuestionService(QuestionRepository questions, SessionQuestionRepository sessionQuestions) {
        this.questions = questions;
        this.sessionQuestions = sessionQuestions;
    }

    @Transactional(readOnly = true)
    public List<QuestionResponse> search(String role, String difficulty, String search) {
        Difficulty diff = (difficulty == null || difficulty.isBlank()) ? null : Difficulty.from(difficulty);
        String roleFilter = (role == null || role.isBlank()) ? null : role.trim();
        String searchFilter = (search == null || search.isBlank()) ? null : search.trim();
        return questions.search(roleFilter, diff, searchFilter).stream().map(QuestionResponse::from).toList();
    }

    @Transactional(readOnly = true)
    public List<String> roles() {
        return questions.findDistinctRoles();
    }

    @Transactional(readOnly = true)
    public List<String> topics() {
        return questions.findDistinctTopics();
    }

    /** Skill tags available for tailoring sessions: bank tags + whatever is stored. */
    @Transactional(readOnly = true)
    public List<String> skills() {
        java.util.Set<String> out = new java.util.TreeSet<>(com.interviewiq.ai.QuestionBank.allSkillTags());
        for (Question q : questions.findAll()) {
            if (q.getSkillTags() != null) {
                for (String tag : q.getSkillTags().split(",")) {
                    String value = tag.trim();
                    if (!value.isEmpty()) {
                        out.add(value);
                    }
                }
            }
        }
        return new java.util.ArrayList<>(out);
    }

    @Transactional
    public QuestionResponse create(QuestionRequest req) {
        Question q = new Question(
                req.text().trim(),
                req.role().trim(),
                blankToNull(req.topic()),
                Difficulty.from(req.difficulty()),
                blankToNull(req.expectedKeywords()),
                blankToNull(req.idealAnswer()),
                QuestionSource.BANK,
                blankToNull(req.skillTags()));
        return QuestionResponse.from(questions.save(q));
    }

    @Transactional
    public void delete(Long id) {
        Question q = questions.findById(id)
                .orElseThrow(() -> new NotFoundException("Question " + id + " not found"));
        long used = sessionQuestions.countByQuestionId(id);
        if (used > 0) {
            throw new IllegalStateException("This question has been served in " + used
                    + " interview(s) and can't be deleted — retire it by editing instead.");
        }
        questions.delete(q);
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
