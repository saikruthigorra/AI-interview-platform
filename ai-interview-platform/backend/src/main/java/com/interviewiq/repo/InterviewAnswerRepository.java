package com.interviewiq.repo;

import com.interviewiq.domain.InterviewAnswer;
import com.interviewiq.domain.SessionStatus;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface InterviewAnswerRepository extends JpaRepository<InterviewAnswer, Long> {

    List<InterviewAnswer> findBySessionIdOrderByIdAsc(Long sessionId);

    long countBySessionId(Long sessionId);

    void deleteBySessionId(Long sessionId);

    /** Every graded answer belonging to a session in the given state — the analytics feed. */
    @Query("""
            select a from InterviewAnswer a
            join fetch a.session s
            join fetch a.question
            where s.status = :status
            """)
    List<InterviewAnswer> findBySessionStatusWithQuestion(@Param("status") SessionStatus status);
}
