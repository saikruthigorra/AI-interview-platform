package com.interviewiq.web;

import com.interviewiq.service.AssessmentService;
import com.interviewiq.web.dto.AssessmentAnalyticsResponse;
import com.interviewiq.web.dto.AssessmentQuestionResponse;
import com.interviewiq.web.dto.AssessmentRequest;
import com.interviewiq.web.dto.AssessmentResponse;
import com.interviewiq.web.dto.AttemptDetailResponse;
import com.interviewiq.web.dto.AttemptSummaryResponse;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * Recruiter console for hosting online assessments. RECRUITER and ADMIN only —
 * an owner can only touch their own assessments (admins see everything).
 */
@RestController
@RequestMapping("/api/assessments")
@PreAuthorize("hasAnyRole('RECRUITER','ADMIN')")
public class AssessmentController {

    private final AssessmentService assessments;

    public AssessmentController(AssessmentService assessments) {
        this.assessments = assessments;
    }

    @GetMapping
    public List<AssessmentResponse> list(Authentication auth) {
        return assessments.list(auth.getName());
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public AssessmentResponse create(@Valid @RequestBody AssessmentRequest request, Authentication auth) {
        return assessments.create(request, auth.getName());
    }

    @GetMapping("/{id}")
    public AssessmentResponse get(@PathVariable Long id, Authentication auth) {
        return assessments.get(id, auth.getName());
    }

    @PutMapping("/{id}")
    public AssessmentResponse update(@PathVariable Long id, @Valid @RequestBody AssessmentRequest request,
                                     Authentication auth) {
        return assessments.update(id, request, auth.getName());
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable Long id, Authentication auth) {
        assessments.delete(id, auth.getName());
    }

    /** AI-generates (or regenerates) the question set during the draft phase. */
    @PostMapping("/{id}/generate")
    public List<AssessmentQuestionResponse> generate(@PathVariable Long id, Authentication auth) {
        return assessments.generate(id, auth.getName());
    }

    @GetMapping("/{id}/questions")
    public List<AssessmentQuestionResponse> questions(@PathVariable Long id, Authentication auth) {
        return assessments.questionSet(id, auth.getName());
    }

    @DeleteMapping("/{id}/questions/{assessmentQuestionId}")
    public List<AssessmentQuestionResponse> removeQuestion(@PathVariable Long id,
                                                           @PathVariable Long assessmentQuestionId,
                                                           Authentication auth) {
        return assessments.removeQuestion(id, assessmentQuestionId, auth.getName());
    }

    /** Makes the OA shareable — returns the access code and share path. */
    @PostMapping("/{id}/publish")
    public AssessmentResponse publish(@PathVariable Long id, Authentication auth) {
        return assessments.publish(id, auth.getName());
    }

    @PostMapping("/{id}/close")
    public AssessmentResponse close(@PathVariable Long id, Authentication auth) {
        return assessments.close(id, auth.getName());
    }

    @GetMapping("/{id}/attempts")
    public List<AttemptSummaryResponse> attempts(@PathVariable Long id, Authentication auth) {
        return assessments.attempts(id, auth.getName());
    }

    /** Full AI analysis of one candidate's attempt. */
    @GetMapping("/{id}/attempts/{sessionId}")
    public AttemptDetailResponse attempt(@PathVariable Long id, @PathVariable Long sessionId, Authentication auth) {
        return assessments.attemptDetail(id, sessionId, auth.getName());
    }

    /** Cohort analysis: distribution, per-question performance, concepts, leaderboard. */
    @GetMapping("/{id}/analytics")
    public AssessmentAnalyticsResponse analytics(@PathVariable Long id, Authentication auth) {
        return assessments.analytics(id, auth.getName());
    }
}
