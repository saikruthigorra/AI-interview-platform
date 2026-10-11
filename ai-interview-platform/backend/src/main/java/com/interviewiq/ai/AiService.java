package com.interviewiq.ai;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.interviewiq.config.AppProperties;
import com.interviewiq.domain.Difficulty;
import com.interviewiq.domain.InterviewAnswer;
import com.interviewiq.domain.Level;
import com.interviewiq.domain.Question;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * Single front door to the AI capabilities.
 *
 * If a remote model is configured ({@code APP_AI_API_KEY} + provider) it is used
 * first and the heuristic engine acts as the safety net; otherwise everything is
 * handled offline so the product works with zero configuration.
 */
@Service
public class AiService {

    private static final Logger log = LoggerFactory.getLogger(AiService.class);

    private final AppProperties props;
    private final HeuristicQuestionGenerator heuristicQuestions;
    private final LlmQuestionGenerator llmQuestions;
    private final HeuristicAnswerEvaluator heuristicEvaluator;
    private final LlmAnswerEvaluator llmEvaluator;
    private final LlmClient llmClient;
    private final ObjectMapper mapper;
    private final SentimentAnalyzer sentimentAnalyzer;

    public AiService(AppProperties props,
                     HeuristicQuestionGenerator heuristicQuestions,
                     LlmQuestionGenerator llmQuestions,
                     HeuristicAnswerEvaluator heuristicEvaluator,
                     LlmAnswerEvaluator llmEvaluator,
                     LlmClient llmClient,
                     ObjectMapper mapper,
                     SentimentAnalyzer sentimentAnalyzer) {
        this.props = props;
        this.heuristicQuestions = heuristicQuestions;
        this.llmQuestions = llmQuestions;
        this.heuristicEvaluator = heuristicEvaluator;
        this.llmEvaluator = llmEvaluator;
        this.llmClient = llmClient;
        this.mapper = mapper;
        this.sentimentAnalyzer = sentimentAnalyzer;
    }

    // ── Review sentiment ─────────────────────────────────────────────────────

    public SentimentAnalyzer.Result sentiment(Integer rating, String comment) {
        if (remoteEnabled() && comment != null && !comment.isBlank()) {
            try {
                return llmSentiment(rating, comment);
            } catch (Exception ex) {
                log.warn("LLM sentiment analysis failed ({}), using the lexical analyser", ex.getMessage());
            }
        }
        return sentimentAnalyzer.analyze(rating, comment);
    }

    private SentimentAnalyzer.Result llmSentiment(Integer rating, String comment) {
        String system = "You classify student reviews of interview sessions. Reply with JSON only.";
        String user = """
                Review rating: %s/5
                Review text: "%s"

                Return JSON exactly in this shape:
                {"sentiment":"POSITIVE|NEUTRAL|NEGATIVE","score":0,"positiveTerms":["..."],"negativeTerms":["..."]}
                "score" is 0-100 where 50 is neutral.
                """.formatted(rating == null ? "?" : rating, comment.replace("\"", "'"));

        com.fasterxml.jackson.databind.JsonNode root;
        try {
            root = mapper.readTree(LlmClient.extractJson(llmClient.chat(system, user)));
        } catch (com.fasterxml.jackson.core.JsonProcessingException ex) {
            throw new IllegalStateException("Could not parse the model's sentiment JSON: " + ex.getMessage(), ex);
        }
        int score = Math.max(0, Math.min(100, root.path("score").asInt(50)));
        String label = root.path("sentiment").asText(score >= 65 ? "POSITIVE" : score >= 40 ? "NEUTRAL" : "NEGATIVE");
        return new SentimentAnalyzer.Result(label.toUpperCase(java.util.Locale.ROOT), score,
                jsonStrings(root.path("positiveTerms")), jsonStrings(root.path("negativeTerms")));
    }

    public boolean remoteEnabled() {
        return props.getAi().remoteEnabled();
    }

    public String engineName() {
        return remoteEnabled() ? props.getAi().getModel() : "heuristic";
    }

    // ── Question generation ──────────────────────────────────────────────────

    public List<Question> generateQuestions(String role, Difficulty difficulty, int count, Set<Long> excludeIds,
                                            List<String> skills, Level level) {
        List<Question> out = new ArrayList<>();
        if (remoteEnabled()) {
            try {
                out.addAll(llmQuestions.generate(role, difficulty, count, excludeIds, skills, level));
            } catch (Exception ex) {
                log.warn("LLM question generation failed ({}), using question bank", ex.getMessage());
            }
        }
        if (out.size() < count) {
            Set<String> seen = new LinkedHashSet<>();
            out.forEach(q -> seen.add(normalize(q.getText())));
            List<Question> bank = heuristicQuestions.generate(role, difficulty, count * 2, excludeIds, skills, level);
            for (Question q : bank) {
                if (out.size() >= count) {
                    break;
                }
                if (seen.add(normalize(q.getText()))) {
                    out.add(q);
                }
            }
        }
        return out.size() > count ? out.subList(0, count) : out;
    }

    // ── Answer evaluation ────────────────────────────────────────────────────

    public EvaluationResult evaluate(Question question, String answerText) {
        if (remoteEnabled()) {
            try {
                return llmEvaluator.evaluate(question, answerText);
            } catch (Exception ex) {
                log.warn("LLM evaluation failed ({}), falling back to the heuristic grader", ex.getMessage());
            }
        }
        return heuristicEvaluator.evaluate(question, answerText);
    }

    // ── Session rollup ───────────────────────────────────────────────────────

    public SessionSummary summarize(String role, List<InterviewAnswer> answers, double overallScore) {
        if (remoteEnabled()) {
            try {
                return llmSummary(role, answers, overallScore);
            } catch (Exception ex) {
                log.warn("LLM summary failed ({}), using derived summary", ex.getMessage());
            }
        }
        return derivedSummary(role, answers, overallScore);
    }

    private SessionSummary llmSummary(String role, List<InterviewAnswer> answers, double overallScore) {
        StringBuilder transcript = new StringBuilder();
        for (InterviewAnswer a : answers) {
            transcript.append("Q: ").append(a.getQuestion().getText()).append('\n')
                    .append("A: ").append(truncate(a.getAnswerText(), 700)).append('\n')
                    .append("Score: ").append(a.getScore()).append("/100\n\n");
        }
        String system = "You are a hiring panel lead writing a concise interview debrief. Reply with JSON only.";
        String user = """
                A candidate interviewed for a "%s" role and scored %.1f/100 overall.
                Transcript:
                %s
                Write a debrief. Return JSON exactly in this shape:
                {"summary":"3-5 sentence assessment of readiness and how they think",
                 "strengths":["3-5 specific strengths"],"gaps":["3-5 specific gaps or risks"]}
                """.formatted(role, overallScore, truncate(transcript.toString(), 6000));

        com.fasterxml.jackson.databind.JsonNode root;
        try {
            root = mapper.readTree(LlmClient.extractJson(llmClient.chat(system, user)));
        } catch (com.fasterxml.jackson.core.JsonProcessingException ex) {
            throw new IllegalStateException("Could not parse the LLM debrief: " + ex.getMessage(), ex);
        }
        return new SessionSummary(
                root.path("summary").asText("Interview complete."),
                jsonStrings(root.path("strengths")),
                jsonStrings(root.path("gaps")));
    }

    /** Builds a debrief straight from the per-answer scores — no model needed. */
    private SessionSummary derivedSummary(String role, List<InterviewAnswer> answers, double overallScore) {
        int n = answers.size();
        double tech = avg(answers, InterviewAnswer::getTechnicalScore);
        double depth = avg(answers, InterviewAnswer::getDepthScore);
        double comm = avg(answers, InterviewAnswer::getCommunicationScore);
        double clarity = avg(answers, InterviewAnswer::getClarityScore);

        Map<String, Integer> missing = new HashMap<>();
        Map<String, Integer> matched = new HashMap<>();
        for (InterviewAnswer a : answers) {
            splitCsv(a.getMissingPoints()).forEach(k -> missing.merge(k, 1, Integer::sum));
            splitCsv(a.getKeywordsMatched()).forEach(k -> matched.merge(k, 1, Integer::sum));
        }

        String band = overallScore >= 80 ? "a strong hire signal"
                : overallScore >= 65 ? "a hire signal with some risk"
                : overallScore >= 50 ? "a borderline profile worth a follow-up round"
                : "not yet ready for this level";
        String dimension = tech >= depth && tech >= comm && tech >= clarity ? "technical coverage"
                : depth >= comm && depth >= clarity ? "depth of reasoning"
                : comm >= clarity ? "communication" : "clarity";

        String summary = ("Across %d answered question%s for the %s role, the candidate averaged %.1f/100 — %s. "
                + "Their strongest dimension was %s (%.0f/100); communication scored %.0f/100 and depth %.0f/100. "
                + "Concept coverage is the main lever: recurring gaps (%s) appeared in multiple answers, "
                + "so a targeted revision round on those topics should move the needle fastest.")
                .formatted(n, n == 1 ? "" : "s", role, overallScore, band, dimension,
                        Math.max(Math.max(tech, depth), Math.max(comm, clarity)),
                        comm, depth, joinTop(missing, 3, "none recurring"));

        List<String> strengths = new ArrayList<>();
        addDimension(strengths, "Technical concept coverage", tech);
        addDimension(strengths, "Depth of reasoning", depth);
        addDimension(strengths, "Structured communication", comm);
        addDimension(strengths, "Clarity of expression", clarity);
        if (!matched.isEmpty()) {
            strengths.add("Repeatedly hit key concepts such as " + joinTop(matched, 4, ""));
        }
        if (strengths.isEmpty()) {
            double best = Math.max(Math.max(tech, depth), Math.max(comm, clarity));
            String bestLabel = best == tech ? "technical concept coverage"
                    : best == depth ? "depth of reasoning"
                    : best == comm ? "structured communication" : "clarity of expression";
            strengths.add("Answered all " + n + " question" + (n == 1 ? "" : "s")
                    + "; best dimension was " + bestLabel + " (" + Math.round(best) + "/100)");
            if (!matched.isEmpty()) {
                strengths.add("Got some concepts right: " + joinTop(matched, 4, ""));
            }
        }

        List<String> gaps = new ArrayList<>();
        if (!missing.isEmpty()) {
            gaps.add("Concepts to revise: " + joinTop(missing, 5, ""));
        }
        weakestDimension(gaps, tech, "technical coverage");
        weakestDimension(gaps, depth, "depth of reasoning");
        weakestDimension(gaps, comm, "structured communication");
        weakestDimension(gaps, clarity, "clarity of expression");
        if (gaps.isEmpty()) {
            gaps.add("Keep polishing: rehearse concise, example-backed answers under time pressure");
        }
        return new SessionSummary(summary, strengths, gaps);
    }

    private void addDimension(List<String> out, String label, double score) {
        if (score >= 70) {
            out.add(label + " — averaged " + Math.round(score) + "/100");
        }
    }

    private void weakestDimension(List<String> out, double score, String label) {
        if (score > 0 && score < 62) {
            out.add(label.substring(0, 1).toUpperCase(Locale.ROOT) + label.substring(1)
                    + " lagged the rest of the session (" + Math.round(score) + "/100)");
        }
    }

    private static double avg(List<InterviewAnswer> answers, java.util.function.Function<InterviewAnswer, Integer> f) {
        return answers.stream()
                .map(f)
                .filter(java.util.Objects::nonNull)
                .mapToInt(Integer::intValue)
                .average()
                .orElse(0);
    }

    private static List<String> splitCsv(String raw) {
        if (raw == null || raw.isBlank()) {
            return List.of();
        }
        List<String> out = new ArrayList<>();
        for (String part : raw.split("[\\n,;]")) {
            String v = part.trim();
            if (!v.isEmpty()) {
                out.add(v);
            }
        }
        return out;
    }

    private static String joinTop(Map<String, Integer> counts, int max, String fallback) {
        String joined = counts.entrySet().stream()
                .sorted(Map.Entry.<String, Integer>comparingByValue(Comparator.reverseOrder()))
                .limit(max)
                .map(Map.Entry::getKey)
                .reduce((a, b) -> a + ", " + b)
                .orElse("");
        return joined.isEmpty() ? fallback : joined;
    }

    private static List<String> jsonStrings(com.fasterxml.jackson.databind.JsonNode node) {
        List<String> out = new ArrayList<>();
        if (node != null && node.isArray()) {
            node.forEach(item -> {
                String v = item.asText("").trim();
                if (!v.isEmpty()) {
                    out.add(v);
                }
            });
        }
        return out;
    }

    private static String normalize(String text) {
        return text == null ? "" : text.toLowerCase(Locale.ROOT).replaceAll("\\s+", " ").trim();
    }

    private static String truncate(String text, int max) {
        if (text == null) {
            return "";
        }
        return text.length() <= max ? text : text.substring(0, max) + "…";
    }
}
