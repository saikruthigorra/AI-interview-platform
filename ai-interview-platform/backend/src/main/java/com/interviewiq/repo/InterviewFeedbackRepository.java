package com.interviewiq.repo;

import com.interviewiq.domain.InterviewFeedback;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface InterviewFeedbackRepository extends JpaRepository<InterviewFeedback, Long> {

    Optional<InterviewFeedback> findBySessionId(Long sessionId);

    List<InterviewFeedback> findAllByOrderBySubmittedAtDesc();

    long countBySentiment(String sentiment);

    @Query("select avg(f.rating) from InterviewFeedback f")
    Double averageRating();

    /** Most recent reviews with their session + candidate loaded for the reviews feed. */
    @Query("""
            select f from InterviewFeedback f
            join fetch f.session s
            join fetch s.candidate
            order by f.submittedAt desc
            """)
    List<InterviewFeedback> findRecentWithSession();

    @Query("select avg(f.rating) from InterviewFeedback f join f.session s where lower(s.role) = lower(:role)")
    Double averageRatingForRole(String role);

    void deleteBySessionId(Long sessionId);
}
