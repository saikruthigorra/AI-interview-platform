package com.interviewiq.service;

import com.interviewiq.domain.InterviewSession;
import com.interviewiq.domain.QuestionSource;
import com.interviewiq.domain.SessionStatus;
import com.interviewiq.repo.CandidateRepository;
import com.interviewiq.repo.InterviewSessionRepository;
import com.interviewiq.repo.QuestionRepository;
import com.interviewiq.repo.SessionQuestionRepository;
import com.interviewiq.web.dto.SessionResponse;
import com.interviewiq.web.dto.StatsResponse;
import java.util.ArrayList;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class StatsService {

    private final CandidateRepository candidates;
    private final QuestionRepository questions;
    private final InterviewSessionRepository sessions;
    private final SessionQuestionRepository sessionQuestions;

    public StatsService(CandidateRepository candidates,
                        QuestionRepository questions,
                        InterviewSessionRepository sessions,
                        SessionQuestionRepository sessionQuestions) {
        this.candidates = candidates;
        this.questions = questions;
        this.sessions = sessions;
        this.sessionQuestions = sessionQuestions;
    }

    @Transactional(readOnly = true)
    public StatsResponse overview() {
        List<InterviewSession> completed = sessions.findCompleted(SessionStatus.COMPLETED);

        List<StatsResponse.RoleStat> byRole = sessions.scoreByRole(SessionStatus.COMPLETED).stream()
                .map(row -> new StatsResponse.RoleStat(
                        (String) row[0],
                        ((Number) row[1]).longValue(),
                        round(row[2])))
                .toList();

        List<StatsResponse.ScoreBand> bands = bands(completed);

        List<SessionResponse> recent = completed.stream()
                .limit(6)
                .map(s -> SessionResponse.from(s, sessionQuestions.countBySessionIdAndAnsweredTrue(s.getId())))
                .toList();

        long aiQuestions = questions.findAll().stream()
                .filter(q -> q.getSource() == QuestionSource.AI)
                .count();

        return new StatsResponse(
                candidates.count(),
                sessions.count(),
                sessions.countByStatus(SessionStatus.COMPLETED),
                sessions.countByStatus(SessionStatus.IN_PROGRESS),
                questions.count(),
                aiQuestions,
                round(sessions.averageScore(SessionStatus.COMPLETED)),
                byRole,
                bands,
                recent);
    }

    private static List<StatsResponse.ScoreBand> bands(List<InterviewSession> completed) {
        long[] buckets = new long[4]; // <50, 50-69, 70-84, 85+
        for (InterviewSession s : completed) {
            double score = s.getOverallScore() == null ? 0 : s.getOverallScore();
            if (score >= 85) {
                buckets[3]++;
            } else if (score >= 70) {
                buckets[2]++;
            } else if (score >= 50) {
                buckets[1]++;
            } else {
                buckets[0]++;
            }
        }
        List<StatsResponse.ScoreBand> out = new ArrayList<>();
        out.add(new StatsResponse.ScoreBand("< 50", buckets[0]));
        out.add(new StatsResponse.ScoreBand("50–69", buckets[1]));
        out.add(new StatsResponse.ScoreBand("70–84", buckets[2]));
        out.add(new StatsResponse.ScoreBand("85+", buckets[3]));
        return out;
    }

    private static Double round(Object value) {
        if (value == null) {
            return null;
        }
        double d = ((Number) value).doubleValue();
        return Math.round(d * 10) / 10.0;
    }
}
