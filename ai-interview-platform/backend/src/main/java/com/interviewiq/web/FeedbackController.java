package com.interviewiq.web;

import com.interviewiq.service.FeedbackService;
import com.interviewiq.web.dto.FeedbackRequest;
import com.interviewiq.web.dto.FeedbackResponse;
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
@RequestMapping("/api")
public class FeedbackController {

    private final FeedbackService feedback;

    public FeedbackController(FeedbackService feedback) {
        this.feedback = feedback;
    }

    /** Rate + comment on a completed interview (upsert — one review per session). */
    @PostMapping("/interviews/{id}/feedback")
    public FeedbackResponse submit(@PathVariable Long id, @Valid @RequestBody FeedbackRequest request) {
        return feedback.submit(id, request);
    }

    @GetMapping("/interviews/{id}/feedback")
    public FeedbackResponse forSession(@PathVariable Long id) {
        return feedback.forSession(id)
                .orElseThrow(() -> new NotFoundException("No review exists for interview " + id));
    }

    @DeleteMapping("/interviews/{id}/feedback")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable Long id) {
        feedback.deleteForSession(id);
    }

    /** Every review, newest first — powers the reviews list. */
    @GetMapping("/feedback")
    public List<FeedbackResponse> all() {
        return feedback.all();
    }
}
