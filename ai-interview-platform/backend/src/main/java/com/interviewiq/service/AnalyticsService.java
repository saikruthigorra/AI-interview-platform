package com.interviewiq.service;

import com.interviewiq.domain.InterviewAnswer;
import com.interviewiq.domain.InterviewFeedback;
import com.interviewiq.domain.InterviewSession;
import com.interviewiq.domain.SessionStatus;
import com.interviewiq.repo.InterviewAnswerRepository;
import com.interviewiq.repo.InterviewFeedbackRepository;
import com.interviewiq.repo.InterviewSessionRepository;
import com.interviewiq.repo.SessionQuestionRepository;
import com.interviewiq.web.dto.AnalyticsResponse;
import com.interviewiq.web.dto.FeedbackResponse;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Computes the analysis dashboard from three signals:
 *
 *   1. interview outcomes  — score trends over time, dimension averages, role & skill breakdowns;
 *   2. answer-level content — which concepts students consistently miss or nail
 *                             (the rubric keywords), plus question calibration;
 *   3. student reviews      — star ratings and comment sentiment.
 *
 * Everything is derived on read; the volumes here are small enough that
 * pre-aggregating would be premature.
 */
@Service
public class AnalyticsService {

    private static final DateTimeFormatter DAY = DateTimeFormatter.ofPattern("d MMM", Locale.ENGLISH);

    private final InterviewSessionRepository sessions;
    private final InterviewAnswerRepository answers;
    private final InterviewFeedbackRepository feedback;
    private final SessionQuestionRepository sessionQuestions;

    public AnalyticsService(InterviewSessionRepository sessions,
                            InterviewAnswerRepository answers,
                            InterviewFeedbackRepository feedback,
                            SessionQuestionRepository sessionQuestions) {
        this.sessions = sessions;
        this.answers = answers;
        this.feedback = feedback;
        this.sessionQuestions = sessionQuestions;
    }

    @Transactional(readOnly = true)
    public AnalyticsResponse dashboard() {
        List<InterviewSession> completed = sessions.findCompleted(SessionStatus.COMPLETED);
        List<InterviewAnswer> graded = answers.findBySessionStatusWithQuestion(SessionStatus.COMPLETED);
        List<InterviewFeedback> reviews = feedback.findRecentWithSession();

        return new AnalyticsResponse(
                summary(completed, graded, reviews),
                scoreTrend(completed),
                dimensionAverages(graded),
                weakestConcepts(graded, 12),
                strongestConcepts(graded, 12),
                sentimentSplit(reviews),
                skillPerformance(completed),
                rolePerformance(completed),
                questionCalibration(graded),
                recentFeedback(reviews, 10));
    }

    // ── Summary ──────────────────────────────────────────────────────────────

    private AnalyticsResponse.Summary summary(List<InterviewSession> completed,
                                               List<InterviewAnswer> graded,
                                               List<InterviewFeedback> reviews) {
        long total = sessions.count();
        long completedCount = completed.size();
        Double avgScore = completed.stream()
                .map(InterviewSession::getOverallScore)
                .filter(java.util.Objects::nonNull)
                .mapToDouble(Double::doubleValue)
                .average().stream().boxed().findFirst().orElse(null);

        Double avgRating = reviews.isEmpty() ? null
                : round(reviews.stream().mapToInt(InterviewFeedback::getRating).average().orElse(0));

        Double completionRate = total == 0 ? null : round(100.0 * completedCount / total);

        Double avgSeconds = graded.stream()
                .map(InterviewAnswer::getTimeTakenSeconds)
                .filter(java.util.Objects::nonNull)
                .mapToInt(Integer::intValue)
                .average().stream().boxed().findFirst().orElse(null);

        long positive = reviews.stream().filter(r -> "POSITIVE".equals(r.getSentiment())).count();
        Double positiveShare = reviews.isEmpty() ? null : round(100.0 * positive / reviews.size());

        return new AnalyticsResponse.Summary(completedCount, total, reviews.size(),
                round(avgScore), avgRating, completionRate, round(avgSeconds), positiveShare);
    }

    // ── Score trend ──────────────────────────────────────────────────────────

    private List<AnalyticsResponse.TrendPoint> scoreTrend(List<InterviewSession> completed) {
        // Keyed by LocalDate so ordering is chronological, not lexicographic.
        Map<LocalDate, List<Double>> byDay = new TreeMap<>();
        Map<LocalDate, Long> counts = new LinkedHashMap<>();
        ZoneId zone = ZoneId.systemDefault();
        for (InterviewSession s : completed) {
            if (s.getOverallScore() == null || s.getCompletedAt() == null) {
                continue;
            }
            LocalDate day = s.getCompletedAt().atZone(zone).toLocalDate();
            byDay.computeIfAbsent(day, k -> new ArrayList<>()).add(s.getOverallScore());
            counts.merge(day, 1L, Long::sum);
        }
        List<AnalyticsResponse.TrendPoint> points = new ArrayList<>();
        for (Map.Entry<LocalDate, List<Double>> entry : byDay.entrySet()) {
            points.add(new AnalyticsResponse.TrendPoint(
                    DAY.format(entry.getKey()),
                    round(entry.getValue().stream().mapToDouble(Double::doubleValue).average().orElse(0)),
                    counts.getOrDefault(entry.getKey(), 0L)));
        }
        return points;
    }

    // ── Dimension averages ───────────────────────────────────────────────────

    private List<AnalyticsResponse.NamedValue> dimensionAverages(List<InterviewAnswer> graded) {
        long n = graded.size();
        if (n == 0) {
            return List.of();
        }
        return List.of(
                new AnalyticsResponse.NamedValue("Technical coverage", round(avg(graded, InterviewAnswer::getTechnicalScore)), n),
                new AnalyticsResponse.NamedValue("Depth", round(avg(graded, InterviewAnswer::getDepthScore)), n),
                new AnalyticsResponse.NamedValue("Communication", round(avg(graded, InterviewAnswer::getCommunicationScore)), n),
                new AnalyticsResponse.NamedValue("Clarity", round(avg(graded, InterviewAnswer::getClarityScore)), n));
    }

    // ── Concept analysis (from the rubric keywords in each answer) ───────────

    private List<AnalyticsResponse.NamedValue> weakestConcepts(List<InterviewAnswer> graded, int limit) {
        Map<String, long[]> counts = new LinkedHashMap<>();   // name → [missed, seen]
        for (InterviewAnswer a : graded) {
            for (String concept : split(a.getMissingPoints())) {
                counts.computeIfAbsent(concept, k -> new long[2])[0]++;
            }
            for (String concept : split(a.getMissingPoints())) {
                counts.get(concept)[1]++;
            }
            for (String concept : split(a.getKeywordsMatched())) {
                counts.computeIfAbsent(concept, k -> new long[2])[1]++;
            }
        }
        return counts.entrySet().stream()
                .filter(e -> e.getValue()[0] > 0)
                .sorted(Map.Entry.<String, long[]>comparingByValue(
                        Comparator.comparingLong((long[] v) -> v[0]).reversed()))
                .limit(limit)
                .map(e -> new AnalyticsResponse.NamedValue(e.getKey(), e.getValue()[0], graded.size()))
                .toList();
    }

    private List<AnalyticsResponse.NamedValue> strongestConcepts(List<InterviewAnswer> graded, int limit) {
        Map<String, Long> counts = new LinkedHashMap<>();
        for (InterviewAnswer a : graded) {
            for (String concept : split(a.getKeywordsMatched())) {
                counts.merge(concept, 1L, Long::sum);
            }
        }
        return counts.entrySet().stream()
                .sorted(Map.Entry.<String, Long>comparingByValue().reversed())
                .limit(limit)
                .map(e -> new AnalyticsResponse.NamedValue(e.getKey(), e.getValue(), graded.size()))
                .toList();
    }

    // ── Sentiment ────────────────────────────────────────────────────────────

    private List<AnalyticsResponse.NamedValue> sentimentSplit(List<InterviewFeedback> reviews) {
        long positive = reviews.stream().filter(r -> "POSITIVE".equals(r.getSentiment())).count();
        long neutral = reviews.stream().filter(r -> "NEUTRAL".equals(r.getSentiment())).count();
        long negative = reviews.stream().filter(r -> "NEGATIVE".equals(r.getSentiment())).count();
        long total = Math.max(1, reviews.size());
        return List.of(
                new AnalyticsResponse.NamedValue("Positive", round(100.0 * positive / total), positive),
                new AnalyticsResponse.NamedValue("Neutral", round(100.0 * neutral / total), neutral),
                new AnalyticsResponse.NamedValue("Negative", round(100.0 * negative / total), negative));
    }

    // ── Skill / role performance ─────────────────────────────────────────────

    private List<AnalyticsResponse.SkillStat> skillPerformance(List<InterviewSession> completed) {
        Map<String, List<InterviewSession>> bySkill = new LinkedHashMap<>();
        Map<String, String> displayName = new LinkedHashMap<>();
        for (InterviewSession s : completed) {
            for (String skill : splitNames(s.getFocusSkills())) {
                String key = skill.toLowerCase(Locale.ROOT);
                displayName.putIfAbsent(key, skill);
                bySkill.computeIfAbsent(key, k -> new ArrayList<>()).add(s);
            }
        }
        return bySkill.entrySet().stream()
                .map(e -> new AnalyticsResponse.SkillStat(
                        displayName.getOrDefault(e.getKey(), e.getKey()),
                        e.getValue().size(),
                        round(e.getValue().stream().map(InterviewSession::getOverallScore)
                                .filter(java.util.Objects::nonNull).mapToDouble(Double::doubleValue)
                                .average().stream().boxed().findFirst().orElse(null)),
                        round(e.getValue().stream()
                                .map(s -> feedback.findBySessionId(s.getId()).map(f -> (double) f.getRating()).orElse(null))
                                .filter(java.util.Objects::nonNull).mapToDouble(Double::doubleValue)
                                .average().stream().boxed().findFirst().orElse(null))))
                .sorted(Comparator.comparingLong(AnalyticsResponse.SkillStat::sessions).reversed())
                .toList();
    }

    private List<AnalyticsResponse.RoleStat> rolePerformance(List<InterviewSession> completed) {
        Map<String, List<InterviewSession>> byRole = new LinkedHashMap<>();
        for (InterviewSession s : completed) {
            byRole.computeIfAbsent(s.getRole(), k -> new ArrayList<>()).add(s);
        }
        return byRole.entrySet().stream()
                .map(e -> new AnalyticsResponse.RoleStat(
                        e.getKey(),
                        e.getValue().size(),
                        round(e.getValue().stream().map(InterviewSession::getOverallScore)
                                .filter(java.util.Objects::nonNull).mapToDouble(Double::doubleValue)
                                .average().stream().boxed().findFirst().orElse(null)),
                        round(e.getValue().stream()
                                .map(s -> feedback.findBySessionId(s.getId()).map(f -> (double) f.getRating()).orElse(null))
                                .filter(java.util.Objects::nonNull).mapToDouble(Double::doubleValue)
                                .average().stream().boxed().findFirst().orElse(null))))
                .sorted(Comparator.comparingLong(AnalyticsResponse.RoleStat::sessions).reversed())
                .toList();
    }

    // ── Question calibration ─────────────────────────────────────────────────

    private List<AnalyticsResponse.QuestionStat> questionCalibration(List<InterviewAnswer> graded) {
        record Agg(List<Integer> scores) {
        }
        Map<Long, Agg> byQuestion = new LinkedHashMap<>();
        Map<Long, InterviewAnswer> sample = new LinkedHashMap<>();
        for (InterviewAnswer a : graded) {
            byQuestion.computeIfAbsent(a.getQuestion().getId(), k -> new Agg(new ArrayList<>()))
                    .scores().add(a.getScore());
            sample.putIfAbsent(a.getQuestion().getId(), a);
        }
        return byQuestion.entrySet().stream()
                .map(e -> {
                    InterviewAnswer a = sample.get(e.getKey());
                    double avgScore = e.getValue().scores().stream().mapToInt(Integer::intValue)
                            .average().orElse(0);
                    String verdict = verdict(avgScore, e.getValue().scores().size(), a.getQuestion().getDifficulty().name());
                    return new AnalyticsResponse.QuestionStat(
                            e.getKey(),
                            a.getQuestion().getText(),
                            a.getQuestion().getRole(),
                            a.getQuestion().getTopic(),
                            a.getQuestion().getDifficulty().name(),
                            e.getValue().scores().size(),
                            round(avgScore),
                            verdict);
                })
                .sorted(Comparator.comparingDouble(AnalyticsResponse.QuestionStat::averageScore))
                .toList();
    }

    private static String verdict(double average, long times, String difficulty) {
        if (times < 2) {
            return "Need more data";
        }
        boolean hard = "HARD".equals(difficulty);
        if (average < (hard ? 45 : 55)) {
            return "Too hard — consider rewording";
        }
        if (average > (hard ? 88 : 90)) {
            return "Too easy — add depth";
        }
        return "Well calibrated";
    }

    // ── Feedback feed ────────────────────────────────────────────────────────

    private List<FeedbackResponse> recentFeedback(List<InterviewFeedback> reviews, int limit) {
        return reviews.stream()
                .limit(limit)
                .map(FeedbackResponse::from)
                .toList();
    }

    // ── helpers ──────────────────────────────────────────────────────────────

    private static double avg(List<InterviewAnswer> answers,
                              java.util.function.Function<InterviewAnswer, Integer> field) {
        return answers.stream().map(field).filter(java.util.Objects::nonNull)
                .mapToInt(Integer::intValue).average().orElse(0);
    }

    /** Splits a comma list preserving original casing (skills, roles). */
    private static List<String> splitNames(String raw) {
        if (raw == null || raw.isBlank()) {
            return List.of();
        }
        List<String> out = new ArrayList<>();
        for (String part : raw.split(",")) {
            String value = part.trim();
            if (!value.isEmpty()) {
                out.add(value);
            }
        }
        return out;
    }

    private static List<String> split(String raw) {
        if (raw == null || raw.isBlank()) {
            return List.of();
        }
        List<String> out = new ArrayList<>();
        for (String part : raw.split("[\\n,;]")) {
            String value = part.trim();
            if (!value.isEmpty()) {
                out.add(value.toLowerCase(Locale.ROOT));
            }
        }
        return out;
    }

    private static Double round(Double value) {
        return value == null ? null : Math.round(value * 10) / 10.0;
    }

    private static Double round(double value) {
        return Math.round(value * 10) / 10.0;
    }

}
