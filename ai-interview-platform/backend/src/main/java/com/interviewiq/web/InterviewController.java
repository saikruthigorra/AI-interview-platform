package com.interviewiq.web;

import com.interviewiq.service.InterviewService;
import com.interviewiq.web.dto.AnswerRequest;
import com.interviewiq.web.dto.AnswerView;
import com.interviewiq.web.dto.SessionDetailResponse;
import com.interviewiq.web.dto.SessionResponse;
import com.interviewiq.web.dto.SessionStartRequest;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/interviews")
public class InterviewController {

    private final InterviewService service;

    public InterviewController(InterviewService service) {
        this.service = service;
    }

    @GetMapping
    public List<SessionResponse> list() {
        return service.list();
    }

    /** Start (or resume) an interview. */
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public SessionDetailResponse start(@Valid @RequestBody SessionStartRequest request) {
        return service.start(request);
    }

    @GetMapping("/{id}")
    public SessionDetailResponse detail(@PathVariable Long id) {
        return service.detail(id);
    }

    /** Submit the answer to the next unanswered question; graded on the spot. */
    @PostMapping("/{id}/answers")
    public AnswerView submitAnswer(@PathVariable Long id, @Valid @RequestBody AnswerRequest request) {
        return service.submitAnswer(id, request);
    }

    /** Finalise the session and produce the report (idempotent). */
    @PostMapping("/{id}/finish")
    public SessionDetailResponse finish(@PathVariable Long id) {
        return service.finish(id);
    }

    /** The scored report — same payload, semantic alias for the UI. */
    @GetMapping("/{id}/report")
    public SessionDetailResponse report(@PathVariable Long id) {
        return service.detail(id);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable Long id) {
        service.delete(id);
    }
}
