package com.interviewiq.repo;

import com.interviewiq.domain.Assessment;
import com.interviewiq.domain.AssessmentStatus;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AssessmentRepository extends JpaRepository<Assessment, Long> {

    List<Assessment> findByOwnerIdOrderByCreatedAtDesc(Long ownerId);

    List<Assessment> findAllByOrderByCreatedAtDesc();

    Optional<Assessment> findByAccessCodeIgnoreCase(String accessCode);

    boolean existsByAccessCodeIgnoreCase(String accessCode);

    long countByOwnerIdAndStatus(Long ownerId, AssessmentStatus status);
}
