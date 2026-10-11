package com.interviewiq.repo;

import com.interviewiq.domain.AssessmentQuestion;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AssessmentQuestionRepository extends JpaRepository<AssessmentQuestion, Long> {

    List<AssessmentQuestion> findByAssessmentIdOrderByPositionAsc(Long assessmentId);

    void deleteByAssessmentId(Long assessmentId);

    long countByAssessmentId(Long assessmentId);
}
