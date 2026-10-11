package com.interviewiq.repo;

import com.interviewiq.domain.Candidate;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CandidateRepository extends JpaRepository<Candidate, Long> {

    Optional<Candidate> findByEmailIgnoreCase(String email);

    List<Candidate> findAllByOrderByCreatedAtDesc();
}
