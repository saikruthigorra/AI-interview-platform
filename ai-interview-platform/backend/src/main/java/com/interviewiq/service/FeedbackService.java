package com.interviewiq.service;

import com.interviewiq.ai.AiService;
import com.interviewiq.ai.SentimentAnalyzer;
import com.interviewiq.domain.InterviewFeedback;
import com.interviewiq.domain.InterviewSession;
import com.interviewiq.domain.SessionStatus;
import com.interviewiq.repo.InterviewFeedbackRepository;
import com.interviewiq.repo.InterviewSessionRepository;
import com.interviewiq.web.NotFoundException;
import com.interviewiq.web.dto.FeedbackRequest;
import com.interviewiq.web.dto.FeedbackResponse;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Reviews: students rate and comment on their interview; we derive sentiment. */
@Service
public class FeedbackService {

    private static final Logger log = LoggerFactory.getLogger(FeedbackService.class);

    private final InterviewFeedbackRepository feedback;
    private final InterviewSessionRepository sessions;
    private final AiService ai;

    public FeedbackService(InterviewFeedbackRepository feedback,
                           InterviewSessionRepository sessions,
                           AiService ai) {
        this.feedback = feedback;
        this.sessions = sessions;
        this.ai = ai;
    }

    /** Create or update the review for a session (one review per session). */
    @Transactional
    public FeedbackResponse submit(Long sessionId, FeedbackRequest request) {
        InterviewSession session = sessions.findById(sessionId)
                .orElseThrow(() -> new NotFoundException("Interview session " + sessionId + " not found"));
        if (session.getStatus() != SessionStatus.COMPLETED) {
            throw new IllegalStateException("You can review an interview once it has been completed");
        }
        String comment = request.comment() == null || request.comment().isBlank() ? null : request.comment().trim();
        SentimentAnalyzer.Result sentiment = ai.sentiment(request.rating(), comment);

        InterviewFeedback entity = feedback.findBySessionId(sessionId).orElseGet(InterviewFeedback::new);
        entity.setSession(session);
        entity.setRating(request.rating());
        entity.setComment(comment);
        entity.setSentiment(sentiment.label());
        entity.setSentimentScore(sentiment.score());
        if (entity.getSubmittedAt() == null) {
            entity.setSubmittedAt(Instant.now());
        }
        InterviewFeedback saved = feedback.save(entity);
        log.info("Review saved for session {} ({}★, {})", sessionId, saved.getRating(), saved.getSentiment());
        return FeedbackResponse.withTerms(saved, sentiment.positiveTerms(), sentiment.negativeTerms());
    }

    @Transactional(readOnly = true)
    public Optional<FeedbackResponse> forSession(Long sessionId) {
        return feedback.findBySessionId(sessionId).map(f -> {
            SentimentAnalyzer.Result result = ai.sentiment(f.getRating(), f.getComment());
            return FeedbackResponse.withTerms(f, result.positiveTerms(), result.negativeTerms());
        });
    }

    @Transactional(readOnly = true)
    public List<FeedbackResponse> all() {
        return feedback.findRecentWithSession().stream().map(FeedbackResponse::from).toList();
    }

    @Transactional
    public void deleteForSession(Long sessionId) {
        feedback.findBySessionId(sessionId).ifPresent(feedback::delete);
    }
}
