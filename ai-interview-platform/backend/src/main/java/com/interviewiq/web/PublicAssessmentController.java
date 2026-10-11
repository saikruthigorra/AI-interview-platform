package com.interviewiq.web;

import com.interviewiq.service.OaAttemptService;
import com.interviewiq.web.dto.AttemptStartRequest;
import com.interviewiq.web.dto.OaAnswerAckResponse;
import com.interviewiq.web.dto.OaAnswerRequest;
import com.interviewiq.web.dto.PublicAssessmentResponse;
import com.interviewiq.web.dto.PublicAttemptResponse;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * Public OA endpoints — no account needed. Candidates reach these through the
 * shared link; the opaque attempt token returned by {@code /attempts} is their
 * credential for everything after that.
 */
@RestController
@RequestMapping("/api/public")
public class PublicAssessmentController {

    private final OaAttemptService attempts;

    public PublicAssessmentController(OaAttemptService attempts) {
        this.attempts = attempts;
    }

    /** Landing page data for an OA link: title, company, duration, rules. */
    @GetMapping("/assessments/{code}")
    public PublicAssessmentResponse info(@PathVariable String code) {
        return attempts.publicInfo(code);
    }

    /** Start (or resume) an attempt with just a name and email. */
    @PostMapping("/assessments/{code}/attempts")
    @ResponseStatus(HttpStatus.CREATED)
    public PublicAttemptResponse start(@PathVariable String code, @Valid @RequestBody AttemptStartRequest request) {
        return attempts.start(code, request);
    }

    @GetMapping("/attempts/{token}")
    public PublicAttemptResponse detail(@PathVariable String token) {
        return attempts.detail(token);
    }

    /** Record an answer; the AI grades it server-side, but scores stay hidden until submit. */
    @PostMapping("/attempts/{token}/answers")
    public OaAnswerAckResponse answer(@PathVariable String token, @Valid @RequestBody OaAnswerRequest request) {
        return attempts.submitAnswer(token, request);
    }

    /** Submit the attempt (early, on time-up, or automatically on the last answer). */
    @PostMapping("/attempts/{token}/submit")
    public PublicAttemptResponse submit(@PathVariable String token) {
        return attempts.submit(token);
    }
}
