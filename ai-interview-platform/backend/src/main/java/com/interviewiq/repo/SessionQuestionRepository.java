package com.interviewiq.repo;

import com.interviewiq.domain.SessionQuestion;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SessionQuestionRepository extends JpaRepository<SessionQuestion, Long> {

    List<SessionQuestion> findBySessionIdOrderByPositionAsc(Long sessionId);

    Optional<SessionQuestion> findFirstBySessionIdAndAnsweredFalseOrderByPositionAsc(Long sessionId);

    long countBySessionIdAndAnsweredTrue(Long sessionId);

    void deleteBySessionId(Long sessionId);

    long countByQuestionId(Long questionId);
}
