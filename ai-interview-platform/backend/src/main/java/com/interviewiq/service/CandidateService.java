package com.interviewiq.service;

import com.interviewiq.domain.Candidate;
import com.interviewiq.domain.Level;
import com.interviewiq.repo.CandidateRepository;
import com.interviewiq.repo.InterviewFeedbackRepository;
import com.interviewiq.repo.InterviewSessionRepository;
import com.interviewiq.web.NotFoundException;
import com.interviewiq.web.dto.CandidateRequest;
import com.interviewiq.web.dto.CandidateResponse;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class CandidateService {

    private final CandidateRepository candidates;
    private final InterviewSessionRepository sessions;
    private final InterviewFeedbackRepository feedback;

    public CandidateService(CandidateRepository candidates, InterviewSessionRepository sessions,
                            InterviewFeedbackRepository feedback) {
        this.candidates = candidates;
        this.sessions = sessions;
        this.feedback = feedback;
    }

    @Transactional(readOnly = true)
    public List<CandidateResponse> list() {
        return candidates.findAllByOrderByCreatedAtDesc().stream()
                .map(c -> CandidateResponse.from(c, sessions.countByCandidateId(c.getId())))
                .toList();
    }

    @Transactional(readOnly = true)
    public CandidateResponse get(Long id) {
        Candidate c = require(id);
        return CandidateResponse.from(c, sessions.countByCandidateId(id));
    }

    @Transactional
    public CandidateResponse create(CandidateRequest req) {
        candidates.findByEmailIgnoreCase(req.email()).ifPresent(existing -> {
            throw new IllegalStateException("A candidate with email " + req.email() + " already exists");
        });
        Candidate saved = candidates.save(new Candidate(
                req.name().trim(), req.email().trim().toLowerCase(),
                blankToNull(req.headline()), blankToNull(req.targetRole()),
                blankToNull(req.skills()), blankToNull(req.education()), Level.from(req.level())));
        return CandidateResponse.from(saved, 0);
    }

    @Transactional
    public CandidateResponse update(Long id, CandidateRequest req) {
        Candidate c = require(id);
        candidates.findByEmailIgnoreCase(req.email())
                .filter(other -> !other.getId().equals(id))
                .ifPresent(other -> {
                    throw new IllegalStateException("A candidate with email " + req.email() + " already exists");
                });
        c.setName(req.name().trim());
        c.setEmail(req.email().trim().toLowerCase());
        c.setHeadline(blankToNull(req.headline()));
        c.setTargetRole(blankToNull(req.targetRole()));
        c.setSkills(blankToNull(req.skills()));
        c.setEducation(blankToNull(req.education()));
        c.setLevel(Level.from(req.level()));
        return CandidateResponse.from(candidates.save(c), sessions.countByCandidateId(id));
    }

    @Transactional
    public void delete(Long id) {
        Candidate c = require(id);
        // Remove dependent interview sessions first (cascades to answers/questions).
        sessions.findByCandidateIdOrderByStartedAtDesc(id).forEach(session -> {
            feedback.findBySessionId(session.getId()).ifPresent(feedback::delete);
            sessions.delete(session);
        });
        candidates.delete(c);
    }

    Candidate require(Long id) {
        return candidates.findById(id)
                .orElseThrow(() -> new NotFoundException("Candidate " + id + " not found"));
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
