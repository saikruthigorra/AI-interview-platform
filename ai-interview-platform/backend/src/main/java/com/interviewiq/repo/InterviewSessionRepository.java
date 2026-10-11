package com.interviewiq.repo;

import com.interviewiq.domain.InterviewSession;
import com.interviewiq.domain.SessionStatus;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface InterviewSessionRepository extends JpaRepository<InterviewSession, Long> {

    List<InterviewSession> findAllByOrderByStartedAtDesc();

    List<InterviewSession> findByCandidateIdOrderByStartedAtDesc(Long candidateId);

    long countByStatus(SessionStatus status);

    long countByCandidateId(Long candidateId);

    @Query("select avg(s.overallScore) from InterviewSession s where s.status = :status and s.overallScore is not null")
    Double averageScore(@Param("status") SessionStatus status);

    @Query("""
            select s.role, count(s), avg(s.overallScore) from InterviewSession s
            where s.status = :status and s.overallScore is not null
            group by s.role order by count(s) desc
            """)
    List<Object[]> scoreByRole(@Param("status") SessionStatus status);

    @Query("""
            select s from InterviewSession s
            where s.status = :status and s.overallScore is not null
            order by s.startedAt desc
            """)
    List<InterviewSession> findCompleted(@Param("status") SessionStatus status);

    // ── OA attempts ──────────────────────────────────────────────────────────

    Optional<InterviewSession> findByAttemptToken(String attemptToken);

    List<InterviewSession> findByAssessmentIdOrderByStartedAtDesc(Long assessmentId);

    long countByAssessmentId(Long assessmentId);

    Optional<InterviewSession> findFirstByAssessmentIdAndCandidateIdOrderByStartedAtDesc(Long assessmentId,
                                                                                         Long candidateId);
}
